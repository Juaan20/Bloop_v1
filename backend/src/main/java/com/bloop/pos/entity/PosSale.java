package com.bloop.pos.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Venta del TPV (ticket de barra o taquilla). Siempre pertenece a una caja abierta. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pos_sales")
public class PosSale extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "terminal_id", nullable = false, updatable = false)
    private UUID terminalId;

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;

    /** Referencia legible del ticket, p. ej. TPV-7KX2M9QP. */
    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosSaleStatus status = PosSaleStatus.COMPLETED;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false)
    private PaymentMethod paymentMethod;

    /** Solo en efectivo: lo que entrega el cliente y la vuelta. */
    @Column(name = "cash_received", precision = 10, scale = 2, updatable = false)
    private BigDecimal cashReceived;

    @Column(name = "change_given", precision = 10, scale = 2, updatable = false)
    private BigDecimal changeGiven;

    @Column(name = "discount_reason", updatable = false)
    private String discountReason;

    /** Supervisor que autorizo el descuento (null si no hubo descuento). */
    @Column(name = "discount_authorized_by", updatable = false)
    private UUID discountAuthorizedBy;

    @Column(name = "sold_by", nullable = false, updatable = false)
    private UUID soldBy;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<PosSaleItem> items = new ArrayList<>();

    public void addItem(PosSaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    public int getItemCount() {
        return items.stream().mapToInt(PosSaleItem::getQuantity).sum();
    }
}
