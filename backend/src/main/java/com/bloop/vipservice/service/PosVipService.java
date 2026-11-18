package com.bloop.vipservice.service;

import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.entity.TerminalType;
import com.bloop.pos.service.PosTerminalAccess;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.repository.ReservationRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.vipservice.dto.request.VipOrderRequest;
import com.bloop.vipservice.dto.response.VipOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * TPV de servicio VIP: mesas ocupadas del local del terminal y carga de botellas a la cuenta de una mesa.
 * Solo terminales de tipo VIP_SERVICE y empleados que pueden trabajar en ese local (PosTerminalAccess).
 */
@Service
@RequiredArgsConstructor
public class PosVipService {

    private final PosTerminalAccess terminalAccess;
    private final ReservationRepository reservationRepository;
    private final VipOrderService orderService;
    private final Clock clock;

    public record OccupiedTable(UUID reservationId, String tableName, String zoneName, String contactName, int guests) {
    }

    @Transactional(readOnly = true)
    public List<OccupiedTable> occupiedTables(UUID terminalId) {
        PosTerminal terminal = vipTerminal(terminalId);
        Instant now = Instant.now(clock);
        return reservationRepository.findInVenueBetween(terminal.getVenueId(), terminal.getTenantId(), now,
                        now.plusSeconds(60), Set.of(ReservationStatus.SEATED)).stream()
                .map(r -> new OccupiedTable(r.getId(), r.getTable().getName(), r.getTable().getZone().getName(),
                        r.getContactName(), r.getGuests()))
                .sorted(Comparator.comparing(OccupiedTable::tableName))
                .toList();
    }

    @Transactional
    public VipOrderResponse charge(UUID terminalId, UUID reservationId, VipOrderRequest order) {
        PosTerminal terminal = vipTerminal(terminalId);
        return orderService.chargeFromTerminal(reservationId, terminal.getTenantId(), terminal.getVenueId(), order);
    }

    private PosTerminal vipTerminal(UUID id) {
        PosTerminal terminal = terminalAccess.findUsable(id);
        if (terminal.getType() != TerminalType.VIP_SERVICE) {
            throw new BadRequestException("Este terminal no es de servicio VIP");
        }
        return terminal;
    }
}
