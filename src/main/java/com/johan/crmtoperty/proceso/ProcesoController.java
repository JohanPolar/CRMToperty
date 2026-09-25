package com.johan.crmtoperty.proceso;

import com.johan.crmtoperty.dominio.EjecucionProceso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Disparo manual, para la demo y para depurar. En producción iría protegido. */
@RestController
public class ProcesoController {

    private final ProcesoAplicaciones proceso;

    public ProcesoController(ProcesoAplicaciones proceso) {
        this.proceso = proceso;
    }

    @PostMapping("/procesos/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutar() {
        return proceso.ejecutar()
                .map(ejecucion -> ResponseEntity.ok(resumen(ejecucion)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Ya hay una corrida en curso")));
    }

    private static Map<String, Object> resumen(EjecucionProceso ejecucion) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("id", ejecucion.getId());
        resumen.put("leidas", ejecucion.getLeidas());
        resumen.put("nuevas", ejecucion.getNuevas());
        resumen.put("duplicadas", ejecucion.getDuplicadas());
        resumen.put("evaluadas", ejecucion.getEvaluadas());
        resumen.put("notificadas", ejecucion.getNotificadas());
        resumen.put("error", ejecucion.getError());
        return resumen;
    }
}
