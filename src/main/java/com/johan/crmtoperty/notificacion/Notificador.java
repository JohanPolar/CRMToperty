package com.johan.crmtoperty.notificacion;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.Decision;
import com.johan.crmtoperty.dominio.EstadoNotificacion;
import com.johan.crmtoperty.dominio.Persona;
import com.johan.crmtoperty.dominio.ResultadoRegla;
import com.johan.crmtoperty.dominio.TipoNotificacion;
import com.johan.crmtoperty.evaluacion.MotorReglas.Veredicto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Notificar" es escribir en la tabla notificaciones; no hay servicio externo.
 * Debe llamarse dentro de la misma transacción que guarda la decisión: o quedan
 * las dos cosas o ninguna (patrón outbox).
 */
@Component
public class Notificador {

    // ON CONFLICT DO NOTHING hace el INSERT idempotente y atómico: si la notificación
    // ya existe no inserta ni lanza error, así que no rompe la transacción de la decisión.
    private static final String INSERTAR = """
            INSERT INTO notificaciones (aplicacion_id, persona_id, tipo, destinatario, asunto, cuerpo, estado)
            VALUES (:aplicacion, :persona, :tipo, :destinatario, :asunto, :cuerpo, :estado)
            ON CONFLICT (aplicacion_id, tipo) DO NOTHING
            """;

    // Cómo se nombra cada regla en el mensaje a la persona
    private static final Map<String, String> EN_PALABRAS = Map.of(
            "ingreso_minimo", "ingreso mínimo",
            "ahorro_minimo", "ahorro mínimo",
            "ciudad", "ciudad donde opera el programa",
            "programa", "programa válido",
            "ingreso_mensual", "ingreso mensual",
            "ahorro", "ahorro");

    private final JdbcClient jdbc;

    public Notificador(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true si se registró una notificación nueva, false si ya existía. */
    public boolean notificar(Aplicacion aplicacion, Veredicto veredicto) {
        Persona persona = aplicacion.getPersona();
        TipoNotificacion tipo = veredicto.incompleta() ? TipoNotificacion.INCOMPLETA : TipoNotificacion.DECISION;
        String programa = veredicto.programa() != null ? veredicto.programa().nombre() : "nuestro programa";
        // Sin email se registra igual, para que el equipo comercial vea a quién hay que contactar por otro medio
        EstadoNotificacion estado = persona.getEmail() != null ? EstadoNotificacion.REGISTRADA : EstadoNotificacion.SIN_DESTINATARIO;

        int filas = jdbc.sql(INSERTAR)
                .param("aplicacion", aplicacion.getId())
                .param("persona", persona.getId())
                .param("tipo", tipo.name())
                .param("destinatario", persona.getEmail())
                .param("asunto", asunto(veredicto, programa))
                .param("cuerpo", cuerpo(persona, veredicto, programa))
                .param("estado", estado.name())
                .update();
        return filas == 1;
    }

    private static String asunto(Veredicto veredicto, String programa) {
        if (veredicto.incompleta()) {
            return "Tu solicitud a " + programa + " está incompleta";
        }
        return veredicto.decision() == Decision.APROBADA
                ? "Tu solicitud a " + programa + " fue aprobada"
                : "Resultado de tu solicitud a " + programa;
    }

    private static String cuerpo(Persona persona, Veredicto veredicto, String programa) {
        String saludo = "Hola " + persona.getNombreCompleto() + ",\n\n";
        if (veredicto.incompleta()) {
            return saludo + "Para evaluar tu solicitud nos falta: " + reglasNoCumplidas(veredicto)
                    + ". Un asesor te contactará para completarla.";
        }
        if (veredicto.decision() == Decision.APROBADA) {
            return saludo + "Cumples los requisitos de " + programa + ". Un asesor te contactará con los siguientes pasos.";
        }
        return saludo + "Por ahora tu solicitud a " + programa + " no cumple: " + reglasNoCumplidas(veredicto) + ".";
    }

    private static String reglasNoCumplidas(Veredicto veredicto) {
        return veredicto.motivos().stream()
                .filter(regla -> !regla.cumple())
                .map(ResultadoRegla::regla)
                .map(nombre -> EN_PALABRAS.getOrDefault(nombre, nombre.replace('_', ' ')))
                .collect(Collectors.joining(", "));
    }
}
