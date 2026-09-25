package com.johan.crmtoperty.web;

import com.johan.crmtoperty.dominio.PersonaRepository;
import com.johan.crmtoperty.proceso.ProcesoAplicaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La pantalla con los datos reales del CSV ya evaluados. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PantallaIntegracionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProcesoAplicaciones proceso;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PersonaRepository personas;

    @BeforeEach
    void cargarDatos() {
        proceso.ejecutar();
    }

    @Test
    void laListaMuestraLasAplicacionesYLosConteos() throws Exception {
        mockMvc.perform(get("/ui/aplicaciones"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("16 aplicaciones")))
                .andExpect(content().string(containsString("Esteban Ramírez Solano")))
                .andExpect(content().string(containsString("$ 19.500.000")));
    }

    @Test
    void filtraPorProgramaYResultado() throws Exception {
        mockMvc.perform(get("/ui/aplicaciones").param("programa", "bahia").param("resultado", "RECHAZADA"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 aplicaciones")))  // Paula y Mónica
                .andExpect(content().string(containsString("Mónica Alejandra Díaz")))
                .andExpect(content().string(not(containsString("Esteban Ramírez Solano"))));
    }

    @Test
    void elDetalleExplicaLaDecision() throws Exception {
        Long personaId = personas.findByEmail("eramirez@gmail.com").orElseThrow().getId();
        Long id = jdbc.sql("SELECT id FROM aplicaciones WHERE persona_id = ?").param(personaId).query(Long.class).single();

        mockMvc.perform(get("/ui/aplicaciones/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No cumple 1 de 3 reglas")))
                .andExpect(content().string(containsString("Requiere ≥ $ 20.000.000")))
                .andExpect(content().string(containsString("no cumple: ahorro mínimo")));
    }

    @Test
    void unaAplicacionQueNoExisteDa404() throws Exception {
        mockMvc.perform(get("/ui/aplicaciones/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }
}
