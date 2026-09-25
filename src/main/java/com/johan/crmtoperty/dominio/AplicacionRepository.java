package com.johan.crmtoperty.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AplicacionRepository extends JpaRepository<Aplicacion, Long> {

    Optional<Aplicacion> findByFuenteAndIdExterno(Fuente fuente, String idExterno);

    long countByPersona(Persona persona);
}
