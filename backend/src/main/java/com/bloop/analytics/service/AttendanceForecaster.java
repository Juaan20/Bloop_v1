package com.bloop.analytics.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimacion de asistencia a partir de datos reales (sin base de datos: probada en AttendanceForecasterTest).
 *
 * Metodo, explicado para cualquiera:
 * 1. Se buscan noches comparables: mismo local y mismo dia de la semana en el ultimo ano.
 * 2. RITMO (si ya hay ventas): en cada noche comparable se mira cuantas entradas se habian vendido
 *    a esta misma distancia del evento y cuantas se vendieron al final. Ese multiplicador
 *    (final / vendidas entonces) se aplica a las ventas actuales.
 * 3. HISTORICO (si aun no hay ventas): se usan las ventas finales de las noches comparables.
 * 4. La asistencia es ventas x tasa real de asistencia (entradas escaneadas / vendidas) de esas noches.
 * 5. El rango es el percentil 25-75 de las noches comparables, y nunca supera el aforo.
 * Confianza: 6 o mas noches con datos = ALTA, 3-5 = MEDIA, 1-2 = BAJA, 0 = no hay estimacion.
 */
public final class AttendanceForecaster {

    public enum Method { PACE, HISTORICAL, ACTUAL, NONE }

    public enum Confidence { HIGH, MEDIUM, LOW, NONE }

    /** Una noche comparable: vendidas al final, escaneadas y vendidas a la misma antelacion que hoy. */
    public record Comparable(long finalSold, long checkedIn, long soldAtLead) {
    }

    public record Result(Method method, Confidence confidence, int dataPoints, long soldLow, long soldMid,
                         long soldHigh, long attendanceLow, long attendanceMid, long attendanceHigh,
                         double attendanceRate) {
    }

    private AttendanceForecaster() {
    }

    /**
     * @param currentSold entradas vendidas hoy para la sesion
     * @param checkedIn   escaneadas (solo cuenta si la sesion ya paso)
     * @param future      la sesion aun no ha empezado
     */
    public static Result forecast(long currentSold, long checkedIn, long capacity, boolean future,
                                  List<Comparable> comparables) {
        List<Double> rates = new ArrayList<>();
        for (Comparable c : comparables) {
            if (c.finalSold() > 0) {
                rates.add((double) c.checkedIn() / c.finalSold());
            }
        }
        double rate = rates.isEmpty() ? 0 : percentile(rates, 50);
        if (!future) {
            double actualRate = currentSold > 0 ? (double) checkedIn / currentSold : 0;
            return new Result(Method.ACTUAL, Confidence.HIGH, comparables.size(), currentSold, currentSold,
                    currentSold, checkedIn, checkedIn, checkedIn, actualRate);
        }
        if (rates.isEmpty()) {
            return new Result(Method.NONE, Confidence.NONE, 0, currentSold, currentSold, currentSold, 0, 0, 0, 0);
        }

        List<Double> multipliers = new ArrayList<>();
        for (Comparable c : comparables) {
            if (c.soldAtLead() > 0 && c.finalSold() > 0) {
                multipliers.add((double) c.finalSold() / c.soldAtLead());
            }
        }
        Method method;
        double low;
        double mid;
        double high;
        int points;
        if (currentSold > 0 && !multipliers.isEmpty()) {
            method = Method.PACE;
            points = multipliers.size();
            low = currentSold * percentile(multipliers, 25);
            mid = currentSold * percentile(multipliers, 50);
            high = currentSold * percentile(multipliers, 75);
        } else {
            method = Method.HISTORICAL;
            List<Double> finals = comparables.stream().filter(c -> c.finalSold() > 0)
                    .map(c -> (double) c.finalSold()).toList();
            points = finals.size();
            low = percentile(finals, 25);
            mid = percentile(finals, 50);
            high = percentile(finals, 75);
        }
        long soldLow = clamp(low, currentSold, capacity);
        long soldMid = clamp(mid, currentSold, capacity);
        long soldHigh = clamp(high, currentSold, capacity);
        double rateLow = percentile(rates, 25);
        double rateHigh = percentile(rates, 75);
        return new Result(method, confidence(points), points, soldLow, soldMid, soldHigh,
                Math.round(soldLow * rateLow), Math.round(soldMid * rate), Math.round(soldHigh * rateHigh), rate);
    }

    public static Confidence confidence(int points) {
        if (points >= 6) {
            return Confidence.HIGH;
        }
        if (points >= 3) {
            return Confidence.MEDIUM;
        }
        return points >= 1 ? Confidence.LOW : Confidence.NONE;
    }

    /** Percentil con interpolacion lineal (p entre 0 y 100). */
    public static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        double index = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        double fraction = index - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }

    private static long clamp(double value, long min, long max) {
        long rounded = Math.round(value);
        return Math.max(min, max > 0 ? Math.min(max, rounded) : rounded);
    }
}
