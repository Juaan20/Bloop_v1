package com.bloop.pos.service;

import com.bloop.pos.entity.PosTerminal;
import com.bloop.pos.repository.PosTerminalRepository;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.venues.service.StaffVenueAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Busqueda de terminales con sus reglas de acceso. La usan PosTerminalService, CashSessionService y
 * PosSaleService (en una clase aparte para que esos servicios no dependan unos de otros en circulo).
 */
@Component
@RequiredArgsConstructor
public class PosTerminalAccess {

    private final PosTerminalRepository terminalRepository;
    private final StaffVenueAccess staffVenueAccess;
    private final CurrentUserProvider currentUserProvider;

    /** Terminal del tenant actual (404 si es de otra empresa). */
    @Transactional(readOnly = true)
    public PosTerminal findInMyTenant(UUID id) {
        return terminalRepository.findByIdAndTenantId(id, currentUserProvider.requireTenantId())
                .orElseThrow(() -> NotFoundException.of("Terminal"));
    }

    /** Terminal activo de un local en el que el usuario puede trabajar (403 si no esta asignado). */
    @Transactional(readOnly = true)
    public PosTerminal findUsable(UUID id) {
        PosTerminal terminal = findOperable(id);
        if (!terminal.isActive()) {
            throw new BadRequestException("El terminal esta desactivado");
        }
        return terminal;
    }

    /** Como findUsable pero admite terminales desactivados (para poder cerrar su caja). */
    @Transactional(readOnly = true)
    public PosTerminal findOperable(UUID id) {
        PosTerminal terminal = findInMyTenant(id);
        staffVenueAccess.assertCanWorkAtVenue(currentUserProvider.get(), terminal.getVenueId());
        return terminal;
    }
}
