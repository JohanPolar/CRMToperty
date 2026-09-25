package com.johan.crmtoperty.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EjecucionProcesoRepository extends JpaRepository<EjecucionProceso, Long> {

    Optional<EjecucionProceso> findFirstByOrderByIdDesc();
}
