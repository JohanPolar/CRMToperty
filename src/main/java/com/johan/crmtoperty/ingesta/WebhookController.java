package com.johan.crmtoperty.ingesta;

import com.johan.crmtoperty.dominio.Fuente;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recibe el webhook como un mapa genérico y no como una clase fija: así se guarda
 * el payload completo tal como llegó (incluidos campos que hoy no usamos) y un
 * campo faltante no rechaza la solicitud, solo la deja incompleta.
 */
@RestController
public class WebhookController {

    private final IngestaService ingesta;

    public WebhookController(IngestaService ingesta) {
        this.ingesta = ingesta;
    }

    @PostMapping("/aplicaciones")
    public ResponseEntity<Map<String, Object>> recibir(@RequestBody Map<String, Object> payload) {
        String submissionId = texto(payload.get("submission_id"));
        // Sin id no hay forma de reconocer un reintento: es el único campo que exigimos
        if (submissionId == null || submissionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "submission_id es obligatorio"));
        }

        Map<?, ?> applicant = mapa(payload.get("applicant"));
        Map<?, ?> answers = mapa(payload.get("answers"));
        String nombre = Stream.of(texto(applicant.get("first_name")), texto(applicant.get("last_name")))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        ResultadoIngesta resultado = ingesta.registrar(new AplicacionEntrante(
                Fuente.WEBHOOK,
                submissionId,
                nombre,
                texto(applicant.get("email")),
                texto(applicant.get("phone")),
                texto(answers.get("city")),
                texto(answers.get("monthly_income")),
                texto(answers.get("savings")),
                texto(payload.get("submitted_at")),
                texto(payload.get("program")),
                payload));

        // 202: recibida, se evaluará en la próxima corrida. 200 en el reintento, no un error:
        // si respondiéramos 409 el emisor podría seguir reintentando.
        HttpStatus estado = resultado.nueva() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(Map.of(
                "id", resultado.aplicacionId(),
                "estado", resultado.nueva() ? "RECIBIDA" : "DUPLICADA"));
    }

    private static Map<?, ?> mapa(Object valor) {
        return valor instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String texto(Object valor) {
        if (valor instanceof Number numero) {
            // Un Double grande se imprime como "1.0E7"; en notación plana el Normalizador lo entiende
            return new BigDecimal(numero.toString()).toPlainString();
        }
        return valor == null ? null : valor.toString();
    }
}
