package com.johan.crmtoperty.evaluacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lee programas.json en cada corrida (sin caché): cambiar un umbral o agregar un
 * programa es editar el archivo, y aplica desde la siguiente corrida sin reiniciar.
 *
 * Si el archivo está mal, falla la corrida completa en vez de evaluar con reglas
 * a medias: las aplicaciones siguen PENDIENTES y se evalúan cuando se corrija.
 */
@Component
public class CatalogoProgramas {

    private final Path archivo;
    private final JsonMapper jsonMapper;

    public CatalogoProgramas(@Value("${app.programas.path}") Path archivo, JsonMapper jsonMapper) {
        this.archivo = archivo;
        this.jsonMapper = jsonMapper;
    }

    /** Programas por código en minúsculas, que es como los guarda la ingesta. */
    public Map<String, Programa> cargar() {
        JsonNode raiz = leerArchivo();
        if (!raiz.isObject() || raiz.isEmpty()) {
            throw new ProgramasInvalidosException(archivo + " debe ser un objeto con al menos un programa");
        }
        Map<String, Programa> programas = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entrada : raiz.properties()) {
            String codigo = entrada.getKey().strip().toLowerCase(Locale.ROOT);
            programas.put(codigo, aPrograma(codigo, entrada.getValue()));
        }
        return programas;
    }

    private JsonNode leerArchivo() {
        try {
            return jsonMapper.readTree(Files.readString(archivo, StandardCharsets.UTF_8));
        } catch (IOException | JacksonException e) {
            throw new ProgramasInvalidosException("No se pudo leer " + archivo + ": " + e.getMessage());
        }
    }

    private static Programa aPrograma(String codigo, JsonNode nodo) {
        String nombre = nodo.path("nombre").isString() ? nodo.get("nombre").asString() : codigo;
        return new Programa(codigo, nombre,
                montoObligatorio(codigo, nodo, "ingreso_minimo"),
                montoObligatorio(codigo, nodo, "ahorro_minimo"),
                ciudades(codigo, nodo));
    }

    private static BigDecimal montoObligatorio(String codigo, JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        if (!valor.isNumber() || valor.decimalValue().signum() < 0) {
            throw new ProgramasInvalidosException("Programa '" + codigo + "': " + campo + " debe ser un número >= 0");
        }
        return valor.decimalValue();
    }

    private static List<String> ciudades(String codigo, JsonNode nodo) {
        JsonNode lista = nodo.path("ciudades");
        List<String> ciudades = new ArrayList<>();
        for (JsonNode ciudad : lista) {
            if (ciudad.isString() && !ciudad.asString().isBlank()) {
                ciudades.add(ciudad.asString().strip());
            }
        }
        if (!lista.isArray() || ciudades.isEmpty()) {
            throw new ProgramasInvalidosException("Programa '" + codigo + "': ciudades debe ser una lista no vacía");
        }
        return List.copyOf(ciudades);
    }

    public static class ProgramasInvalidosException extends RuntimeException {
        public ProgramasInvalidosException(String mensaje) {
            super(mensaje);
        }
    }
}
