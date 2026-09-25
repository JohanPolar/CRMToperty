package com.johan.crmtoperty.evaluacion;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.Decision;
import com.johan.crmtoperty.dominio.ResultadoRegla;
import com.johan.crmtoperty.ingesta.Normalizador;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decide con las tres reglas del enunciado. No sabe nada de base de datos ni de
 * notificaciones: recibe una aplicación y el catálogo y devuelve un veredicto.
 *
 * Primero revisa que haya con qué decidir; si falta algo, el veredicto es
 * INCOMPLETA y no se evalúa nada más (rechazar por un dato que no llegó sería
 * un error de negocio).
 */
@Component
public class MotorReglas {

    private final Normalizador normalizador;

    public MotorReglas(Normalizador normalizador) {
        this.normalizador = normalizador;
    }

    public Veredicto evaluar(Aplicacion aplicacion, Map<String, Programa> catalogo) {
        Programa programa = catalogo.get(aplicacion.getProgramaCodigo());
        List<ResultadoRegla> faltantes = new ArrayList<>();
        if (programa == null) {
            faltantes.add(new ResultadoRegla("programa", false, textoOVacio(aplicacion.getProgramaCodigo()),
                    "uno de " + catalogo.keySet()));
        }
        exigir(faltantes, "ingreso_mensual", aplicacion.getIngresoMensual());
        exigir(faltantes, "ahorro", aplicacion.getAhorro());
        exigir(faltantes, "ciudad", aplicacion.getCiudad());
        if (!faltantes.isEmpty()) {
            return Veredicto.incompleta(programa, faltantes);
        }

        List<ResultadoRegla> reglas = List.of(
                minimo("ingreso_minimo", aplicacion.getIngresoMensual(), programa.ingresoMinimo()),
                minimo("ahorro_minimo", aplicacion.getAhorro(), programa.ahorroMinimo()),
                ciudad(aplicacion.getCiudad(), programa.ciudades()));
        boolean cumpleTodas = reglas.stream().allMatch(ResultadoRegla::cumple);
        return Veredicto.decidida(programa, cumpleTodas ? Decision.APROBADA : Decision.RECHAZADA, reglas);
    }

    // "Mínimo" se interpreta como >=: quien tiene exactamente el umbral cumple
    private static ResultadoRegla minimo(String regla, BigDecimal valor, BigDecimal umbral) {
        return new ResultadoRegla(regla, valor.compareTo(umbral) >= 0, valor.toPlainString(), ">= " + umbral.toPlainString());
    }

    // Se compara la clave sin tildes ni mayúsculas en los dos lados: "Bogota" del CSV
    // coincide con "Bogotá" del JSON, y quien edite el JSON tampoco tiene que cuidar tildes
    private ResultadoRegla ciudad(String ciudad, List<String> permitidas) {
        String clave = normalizador.claveCiudad(ciudad);
        boolean cumple = permitidas.stream().map(normalizador::claveCiudad).anyMatch(clave::equals);
        return new ResultadoRegla("ciudad", cumple, ciudad, "una de " + permitidas);
    }

    private static void exigir(List<ResultadoRegla> faltantes, String campo, Object valor) {
        if (valor == null) {
            faltantes.add(new ResultadoRegla(campo, false, "(vacío)", "dato requerido"));
        }
    }

    private static String textoOVacio(String valor) {
        return valor == null || valor.isBlank() ? "(vacío)" : valor;
    }

    /** decision es null cuando la aplicación está incompleta; programa es null si no existe. */
    public record Veredicto(Programa programa, Decision decision, List<ResultadoRegla> motivos) {

        static Veredicto incompleta(Programa programa, List<ResultadoRegla> faltantes) {
            return new Veredicto(programa, null, faltantes);
        }

        static Veredicto decidida(Programa programa, Decision decision, List<ResultadoRegla> reglas) {
            return new Veredicto(programa, decision, reglas);
        }

        public boolean incompleta() {
            return decision == null;
        }
    }
}
