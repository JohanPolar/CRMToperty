package com.johan.crmtoperty.web;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.AplicacionRepository;
import com.johan.crmtoperty.dominio.Decision;
import com.johan.crmtoperty.dominio.EjecucionProceso;
import com.johan.crmtoperty.dominio.EjecucionProcesoRepository;
import com.johan.crmtoperty.dominio.EstadoAplicacion;
import com.johan.crmtoperty.dominio.NotificacionRepository;
import com.johan.crmtoperty.evaluacion.CatalogoProgramas;
import com.johan.crmtoperty.evaluacion.Programa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Lecturas para la pantalla del equipo comercial. Nunca modifica nada. */
@Service
@Transactional(readOnly = true)
public class ConsultaAplicaciones {

    private static final Logger log = LoggerFactory.getLogger(ConsultaAplicaciones.class);
    private static final int POR_PAGINA = 20;
    // Lo último que llegó primero: es lo que el equipo necesita trabajar
    private static final Sort ORDEN = Sort.by(Sort.Order.desc("recibidaEn"), Sort.Order.desc("id"));

    private final AplicacionRepository aplicaciones;
    private final NotificacionRepository notificaciones;
    private final EjecucionProcesoRepository ejecuciones;
    private final CatalogoProgramas catalogo;
    private final JsonMapper jsonMapper;

    public ConsultaAplicaciones(AplicacionRepository aplicaciones, NotificacionRepository notificaciones,
                                EjecucionProcesoRepository ejecuciones, CatalogoProgramas catalogo,
                                JsonMapper jsonMapper) {
        this.aplicaciones = aplicaciones;
        this.notificaciones = notificaciones;
        this.ejecuciones = ejecuciones;
        this.catalogo = catalogo;
        this.jsonMapper = jsonMapper;
    }

    public Page<Vistas.Fila> buscar(Filtros filtros) {
        Resultado resultado = filtros.resultado();
        EstadoAplicacion estado = resultado == null ? null : resultado.getEstado();
        Decision decision = resultado == null ? null : resultado.getDecision();
        Map<String, Programa> programas = programasSinFallar();
        return aplicaciones.buscar(filtros.programa(), estado, decision, PageRequest.of(filtros.pagina(), POR_PAGINA, ORDEN))
                .map(aplicacion -> fila(aplicacion, programas));
    }

    /** Cuántas hay de cada resultado dentro del programa filtrado; alimenta las pestañas. */
    public List<Vistas.Conteo> conteos(String programa) {
        Map<Resultado, Long> totales = new EnumMap<>(Resultado.class);
        for (Object[] grupo : aplicaciones.contarPorResultado(programa)) {
            Resultado resultado = Resultado.de((EstadoAplicacion) grupo[0], (Decision) grupo[1]);
            totales.merge(resultado, (Long) grupo[2], Long::sum);
        }
        return Arrays.stream(Resultado.values())
                .map(resultado -> new Vistas.Conteo(resultado, totales.getOrDefault(resultado, 0L)))
                .toList();
    }

    /**
     * Opciones del filtro: los programas de programas.json más cualquier código que haya
     * llegado en una aplicación aunque no exista, para poder encontrar esas incompletas.
     */
    public List<Vistas.OpcionPrograma> programas() {
        Map<String, Programa> programas = programasSinFallar();
        TreeSet<String> codigos = new TreeSet<>(programas.keySet());
        aplicaciones.programasUsados().stream().filter(codigo -> !codigo.isBlank()).forEach(codigos::add);
        return codigos.stream().map(codigo -> new Vistas.OpcionPrograma(codigo, nombre(codigo, programas))).toList();
    }

    public Optional<EjecucionProceso> ultimaCorrida() {
        return ejecuciones.findFirstByOrderByIdDesc();
    }

    public Optional<Vistas.Detalle> detalle(Long id) {
        Map<String, Programa> programas = programasSinFallar();
        return aplicaciones.findById(id).map(aplicacion -> {
            var persona = aplicacion.getPersona();
            List<Vistas.Fila> otras = aplicaciones.findByPersonaIdAndIdNotOrderByIdDesc(persona.getId(), id).stream()
                    .map(otra -> fila(otra, programas))
                    .toList();
            List<Vistas.Aviso> avisos = notificaciones.findByAplicacionIdOrderByCreadaEnAsc(id).stream()
                    .map(n -> new Vistas.Aviso(n.getTipo().name(), n.getDestinatario(), n.getAsunto(), n.getCuerpo(),
                            n.getEstado().name(), n.getCreadaEn()))
                    .toList();
            return new Vistas.Detalle(aplicacion.getId(),
                    Resultado.de(aplicacion.getEstado(), aplicacion.getDecision()),
                    aplicacion.getProgramaCodigo(), nombre(aplicacion.getProgramaCodigo(), programas),
                    aplicacion.getCiudad(), aplicacion.getIngresoMensual(), aplicacion.getAhorro(),
                    aplicacion.getFechaSolicitud(), aplicacion.getRecibidaEn(), aplicacion.getEvaluadaEn(),
                    aplicacion.getFuente(), aplicacion.getIdExterno(),
                    aplicacion.getMotivos(), List.copyOf(aplicacion.getAdvertencias()),
                    aplicacion.getUmbralesAplicados(), jsonMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(aplicacion.getPayloadCrudo()),
                    new Vistas.Persona(persona.getNombreCompleto(), persona.getEmail(), persona.getCelular()),
                    otras, avisos);
        });
    }

    private static Vistas.Fila fila(Aplicacion aplicacion, Map<String, Programa> programas) {
        var persona = aplicacion.getPersona();
        return new Vistas.Fila(aplicacion.getId(), persona.getNombreCompleto(), persona.getEmail(),
                nombre(aplicacion.getProgramaCodigo(), programas), aplicacion.getCiudad(),
                aplicacion.getIngresoMensual(), aplicacion.getAhorro(), aplicacion.getFechaSolicitud(),
                aplicacion.getFuente(), Resultado.de(aplicacion.getEstado(), aplicacion.getDecision()),
                List.copyOf(aplicacion.getAdvertencias()));
    }

    private static String nombre(String codigo, Map<String, Programa> programas) {
        if (codigo == null || codigo.isBlank()) {
            return "Sin programa";
        }
        Programa programa = programas.get(codigo);
        return programa != null ? programa.nombre() : codigo;
    }

    // Si programas.json está roto, la pantalla sigue funcionando: muestra los códigos en vez de los nombres
    private Map<String, Programa> programasSinFallar() {
        try {
            return catalogo.cargar();
        } catch (CatalogoProgramas.ProgramasInvalidosException e) {
            log.warn("No se pudieron cargar los programas para la pantalla: {}", e.getMessage());
            return Map.of();
        }
    }
}
