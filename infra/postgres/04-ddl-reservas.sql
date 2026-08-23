-- Spec 01 / T4 — DDL de reservas_db
-- Los scripts de docker-entrypoint-initdb.d corren como superusuario conectados a la base
-- "postgres": sin \c y SET ROLE la tabla quedaria de admin y ddl-auto=validate fallaria.
\c reservas_db
SET ROLE reservas_user;

CREATE TABLE reserva (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    -- usuario_id y cancha_id son referencias sin clave foranea: viven en otras bases y la
    -- integracion es por REST, nunca por SQL cruzado.
    usuario_id  BIGINT      NOT NULL,
    cancha_id   BIGINT      NOT NULL,
    fecha       DATE        NOT NULL,
    hora_inicio TIME        NOT NULL,
    hora_fin    TIME        NOT NULL,
    estado      VARCHAR(12) NOT NULL,
    CONSTRAINT pk_reserva PRIMARY KEY (id),
    -- RN-01: el bloque horario es de exactamente una hora.
    CONSTRAINT ck_reserva_bloque_una_hora CHECK (hora_fin = hora_inicio + INTERVAL '1 hour'),
    -- RN-08: estados validos de una reserva.
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('CONFIRMADA', 'CANCELADA', 'FINALIZADA'))
);

-- RN-02: un bloque ocupado no se puede volver a reservar.
-- RN-05: el indice es parcial, por lo que una reserva CANCELADA libera el bloque.
CREATE UNIQUE INDEX ux_reserva_bloque_confirmada
    ON reserva (cancha_id, fecha, hora_inicio)
    WHERE estado = 'CONFIRMADA';

-- Historial propio del usuario y conteo de reservas activas (RN-06).
CREATE INDEX ix_reserva_usuario_estado ON reserva (usuario_id, estado);

-- Consulta de disponibilidad por cancha y fecha.
CREATE INDEX ix_reserva_cancha_fecha ON reserva (cancha_id, fecha);

RESET ROLE;
