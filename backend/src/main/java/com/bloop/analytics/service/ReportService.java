package com.bloop.analytics.service;

import com.bloop.analytics.dto.response.AnalyticsResponse;
import com.bloop.analytics.repository.AnalyticsRepository;
import com.bloop.analytics.repository.AnalyticsRepository.Period;
import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Informes descargables en CSV (se abren con Excel o LibreOffice).
 *
 * Formato pensado para Excel en espanol: separador ";" (la coma es el separador decimal),
 * importes con coma decimal, fechas en hora de Espana y BOM UTF-8 para que se vean las tildes.
 * Los informes contienen datos personales (email de compradores): su descarga queda auditada.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Tipos de informe disponibles: /api/analytics/reports/{tipo}.csv */
    public enum ReportType {
        ORDERS("pedidos"),
        POS_SALES("ventas-tpv"),
        PRODUCTS("productos"),
        PROMOTERS("promotores");

        private final String fileName;

        ReportType(String fileName) {
            this.fileName = fileName;
        }

        public static ReportType fromPath(String value) {
            return switch (value) {
                case "orders" -> ORDERS;
                case "pos-sales" -> POS_SALES;
                case "products" -> PRODUCTS;
                case "promoters" -> PROMOTERS;
                default -> throw NotFoundException.of("Informe");
            };
        }
    }

    public record CsvReport(String fileName, String content) {
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(AnalyticsService.ZONE);

    private final AnalyticsService analyticsService;
    private final AnalyticsRepository analyticsRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public CsvReport generate(ReportType type, LocalDate from, LocalDate to, UUID venueId) {
        Period period = analyticsService.period(from, to, venueId);
        StringBuilder csv = new StringBuilder("﻿");
        switch (type) {
            case ORDERS -> {
                line(csv, "Referencia", "Estado", "Creado", "Pagado", "Reembolsado", "Evento", "Local", "Sesion",
                        "Comprador", "Email", "Entradas", "Subtotal", "Descuento", "Total", "Codigo promocional",
                        "Comision promotor");
                analyticsRepository.forEachOrderRow(period, row -> line(csv, row));
            }
            case POS_SALES -> {
                line(csv, "Referencia", "Estado", "Fecha", "Local", "Terminal", "Metodo de pago", "Subtotal",
                        "Descuento", "Total", "Vendido por", "Motivo anulacion");
                analyticsRepository.forEachPosSaleRow(period, row -> line(csv, row));
            }
            case PRODUCTS -> {
                line(csv, "Producto", "Variante", "Unidades", "Importe");
                for (AnalyticsResponse.TopProduct p : analyticsRepository.topProducts(period, 10_000)) {
                    line(csv, p.productName(), p.variantName(), p.quantity(), p.revenue());
                }
            }
            case PROMOTERS -> {
                line(csv, "Promotor", "Codigo", "Pedidos", "Entradas", "Ventas", "Comision");
                for (AnalyticsResponse.PromoterPerformance p : analyticsRepository.promoters(period)) {
                    line(csv, p.promoterName(), p.referralCode(), p.orders(), p.tickets(), p.revenue(), p.commission());
                }
            }
        }
        auditService.record(AuditAction.REPORT_EXPORTED, "Report", type.name(), "Informe " + type.fileName
                + " del " + from + " al " + to + (venueId != null ? " local=" + venueId : ""));
        return new CsvReport("bloop-" + type.fileName + "-" + from + "-a-" + to + ".csv", csv.toString());
    }

    private static void line(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(format(values[i]));
        }
        csv.append("\r\n");
    }

    /** Convierte un valor a texto CSV y lo protege de comillas, separadores e inyeccion de formulas. */
    static String format(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        if (value instanceof BigDecimal amount) {
            text = amount.toPlainString().replace('.', ',');
        } else if (value instanceof Instant instant) {
            text = DATE_TIME.format(instant);
        } else {
            text = value.toString();
        }
        // Un texto que empieza por =, +, - o @ se ejecutaria como formula en Excel (CSV injection).
        if (!(value instanceof Number) && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
