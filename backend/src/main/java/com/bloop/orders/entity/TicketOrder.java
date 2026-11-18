package com.bloop.orders.entity;

import com.bloop.events.entity.EventSession;
import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pedido de entradas de un cliente para una sesion concreta. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_orders")
public class TicketOrder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Referencia legible que ve el cliente (p. ej. VF-7KX2M9QP). */
    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private UUID customerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private EventSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Suma de las lineas antes del descuento. */
    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    /** Descuento del codigo promocional (0 si no se uso ninguno). */
    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Importe a pagar: subtotal - descuento. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    /** Texto del codigo usado (se conserva aunque el codigo se borre). */
    @Column(name = "promo_code")
    private String promoCode;

    /** Promotor al que se atribuye la venta (enlace ?rrpp= o codigo del promotor). */
    @Column(name = "promoter_id")
    private UUID promoterId;

    /** Comision del promotor, calculada al crear el pedido. Solo cuenta si el pedido se paga. */
    @Column(name = "commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    /** El cliente acepto en la compra recibir comunicaciones del organizador. */
    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "buyer_email", nullable = false)
    private String buyerEmail;

    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    /** Clave que envia el cliente para que un reintento no cree un segundo pedido. */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Fecha del reembolso (estado REFUNDED). */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason")
    private String refundReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<TicketOrderItem> items = new ArrayList<>();

    @Column(name = "buyer_phone")
    private String buyerPhone;

    /** Asistentes indicados en la compra (uno por entrada, en orden). */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("position")
    @BatchSize(size = 50)
    private List<OrderAttendee> attendees = new ArrayList<>();

    public void addAttendee(OrderAttendee attendee) {
        attendee.setOrder(this);
        attendee.setTenantId(tenantId);
        attendees.add(attendee);
    }

    public void addItem(TicketOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public int getTicketCount() {
        return items.stream().mapToInt(TicketOrderItem::getQuantity).sum();
    }
}
