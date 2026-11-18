package com.bloop.payments.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Intento de cobro de un pedido. Un pedido puede tener varios intentos (p. ej. uno fallido
 * y otro correcto). No guardamos datos de tarjeta: solo el id que nos da el proveedor.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderType provider;

    /** Identificador unico en el proveedor: garantiza que un mismo cobro no se procese dos veces. */
    @Column(name = "provider_payment_id", nullable = false, unique = true)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Referencia del cobro en el proveedor (Stripe: PaymentIntent pi_...). Necesaria para reembolsar. */
    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "refunded_at")
    private java.time.Instant refundedAt;
}
