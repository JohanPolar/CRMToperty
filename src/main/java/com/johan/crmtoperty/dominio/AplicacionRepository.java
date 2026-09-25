package com.johan.crmtoperty.dominio;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AplicacionRepository extends JpaRepository<Aplicacion, Long> {

    Optional<Aplicacion> findByFuenteAndIdExterno(Fuente fuente, String idExterno);

    long countByPersona(Persona persona);

    // Solo los ids: cada aplicación se carga y evalúa después en su propia transacción
    @Query("select a.id from Aplicacion a where a.estado = com.johan.crmtoperty.dominio.EstadoAplicacion.PENDIENTE order by a.id")
    List<Long> idsPendientes(Pageable lote);
}
