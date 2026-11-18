package com.bloop.pos.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.pos.dto.request.CashRequests;
import com.bloop.pos.dto.response.CashSessionResponse;
import com.bloop.pos.dto.response.PosSaleResponse;
import com.bloop.pos.entity.CashMovement;
import com.bloop.pos.entity.CashMovementType;
import com.bloop.pos.entity.CashSession;
import com.bloop.pos.entity.CashSessionStatus;
import com.bloop.pos.entity.PaymentMethod;
import com.bloop.pos.entity.PosSale;
import com.bloop.pos.entity.PosSaleStatus;
import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.mapper.PosMapper;
import com.bloop.pos.repository.CashMovementRepository;
import com.bloop.pos.repository.CashSessionRepository;
import com.bloop.pos.repository.PosSaleRepository;
import com.bloop.pos.repository.PosTerminalRepository;
import com.bloop.shared.exception.ConflictException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.users.service.UserNames;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cajas (turnos) de un terminal: apertura con fondo, movimientos de efectivo, resumen y cierre con arqueo.
 *
 * Bloqueo por terminal:
 * - Solo una caja abierta por terminal (indice unico parcial en la BD: si dos personas la abren a la vez,
 *   la segunda recibe 409).
 * - Movimientos, ventas, anulaciones y cierre bloquean la fila de la caja (SELECT ... FOR UPDATE):
 *   se ejecutan de uno en uno y el cierre nunca se mezcla con una venta a medias.
 */
