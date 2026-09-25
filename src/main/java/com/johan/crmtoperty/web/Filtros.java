package com.johan.crmtoperty.web;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Filtros activos de la lista. Arma las URLs de las pestañas y de la paginación
 * para que cada enlace conserve los demás filtros.
 */
public record Filtros(String programa, Resultado resultado, int pagina) {

    public String url() {
        return url(programa, resultado, pagina);
    }

    public String conResultado(Resultado otro) {
        return url(programa, otro, 0);
    }

    public String conPagina(int otra) {
        return url(programa, resultado, otra);
    }

    private static String url(String programa, Resultado resultado, int pagina) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/ui/aplicaciones");
        if (programa != null) {
            uri.queryParam("programa", programa);
        }
        if (resultado != null) {
            uri.queryParam("resultado", resultado.name());
        }
        if (pagina > 0) {
            uri.queryParam("pagina", pagina);
        }
        return uri.encode().toUriString();
    }
}
