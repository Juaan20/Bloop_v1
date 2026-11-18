package com.bloop.payments.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Registro de webhooks de Stripe ya procesados (tabla stripe_webhook_events).
 * Es una tabla sin entidad JPA: solo necesitamos "insertar si no existe".
 */
@Repository
@RequiredArgsConstructor
public class StripeWebhookEventRepository {

    private final JdbcClient jdbc;

    /**
     * Inserta el evento si es nuevo. ON CONFLICT DO NOTHING es atomico: si dos peticiones con el
     * mismo evento llegan a la vez, solo una obtiene true.
     *
     * @return true si el evento no se habia recibido antes
     */
    public boolean registerIfNew(String eventId, String type, Instant receivedAt) {
        return jdbc.sql("""
                        INSERT INTO stripe_webhook_events (event_id, type, received_at)
                        VALUES (:id, :type, :at) ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("id", eventId).param("type", type).param("at", Timestamp.from(receivedAt))
                .update() == 1;
    }
}
