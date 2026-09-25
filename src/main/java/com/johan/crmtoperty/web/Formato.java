package com.johan.crmtoperty.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Formatos para las plantillas, que lo usan como ${@fmt.pesos(...)}.
 * Todo en convención colombiana: $ 6.400.000 y fechas en hora de Bogotá.
 */
@Component("fmt")
public class Formato {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final String SIN_DATO = "—";
    private static final Pattern ES_MINIMO = Pattern.compile(">=\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern LISTA = Pattern.compile("(un[oa]) de \\[(.*)]");
    private static final Map<String, String> REGLAS = Map.of(
            "ingreso_minimo", "Ingreso mínimo",
            "ahorro_minimo", "Ahorro mínimo",
            "ciudad", "Ciudad",
            "programa", "Programa",
            "ingreso_mensual", "Ingreso mensual",
            "ahorro", "Ahorro");

    private final Clock clock;
    private final DateTimeFormatter fecha;
    private final DateTimeFormatter fechaHora;

    public Formato(Clock clock) {
        this.clock = clock;
        this.fecha = DateTimeFormatter.ofPattern("d MMM yyyy", ES_CO).withZone(clock.getZone());
        this.fechaHora = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ES_CO).withZone(clock.getZone());
    }

    /** Acepta BigDecimal, cualquier Number (p. ej. de un jsonb) o texto numérico. */
    public String pesos(Object valor) {
        BigDecimal monto = aDecimal(valor);
        if (monto == null) {
            return SIN_DATO;
        }
        boolean conCentavos = monto.stripTrailingZeros().scale() > 0;
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(ES_CO);
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        return "$ " + new DecimalFormat(conCentavos ? "#,##0.00" : "#,##0", simbolos).format(monto);
    }

    public String fecha(Instant instante) {
        return instante == null ? SIN_DATO : fecha.format(instante);
    }

    public String fechaHora(Instant instante) {
        return instante == null ? SIN_DATO : fechaHora.format(instante);
    }

    public String hace(Instant instante) {
        if (instante == null) {
            return "nunca";
        }
        Duration transcurrido = Duration.between(instante, clock.instant());
        if (transcurrido.toMinutes() < 1) {
            return "hace unos segundos";
        }
        if (transcurrido.toHours() < 1) {
            return "hace " + transcurrido.toMinutes() + " min";
        }
        if (transcurrido.toDays() < 1) {
            return "hace " + transcurrido.toHours() + " h";
        }
        return fechaHora(instante);
    }

    /** "3118887766" -> "311 888 7766". */
    public String celular(String celular) {
        if (celular == null) {
            return SIN_DATO;
        }
        return celular.length() == 10
                ? celular.substring(0, 3) + " " + celular.substring(3, 6) + " " + celular.substring(6)
                : celular;
    }

    public String regla(String clave) {
        return REGLAS.getOrDefault(clave, clave);
    }

    /** Valor de una regla: los montos se muestran en pesos, lo demás tal cual. */
    public String valorRegla(String regla, String valor) {
        return regla.endsWith("minimo") && aDecimal(valor) != null ? pesos(valor) : valor;
    }

    /** ">= 20000000" -> "Requiere ≥ $ 20.000.000"; "una de [Bogotá, Cali]" -> "Requiere una de: Bogotá, Cali". */
    public String esperadoRegla(String esperado) {
        Matcher minimo = ES_MINIMO.matcher(esperado);
        if (minimo.matches()) {
            return "Requiere ≥ " + pesos(minimo.group(1));
        }
        Matcher lista = LISTA.matcher(esperado);
        if (lista.matches()) {
            return "Requiere " + lista.group(1) + " de: " + lista.group(2);
        }
        return "dato requerido".equals(esperado) ? "Dato obligatorio que no llegó" : esperado;
    }

    public String lista(Object valor) {
        if (valor instanceof Collection<?> elementos) {
            return elementos.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return valor == null ? SIN_DATO : valor.toString();
    }

    private static BigDecimal aDecimal(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(valor.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
