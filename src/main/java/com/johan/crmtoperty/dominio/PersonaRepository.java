package com.johan.crmtoperty.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    Optional<Persona> findByEmail(String email);

    // Respaldo para quien no trae email. Solo busca entre personas sin email: el celular
    // no es único (familias), así que no debe unir a alguien con otra persona ya identificada.
    Optional<Persona> findFirstByCelularAndEmailIsNullOrderByIdAsc(String celular);
}
