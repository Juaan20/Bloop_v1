package com.bloop.customers.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Exportacion RGPD (derecho de acceso y portabilidad) de los datos que UNA empresa tiene
 * de un cliente. Se descarga como JSON desde la ficha del cliente.
 */
public record CustomerExportResponse(Instant exportedAt, String companyName, CustomerResponse customer, String notes,
                                     List<CustomerDetailResponse.ConsentInfo> consents,
                                     List<CustomerDetailResponse.OrderSummary> orders,
                                     List<CustomerDetailResponse.ReservationSummary> reservations) {
}
