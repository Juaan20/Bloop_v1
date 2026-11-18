package com.bloop.reservations.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.reservations.dto.request.FloorPlanRequest;
import com.bloop.reservations.entity.VipZone;
import com.bloop.reservations.repository.VipZoneRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Forma de cada sala (cada discoteca es distinta: en L, en U, con terraza...).
 * Se guarda como JSON en vip_zones.layout. Si una sala aun no se ha disenado se devuelve null
 * y el editor propone un rectangulo.
 */
@Service
@RequiredArgsConstructor
public class FloorPlanService {

    private final VipZoneRepository zoneRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;

    /** JSON guardado -> objeto (null si la sala no tiene forma). */
    public FloorPlanRequest parse(String layout) {
        return layout == null || layout.isBlank() ? null : jsonMapper.readValue(layout, FloorPlanRequest.class);
    }

    @Transactional(readOnly = true)
    public FloorPlanRequest get(UUID zoneId) {
        return parse(findZone(zoneId).getLayout());
    }

    @Transactional
    public FloorPlanRequest save(UUID zoneId, FloorPlanRequest request) {
        VipZone zone = findZone(zoneId);
        if (request.cells().length() != request.cols() * request.rows()) {
            throw new BadRequestException("La rejilla no coincide con las columnas y filas indicadas");
        }
        if (request.cells().indexOf('1') < 0) {
            throw new BadRequestException("Pinta al menos una parte de la sala");
        }
        zone.setLayout(jsonMapper.writeValueAsString(request));
        auditService.record(AuditAction.VIP_TABLE_CHANGED, "VipZone", zoneId, "Forma de la sala " + zone.getName()
                + " guardada (" + request.cols() + "x" + request.rows() + ", " + request.elements().size()
                + " elementos)");
        return request;
    }

    private VipZone findZone(UUID zoneId) {
        return zoneRepository.findByIdAndTenantId(zoneId, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Sala"));
    }
}
