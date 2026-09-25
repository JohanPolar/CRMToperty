package com.johan.crmtoperty.proceso;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el proceso cada pocos minutos. fixedDelay cuenta desde que termina la
 * corrida anterior, así que dentro de una instancia nunca se solapan; entre
 * instancias los separa el CandadoProceso.
 *
 * En producción esto podría ser un cron o un job de Kubernetes que llame a lo
 * mismo; por eso la lógica vive en ProcesoAplicaciones y aquí solo está el reloj.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.proceso.habilitado", havingValue = "true", matchIfMissing = true)
public class ProgramadorProceso {

    private final ProcesoAplicaciones proceso;

    public ProgramadorProceso(ProcesoAplicaciones proceso) {
        this.proceso = proceso;
    }

    @Scheduled(initialDelayString = "${app.proceso.espera-inicial:PT10S}", fixedDelayString = "${app.proceso.intervalo:PT2M}")
    public void ejecutar() {
        proceso.ejecutar();
    }
}
