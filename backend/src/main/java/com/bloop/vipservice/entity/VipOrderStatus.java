package com.bloop.vipservice.entity;

import java.util.Set;

/**
 * Estados de un pedido de mesa VIP (flujo del personal de servicio):
 *
 * <pre>
 * NEW -> PENDING -> PREPARING -> READY -> ON_THE_WAY -> DELIVERED
 *  \________\___________\__________\__________ CANCELLED
 * </pre>
 */
public enum VipOrderStatus {
    /** Recien pedido: nadie lo ha visto todavia. */
    NEW,
    /** Visto por el personal, pendiente de preparar. */
    PENDING,
    PREPARING,
    READY,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED;

    /** Pedidos que aun requieren atencion del personal. */
    public static final Set<VipOrderStatus> OPEN = Set.of(NEW, PENDING, PREPARING, READY, ON_THE_WAY);

    /** Siguiente paso del flujo (un solo toque en el panel). null si ya ha terminado. */
    public VipOrderStatus next() {
        return switch (this) {
            case NEW -> PENDING;
            case PENDING -> PREPARING;
            case PREPARING -> READY;
            case READY -> ON_THE_WAY;
            case ON_THE_WAY -> DELIVERED;
            case DELIVERED, CANCELLED -> null;
        };
    }

    public boolean canChangeTo(VipOrderStatus target) {
        if (target == CANCELLED) {
            return OPEN.contains(this);
        }
        return target == next();
    }
}
