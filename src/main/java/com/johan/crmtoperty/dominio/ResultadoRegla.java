package com.johan.crmtoperty.dominio;

/**
 * Resultado de una regla sobre una aplicación; se guarda en aplicaciones.motivos
 * para que la pantalla muestre por qué se aprobó o rechazó.
 */
public record ResultadoRegla(String regla, boolean cumple, String valor, String esperado) {
}
