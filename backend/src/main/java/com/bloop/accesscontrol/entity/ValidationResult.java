package com.bloop.accesscontrol.entity;

/** Resultado de un escaneo en la puerta. Solo ACCEPTED permite la entrada. */
public enum ValidationResult {
    ACCEPTED,
    ALREADY_USED,
    INVALID_CODE,
    WRONG_SESSION,
    CANCELLED,
    NOT_PAID
}
