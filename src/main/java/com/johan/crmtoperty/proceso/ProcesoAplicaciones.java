package com.johan.crmtoperty.proceso;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.AplicacionRepository;
import com.johan.crmtoperty.dominio.EjecucionProceso;
import com.johan.crmtoperty.dominio.EjecucionProcesoRepository;
import com.johan.crmtoperty.evaluacion.CatalogoProgramas;
import com.johan.crmtoperty.evaluacion.MotorReglas;
import com.johan.crmtoperty.evaluacion.MotorReglas.Veredicto;
import com.johan.crmtoperty.evaluacion.Programa;
import com.johan.crmtoperty.ingesta.LectorFuenteA;
import com.johan.crmtoperty.notificacion.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Una corrida completa: leer el CSV, evaluar lo pendiente y notificar.
 * Es segura de repetir porque cada paso es idempotente por sí mismo:
 * la ingesta descarta lo ya visto, solo se evalúa lo PENDIENTE y la
 * notificación tiene una llave única.
 */
@Service
public class ProcesoAplicaciones {

    private static final Logger log = LoggerFactory.getLogger(ProcesoAplicaciones.class);

    private final CandadoProceso candado;
    private final LectorFuenteA lectorFuenteA;
    private final CatalogoProgramas catalogoProgramas;
    private final MotorReglas motor;
    private final Notificador notificador;
    private final AplicacionRepository aplicaciones;
    private final EjecucionProcesoRepository ejecuciones;
    private final TransactionTemplate transaccion;
    private final Clock clock;
    private final int tamanoLote;

    public ProcesoAplicaciones(CandadoProceso candado, LectorFuenteA lectorFuenteA,
                               CatalogoProgramas catalogoProgramas, MotorReglas motor, Notificador notificador,
                               AplicacionRepository aplicaciones, EjecucionProcesoRepository ejecuciones,
                               TransactionTemplate transaccion, Clock clock,
                               @Value("${app.proceso.lote:500}") int tamanoLote) {
        this.candado = candado;
        this.lectorFuenteA = lectorFuenteA;
        this.catalogoProgramas = catalogoProgramas;
        this.motor = motor;
        this.notificador = notificador;
        this.aplicaciones = aplicaciones;
        this.ejecuciones = ejecuciones;
        this.transaccion = transaccion;
        this.clock = clock;
        this.tamanoLote = tamanoLote;
    }

    /** @return la bitácora de la corrida, o vacío si ya había otra corriendo. */
    public Optional<EjecucionProceso> ejecutar() {
        Optional<CandadoProceso.Candado> tomado = candado.intentarTomar();
        if (tomado.isEmpty()) {
            log.info("Ya hay una corrida en curso; se omite esta");
            return Optional.empty();
        }
        try (CandadoProceso.Candado ignorado = tomado.get()) {
            return Optional.of(correr());
        }
    }

    private EjecucionProceso correr() {
        EjecucionProceso ejecucion = ejecuciones.save(new EjecucionProceso(clock.instant()));
        try {
            LectorFuenteA.ResumenLectura lectura = lectorFuenteA.leer();
            ejecucion.registrarIngesta(lectura.leidas(), lectura.nuevas(), lectura.duplicadas());

            // Se lee una vez por corrida: todas las aplicaciones de esta corrida usan las mismas reglas
            Map<String, Programa> catalogo = catalogoProgramas.cargar();
            for (Long id : aplicaciones.idsPendientes(PageRequest.of(0, tamanoLote))) {
                evaluarUna(id, catalogo, ejecucion);
            }
            ejecucion.finalizar(clock.instant());
        } catch (RuntimeException e) {
            log.error("La corrida {} falló", ejecucion.getId(), e);
            ejecucion.fallar(e.getMessage(), clock.instant());
        }
        EjecucionProceso guardada = ejecuciones.save(ejecucion);
        log.info("Corrida {}: leídas={} nuevas={} duplicadas={} evaluadas={} notificadas={}",
                guardada.getId(), guardada.getLeidas(), guardada.getNuevas(), guardada.getDuplicadas(),
                guardada.getEvaluadas(), guardada.getNotificadas());
        return guardada;
    }

    /**
     * Una transacción por aplicación: la decisión y su notificación se guardan juntas
     * o no se guarda ninguna. Si una aplicación falla, queda PENDIENTE para la próxima
     * corrida y las demás siguen.
     */
    private void evaluarUna(Long id, Map<String, Programa> catalogo, EjecucionProceso ejecucion) {
        try {
            Boolean notificada = transaccion.execute(estado -> {
                Aplicacion aplicacion = aplicaciones.findById(id).orElseThrow();
                Veredicto veredicto = motor.evaluar(aplicacion, catalogo);
                if (veredicto.incompleta()) {
                    aplicacion.marcarIncompleta(veredicto.motivos(), clock.instant());
                } else {
                    aplicacion.registrarDecision(veredicto.decision(), veredicto.motivos(),
                            veredicto.programa().comoUmbrales(), clock.instant());
                }
                aplicaciones.flush();
                return notificador.notificar(aplicacion, veredicto);
            });
            ejecucion.registrarEvaluadas(1);
            if (Boolean.TRUE.equals(notificada)) {
                ejecucion.registrarNotificadas(1);
            }
        } catch (RuntimeException e) {
            log.error("No se pudo evaluar la aplicación {}; se reintentará en la próxima corrida", id, e);
        }
    }
}
