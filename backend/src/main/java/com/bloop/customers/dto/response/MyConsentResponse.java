package com.bloop.customers.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Empresa que tiene una ficha del cliente y su consentimiento comercial actual. */
public record MyConsentResponse(UUID customerId, String companyName, boolean marketingConsent,
                                Instant lastPurchaseAt) {
}
