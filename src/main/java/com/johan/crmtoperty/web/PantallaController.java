package com.johan.crmtoperty.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Pantalla del equipo comercial: lista con filtros y detalle de una aplicación. */
@Controller
public class PantallaController {

    private final ConsultaAplicaciones consulta;

    public PantallaController(ConsultaAplicaciones consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/")
    public String inicio() {
        return "redirect:/ui/aplicaciones";
    }

    @GetMapping("/ui/aplicaciones")
    public String lista(@RequestParam(required = false) String programa,
                        @RequestParam(required = false) Resultado resultado,
                        @RequestParam(defaultValue = "0") int pagina,
                        Model modelo) {
        // "Todos los programas" llega como texto vacío
        String programaFiltrado = programa == null || programa.isBlank() ? null : programa;
        Filtros filtros = new Filtros(programaFiltrado, resultado, Math.max(pagina, 0));

        modelo.addAttribute("filtros", filtros);
        modelo.addAttribute("pagina", consulta.buscar(filtros));
        modelo.addAttribute("conteos", consulta.conteos(programaFiltrado));
        modelo.addAttribute("programas", consulta.programas());
        modelo.addAttribute("corrida", consulta.ultimaCorrida().orElse(null));
        return "aplicaciones/lista";
    }

    @GetMapping("/ui/aplicaciones/{id}")
    public String detalle(@PathVariable Long id, Model modelo) {
        modelo.addAttribute("a", consulta.detalle(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la aplicación " + id)));
        modelo.addAttribute("corrida", consulta.ultimaCorrida().orElse(null));
        return "aplicaciones/detalle";
    }
}
