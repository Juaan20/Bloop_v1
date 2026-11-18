package com.bloop.ticketing.entity;

import com.bloop.events.entity.EventSession;
import com.bloop.events.entity.TicketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada emitida tras un pago confirmado. Cada entrada tiene un codigo aleatorio unico;
 * el QR contiene ese codigo mas una firma HMAC (ver QrCodeService).
 * El pedido se referencia por id para no acoplar este modulo al de pedidos.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private EventSession session;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /** Se regenera al transferir la entrada: el QR anterior (p. ej. una captura) deja de valer. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    /** Solo si el evento tiene edad minima. */
    @Column(name = "holder_birth_date")
    private java.time.LocalDate holderBirthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_document_type")
    private com.bloop.orders.entity.DocumentType holderDocumentType;

    /** 4 ultimos caracteres del documento (nunca el numero completo). */
    @Column(name = "holder_document_last4")
    private String holderDocumentLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_user_id")
    private UUID usedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
