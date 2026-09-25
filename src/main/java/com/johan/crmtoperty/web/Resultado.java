package com.johan.crmtoperty.web;

import com.johan.crmtoperty.dominio.Decision;
import com.johan.crmtoperty.dominio.EstadoAplicacion;

/**
 * Lo que ve el equipo comercial: une estado y decisión en un solo filtro.
 * En la base son dos columnas porque una aplicación incompleta o pendiente no tiene decisión.
 */
public enum Resultado {
    APROBADA("Aprobadas", EstadoAplicacion.EVALUADA, Decision.APROBADA),
    RECHAZADA("Rechazadas", EstadoAplicacion.EVALUADA, Decision.RECHAZADA),
    INCOMPLETA("Incompletas", EstadoAplicacion.INCOMPLETA, null),
    PENDIENTE("Pendientes", EstadoAplicacion.PENDIENTE, null);

    private final String plural;
    private final EstadoAplicacion estado;
    private final Decision decision;

    Resultado(String plural, EstadoAplicacion estado, Decision decision) {
        this.plural = plural;
        this.estado = estado;
        this.decision = decision;
    }

    public static Resultado de(EstadoAplicacion estado, Decision decision) {
        return switch (estado) {
            case PENDIENTE -> PENDIENTE;
            case INCOMPLETA -> INCOMPLETA;
            case EVALUADA -> decision == Decision.APROBADA ? APROBADA : RECHAZADA;
        };
    }

    public String getPlural() {
        return plural;
    }

    public String getSingular() {
        return plural.substring(0, plural.length() - 1);
    }

    public String getClase() {
        return name().toLowerCase();
    }

    public EstadoAplicacion getEstado() {
        return estado;
    }

    public Decision getDecision() {
        return decision;
    }
}
