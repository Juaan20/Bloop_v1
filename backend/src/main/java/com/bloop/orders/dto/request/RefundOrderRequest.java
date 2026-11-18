package com.bloop.orders.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo del reembolso: queda en el pedido, en la auditoria y en el email al cliente. */
public record RefundOrderRequest(@NotBlank(message = "Indica el motivo del reembolso") @Size(max = 255) String reason) {
}
