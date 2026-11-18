package com.bloop.promoters.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.events.entity.Event;
import com.bloop.events.repository.EventRepository;
import com.bloop.promoters.dto.request.PromoCodeRequest;
import com.bloop.promoters.dto.response.PromoCodeResponse;
import com.bloop.promoters.entity.DiscountType;
import com.bloop.promoters.entity.PromoCode;
import com.bloop.promoters.entity.Promoter;
import com.bloop.promoters.repository.PromoCodeRepository;
import com.bloop.promoters.repository.PromoterRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Codigos promocionales: gestion en el backoffice y aplicacion en la compra.
 *
 * Los usos se controlan igual que el stock de entradas: al crear el pedido se consume un uso
 * con un UPDATE condicional (reserveUse) y, si el pedido caduca o se cancela, se devuelve (releaseUse).
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoterRepository promoterRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> list() {
        return promoCodeRepository.findByTenantIdOrderByCreatedAtDesc(currentUserProvider.requireTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeRequest request) {
        UUID tenantId = currentUserProvider.requireTenantId();
        String code = normalize(request.code());
        if (promoCodeRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new ConflictException("Ya existe el codigo '" + code + "'");
        }
        PromoCode promo = new PromoCode();
        promo.setTenantId(tenantId);
        promo.setCode(code);
        apply(promo, request);
        promo = promoCodeRepository.save(promo);
        auditService.record(AuditAction.PROMO_CODE_CREATED, "PromoCode", promo.getId(), code);
        return toResponse(promo);
    }

    @Transactional
    public PromoCodeResponse update(UUID id, PromoCodeRequest request) {
        PromoCode promo = promoCodeRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Codigo promocional"));
        if (request.maxUses() != null && request.maxUses() < promo.getTimesUsed()) {
            throw new BadRequestException("El limite de usos no puede ser menor que los usos actuales ("
                    + promo.getTimesUsed() + ")");
        }
        apply(promo, request);
        auditService.record(AuditAction.PROMO_CODE_UPDATED, "PromoCode", id,
                promo.getCode() + " activo=" + promo.isActive());
        return toResponse(promo);
    }

    /**
     * Busca un codigo para aplicarlo en una compra. Lanza 400 con un mensaje claro si no sirve,
     * porque el cliente lo ha escrito y debe saber por que no se aplica.
     */
    @Transactional(readOnly = true)
    public PromoCode resolveForCheckout(UUID tenantId, UUID eventId, String rawCode, Instant now) {
        PromoCode promo = promoCodeRepository.findByTenantIdAndCode(tenantId, normalize(rawCode))
                .orElseThrow(() -> new BadRequestException("El codigo promocional no existe"));
        if (!promo.isValidAt(now)) {
            throw new BadRequestException("El codigo promocional no esta activo o ha caducado");
        }
        if (!promo.appliesToEvent(eventId)) {
            throw new BadRequestException("El codigo promocional no es valido para este evento");
        }
        return promo;
    }

    /** Consume un uso dentro de la transaccion del checkout (409 si se agoto). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveUse(PromoCode promo) {
        if (promoCodeRepository.reserveUse(promo.getId()) == 0) {
            throw new ConflictException("El codigo promocional ha alcanzado su limite de usos");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUse(UUID promoCodeId) {
        promoCodeRepository.releaseUse(promoCodeId);
    }

    public PromoCodeResponse toResponse(PromoCode p) {
        String eventName = p.getEventId() == null ? null
                : eventRepository.findById(p.getEventId()).map(Event::getName).orElse(null);
        String promoterName = p.getPromoterId() == null ? null
                : promoterRepository.findById(p.getPromoterId())
                        .flatMap(pr -> userRepository.findById(pr.getUserId()))
                        .map(User::getFullName).orElse(null);
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getDescription(), p.getDiscountType(),
                p.getDiscountValue(), p.getEventId(), eventName, p.getPromoterId(), promoterName, p.getMaxUses(),
                p.getTimesUsed(), p.getValidFrom(), p.getValidUntil(), p.isActive(), p.getCreatedAt());
    }

    private void apply(PromoCode promo, PromoCodeRequest r) {
        UUID tenantId = promo.getTenantId();
        if (r.discountType() == DiscountType.PERCENTAGE && r.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Un descuento porcentual no puede superar el 100 %");
        }
        if (r.validFrom() != null && r.validUntil() != null && !r.validUntil().isAfter(r.validFrom())) {
            throw new BadRequestException("La fecha de fin debe ser posterior a la de inicio");
        }
        if (r.eventId() != null && eventRepository.findByIdAndTenantId(r.eventId(), tenantId).isEmpty()) {
            throw NotFoundException.of("Evento");
        }
        if (r.promoterId() != null && promoterRepository.findByIdAndTenantId(r.promoterId(), tenantId)
                .map(Promoter::getId).isEmpty()) {
            throw NotFoundException.of("Promotor");
        }
        promo.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        promo.setDiscountType(r.discountType());
        promo.setDiscountValue(r.discountValue());
        promo.setEventId(r.eventId());
        promo.setPromoterId(r.promoterId());
        promo.setMaxUses(r.maxUses());
        promo.setValidFrom(r.validFrom());
        promo.setValidUntil(r.validUntil());
        promo.setActive(r.active());
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
