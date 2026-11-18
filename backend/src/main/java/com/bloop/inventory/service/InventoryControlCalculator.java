package com.bloop.inventory.service;

import com.bloop.inventory.entity.StockMovementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Cuentas del control de inventario de un producto en un periodo (logica pura, sin base de datos).
 *
 *   inicio + recibido - enviado - vendido + merma = final
 *
 * - recibido: entradas de proveedor y traspasos que llegan.
 * - enviado: salidas manuales (roturas, invitaciones...) y traspasos que se van.
 * - vendido: lo que descontaron las ventas del TPV (menos las anuladas).
 * - merma: lo que corrigieron los recuentos (negativo = faltaba).
 */
public final class InventoryControlCalculator {

    /** Resultado en la unidad de inventario (ml o unidades). */
    public record Result(int opening, int received, int sent, int sold, int merma, int closing) {
    }

    private InventoryControlCalculator() {
    }

    /**
     * @param inPeriod    movimientos del periodo por tipo (cantidades con signo, como se guardan)
     * @param afterPeriod suma de los movimientos posteriores al periodo
     * @param current     existencias actuales
     */
    public static Result compute(Map<StockMovementType, Integer> inPeriod, int afterPeriod, int current) {
        int received = get(inPeriod, StockMovementType.IN) + get(inPeriod, StockMovementType.TRANSFER_IN);
        int sent = -(get(inPeriod, StockMovementType.OUT) + get(inPeriod, StockMovementType.TRANSFER_OUT));
        int sold = -(get(inPeriod, StockMovementType.SALE) + get(inPeriod, StockMovementType.SALE_VOID));
        int merma = get(inPeriod, StockMovementType.ADJUSTMENT);
        int closing = current - afterPeriod;
        int opening = closing - received + sent + sold - merma;
        return new Result(opening, received, sent, sold, merma, closing);
    }

    /**
     * Valor de lo que falta segun el coste de compra. Si la variante se cuenta en ml, el coste es por
     * botella y se reparte entre sus ml.
     */
    public static BigDecimal mermaValue(int merma, BigDecimal cost, Integer unitSizeMl) {
        if (merma >= 0 || cost == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal perUnit = unitSizeMl == null ? cost
                : cost.divide(BigDecimal.valueOf(unitSizeMl), 6, RoundingMode.HALF_UP);
        return perUnit.multiply(BigDecimal.valueOf(-merma)).setScale(2, RoundingMode.HALF_UP);
    }

    private static int get(Map<StockMovementType, Integer> map, StockMovementType type) {
        return map.getOrDefault(type, 0);
    }
}
