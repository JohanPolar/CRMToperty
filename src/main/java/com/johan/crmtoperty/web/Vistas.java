package com.johan.crmtoperty.web;

import com.johan.crmtoperty.dominio.Fuente;
import com.johan.crmtoperty.dominio.ResultadoRegla;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lo que reciben las plantillas. Son copias planas armadas dentro de la transacción,
 * así la vista nunca toca una relación perezosa de JPA (open-in-view está apagado).
 */
public final class Vistas {

    private Vistas() {
    }

    public record Fila(Long id, String nombre, String email, String programa, String ciudad,
                       BigDecimal ingreso, BigDecimal ahorro, Instant fechaSolicitud, Fuente fuente,
                       Resultado resultado, List<String> advertencias) {
    }

    public record Conteo(Resultado resultado, long total) {
    }

    public record OpcionPrograma(String codigo, String nombre) {
    }

    public record Detalle(Long id, Resultado resultado, String programaCodigo, String programaNombre,
                          String ciudad, BigDecimal ingreso, BigDecimal ahorro, Instant fechaSolicitud,
                          Instant recibidaEn, Instant evaluadaEn, Fuente fuente, String idExterno,
                          List<ResultadoRegla> motivos, List<String> advertencias,
                          Map<String, Object> umbrales, String payload,
                          Persona persona, List<Fila> otras, List<Aviso> notificaciones) {

        public long reglasCumplidas() {
            return motivos == null ? 0 : motivos.stream().filter(ResultadoRegla::cumple).count();
        }
    }

    public record Persona(String nombre, String email, String celular) {

        /** "Juan Camilo Torres" -> "JC"; para el avatar. */
        public String iniciales() {
            String iniciales = Arrays.stream(nombre.split("\\s+"))
                    .filter(parte -> !parte.isEmpty() && Character.isLetter(parte.charAt(0)))
                    .limit(2)
                    .map(parte -> parte.substring(0, 1).toUpperCase())
                    .collect(Collectors.joining());
            return iniciales.isEmpty() ? "?" : iniciales;
        }
    }

    public record Aviso(String tipo, String destinatario, String asunto, String cuerpo, String estado, Instant creadaEn) {
    }
}
