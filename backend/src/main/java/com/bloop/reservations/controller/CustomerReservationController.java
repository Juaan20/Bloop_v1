package com.bloop.reservations.controller;

import com.bloop.reservations.dto.request.RequestReservationRequest;
import com.bloop.reservations.dto.response.ReservationResponse;
import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.service.CustomerReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Reservas VIP desde la web: mesas disponibles (publico) y reservas del cliente ("Mi cuenta"). */
@RestController
@RequiredArgsConstructor
public class CustomerReservationController {

    private final CustomerReservationService customerReservationService;

    /** Publico: mesas VIP del local de la sesion y su disponibilidad. */
    @GetMapping("/api/public/sessions/{sessionId}/vip-tables")
    public List<TableAvailabilityResponse> tables(@PathVariable UUID sessionId) {
        return customerReservationService.tablesForSession(sessionId);
    }

    @GetMapping("/api/me/reservations")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ReservationResponse> mine() {
        return customerReservationService.mine();
    }

    @PostMapping("/api/me/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReservationResponse request(@Valid @RequestBody RequestReservationRequest request) {
        return customerReservationService.request(request);
    }

    @PostMapping("/api/me/reservations/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return customerReservationService.cancelMine(id);
    }
}
