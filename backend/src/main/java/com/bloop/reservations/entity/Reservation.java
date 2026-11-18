package com.bloop.reservations.entity;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reserva de una mesa VIP para un intervalo de tiempo (normalmente una sesion de un evento).
 * La base de datos impide que dos reservas activas de la misma mesa se solapen
 * (restriccion EXCLUDE ex_reservations_no_overlap en V7).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reservations")
public class Reservation extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private UUID venueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false, updatable = false)
    private VipTable table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", updatable = false)
    private EventSession session;

    /** Ficha del CRM (si la hay). */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Cuenta web del cliente (reservas hechas desde la web). */
    @Column(name = "customer_user_id")
    private UUID customerUserId;

    /** Referencia legible, p. ej. VR-7KX2M9. */
    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(nullable = false)
    private int guests;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "min_spend", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSpend;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_paid_at")
    private Instant depositPaidAt;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReservationSource source;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Como se pago la reserva + paquete (efectivo/tarjeta), para cuadrar la caja. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "prepaid_method")
    private com.bloop.pos.entity.PaymentMethod prepaidMethod;

    /** Token del QR de la mesa (carta y pedidos desde el movil). null hasta que se genera. */
    @Column(name = "table_token")
    private String tableToken;

    @Column(name = "table_token_active", nullable = false)
    private boolean tableTokenActive;

    /** Momento en que se cerro la cuenta de la mesa (todo cobrado). */
    @Column(name = "account_closed_at")
    private Instant accountClosedAt;

    /** Paquete incluido (copiado de la mesa al reservar). */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationPackageItem> packageItems = new ArrayList<>();

    /** El acceso exige haber pagado antes la reserva y el paquete (si tienen importe). */
    public boolean isPrepaid() {
        return depositAmount.signum() == 0 || depositPaidAt != null;
    }

    public boolean isDepositPending() {
        return depositAmount.signum() > 0 && depositPaidAt == null;
    }
}
