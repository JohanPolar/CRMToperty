package com.johan.crmtoperty.ingesta;

import com.johan.crmtoperty.dominio.Aplicacion;
import com.johan.crmtoperty.dominio.AplicacionRepository;
import com.johan.crmtoperty.dominio.EstadoAplicacion;
import com.johan.crmtoperty.dominio.Persona;
import com.johan.crmtoperty.dominio.PersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Carga los archivos reales de data/ por los dos caminos y verifica deduplicación
 * e idempotencia. Corre contra el Postgres de compose.yml y revierte todo al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngestaIntegracionTest {

    @Autowired
    private LectorFuenteA lectorFuenteA;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private AplicacionRepository aplicaciones;
    @Autowired
    private PersonaRepository personas;

    @Test
    void releerElCsvNoDuplicaNada() {
        var primera = lectorFuenteA.leer();
        assertThat(primera).isEqualTo(new LectorFuenteA.ResumenLectura(16, 16, 0, 0));

        var segunda = lectorFuenteA.leer();
        assertThat(segunda).isEqualTo(new LectorFuenteA.ResumenLectura(16, 0, 16, 0));
        assertThat(aplicaciones.count()).isEqualTo(16);
        assertThat(personas.count()).isEqualTo(16);
    }

    @Test
    void elReintentoDelWebhookDevuelveLaMismaAplicacion() throws Exception {
        JsonNode webhooks = jsonMapper.readTree(Files.readString(Path.of("data/webhooks.json")));
        // Los elementos 1 y 2 son el mismo envío (sub_3b81de20)
        enviar(webhooks.get(1)).andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("RECIBIDA"));
        enviar(webhooks.get(2)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DUPLICADA"));

        assertThat(aplicaciones.count()).isEqualTo(1);
    }

    @Test
    void lasDosFuentesJuntasDan21AplicacionesY20Personas() throws Exception {
        lectorFuenteA.leer();
        JsonNode webhooks = jsonMapper.readTree(Files.readString(Path.of("data/webhooks.json")));
        for (JsonNode webhook : webhooks) {
            enviar(webhook);
        }

        assertThat(aplicaciones.count()).isEqualTo(21);
        assertThat(personas.count()).isEqualTo(20);
        assertThat(aplicaciones.findAll()).allMatch(a -> a.getEstado() == EstadoAplicacion.PENDIENTE);

        // Juan llegó por el CSV con el email en mayúsculas y por el webhook con +57: es una sola persona
        Persona juan = personas.findByEmail("jctorres@gmail.com").orElseThrow();
        assertThat(aplicaciones.countByPersona(juan)).isEqualTo(2);
    }

    @Test
    void losDatosSuciosDelCsvQuedanInterpretadosOAdvertidos() {
        lectorFuenteA.leer();

        Aplicacion luz = aplicacionDe("lamejia@gmail.com");
        assertThat(luz.getIngresoMensual()).isEqualByComparingTo("6400000");
        assertThat(luz.getAhorro()).isEqualByComparingTo("24500000");

        Aplicacion sandra = aplicacionDe("smvargas@gmail.com");
        assertThat(sandra.getAhorro()).isNull();

        Aplicacion jorge = aplicacionDe("jlcamargo@gmail.com");
        assertThat(jorge.getAhorro()).isEqualByComparingTo("0");

        assertThat(aplicacionDe("fsantamaria@gmail.com").getAdvertencias()).anyMatch(a -> a.contains("futuro"));

        Aplicacion natalia = aplicaciones.findAll().stream()
                .filter(a -> a.getPersona().getNombreCompleto().equals("Natalia Quintero"))
                .findFirst().orElseThrow();
        assertThat(natalia.getPersona().getEmail()).isNull();
        assertThat(natalia.getAdvertencias()).anyMatch(a -> a.contains("sin email ni celular"));
    }

    private Aplicacion aplicacionDe(String email) {
        Persona persona = personas.findByEmail(email).orElseThrow();
        return aplicaciones.findAll().stream()
                .filter(a -> a.getPersona().getId().equals(persona.getId()))
                .findFirst().orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions enviar(JsonNode cuerpo) throws Exception {
        return mockMvc.perform(post("/aplicaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.toString()));
    }
}
