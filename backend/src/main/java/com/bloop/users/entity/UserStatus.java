package com.bloop.users.entity;

/** Estado de una cuenta. Un usuario DISABLED no puede iniciar sesion ni renovar su token. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
