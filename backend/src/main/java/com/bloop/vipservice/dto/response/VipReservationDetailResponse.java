package com.bloop.vipservice.dto.response;

import com.bloop.reservations.dto.response.ReservationResponse;

import java.util.List;

/** Ficha VIP de una reserva: datos, cuenta, pedidos y QR de la mesa. */
public record VipReservationDetailResponse(ReservationResponse reservation, VipAccountResponse account,
                                           List<VipOrderResponse> orders, TableQr qr) {

    /** token null = aun no generado. La pagina publica es /mesa/{token}. */
    public record TableQr(String token, boolean active) {
    }
}
