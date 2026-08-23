-- Spec 01 / T2 — DDL de canchas_db
-- Los scripts de docker-entrypoint-initdb.d corren como superusuario conectados a la base
-- "postgres": sin \c y SET ROLE la tabla quedaria de admin y ddl-auto=validate fallaria.
\c canchas_db
SET ROLE canchas_user;

CREATE TABLE cancha (
    cancha_id     BIGINT      GENERATED ALWAYS AS IDENTITY,
    nombre        VARCHAR(80) NOT NULL,
    deporte       VARCHAR(8)  NOT NULL,
    hora_apertura TIME        NOT NULL,
    hora_cierre   TIME        NOT NULL,
    activa        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_cancha PRIMARY KEY (cancha_id),
    CONSTRAINT uq_cancha_nombre UNIQUE (nombre),
    CONSTRAINT ck_cancha_deporte CHECK (deporte IN ('PADEL', 'TENIS', 'BASQUET')),
    -- RN-07: el horario de atencion debe ser un rango coherente.
    CONSTRAINT ck_cancha_horario CHECK (hora_cierre > hora_apertura)
);

CREATE TABLE bloqueo_mantenimiento (
    bloqueo_id  BIGINT       GENERATED ALWAYS AS IDENTITY,
    cancha_id   BIGINT       NOT NULL,
    fecha       DATE         NOT NULL,
    hora_inicio TIME         NOT NULL,
    hora_fin    TIME         NOT NULL,
    motivo      VARCHAR(200) NOT NULL,
    CONSTRAINT pk_bloqueo PRIMARY KEY (bloqueo_id),
    -- La clave foranea es valida: cancha y bloqueo_mantenimiento viven en canchas_db.
    CONSTRAINT fk_bloqueo_cancha FOREIGN KEY (cancha_id)
        REFERENCES cancha (cancha_id) ON DELETE RESTRICT,
    CONSTRAINT uq_bloqueo_franja UNIQUE (cancha_id, fecha, hora_inicio),
    CONSTRAINT ck_bloqueo_rango CHECK (hora_fin > hora_inicio)
);

RESET ROLE;
