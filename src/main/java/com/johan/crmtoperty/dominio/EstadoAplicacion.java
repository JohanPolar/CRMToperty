package com.johan.crmtoperty.dominio;

/**
 * PENDIENTE: recién ingerida, el job aún no la evalúa.
 * EVALUADA: tiene decisión (aprobada o rechazada).
 * INCOMPLETA: falta un dato necesario para decidir; no es un rechazo de negocio.
 */
public enum EstadoAplicacion {
    PENDIENTE,
    EVALUADA,
    INCOMPLETA
}
