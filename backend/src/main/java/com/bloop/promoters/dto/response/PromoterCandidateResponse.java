package com.bloop.promoters.dto.response;

import java.util.UUID;

/** Empleado con rol PROMOTER que aun no tiene ficha de promotor. */
public record PromoterCandidateResponse(UUID userId, String name, String email) {
}
