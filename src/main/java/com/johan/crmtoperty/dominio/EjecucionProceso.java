package com.johan.crmtoperty.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Bitácora de una corrida del proceso automático: qué leyó, qué hizo y si falló. */
@Entity
@Table(name = "ejecuciones_proceso")
public class EjecucionProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iniciada_en", nullable = false, updatable = false)
    private Instant iniciadaEn;

    @Column(name = "finalizada_en")
    private Instant finalizadaEn;

    @Column(nullable = false)
    private int leidas;

    @Column(nullable = false)
    private int nuevas;

    @Column(nullable = false)
    private int duplicadas;

    @Column(nullable = false)
    private int evaluadas;

    @Column(nullable = false)
    private int notificadas;

    private String error;

    protected EjecucionProceso() {
    }

    public EjecucionProceso(Instant iniciadaEn) {
        this.iniciadaEn = iniciadaEn;
    }

    public void registrarIngesta(int leidas, int nuevas, int duplicadas) {
        this.leidas += leidas;
        this.nuevas += nuevas;
        this.duplicadas += duplicadas;
    }

    public void registrarEvaluadas(int evaluadas) {
        this.evaluadas += evaluadas;
    }

    public void registrarNotificadas(int notificadas) {
        this.notificadas += notificadas;
    }

    public void finalizar(Instant ahora) {
        this.finalizadaEn = ahora;
    }

    public void fallar(String error, Instant ahora) {
        this.error = error;
        this.finalizadaEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public Instant getIniciadaEn() {
        return iniciadaEn;
    }

    public Instant getFinalizadaEn() {
        return finalizadaEn;
    }

    public int getLeidas() {
        return leidas;
    }

    public int getNuevas() {
        return nuevas;
    }

    public int getDuplicadas() {
        return duplicadas;
    }

    public int getEvaluadas() {
        return evaluadas;
    }

    public int getNotificadas() {
        return notificadas;
    }

    public String getError() {
        return error;
    }
}
