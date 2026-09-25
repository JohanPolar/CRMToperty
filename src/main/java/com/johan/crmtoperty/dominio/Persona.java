package com.johan.crmtoperty.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Una fila por ser humano. El email llega ya normalizado (minúsculas) y es la llave
 * de deduplicación; puede ser null, en cuyo caso la persona no se deduplica.
 */
@Entity
@Table(name = "personas")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(unique = true)
    private String email;

    private String celular;

    @CreationTimestamp
    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @UpdateTimestamp
    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    protected Persona() {
    }

    public Persona(String nombreCompleto, String email, String celular) {
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.celular = celular;
    }

    /** Gana el dato más reciente; cada aplicación conserva su copia original en payload_crudo. */
    public void actualizarContacto(String nombreCompleto, String celular) {
        this.nombreCompleto = nombreCompleto;
        if (celular != null) {
            this.celular = celular;
        }
    }

    public Long getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getEmail() {
        return email;
    }

    public String getCelular() {
        return celular;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }
}
