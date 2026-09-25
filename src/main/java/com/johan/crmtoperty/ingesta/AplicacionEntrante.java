package com.johan.crmtoperty.ingesta;

import com.johan.crmtoperty.dominio.Fuente;

import java.util.Map;

/**
 * Modelo canónico: el CSV y el webhook se traducen a esto, sin limpiar todavía.
 * Los valores van como texto tal cual llegaron; el Normalizador es el único que
 * los interpreta, así las dos fuentes pasan por exactamente las mismas reglas.
 */
public record AplicacionEntrante(
        Fuente fuente,
        String idExterno,
        String nombreCompleto,
        String email,
        String celular,
        String ciudad,
        String ingresoMensual,
        String ahorro,
        String fechaSolicitud,
        String programa,
        Map<String, Object> payloadCrudo
) {
}
