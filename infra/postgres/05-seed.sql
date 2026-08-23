-- Spec 01 / T5 — Datos semilla
-- Idempotente: se puede ejecutar varias veces sin duplicar filas ni fallar.
-- Hashes BCrypt de coste 10 generados con htpasswd -nbBC 10 (Spring Security acepta $2y).
--   admin@canchas.ec   -> Admin123
--   usuario@canchas.ec -> Usuario123

\c usuarios_db
SET ROLE usuarios_user;

INSERT INTO usuario (nombre, email, password_hash, rol, activo) VALUES
    ('Administrador', 'admin@canchas.ec',   '$2y$10$RShSIvO/lTjtbA6gYxOwH.h2DIJ1SBr480ROCBSUeb2p9iq5Phpw6', 'ADMIN',   TRUE),
    ('Usuario Demo',  'usuario@canchas.ec', '$2y$10$KbZCV0ZwkQL3ARFQSQziDerqo/3CdY8ZHB3nnzhTGy2EnFt4mIiBe', 'USUARIO', TRUE)
ON CONFLICT (email) DO NOTHING;

RESET ROLE;

\c canchas_db
SET ROLE canchas_user;

INSERT INTO cancha (nombre, deporte, hora_apertura, hora_cierre, activa) VALUES
    ('Padel 1',   'PADEL',   '07:00', '22:00', TRUE),
    ('Tenis 1',   'TENIS',   '07:00', '22:00', TRUE),
    ('Basquet 1', 'BASQUET', '07:00', '22:00', TRUE)
ON CONFLICT (nombre) DO NOTHING;

RESET ROLE;
