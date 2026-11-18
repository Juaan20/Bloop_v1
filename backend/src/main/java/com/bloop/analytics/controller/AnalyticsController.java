package com.bloop.analytics.controller;

import com.bloop.analytics.dto.response.AnalyticsResponse;
import com.bloop.analytics.service.AnalyticsService;
import com.bloop.analytics.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/** Analitica avanzada e informes descargables (permiso REPORT_VIEW). */
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAuthority('REPORT_VIEW')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;
    private final com.bloop.analytics.service.ForecastService forecastService;

    /** Proximas sesiones para elegir cual prever. */
    @GetMapping("/forecast")
    public java.util.List<com.bloop.analytics.dto.response.ForecastResponse.UpcomingSession> upcoming() {
        return forecastService.upcoming();
    }

    /** Prevision de asistencia y comparativa con noches similares de una sesion. */
    @GetMapping("/forecast/{sessionId}")
    public com.bloop.analytics.dto.response.ForecastResponse forecast(@org.springframework.web.bind.annotation.PathVariable UUID sessionId) {
        return forecastService.forecast(sessionId);
    }

    /** Ejemplo: GET /api/analytics?from=2026-11-01&to=2026-11-30&venueId=... */
    @GetMapping
    public AnalyticsResponse overview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID venueId) {
        return analyticsService.overview(from, to, venueId);
    }

    /** Informes CSV: orders, pos-sales, products, promoters. */
    @GetMapping("/reports/{type}.csv")
    public ResponseEntity<byte[]> report(@PathVariable String type,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) UUID venueId) {
        var report = reportService.generate(ReportService.ReportType.fromPath(type), from, to, venueId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.content().getBytes(StandardCharsets.UTF_8));
    }
}
