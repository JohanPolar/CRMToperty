package com.johan.crmtoperty.dominio;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Corre contra el Postgres de compose.yml; @Transactional revierte todo al terminar.
@SpringBootTest
@Transactional
class AplicacionPersistenciaTest {

    @Autowired
    private EntityManager em;

    @Test
    void guardaYLeeLosCamposJsonbYLaDecision() {
        Persona persona = new Persona("Juan Camilo Torres", "jctorres@gmail.com", "3118887766");
        em.persist(persona);

        Aplicacion aplicacion = new Aplicacion(persona, Fuente.WEBHOOK, "sub_9f2a41c7", "aurora", "Bogotá",
                new BigDecimal("9400000"), new BigDecimal("45000000"), Instant.parse("2026-09-08T14:22:05Z"),
                Map.of("submission_id", "sub_9f2a41c7", "answers", Map.of("savings", 45000000)));
        aplicacion.agregarAdvertencia("prueba");
        em.persist(aplicacion);
        em.flush();

        aplicacion.registrarDecision(Decision.APROBADA,
                List.of(new ResultadoRegla("ahorro_minimo", true, "45000000", ">= 20000000")),
                Map.of("ahorro_minimo", 20000000), Instant.now());
        em.flush();
        em.clear();

        Aplicacion leida = em.find(Aplicacion.class, aplicacion.getId());
        assertThat(leida.getEstado()).isEqualTo(EstadoAplicacion.EVALUADA);
        assertThat(leida.getDecision()).isEqualTo(Decision.APROBADA);
        assertThat(leida.getMotivos()).containsExactly(
                new ResultadoRegla("ahorro_minimo", true, "45000000", ">= 20000000"));
        assertThat(leida.getAdvertencias()).containsExactly("prueba");
        assertThat(leida.getPayloadCrudo()).containsEntry("submission_id", "sub_9f2a41c7");
        assertThat(leida.getAhorro()).isEqualByComparingTo("45000000");
        assertThat(leida.getRecibidaEn()).isNotNull();
    }

    @Test
    void unaAplicacionYaEvaluadaNoSePuedeReevaluar() {
        Persona persona = new Persona("Camila Rueda", "crueda@gmail.com", null);
        em.persist(persona);
        Aplicacion aplicacion = new Aplicacion(persona, Fuente.WEBHOOK, "sub_7e55b013", "aurora", "Medellín",
                new BigDecimal("7800000"), BigDecimal.ZERO, Instant.now(), Map.of());
        aplicacion.registrarDecision(Decision.RECHAZADA, List.of(), Map.of(), Instant.now());

        assertThatThrownBy(() -> aplicacion.marcarIncompleta(List.of(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
