package com.johan.crmtoperty.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Bandeja de salida: escribir una fila aquí es "notificar". La unicidad
 * (aplicacion_id, tipo) garantiza que repetir el proceso no genere otra.
 * Se inserta con INSERT ... ON CONFLICT DO NOTHING (JPA no lo expresa), así que
 * aquí es de solo lectura.
 */
@Entity
@Immutable
@Table(name = "notificaciones",
        uniqueConstraints = @UniqueConstraint(name = "uq_notificacion", columnNames = {"aplicacion_id", "tipo"}))
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aplicacion_id", nullable = false)
    private Aplicacion aplicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacion tipo;

    private String destinatario;

    @Column(nullable = false)
    private String asunto;

    @Column(nullable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNotificacion estado;

    @CreationTimestamp
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    protected Notificacion() {
    }

    public Long getId() {
        return id;
    }

    public Aplicacion getAplicacion() {
        return aplicacion;
    }

    public Persona getPersona() {
        return persona;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
