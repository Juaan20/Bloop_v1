package com.bloop.customers.entity;

/** Donde se dio o retiro un consentimiento. */
public enum ConsentSource {
    /** Casilla marcada al comprar entradas. */
    CHECKOUT,
    /** El propio cliente desde "Mi cuenta > Privacidad". */
    CUSTOMER,
    /** Un empleado lo registro a peticion del cliente. */
    BACKOFFICE,
    /** Dato importado al crear el CRM. */
    MIGRATION
}
