package com.bloop.ticketing.controller;

import com.bloop.ticketing.dto.request.ChangeHolderRequest;
import com.bloop.ticketing.dto.request.TransferTicketRequest;
import com.bloop.ticketing.dto.response.MyTicketResponse;
import com.bloop.ticketing.service.MyTicketService;
import com.bloop.ticketing.service.TicketTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Entradas del usuario autenticado. Solo devuelve entradas cuyo propietario es el usuario del token. */
@RestController
@RequestMapping("/api/me/tickets")
// Zona de clientes: solo cuentas de cliente (un trabajador recibe 403).
@org.springframework.security.access.prepost.PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MyTicketController {

    private final MyTicketService myTicketService;
    private final TicketTransferService ticketTransferService;

    @GetMapping
    public List<MyTicketResponse> list() {
        return myTicketService.listMine();
    }

    @GetMapping("/{id}")
    public MyTicketResponse get(@PathVariable UUID id) {
        return myTicketService.getMine(id);
    }

    /** Cambia el nombre que figura en la entrada (el QR no cambia). */
    @PutMapping("/{id}/holder")
    public MyTicketResponse changeHolder(@PathVariable UUID id, @Valid @RequestBody ChangeHolderRequest request) {
        return ticketTransferService.renameMine(id, request);
    }

    /** Transfiere la entrada a otra cuenta: deja de ser del usuario y se genera un QR nuevo. */
    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@PathVariable UUID id, @Valid @RequestBody TransferTicketRequest request) {
        ticketTransferService.transferMine(id, request);
    }
}
