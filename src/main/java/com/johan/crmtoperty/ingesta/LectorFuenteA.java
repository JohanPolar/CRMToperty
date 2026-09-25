package com.johan.crmtoperty.ingesta;

import com.johan.crmtoperty.dominio.Fuente;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee fuente-a.csv completo en cada corrida. Releerlo es seguro porque cada fila
 * tiene un id estable (hash de su contenido) y la ingesta descarta las ya vistas.
 * Así no hace falta mover ni borrar el archivo, que es otra operación que puede fallar a medias.
 */
@Component
public class LectorFuenteA {

    private static final Logger log = LoggerFactory.getLogger(LectorFuenteA.class);
    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private final Path archivo;
    private final IngestaService ingesta;

    public LectorFuenteA(@Value("${app.fuente-a.path}") Path archivo, IngestaService ingesta) {
        this.archivo = archivo;
        this.ingesta = ingesta;
    }

    public ResumenLectura leer() {
        if (!Files.exists(archivo)) {
            log.info("No hay archivo en {}; nada que leer", archivo.toAbsolutePath());
            return new ResumenLectura(0, 0, 0, 0);
        }
        int leidas = 0, nuevas = 0, duplicadas = 0, descartadas = 0;
        try (Reader lector = Files.newBufferedReader(archivo, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(lector, FORMATO)) {
            for (CSVRecord fila : parser) {
                leidas++;
                // Una fila mala no debe tumbar el archivo completo: se registra y se sigue
                if (!fila.isConsistent()) {
                    descartadas++;
                    log.warn("Fila {} descartada: tiene {} columnas y el encabezado {}",
                            fila.getRecordNumber(), fila.size(), parser.getHeaderNames().size());
                    continue;
                }
                ResultadoIngesta resultado = ingesta.registrar(aEntrante(fila));
                if (resultado.nueva()) {
                    nuevas++;
                } else {
                    duplicadas++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
        return new ResumenLectura(leidas, nuevas, duplicadas, descartadas);
    }

    private static AplicacionEntrante aEntrante(CSVRecord fila) {
        Map<String, Object> payload = new LinkedHashMap<>(fila.toMap());
        return new AplicacionEntrante(
                Fuente.CSV,
                huella(fila),
                fila.get("nombre_completo"),
                fila.get("correo"),
                fila.get("celular"),
                fila.get("ciudad"),
                fila.get("ingresos_mensuales"),
                fila.get("ahorros"),
                fila.get("fecha_solicitud"),
                fila.get("programa"),
                payload);
    }

    /**
     * El CSV no trae id: se usa el SHA-256 de los valores de la fila. Misma fila, mismo id.
     * Costo asumido: si alguien corrige una fila, entra como una aplicación nueva.
     */
    private static String huella(CSVRecord fila) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(String.join("\u001F", fila.values()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 siempre existe en la JVM", e);
        }
    }

    public record ResumenLectura(int leidas, int nuevas, int duplicadas, int descartadas) {
    }
}
