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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Una solicitud de una persona a un programa. Nace PENDIENTE y el job la pasa
 * una sola vez a EVALUADA o INCOMPLETA; después no vuelve a cambiar.
 */
@Entity
@Table(name = "aplicaciones")
public class Aplicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Fuente fuente;

    @Column(name = "id_externo", nullable = false)
    private String idExterno;

    @Column(name = "programa_codigo", nullable = false)
    private String programaCodigo;

    private String ciudad;

    // null significa "no llegó el dato", que es distinto de 0
    @Column(name = "ingreso_mensual", precision = 15, scale = 2)
    private BigDecimal ingresoMensual;

    @Column(precision = 15, scale = 2)
    private BigDecimal ahorro;

    @Column(name = "fecha_solicitud")
    private Instant fechaSolicitud;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_crudo", nullable = false)
    private Map<String, Object> payloadCrudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAplicacion estado = EstadoAplicacion.PENDIENTE;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ResultadoRegla> motivos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "umbrales_aplicados")
    private Map<String, Object> umbralesAplicados;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> advertencias = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "recibida_en", nullable = false, updatable = false)
    private Instant recibidaEn;

    @Column(name = "evaluada_en")
    private Instant evaluadaEn;

    protected Aplicacion() {
    }

    public Aplicacion(Persona persona, Fuente fuente, String idExterno, String programaCodigo, String ciudad,
                      BigDecimal ingresoMensual, BigDecimal ahorro, Instant fechaSolicitud,
                      Map<String, Object> payloadCrudo) {
        this.persona = persona;
        this.fuente = fuente;
        this.idExterno = idExterno;
        this.programaCodigo = programaCodigo;
        this.ciudad = ciudad;
        this.ingresoMensual = ingresoMensual;
        this.ahorro = ahorro;
        this.fechaSolicitud = fechaSolicitud;
        this.payloadCrudo = payloadCrudo;
    }

    public void agregarAdvertencia(String advertencia) {
        advertencias.add(advertencia);
    }

    public void registrarDecision(Decision decision, List<ResultadoRegla> motivos,
                                  Map<String, Object> umbralesAplicados, Instant ahora) {
        exigirPendiente();
        this.estado = EstadoAplicacion.EVALUADA;
        this.decision = decision;
        this.motivos = motivos;
        this.umbralesAplicados = umbralesAplicados;
        this.evaluadaEn = ahora;
    }

    public void marcarIncompleta(List<ResultadoRegla> motivos, Instant ahora) {
        exigirPendiente();
        this.estado = EstadoAplicacion.INCOMPLETA;
        this.motivos = motivos;
        this.evaluadaEn = ahora;
    }

    // Misma regla que ck_decision_coherente en la base: el código falla antes que el INSERT
    private void exigirPendiente() {
        if (estado != EstadoAplicacion.PENDIENTE) {
            throw new IllegalStateException("La aplicación " + id + " ya fue procesada (" + estado + ")");
        }
    }

    public Long getId() {
        return id;
    }

    public Persona getPersona() {
        return persona;
    }

    public Fuente getFuente() {
        return fuente;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getProgramaCodigo() {
        return programaCodigo;
    }

    public String getCiudad() {
        return ciudad;
    }

    public BigDecimal getIngresoMensual() {
        return ingresoMensual;
    }

    public BigDecimal getAhorro() {
        return ahorro;
    }

    public Instant getFechaSolicitud() {
        return fechaSolicitud;
    }

    public Map<String, Object> getPayloadCrudo() {
        return payloadCrudo;
    }

    public EstadoAplicacion getEstado() {
        return estado;
    }

    public Decision getDecision() {
        return decision;
    }

    public List<ResultadoRegla> getMotivos() {
        return motivos;
    }

    public Map<String, Object> getUmbralesAplicados() {
        return umbralesAplicados;
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }

    public Instant getRecibidaEn() {
        return recibidaEn;
    }

    public Instant getEvaluadaEn() {
        return evaluadaEn;
    }
}
