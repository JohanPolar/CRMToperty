package com.johan.crmtoperty.dominio;

import org.springframework.data.domain.Page;
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

    // Pantalla: cada filtro es opcional (null = no filtra). join fetch trae la persona
    // en la misma consulta para no hacer una consulta extra por fila.
    @Query(value = """
            select a from Aplicacion a join fetch a.persona
            where (:programa is null or a.programaCodigo = :programa)
              and (:estado is null or a.estado = :estado)
              and (:decision is null or a.decision = :decision)""",
            countQuery = """
            select count(a) from Aplicacion a
            where (:programa is null or a.programaCodigo = :programa)
              and (:estado is null or a.estado = :estado)
              and (:decision is null or a.decision = :decision)""")
    Page<Aplicacion> buscar(String programa, EstadoAplicacion estado, Decision decision, Pageable pagina);

    @Query("""
            select a.estado, a.decision, count(a) from Aplicacion a
            where (:programa is null or a.programaCodigo = :programa)
            group by a.estado, a.decision""")
    List<Object[]> contarPorResultado(String programa);

    @Query("select distinct a.programaCodigo from Aplicacion a")
    List<String> programasUsados();

    List<Aplicacion> findByPersonaIdAndIdNotOrderByIdDesc(Long personaId, Long id);
}
