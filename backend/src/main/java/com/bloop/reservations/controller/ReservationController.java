package com.bloop.reservations.controller;

import com.bloop.reservations.dto.request.ChangeReservationStatusRequest;
import com.bloop.reservations.dto.request.CreateReservationRequest;
import com.bloop.reservations.dto.response.ReservationResponse;
import com.bloop.reservations.dto.response.TableAvailabilityResponse;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.reservations.service.ReservationService;
import com.bloop.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reservas VIP de la empresa (backoffice). */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_VIEW')")
    public PageResponse<ReservationResponse> list(@RequestParam(required = false) Instant from,
                                                  @RequestParam(required = false) Instant to,
                                                  @RequestParam(required = false) UUID venueId,
                                                  @RequestParam(required = false) ReservationStatus status,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return reservationService.list(from, to, venueId, status, search, page, size);
    }

    /** Mesas del plano de un local y si estan libres para una fiesta. */
    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('RESERVATION_VIEW')")
    public List<TableAvailabilityResponse> availability(@RequestParam UUID venueId, @RequestParam UUID sessionId) {
        return reservationService.availability(venueId, sessionId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RESERVATION_VIEW')")
    public ReservationResponse get(@PathVariable UUID id) {
        return reservationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public ReservationResponse changeStatus(@PathVariable UUID id,
                                            @Valid @RequestBody ChangeReservationStatusRequest request) {
        return reservationService.changeStatus(id, request);
    }

    /** Mesa vendida en taquilla: reserva desde ahora, cobro y acceso a la mesa en un paso. */
    @PostMapping("/walk-in")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public ReservationResponse walkIn(@Valid @RequestBody com.bloop.reservations.dto.request.WalkInRequest request) {
        return reservationService.walkIn(request);
    }

    @PostMapping("/{id}/deposit")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    public ReservationResponse registerDeposit(@PathVariable UUID id,
                                               @RequestParam(required = false) com.bloop.pos.entity.PaymentMethod method) {
        return reservationService.registerDeposit(id, method);
    }
}