@Service
@RequiredArgsConstructor
public class CashSessionService {

    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository movementRepository;
    private final PosSaleRepository saleRepository;
    private final PosTerminalRepository terminalRepository;
    private final PosTerminalAccess terminalAccess;
    private final UserNames userNames;
    private final PosMapper posMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public CashSessionResponse open(UUID terminalId, CashRequests.Open request) {
        PosTerminal terminal = terminalAccess.findUsable(terminalId);
        if (cashSessionRepository.findByTerminalIdAndStatus(terminal.getId(), CashSessionStatus.OPEN).isPresent()) {
            throw new ConflictException("Ya hay una caja abierta en este terminal");
        }
        Instant now = Instant.now(clock);
        CashSession session = new CashSession();
        session.setTenantId(terminal.getTenantId());
        session.setTerminalId(terminal.getId());
        session.setOpenedBy(currentUserProvider.get().userId());
        session.setOpenedAt(now);
        session.setOpeningFloat(request.openingFloat());
        try {
            // saveAndFlush: si otra persona abrio la caja a la vez, el indice unico salta aqui.
            session = cashSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya hay una caja abierta en este terminal");
        }
        auditService.record(AuditAction.CASH_SESSION_OPENED, "CashSession", session.getId(),
                terminal.getName() + " fondo=" + request.openingFloat());
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public CashSessionResponse get(UUID sessionId) {
        return toResponse(findAccessible(sessionId));
    }

    @Transactional
    public CashSessionResponse addMovement(UUID sessionId, CashRequests.Movement request) {
        CashSession session = lockOpen(sessionId);
        if (request.type() == CashMovementType.CASH_OUT
                && request.amount().compareTo(expectedCash(session)) > 0) {
            throw new ConflictException("No hay tanto efectivo en la caja (esperado " + expectedCash(session) + " EUR)");
        }
        CashMovement movement = new CashMovement();
        movement.setTenantId(session.getTenantId());
        movement.setCashSessionId(session.getId());
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setReason(request.reason().trim());
        movement.setUserId(currentUserProvider.get().userId());
        movement.setCreatedAt(Instant.now(clock));
        movementRepository.save(movement);
        auditService.record(AuditAction.CASH_MOVEMENT, "CashSession", sessionId,
                request.type() + " " + request.amount() + " - " + movement.getReason());
        return toResponse(session);
    }

    /** Cierre con arqueo: se guarda el efectivo esperado, el contado y la diferencia. */
    @Transactional
    public CashSessionResponse close(UUID sessionId, CashRequests.Close request) {
        CashSession session = lockOpen(sessionId);
        BigDecimal expected = expectedCash(session);
        session.setStatus(CashSessionStatus.CLOSED);
        session.setClosedBy(currentUserProvider.get().userId());
        session.setClosedAt(Instant.now(clock));
        session.setExpectedCash(expected);
        session.setCountedCash(request.countedCash());
        session.setDifference(request.countedCash().subtract(expected));
        session.setClosingNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        auditService.record(AuditAction.CASH_SESSION_CLOSED, "CashSession", sessionId,
                "esperado=" + expected + " contado=" + request.countedCash() + " diferencia=" + session.getDifference());
        return toResponse(session);
    }

    /** Historial de cajas (POS_MANAGE). */
    @Transactional(readOnly = true)
    public PageResponse<CashSessionResponse> history(UUID terminalId, int page, int size) {
        var result = cashSessionRepository.search(currentUserProvider.requireTenantId(), terminalId,
                PageRequest.of(page, Math.min(size, 50)));
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<PosSaleResponse> sales(UUID sessionId) {
        CashSession session = findAccessible(sessionId);
        List<PosSale> sales = saleRepository.findByCashSessionIdOrderByCreatedAtDesc(session.getId());
        List<UUID> ids = new ArrayList<>();
        sales.forEach(s -> {
            ids.add(s.getSoldBy());
            ids.add(s.getDiscountAuthorizedBy());
            ids.add(s.getVoidedBy());
        });
        Map<UUID, String> users = userNames.of(ids);
        return sales.stream().map(s -> posMapper.toResponse(s, users)).toList();
    }

    /**
     * Caja ABIERTA bloqueada (SELECT ... FOR UPDATE) de un terminal en el que el usuario puede trabajar.
     * La usan tambien las ventas.
     */
    @Transactional
    public CashSession lockOpen(UUID sessionId) {
        CashSession session = cashSessionRepository.findByIdForUpdate(sessionId)
                .filter(s -> s.getTenantId().equals(currentUserProvider.requireTenantId()))
                .orElseThrow(() -> NotFoundException.of("Caja"));
        terminalAccess.findOperable(session.getTerminalId());
        if (!session.isOpen()) {
            throw new ConflictException("La caja esta cerrada");
        }
        return session;
    }

    /** Fondo + ventas en efectivo + entradas - salidas. */
    BigDecimal expectedCash(CashSession session) {
        return session.getOpeningFloat()
                .add(saleRepository.sumCompleted(session.getId(), PaymentMethod.CASH))
                .add(movementRepository.sumByType(session.getId(), CashMovementType.CASH_IN))
                .subtract(movementRepository.sumByType(session.getId(), CashMovementType.CASH_OUT));
    }

    public CashSessionResponse toResponse(CashSession session) {
        BigDecimal cash = saleRepository.sumCompleted(session.getId(), PaymentMethod.CASH);
        BigDecimal card = saleRepository.sumCompleted(session.getId(), PaymentMethod.CARD);
        BigDecimal cashIn = movementRepository.sumByType(session.getId(), CashMovementType.CASH_IN);
        BigDecimal cashOut = movementRepository.sumByType(session.getId(), CashMovementType.CASH_OUT);
        List<CashMovement> movements = movementRepository.findByCashSessionIdOrderByCreatedAt(session.getId());
        List<UUID> ids = new ArrayList<>(List.of(session.getOpenedBy()));
        if (session.getClosedBy() != null) {
            ids.add(session.getClosedBy());
        }
        movements.forEach(m -> ids.add(m.getUserId()));
        Map<UUID, String> users = userNames.of(ids);
        var top = saleRepository.topProducts(session.getId()).stream().limit(10)
                .map(r -> new CashSessionResponse.TopProduct(r.productName(), r.variantName(), r.quantity(), r.total()))
                .toList();
        var summary = new CashSessionResponse.Summary(cash, card, cash.add(card),
                saleRepository.sumDiscounts(session.getId()), cashIn, cashOut,
                session.getOpeningFloat().add(cash).add(cashIn).subtract(cashOut),
                saleRepository.countByCashSessionIdAndStatus(session.getId(), PosSaleStatus.COMPLETED),
                saleRepository.countByCashSessionIdAndStatus(session.getId(), PosSaleStatus.VOIDED), top,
                movements.stream().map(m -> posMapper.toResponse(m, users)).toList());
        String terminalName = terminalRepository.findById(session.getTerminalId()).map(PosTerminal::getName).orElse("");
        return posMapper.toResponse(session, terminalName, users, summary);
    }

    /** Caja de la empresa en un terminal al que el usuario tiene acceso. */
    private CashSession findAccessible(UUID sessionId) {
        CashSession session = cashSessionRepository.findByIdAndTenantId(sessionId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Caja"));
        terminalAccess.findInMyTenant(session.getTerminalId());
        return session;
    }
}
