package com.johan.crmtoperty.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByAplicacionIdOrderByCreadaEnAsc(Long aplicacionId);
}
