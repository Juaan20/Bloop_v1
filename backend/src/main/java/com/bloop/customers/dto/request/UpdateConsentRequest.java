package com.bloop.customers.dto.request;

/** El cliente da o retira su consentimiento comercial para una empresa. */
public record UpdateConsentRequest(boolean marketingConsent) {
}
