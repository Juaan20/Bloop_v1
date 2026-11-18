package com.bloop.pos.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.inventory.service.StockService;
import com.bloop.pos.dto.request.PosSaleRequest;
import com.bloop.pos.dto.request.VoidSaleRequest;
import com.bloop.pos.dto.response.PosSaleResponse;
import com.bloop.pos.entity.CashSession;
import com.bloop.pos.entity.PaymentMethod;
import com.bloop.pos.entity.PosSale;
import com.bloop.pos.entity.PosSaleItem;
import com.bloop.pos.entity.PosSaleStatus;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.ProductVariant;
import com.bloop.pos.mapper.PosMapper;
import com.bloop.pos.repository.CashSessionRepository;
import com.bloop.pos.repository.PosSaleRepository;
import com.bloop.pos.repository.ProductVariantRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.ForbiddenException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.security.Permissions;
import com.bloop.shared.util.SecureTokens;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import com.bloop.users.service.UserNames;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ventas del TPV.
 *
 * Una venta, dentro de UNA transaccion:
 * 1. Idempotencia: la misma Idempotency-Key en el mismo terminal devuelve la venta ya hecha (doble toque).
 * 2. Bloquea la caja abierta del terminal (SELECT ... FOR UPDATE): ventas y cierre de una caja van de una en una.
 * 3. Calcula importes con los precios actuales y aplica el descuento autorizado por un supervisor.
 * 4. Descuenta las existencias del almacen del terminal con UPDATE atomicos (409 si falta stock: no se cobra).
 */
@Service
@RequiredArgsConstructor
public class PosSaleService {

