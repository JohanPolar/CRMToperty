package com.johan.crmtoperty.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoTest {

    private final Formato fmt = new Formato(Clock.fixed(Instant.parse("2026-09-25T17:00:00Z"), ZoneId.of("America/Bogota")));

    @Test
    void pesosEnFormatoColombiano() {
        assertThat(fmt.pesos(new BigDecimal("6400000"))).isEqualTo("$ 6.400.000");
        assertThat(fmt.pesos(new BigDecimal("1200000.50"))).isEqualTo("$ 1.200.000,50");
        assertThat(fmt.pesos(20000000)).isEqualTo("$ 20.000.000");
        assertThat(fmt.pesos(null)).isEqualTo("—");
    }

    @Test
    void reglasEnLenguajeDelEquipoComercial() {
        assertThat(fmt.esperadoRegla(">= 20000000")).isEqualTo("Requiere ≥ $ 20.000.000");
        assertThat(fmt.esperadoRegla("una de [Bogotá, Medellín, Cali]")).isEqualTo("Requiere una de: Bogotá, Medellín, Cali");
        assertThat(fmt.esperadoRegla("dato requerido")).isEqualTo("Dato obligatorio que no llegó");
        assertThat(fmt.valorRegla("ahorro_minimo", "19500000")).isEqualTo("$ 19.500.000");
        assertThat(fmt.valorRegla("ciudad", "Bogota")).isEqualTo("Bogota");
    }

    @Test
    void celularYListas() {
        assertThat(fmt.celular("3118887766")).isEqualTo("311 888 7766");
        assertThat(fmt.lista(List.of("Bogotá", "Cali"))).isEqualTo("Bogotá, Cali");
    }
}
