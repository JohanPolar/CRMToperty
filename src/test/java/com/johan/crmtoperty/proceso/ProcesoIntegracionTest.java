package com.johan.crmtoperty.proceso;

import com.johan.crmtoperty.dominio.EjecucionProceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * El sistema completo con los archivos reales de data/: webhooks + CSV, evaluación
 * y notificación. Verifica los totales esperados y que correr el proceso diez veces
 * no cambie nada. Corre contra el Postgres de compose.yml y revierte todo al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProcesoIntegracionTest {

    @Autowired
    private ProcesoAplicaciones proceso;
    @Autowired
    private CandadoProceso candado;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void enviarWebhooks() throws Exception {
        JsonNode webhooks = jsonMapper.readTree(Files.readString(Path.of("data/webhooks.json")));
        for (JsonNode webhook : webhooks) {
            mockMvc.perform(post("/aplicaciones").contentType(MediaType.APPLICATION_JSON).content(webhook.toString()));
        }
    }

    @Test
    void laPrimeraCorridaEvaluaYNotificaTodo() {
        EjecucionProceso corrida = proceso.ejecutar().orElseThrow();

        assertThat(corrida.getError()).isNull();
        assertThat(corrida.getNuevas()).isEqualTo(16);
        assertThat(corrida.getEvaluadas()).isEqualTo(21);
        assertThat(corrida.getNotificadas()).isEqualTo(21);

        assertThat(contarPor("coalesce(decision, estado)", "aplicaciones")).isEqualTo(Map.of(
                "APROBADA", 11L, "RECHAZADA", 9L, "INCOMPLETA", 1L));
        assertThat(contarPor("tipo", "notificaciones")).isEqualTo(Map.of("DECISION", 20L, "INCOMPLETA", 1L));
        // Natalia no tiene email: su notificación queda registrada para que el equipo la contacte
        assertThat(contarPor("estado", "notificaciones")).isEqualTo(Map.of("REGISTRADA", 20L, "SIN_DESTINATARIO", 1L));
    }

    @Test
    void correrElProcesoDiezVecesNoGeneraDiezNotificaciones() {
        proceso.ejecutar();
        for (int i = 0; i < 9; i++) {
            EjecucionProceso corrida = proceso.ejecutar().orElseThrow();
            assertThat(corrida.getNuevas()).isZero();
            assertThat(corrida.getEvaluadas()).isZero();
            assertThat(corrida.getNotificadas()).isZero();
        }

        assertThat(jdbc.sql("SELECT count(*) FROM notificaciones").query(Long.class).single()).isEqualTo(21);
        // Juan aplicó dos veces: una notificación por cada aplicación, no diez
        assertThat(jdbc.sql("""
                SELECT count(*) FROM notificaciones n JOIN personas p ON p.id = n.persona_id
                WHERE p.email = 'jctorres@gmail.com'""").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void siOtraCorridaTieneElCandadoEstaSeOmite() {
        try (CandadoProceso.Candado ocupado = candado.intentarTomar().orElseThrow()) {
            assertThat(proceso.ejecutar()).isEmpty();
        }
        assertThat(proceso.ejecutar()).isPresent();
    }

    private Map<String, Long> contarPor(String columna, String tabla) {
        return jdbc.sql("SELECT " + columna + " AS clave, count(*) AS total FROM " + tabla + " GROUP BY 1")
                .query((fila, n) -> Map.entry(fila.getString("clave"), fila.getLong("total")))
                .list().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
