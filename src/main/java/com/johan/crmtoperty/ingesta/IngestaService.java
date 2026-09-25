package com.johan.crmtoperty.ingesta;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.AplicacionRepository;
import com.johan.crmtoperty.dominio.Persona;
import com.johan.crmtoperty.dominio.PersonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Punto único de entrada al sistema: el CSV y el webhook terminan aquí.
 * Guarda la aplicación como PENDIENTE; evaluar es trabajo del proceso automático.
 */
@Service
public class IngestaService {

    private static final Logger log = LoggerFactory.getLogger(IngestaService.class);
    private static final String SIN_NOMBRE = "(sin nombre)";

    private final AplicacionRepository aplicaciones;
    private final PersonaRepository personas;
    private final Normalizador normalizador;
    private final TransactionTemplate transaccion;

    public IngestaService(AplicacionRepository aplicaciones, PersonaRepository personas,
                          Normalizador normalizador, TransactionTemplate transaccion) {
        this.aplicaciones = aplicaciones;
        this.personas = personas;
        this.normalizador = normalizador;
        this.transaccion = transaccion;
    }

    public ResultadoIngesta registrar(AplicacionEntrante entrante) {
        try {
            return transaccion.execute(estado -> registrarEnTransaccion(entrante));
        } catch (DataIntegrityViolationException carrera) {
            // Dos llegadas simultáneas de la misma aplicación o de una persona nueva:
            // la restricción UNIQUE frenó a una. Al reintentar ya encuentra lo que guardó la otra.
            log.info("Conflicto de unicidad con {} {}, reintentando", entrante.fuente(), entrante.idExterno());
            return transaccion.execute(estado -> registrarEnTransaccion(entrante));
        }
    }

    private ResultadoIngesta registrarEnTransaccion(AplicacionEntrante entrante) {
        // Si ya existe no se toca nada, ni siquiera la persona: releer el CSV viejo
        // no debe pisar datos de contacto más recientes que llegaron por el webhook.
        var existente = aplicaciones.findByFuenteAndIdExterno(entrante.fuente(), entrante.idExterno());
        if (existente.isPresent()) {
            return ResultadoIngesta.duplicada(existente.get().getId());
        }

        List<String> advertencias = new ArrayList<>();
        BigDecimal ingreso = conAdvertencia(normalizador.monto("ingreso_mensual", entrante.ingresoMensual()), advertencias);
        BigDecimal ahorro = conAdvertencia(normalizador.monto("ahorro", entrante.ahorro()), advertencias);
        Instant fecha = conAdvertencia(normalizador.fecha(entrante.fechaSolicitud()), advertencias);

        Persona persona = resolverPersona(entrante, advertencias);
        String programa = Objects.requireNonNullElse(normalizador.programa(entrante.programa()), "");

        Aplicacion aplicacion = new Aplicacion(persona, entrante.fuente(), entrante.idExterno(), programa,
                normalizador.texto(entrante.ciudad()), ingreso, ahorro, fecha, entrante.payloadCrudo());
        advertencias.forEach(aplicacion::agregarAdvertencia);
        aplicaciones.saveAndFlush(aplicacion);
        return ResultadoIngesta.nueva(aplicacion.getId());
    }

    /**
     * Identidad: email normalizado; si no hay, celular entre personas sin email;
     * si no hay ninguno, es una persona nueva que no se puede deduplicar.
     */
    private Persona resolverPersona(AplicacionEntrante entrante, List<String> advertencias) {
        String email = normalizador.email(entrante.email());
        String celular = normalizador.celular(entrante.celular());
        String nombre = normalizador.texto(entrante.nombreCompleto());
        if (nombre == null) {
            advertencias.add("sin nombre");
        }
        if (email == null && celular == null) {
            advertencias.add("sin email ni celular: no se puede deduplicar ni notificar");
        }

        Optional<Persona> encontrada = buscarPersona(email, celular);
        if (encontrada.isPresent()) {
            Persona persona = encontrada.get();
            persona.actualizarContacto(nombre != null ? nombre : persona.getNombreCompleto(), celular);
            return persona;
        }
        return personas.saveAndFlush(new Persona(nombre != null ? nombre : SIN_NOMBRE, email, celular));
    }

    private Optional<Persona> buscarPersona(String email, String celular) {
        if (email != null) {
            return personas.findByEmail(email);
        }
        if (celular != null) {
            return personas.findFirstByCelularAndEmailIsNullOrderByIdAsc(celular);
        }
        return Optional.empty();
    }

    private static <T> T conAdvertencia(Normalizador.Resultado<T> resultado, List<String> advertencias) {
        if (resultado.advertencia() != null) {
            advertencias.add(resultado.advertencia());
        }
        return resultado.valor();
    }
}
