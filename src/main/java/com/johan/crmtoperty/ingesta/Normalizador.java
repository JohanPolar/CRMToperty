package com.johan.crmtoperty.ingesta;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Limpia los datos "como vienen en la vida real". Regla general: si un valor no se
 * puede interpretar con certeza, se devuelve null y se deja una advertencia; nunca
 * se adivina (p. ej. no se borran caracteres a ciegas de un monto).
 */
@Component
public class Normalizador {

    // Un separador seguido de exactamente 3 dígitos es de miles; seguido de 1 o 2, es decimal.
    // Por eso "6.400" es seis mil cuatrocientos y "6,40" es seis con cuarenta.
    // 6.400.000 o 6.400.000,50 (formato colombiano: punto de miles, coma decimal)
    private static final Pattern MILES_CON_PUNTO = Pattern.compile("\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?");
    // 1,200,000 o 1,200,000.50 (formato anglosajón: coma de miles, punto decimal)
    private static final Pattern MILES_CON_COMA = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?");
    // 8200000, 9400000.5 o 9400000,5
    private static final Pattern MONTO_SIMPLE = Pattern.compile("\\d+([.,]\\d{1,2})?");
    // 1.2M, 1,2 MM, 2 millones: el número antes del sufijo puede tener cualquier decimal
    private static final Pattern EN_MILLONES =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:m|mm|mill|millon|millón|millones)\\.?", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal UN_MILLON = BigDecimal.valueOf(1_000_000);
    private static final Pattern NO_DIGITOS = Pattern.compile("\\D");
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,          // 2026-09-01
            DateTimeFormatter.ofPattern("dd/MM/uuuu")  // 12/09/2026: día/mes, porque existe 15/09
    );

    private final Clock clock;

    public Normalizador(Clock clock) {
        this.clock = clock;
    }

    public String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = ESPACIOS.matcher(valor.strip()).replaceAll(" ");
        return limpio.isEmpty() ? null : limpio;
    }

    public String email(String valor) {
        String limpio = texto(valor);
        return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
    }

    /** Deja los 10 dígitos de un celular colombiano: "+57 311 888 7766" -> "3118887766". */
    public String celular(String valor) {
        String limpio = texto(valor);
        if (limpio == null) {
            return null;
        }
        String digitos = NO_DIGITOS.matcher(limpio).replaceAll("");
        if (digitos.length() == 12 && digitos.startsWith("57")) {
            digitos = digitos.substring(2);
        }
        return digitos.isEmpty() ? null : digitos;
    }

    public String programa(String valor) {
        String limpio = texto(valor);
        return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
    }

    /**
     * Llave para comparar ciudades: sin tildes, minúsculas y espacios simples.
     * "Bogotá", "BOGOTA" y " bogota " dan "bogota".
     */
    public String claveCiudad(String valor) {
        String limpio = texto(valor);
        if (limpio == null) {
            return null;
        }
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(limpio, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    public Resultado<BigDecimal> monto(String campo, String valor) {
        String limpio = texto(valor);
        if (limpio == null) {
            return Resultado.vacio();
        }
        String sinSimbolo = limpio.startsWith("$") ? limpio.substring(1).strip() : limpio;

        var enMillones = EN_MILLONES.matcher(sinSimbolo);
        if (enMillones.matches()) {
            BigDecimal monto = new BigDecimal(enMillones.group(1).replace(',', '.')).multiply(UN_MILLON);
            // Es una abreviatura, no un monto exacto: se deja constancia de cómo se leyó
            return Resultado.conAdvertencia(monto,
                    campo + " abreviado: '" + limpio + "' se leyó como " + monto.toPlainString());
        }

        BigDecimal monto = numeroConSeparadores(sinSimbolo);
        return monto != null ? Resultado.de(monto) : Resultado.invalido(campo + " ilegible: '" + limpio + "'");
    }

    /** Convierte a BigDecimal cualquiera de los tres formatos de monto aceptados, o null si no es ninguno. */
    private static BigDecimal numeroConSeparadores(String valor) {
        if (MILES_CON_PUNTO.matcher(valor).matches()) {
            return new BigDecimal(valor.replace(".", "").replace(',', '.'));
        }
        if (MILES_CON_COMA.matcher(valor).matches()) {
            return new BigDecimal(valor.replace(",", ""));
        }
        if (MONTO_SIMPLE.matcher(valor).matches()) {
            return new BigDecimal(valor.replace(',', '.'));
        }
        return null;
    }

    /**
     * Acepta ISO con zona (webhook), aaaa-mm-dd y dd/mm/aaaa (CSV). Las fechas sin hora
     * se toman al inicio del día en la zona configurada. Una fecha futura se acepta
     * pero se advierte: no participa en las reglas y rechazar por un error de captura
     * sería peor.
     */
    public Resultado<Instant> fecha(String valor) {
        String limpio = texto(valor);
        if (limpio == null) {
            return Resultado.vacio();
        }
        Instant instante = interpretarFecha(limpio);
        if (instante == null) {
            return Resultado.invalido("fecha_solicitud ilegible: '" + limpio + "'");
        }
        if (instante.isAfter(clock.instant())) {
            return Resultado.conAdvertencia(instante, "fecha_solicitud en el futuro: " + limpio);
        }
        return Resultado.de(instante);
    }

    private Instant interpretarFecha(String valor) {
        try {
            return OffsetDateTime.parse(valor).toInstant();
        } catch (DateTimeParseException ignorada) {
            // no trae hora; se prueban los formatos de solo fecha
        }
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(valor, formato).atStartOfDay(clock.getZone()).toInstant();
            } catch (DateTimeParseException ignorada) {
                // siguiente formato
            }
        }
        return null;
    }

    /** Valor interpretado (o null) más una advertencia opcional para mostrar al equipo comercial. */
    public record Resultado<T>(T valor, String advertencia) {

        static <T> Resultado<T> de(T valor) {
            return new Resultado<>(valor, null);
        }

        static <T> Resultado<T> vacio() {
            return new Resultado<>(null, null);
        }

        static <T> Resultado<T> invalido(String advertencia) {
            return new Resultado<>(null, advertencia);
        }

        static <T> Resultado<T> conAdvertencia(T valor, String advertencia) {
            return new Resultado<>(valor, advertencia);
        }
    }
}
