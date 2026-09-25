package com.johan.crmtoperty.evaluacion;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.Decision;
import com.johan.crmtoperty.dominio.Fuente;
import com.johan.crmtoperty.dominio.Persona;
import com.johan.crmtoperty.dominio.ResultadoRegla;
import com.johan.crmtoperty.ingesta.Normalizador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MotorReglasTest {

    private static final Map<String, Programa> CATALOGO = Map.of("aurora", new Programa("aurora", "Programa Aurora",
            new BigDecimal("4000000"), new BigDecimal("20000000"), List.of("Bogotá", "Medellín", "Cali")));

    private final MotorReglas motor = new MotorReglas(new Normalizador(Clock.systemUTC()));

    @Test
    void apruebaSiCumpleLasTres() {
        var veredicto = motor.evaluar(aplicacion("aurora", "Bogotá", "8200000", "31000000"), CATALOGO);
        assertThat(veredicto.decision()).isEqualTo(Decision.APROBADA);
        assertThat(veredicto.motivos()).allMatch(ResultadoRegla::cumple);
    }

    @Test
    void elUmbralExactoCumple() {
        var veredicto = motor.evaluar(aplicacion("aurora", "Cali", "4000000", "20000000"), CATALOGO);
        assertThat(veredicto.decision()).isEqualTo(Decision.APROBADA);
    }

    @Test
    void rechazaYDiceQueReglaFallo() {
        // Esteban: le faltan 500 mil de ahorro
        var veredicto = motor.evaluar(aplicacion("aurora", "Medellín", "7600000", "19500000"), CATALOGO);
        assertThat(veredicto.decision()).isEqualTo(Decision.RECHAZADA);
        assertThat(veredicto.motivos()).filteredOn(r -> !r.cumple())
                .extracting(ResultadoRegla::regla).containsExactly("ahorro_minimo");
    }

    @Test
    void laCiudadSeComparaSinTildes() {
        var veredicto = motor.evaluar(aplicacion("aurora", "Bogota", "7900000", "28000000"), CATALOGO);
        assertThat(veredicto.decision()).isEqualTo(Decision.APROBADA);
    }

    @Test
    void ahorroCeroEsUnRechazoPeroAhorroVacioEsIncompleta() {
        assertThat(motor.evaluar(aplicacion("aurora", "Bogotá", "5400000", "0"), CATALOGO).decision())
                .isEqualTo(Decision.RECHAZADA);

        var sinAhorro = motor.evaluar(aplicacion("aurora", "Cali", "6100000", null), CATALOGO);
        assertThat(sinAhorro.incompleta()).isTrue();
        assertThat(sinAhorro.motivos()).extracting(ResultadoRegla::regla).containsExactly("ahorro");
    }

    @Test
    void programaDesconocidoEsIncompleta() {
        var veredicto = motor.evaluar(aplicacion("marea", "Bogotá", "9000000", "50000000"), CATALOGO);
        assertThat(veredicto.incompleta()).isTrue();
        assertThat(veredicto.programa()).isNull();
        assertThat(veredicto.motivos()).extracting(ResultadoRegla::regla).containsExactly("programa");
    }

    private static Aplicacion aplicacion(String programa, String ciudad, String ingreso, String ahorro) {
        return new Aplicacion(new Persona("Prueba", "prueba@correo.com", null), Fuente.WEBHOOK, "x", programa, ciudad,
                ingreso == null ? null : new BigDecimal(ingreso), ahorro == null ? null : new BigDecimal(ahorro),
                Instant.now(), Map.of());
    }
}
