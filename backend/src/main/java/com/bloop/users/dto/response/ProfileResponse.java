package com.bloop.users.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(UUID id, String email, String firstName, String lastName, String phone,
                              boolean marketingConsent, Instant privacyAcceptedAt, Instant createdAt) {
}
