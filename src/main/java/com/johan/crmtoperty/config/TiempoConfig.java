package com.johan.crmtoperty.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TiempoConfig {

    // Un Clock inyectado permite probar reglas que dependen de "hoy" (p. ej. fechas futuras).
    // Las fechas sin hora del CSV se interpretan en esta zona.
    @Bean
    public Clock clock(@Value("${app.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
