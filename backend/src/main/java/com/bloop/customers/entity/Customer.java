package com.bloop.customers.entity;

import com.bloop.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ficha de cliente de UNA empresa (CRM). La crea automaticamente el primer pedido pagado
 * (o la primera reserva) del cliente en esa empresa, y sus estadisticas se actualizan con cada compra.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Cuenta del cliente en la web; null si la ficha fue anonimizada. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    /** Notas internas del equipo (nunca las ve el cliente salvo en su exportacion RGPD). */
    private String notes;

    /** Etiquetas libres separadas por comas, p. ej. "cumpleanos, grupo empresa". */
    private String tags;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(name = "orders_count", nullable = false)
    private int ordersCount;

    @Column(name = "tickets_count", nullable = false)
    private int ticketsCount;

    @Column(name = "total_spent", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "first_purchase_at")
    private Instant firstPurchaseAt;

    @Column(name = "last_purchase_at")
    private Instant lastPurchaseAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }
}