    static final int MAX_UNITS_PER_SALE = 200;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,80}");

    private final PosSaleRepository saleRepository;
    private final CashSessionRepository cashSessionRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CashSessionService cashSessionService;
    private final PosTerminalAccess terminalAccess;
    private final StockService stockService;
    private final PasswordEncoder passwordEncoder;
    private final UserNames userNames;
    private final PosMapper posMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public PosSaleResponse sell(UUID terminalId, PosSaleRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("Cabecera Idempotency-Key ausente o no valida");
        }
        PosTerminal terminal = terminalAccess.findUsable(terminalId);
        var existing = saleRepository.findByTerminalIdAndIdempotencyKey(terminal.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        CashSession session = cashSessionRepository.findOpenForUpdate(terminal.getId())
                .orElseThrow(() -> new ConflictException("La caja de este terminal esta cerrada. Abrela para vender"));
        CurrentUser seller = currentUserProvider.get();

        // Lineas: se agrupan las repetidas (misma variante y mismo refresco) y se usan los precios
        // actuales de la BD (nunca los del cliente).
        record Key(UUID variantId, UUID mixerId) {
        }
        Map<Key, Integer> quantities = new LinkedHashMap<>();
        request.items().forEach(i -> quantities.merge(new Key(i.variantId(), i.mixerVariantId()), i.quantity(),
                Integer::sum));
        if (quantities.values().stream().mapToInt(Integer::intValue).sum() > MAX_UNITS_PER_SALE) {
            throw new BadRequestException("Maximo " + MAX_UNITS_PER_SALE + " unidades por venta");
        }
        Set<UUID> ids = new HashSet<>();
        quantities.keySet().forEach(k -> {
            ids.add(k.variantId());
            if (k.mixerId() != null) {
                ids.add(k.mixerId());
            }
        });
        Map<UUID, ProductVariant> variants = new HashMap<>();
        variantRepository.findAllWithProduct(ids, terminal.getTenantId()).forEach(v -> variants.put(v.getId(), v));
        if (variants.size() != ids.size()) {
            throw NotFoundException.of("Producto");
        }
        PosSale sale = new PosSale();
        sale.setTenantId(terminal.getTenantId());
        sale.setTerminalId(terminal.getId());
        sale.setCashSessionId(session.getId());
        sale.setReference(newReference());
        sale.setSoldBy(seller.userId());
        sale.setIdempotencyKey(idempotencyKey);
        BigDecimal subtotal = BigDecimal.ZERO;
        // Lo que se descuenta del almacen: componentes de las recetas (ml de cada botella) o la propia variante.
        Map<ProductVariant, Integer> consumption = new LinkedHashMap<>();
        for (Map.Entry<Key, Integer> entry : quantities.entrySet()) {
            ProductVariant variant = variants.get(entry.getKey().variantId());
            ProductVariant mixer = entry.getKey().mixerId() == null ? null : variants.get(entry.getKey().mixerId());
            checkSellable(variant, terminal);
            if (variant.isRequiresMixer() && mixer == null) {
                throw new BadRequestException("Elige el refresco de " + label(variant));
            }
            if (mixer != null) {
                if (!variant.isRequiresMixer()) {
                    throw new BadRequestException(label(variant) + " no se sirve con refresco");
                }
                if (!mixer.getProduct().isMixer() || !mixer.isSellable()) {
                    throw new BadRequestException(label(mixer) + " no se puede elegir como refresco");
                }
            }
            int quantity = entry.getValue();
            // Precio de la copa + suplemento del refresco (no su precio suelto).
            BigDecimal unitPrice = variant.getPrice().add(mixer == null ? BigDecimal.ZERO
                    : mixer.getProduct().getMixerSurcharge());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            PosSaleItem item = new PosSaleItem();
            item.setVariantId(variant.getId());
            item.setProductName(variant.getProduct().getName());
            item.setVariantName(variant.getName());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            if (mixer != null) {
                item.setMixerVariantId(mixer.getId());
                item.setMixerName(mixer.getProduct().getName());
                StockConsumption.add(consumption, mixer, quantity);
            }
            sale.addItem(item);
            subtotal = subtotal.add(lineTotal);
            StockConsumption.add(consumption, variant, quantity);
        }
        List<StockService.Line> stockLines = consumption.entrySet().stream()
                .map(e -> new StockService.Line(e.getKey().getId(), e.getValue(), label(e.getKey()),
                        e.getKey().getUnitSizeMl()))
                .toList();
        sale.setSubtotalAmount(subtotal);

        if (request.discount() != null) {
            applyDiscount(sale, request.discount(), seller);
        }
        BigDecimal total = subtotal.subtract(sale.getDiscountAmount());
        sale.setTotalAmount(total);

        sale.setPaymentMethod(request.paymentMethod());
        if (request.paymentMethod() == PaymentMethod.CASH) {
            BigDecimal received = request.cashReceived() == null ? total : request.cashReceived();
            if (received.compareTo(total) < 0) {
                throw new BadRequestException("El efectivo entregado es menor que el total (" + total + " EUR)");
            }
            sale.setCashReceived(received);
            sale.setChangeGiven(received.subtract(total));
        } else if (request.cashReceived() != null) {
            throw new BadRequestException("El pago con tarjeta no lleva efectivo entregado");
        }

        // saveAndFlush: si otra peticion con la misma clave se adelanto, la restriccion UNIQUE salta aqui (409).
        sale = saleRepository.saveAndFlush(sale);
        stockService.consumeForSale(sale.getTenantId(), terminal.getWarehouseId(), stockLines, sale.getId(),
                seller.userId());
        if (sale.getDiscountAmount().signum() > 0) {
            auditService.record(AuditAction.POS_SALE_DISCOUNT, "PosSale", sale.getId(),
                    sale.getReference() + " descuento=" + sale.getDiscountAmount() + " (" + sale.getDiscountReason() + ")");
        }
        return toResponse(sale);
    }

    /** Anula una venta de una caja abierta: devuelve el stock y deja de contar en la caja (POS_SUPERVISE). */
    @Transactional
    public PosSaleResponse voidSale(UUID saleId, VoidSaleRequest request) {
        PosSale sale = saleRepository.findByIdAndTenantId(saleId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Venta"));
        CashSession session = cashSessionService.lockOpen(sale.getCashSessionId());
        if (sale.getStatus() != PosSaleStatus.COMPLETED) {
            throw new ConflictException("La venta ya estaba anulada");
        }
        PosTerminal terminal = terminalAccess.findOperable(sale.getTerminalId());
        UUID userId = currentUserProvider.get().userId();
        sale.setStatus(PosSaleStatus.VOIDED);
        sale.setVoidedBy(userId);
        sale.setVoidedAt(Instant.now(clock));
        sale.setVoidReason(request.reason().trim());

        // Se devuelve exactamente lo que se desconto al vender (ml de cada botella incluidos).
        stockService.restoreForVoid(sale.getTenantId(), terminal.getWarehouseId(), sale.getId(), userId);
        auditService.record(AuditAction.POS_SALE_VOIDED, "PosSale", saleId,
                sale.getReference() + " total=" + sale.getTotalAmount() + " motivo: " + sale.getVoidReason());
        return toResponse(sale);
    }

    private static void checkSellable(ProductVariant variant, PosTerminal terminal) {
        if (!variant.isSellable()) {
            throw new BadRequestException(label(variant) + " no esta a la venta");
        }
        if (!terminal.allowedKindSet().contains(variant.getProduct().getCategory().getKind())) {
            throw new BadRequestException(label(variant) + " no se vende en este terminal");
        }
    }

    @Transactional(readOnly = true)
    public PosSaleResponse get(UUID saleId) {
        return toResponse(saleRepository.findByIdAndTenantId(saleId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Venta")));
    }

    /**
     * Descuento autorizado: si quien vende tiene POS_SUPERVISE se autoriza a si mismo; si no, un supervisor
     * de la misma empresa escribe su email y contrasena en el TPV.
     */
    private void applyDiscount(PosSale sale, PosSaleRequest.Discount discount, CurrentUser seller) {
        UUID authorizedBy;
        if (seller.hasPermission(Permissions.POS_SUPERVISE)) {
            authorizedBy = seller.userId();
        } else {
            authorizedBy = verifySupervisor(discount.supervisorEmail(), discount.supervisorPassword(),
                    sale.getTenantId());
        }
        BigDecimal subtotal = sale.getSubtotalAmount();
        BigDecimal amount = switch (discount.type()) {
            case PERCENTAGE -> {
                if (discount.value().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new BadRequestException("Un descuento porcentual no puede superar el 100 %");
                }
                yield subtotal.multiply(discount.value()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            case FIXED_AMOUNT -> discount.value();
        };
        sale.setDiscountAmount(amount.min(subtotal).setScale(2, RoundingMode.HALF_UP));
        sale.setDiscountReason(discount.reason().trim());
        sale.setDiscountAuthorizedBy(authorizedBy);
    }

    private UUID verifySupervisor(String email, String password, UUID tenantId) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ForbiddenException("El descuento necesita la autorizacion de un supervisor");
        }
        User supervisor = userRepository.findByEmail(UserAccountService.normalizeEmail(email))
                .filter(User::isActive)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ForbiddenException("Autorizacion de supervisor no valida"));
        if (!supervisor.getPermissionCodes().contains(Permissions.POS_SUPERVISE)) {
            throw new ForbiddenException("Ese usuario no puede autorizar descuentos");
        }
        return supervisor.getId();
    }

    private PosSaleResponse toResponse(PosSale sale) {
        Map<UUID, String> users = userNames.of(java.util.Arrays.asList(sale.getSoldBy(),
                sale.getDiscountAuthorizedBy(), sale.getVoidedBy()));
        return posMapper.toResponse(sale, users);
    }

    private String newReference() {
        String reference;
        do {
            reference = "TPV-" + SecureTokens.randomReference(8);
        } while (saleRepository.existsByReference(reference));
        return reference;
    }

    private static String label(ProductVariant v) {
        return v.getProduct().getName() + " (" + v.getName() + ")";
    }
}
