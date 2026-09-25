package com.johan.crmtoperty.evaluacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un programa tal como viene en programas.json. */
public record Programa(String codigo, String nombre, BigDecimal ingresoMinimo, BigDecimal ahorroMinimo,
                       List<String> ciudades) {

    /** Copia que se guarda en aplicaciones.umbrales_aplicados: con qué reglas se decidió. */
    public Map<String, Object> comoUmbrales() {
        Map<String, Object> umbrales = new LinkedHashMap<>();
        umbrales.put("programa", codigo);
        umbrales.put("nombre", nombre);
        umbrales.put("ingreso_minimo", ingresoMinimo);
        umbrales.put("ahorro_minimo", ahorroMinimo);
        umbrales.put("ciudades", ciudades);
        return umbrales;
    }
}
