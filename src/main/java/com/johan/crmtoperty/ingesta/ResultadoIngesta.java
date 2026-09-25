package com.johan.crmtoperty.ingesta;

public record ResultadoIngesta(Long aplicacionId, boolean nueva) {

    static ResultadoIngesta nueva(Long aplicacionId) {
        return new ResultadoIngesta(aplicacionId, true);
    }

    static ResultadoIngesta duplicada(Long aplicacionId) {
        return new ResultadoIngesta(aplicacionId, false);
    }
}
