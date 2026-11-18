package com.bloop.promoters.entity;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Codigo promocional de una empresa. Puede limitarse a un evento, a un numero de usos
 * y a una ventana de fechas. Si tiene promotor, las ventas con el codigo se le atribuyen.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Siempre en mayusculas (lo normaliza el servicio). Unico dentro de la empresa. */
    @Column(nullable = false, updatable = false)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** null = valido en todos los eventos de la empresa. */
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "promoter_id")
    private UUID promoterId;

    /** null = usos ilimitados. */
    @Column(name = "max_uses")
    private Integer maxUses;

    /** Se modifica SOLO con PromoCodeRepository.reserveUse / releaseUse (UPDATE atomico). */
    @Column(name = "times_used", nullable = false)
    private int timesUsed;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(nullable = false)
    private boolean active = true;

    /** Activo y dentro de su ventana de fechas (no comprueba los usos: eso lo hace el UPDATE atomico). */
    public boolean isValidAt(Instant now) {
        return active
                && (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }

    public boolean appliesToEvent(UUID otherEventId) {
        return eventId == null || eventId.equals(otherEventId);
    }

    /** Descuento sobre un subtotal, redondeado a centimos y nunca mayor que el subtotal. */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal discount = switch (discountType) {
            case PERCENTAGE -> subtotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> discountValue;
        };
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }
}
