package com.bloop.vipservice.service;

import com.bloop.reservations.entity.Reservation;
import com.bloop.reservations.entity.ReservationPackageItem;
import com.bloop.reservations.entity.ReservationStatus;
import com.bloop.vipservice.dto.response.VipAccountResponse;
import com.bloop.vipservice.entity.VipAccountPayment;
import com.bloop.vipservice.entity.VipOrder;
import com.bloop.vipservice.entity.VipOrderItem;
import com.bloop.vipservice.entity.VipOrderStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calculos de la cuenta de una mesa VIP (sin acceso a base de datos: facil de probar).
 * Probado en VipAccountCalculatorTest.
 */
public final class VipAccountCalculator {

    private VipAccountCalculator() {
    }

    /** Unidades del paquete ya pedidas por producto (pedidos no cancelados). */
    public static Map<UUID, Integer> consumedFromPackage(List<VipOrder> orders) {
        Map<UUID, Integer> consumed = new HashMap<>();
        for (VipOrder o : orders) {
            if (o.getStatus() == VipOrderStatus.CANCELLED) {
                continue;
            }
            for (VipOrderItem i : o.getItems()) {
                if (i.isIncluded()) {
                    consumed.merge(i.getVariantId(), i.getQuantity(), Integer::sum);
                }
            }
        }
        return consumed;
    }

    /** Unidades que quedan del paquete por producto. */
    public static Map<UUID, Integer> remainingInPackage(Reservation reservation, List<VipOrder> orders) {
        Map<UUID, Integer> consumed = consumedFromPackage(orders);
        Map<UUID, Integer> remaining = new HashMap<>();
        for (ReservationPackageItem p : reservation.getPackageItems()) {
            remaining.put(p.getVariantId(), Math.max(0, p.getQuantity() - consumed.getOrDefault(p.getVariantId(), 0)));
        }
        return remaining;
    }

    /**
     * Reparte una cantidad pedida entre lo que queda del paquete (incluido, 0 euros) y el resto
     * (consumicion adicional). Devuelve [incluidas, adicionales].
     */
    public static int[] split(int requested, int remainingInPackage) {
        int included = Math.min(requested, Math.max(0, remainingInPackage));
        return new int[] {included, requested - included};
    }

    public static BigDecimal extrasTotal(List<VipOrder> orders) {
        return orders.stream().filter(o -> o.getStatus() != VipOrderStatus.CANCELLED).map(VipOrder::extraTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static long openOrders(List<VipOrder> orders) {
        return orders.stream().filter(o -> VipOrderStatus.OPEN.contains(o.getStatus())).count();
    }

    public static VipAccountResponse account(Reservation r, List<VipOrder> orders, List<VipAccountPayment> payments) {
        BigDecimal extras = extrasTotal(orders);
        BigDecimal paid = payments.stream().map(VipAccountPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = extras.subtract(paid).max(BigDecimal.ZERO);
        long open = openOrders(orders);
        Map<UUID, Integer> consumed = consumedFromPackage(orders);
        List<VipAccountResponse.PackageLine> lines = new ArrayList<>();
        for (ReservationPackageItem p : r.getPackageItems()) {
            int used = consumed.getOrDefault(p.getVariantId(), 0);
            lines.add(new VipAccountResponse.PackageLine(p.getVariantId(), p.getProductName(), p.getQuantity(), used,
                    Math.max(0, p.getQuantity() - used)));
        }
        boolean closed = r.getAccountClosedAt() != null;
        boolean canClose = !closed && r.getStatus() == ReservationStatus.SEATED && r.isPrepaid() && open == 0
                && pending.signum() == 0;
        var paymentDtos = payments.stream()
                .map(p -> new VipAccountResponse.Payment(p.getId(), p.getAmount(), p.getMethod().name(), p.getCreatedAt()))
                .toList();
        return new VipAccountResponse(r.getDepositAmount(), r.isPrepaid(), r.getDepositPaidAt(), extras, paid, pending,
                open, closed, r.getAccountClosedAt(), canClose, lines, paymentDtos);
    }
}
