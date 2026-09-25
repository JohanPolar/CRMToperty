package com.johan.crmtoperty.ingesta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

// Casos tomados de los datos reales de fuente-a.csv y webhooks.json
class NormalizadorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final Normalizador normalizador =
            new Normalizador(Clock.fixed(Instant.parse("2026-09-25T17:00:00Z"), BOGOTA));

    @Test
    void emailEnMinusculas() {
        assertThat(normalizador.email(" JCTORRES@Gmail.com ")).isEqualTo("jctorres@gmail.com");
        assertThat(normalizador.email("")).isNull();
    }

    @Test
    void celularSinIndicativoNiEspacios() {
        assertThat(normalizador.celular("+57 311 888 7766")).isEqualTo("3118887766");
        assertThat(normalizador.celular("3118887766")).isEqualTo("3118887766");
        assertThat(normalizador.celular("")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"Bogotá", "Bogota", "BOGOTÁ", "' bogota '"})
    void ciudadesEquivalentesDanLaMismaClave(String ciudad) {
        assertThat(normalizador.claveCiudad(ciudad)).isEqualTo("bogota");
    }

    @ParameterizedTest
    @CsvSource({
            "8200000,       8200000",
            "'$6.400.000',  6400000",
            "'$24.500.000', 24500000",
            "0,             0",
            "9400000.5,     9400000.5",
            "'9400000,5',   9400000.5",
            "'1,200,000',   1200000",
            "'$1,200,000.50', 1200000.50",
            "'1.200.000,50', 1200000.50",
            "'6.400',       6400",
            "'6,40',        6.40",
    })
    void montosValidos(String crudo, BigDecimal esperado) {
        var resultado = normalizador.monto("ahorro", crudo);
        assertThat(resultado.valor()).isEqualByComparingTo(esperado);
        assertThat(resultado.advertencia()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "1.2M,          1200000",
            "'1,2M',        1200000",
            "'$1.2 MM',     1200000",
            "2 millones,    2000000",
            "'1 millón',    1000000",
            "3.25 mill,     3250000",
    })
    void montosEnMillonesSeConviertenYSeAdvierten(String crudo, BigDecimal esperado) {
        var resultado = normalizador.monto("ahorro", crudo);
        assertThat(resultado.valor()).isEqualByComparingTo(esperado);
        assertThat(resultado.advertencia()).contains("abreviado");
    }

    @Test
    void montoVacioNoEsCero() {
        var resultado = normalizador.monto("ahorro", "");
        assertThat(resultado.valor()).isNull();
        assertThat(resultado.advertencia()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"abc", "-500", "'1,20,000'", "'1.200,000'", "1.2K", "'M'"})
    void montoIlegibleNoSeAdivina(String crudo) {
        var resultado = normalizador.monto("ahorro", crudo);
        assertThat(resultado.valor()).isNull();
        assertThat(resultado.advertencia()).contains("ilegible");
    }

    @Test
    void fechasEnLosTresFormatos() {
        Instant inicioDelDia = Instant.parse("2026-09-12T05:00:00Z"); // 00:00 en Bogotá
        assertThat(normalizador.fecha("2026-09-12").valor()).isEqualTo(inicioDelDia);
        assertThat(normalizador.fecha("12/09/2026").valor()).isEqualTo(inicioDelDia);
        assertThat(normalizador.fecha("2026-09-08T14:22:05Z").valor()).isEqualTo(Instant.parse("2026-09-08T14:22:05Z"));
    }

    @Test
    void fechaFuturaSeConservaConAdvertencia() {
        var resultado = normalizador.fecha("2027-01-15");
        assertThat(resultado.valor()).isNotNull();
        assertThat(resultado.advertencia()).contains("futuro");
    }

    @Test
    void fechaIlegible() {
        var resultado = normalizador.fecha("09-12-2026");
        assertThat(resultado.valor()).isNull();
        assertThat(resultado.advertencia()).contains("ilegible");
    }
}
