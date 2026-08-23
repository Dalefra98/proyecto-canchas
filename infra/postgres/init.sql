CREATE USER usuarios_user  WITH PASSWORD 'usuarios_pass';
CREATE USER canchas_user   WITH PASSWORD 'canchas_pass';
CREATE USER reservas_user  WITH PASSWORD 'reservas_pass';

CREATE DATABASE usuarios_db OWNER usuarios_user;
CREATE DATABASE canchas_db  OWNER canchas_user;
CREATE DATABASE reservas_db OWNER reservas_user;

REVOKE ALL ON DATABASE usuarios_db FROM PUBLIC;
REVOKE ALL ON DATABASE canchas_db  FROM PUBLIC;
REVOKE ALL ON DATABASE reservas_db FROM PUBLIC;
