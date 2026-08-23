-- Spec 01 / T1 — DDL de usuarios_db
-- Los scripts de docker-entrypoint-initdb.d corren como superusuario conectados a la base
-- "postgres": sin \c y SET ROLE la tabla quedaria de admin y ddl-auto=validate fallaria.
\c usuarios_db
SET ROLE usuarios_user;

CREATE TABLE usuario (
    usuario_id    BIGINT       GENERATED ALWAYS AS IDENTITY,
    nombre        VARCHAR(80)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    rol           VARCHAR(8)   NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_usuario PRIMARY KEY (usuario_id),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    -- La contrasena se guarda como hash BCrypt (60 caracteres, prefijo $2), nunca en claro.
    CONSTRAINT ck_usuario_password_bcrypt CHECK (password_hash LIKE '$2%'),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'USUARIO'))
);

RESET ROLE;
