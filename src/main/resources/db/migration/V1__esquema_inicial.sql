-- Esquema inicial. Las restricciones UNIQUE son la base de la idempotencia:
-- repetir la ingesta, la evaluación o la notificación no puede duplicar filas.

-- Una fila por ser humano. Se identifica por email normalizado (minúsculas);
-- varios NULL no chocan en Postgres, así que una persona sin email también cabe.
CREATE TABLE personas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_completo TEXT        NOT NULL,
    email           TEXT        UNIQUE,
    celular         TEXT,                     -- 10 dígitos sin +57; no es único (familias comparten número)
    creada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizada_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_personas_celular ON personas (celular);

-- Una fila por solicitud. Una persona puede tener varias.
CREATE TABLE aplicaciones (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    persona_id         BIGINT        NOT NULL REFERENCES personas (id),
    fuente             TEXT          NOT NULL CHECK (fuente IN ('CSV', 'WEBHOOK')),
    id_externo         TEXT          NOT NULL,  -- submission_id del webhook o hash de la fila CSV
    programa_codigo    TEXT          NOT NULL,  -- sin FK: los programas viven en programas.json
    ciudad             TEXT,
    ingreso_mensual    NUMERIC(15, 2),          -- NULL = no llegó el dato (distinto de 0)
    ahorro             NUMERIC(15, 2),          -- NULL = no llegó el dato (distinto de 0)
    fecha_solicitud    TIMESTAMPTZ,
    payload_crudo      JSONB         NOT NULL,  -- el registro tal como llegó, para auditar o reprocesar
    estado             TEXT          NOT NULL DEFAULT 'PENDIENTE'
                                     CHECK (estado IN ('PENDIENTE', 'EVALUADA', 'INCOMPLETA')),
    decision           TEXT          CHECK (decision IN ('APROBADA', 'RECHAZADA')),
    motivos            JSONB,                   -- resultado regla por regla, o qué dato falta
    umbrales_aplicados JSONB,                   -- copia del programa usada al evaluar
    advertencias       JSONB         NOT NULL DEFAULT '[]',
    recibida_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    evaluada_en        TIMESTAMPTZ,

    CONSTRAINT uq_aplicacion_origen UNIQUE (fuente, id_externo),
    CONSTRAINT ck_decision_coherente CHECK ((estado = 'EVALUADA') = (decision IS NOT NULL))
);

-- El job busca pendientes en cada corrida; el índice parcial solo contiene esas filas.
CREATE INDEX ix_aplicaciones_pendientes ON aplicaciones (id) WHERE estado = 'PENDIENTE';
-- Filtros de la pantalla comercial.
CREATE INDEX ix_aplicaciones_filtros ON aplicaciones (programa_codigo, decision);
CREATE INDEX ix_aplicaciones_persona ON aplicaciones (persona_id);

-- Bandeja de salida: escribir aquí es "notificar". Una notificación por aplicación y tipo,
-- así repetir el proceso nunca genera otra (INSERT ... ON CONFLICT DO NOTHING).
CREATE TABLE notificaciones (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aplicacion_id BIGINT      NOT NULL REFERENCES aplicaciones (id),
    persona_id    BIGINT      NOT NULL REFERENCES personas (id),
    tipo          TEXT        NOT NULL CHECK (tipo IN ('DECISION', 'INCOMPLETA')),
    destinatario  TEXT,                        -- NULL si la persona no tiene email
    asunto        TEXT        NOT NULL,
    cuerpo        TEXT        NOT NULL,
    estado        TEXT        NOT NULL CHECK (estado IN ('REGISTRADA', 'SIN_DESTINATARIO')),
    creada_en     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_notificacion UNIQUE (aplicacion_id, tipo)
);

CREATE INDEX ix_notificaciones_persona ON notificaciones (persona_id);

-- Bitácora de cada corrida del proceso automático.
CREATE TABLE ejecuciones_proceso (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    iniciada_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalizada_en TIMESTAMPTZ,
    leidas        INT         NOT NULL DEFAULT 0,
    nuevas        INT         NOT NULL DEFAULT 0,
    duplicadas    INT         NOT NULL DEFAULT 0,
    evaluadas     INT         NOT NULL DEFAULT 0,
    notificadas   INT         NOT NULL DEFAULT 0,
    error         TEXT
);
