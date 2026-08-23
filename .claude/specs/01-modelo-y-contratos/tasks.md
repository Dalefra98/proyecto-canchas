# Spec 01 — Modelo de datos y contratos · tasks.md

Base: `requirements.md` (C1, aprobado 23/08/2026) y `design.md` (C2, aprobado 23/08/2026).
Se ejecuta **una tarea a la vez**; al terminar cada una, alto y espera aprobación.

## Reglas de ejecución

- Los scripts de `docker-entrypoint-initdb.d` solo corren cuando el volumen está vacío. Por
  eso cada verificación empieza con `docker compose down -v`, que **borra los datos**. Como
  consecuencia, cada tarea descarta lo que probó la anterior; la convivencia de todos los
  scripts se comprueba una sola vez, en T6 parte A.
- Nunca invocar `psql` sin `-d`: sin base explícita asume una con el nombre del usuario y
  falla con *database "admin" does not exist*.
- Cada script nuevo se monta en `docker-compose.yml` en la misma tarea que lo crea: así el
  proyecto nunca queda con un montaje apuntando a un archivo inexistente.
- `admin` es el valor de `POSTGRES_USER` en `.env`. Si tu `.env` usa otro, ajusta el `-U`.
- Todo script abre con `\c <base>` y `SET ROLE <usuario>;` (design §2).

## Orden de tareas

| # | Tarea | Archivos | Cubre |
|---|---|---|---|
| T1 | DDL de `usuario` | `02-ddl-usuarios.sql`, `docker-compose.yml` | HU-01, HU-06 |
| T2 | DDL de `cancha` | `03-ddl-canchas.sql`, `docker-compose.yml` | HU-02, HU-06 |
| T3 | DDL de `bloqueo_mantenimiento` | `03-ddl-canchas.sql` | HU-03 |
| T4 | DDL de `reserva` con índices | `04-ddl-reservas.sql`, `docker-compose.yml` | HU-04, HU-05, RN-01, RN-02, RN-05, RN-08 |
| T5 | Datos semilla | `05-seed.sql`, `docker-compose.yml`, baja de `seed.sql` | HU-01, HU-02, HU-08 |
| T6 | Verificación final integral: arranque limpio con los cinco scripts + independencia | ninguno (solo comandos) | HU-05, HU-06, E-01 a E-04 |
| T7 | Registro en la bitácora | `docs/bitacora.md` | cierre de la spec |

---

## T1 — DDL de `usuario`

**Qué hace.** Crea `infra/postgres/02-ddl-usuarios.sql` con la tabla `usuario` según design
§3.1: `usuario_id` identity, `nombre`, `email` único, `password_hash` con `CHECK` de prefijo
`$2`, `rol` con `CHECK` `ADMIN`/`USUARIO`, `activo` con `DEFAULT TRUE`. Abre con
`\c usuarios_db` y `SET ROLE usuarios_user;`. Monta el archivo en `docker-compose.yml` como
`/docker-entrypoint-initdb.d/02-ddl-usuarios.sql:ro`.

**Cubre.** HU-01 (modelo de usuarios y roles), HU-06 (propiedad de tablas), decisiones D-01,
D-06, D-08, D-10.

**Verificación.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d usuarios_db -c "\d usuario"
docker compose exec postgres psql -U admin -d usuarios_db -c "SELECT tableowner FROM pg_tables WHERE tablename='usuario'"
docker compose exec postgres psql -U admin -d usuarios_db -c "INSERT INTO usuario (nombre,email,password_hash,rol) VALUES ('X','x@x.ec','\$2a\$10\$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','JEFE')"
```

Esperado: la tabla existe, `tableowner` es `usuarios_user` y el último `INSERT` falla con
violación de `ck_usuario_rol`.

---

## T2 — DDL de `cancha`

**Qué hace.** Crea `infra/postgres/03-ddl-canchas.sql` con la tabla `cancha` según design
§3.2: `cancha_id` identity, `nombre` único, `deporte` con `CHECK`
`PADEL`/`TENIS`/`BASQUET`, `hora_apertura`, `hora_cierre` con `CHECK`
`hora_cierre > hora_apertura`, `activa` con `DEFAULT TRUE`. Abre con `\c canchas_db` y
`SET ROLE canchas_user;`. Monta el archivo en `docker-compose.yml`.

**Cubre.** HU-02 (canchas y horario de atención), HU-06, RN-07 a nivel de datos.

**Verificación.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d canchas_db -c "\d cancha"
docker compose exec postgres psql -U admin -d canchas_db -c "INSERT INTO cancha (nombre,deporte,hora_apertura,hora_cierre) VALUES ('C1','PADEL','22:00','07:00')"
docker compose exec postgres psql -U admin -d canchas_db -c "INSERT INTO cancha (nombre,deporte,hora_apertura,hora_cierre) VALUES ('C2','FUTBOL','07:00','22:00')"
```

Esperado: la tabla existe y ambos `INSERT` fallan, el primero por `ck_cancha_horario` y el
segundo por `ck_cancha_deporte`.

---

## T3 — DDL de `bloqueo_mantenimiento`

**Qué hace.** Agrega a `infra/postgres/03-ddl-canchas.sql` la tabla
`bloqueo_mantenimiento` según design §3.3: `bloqueo_id` identity, `cancha_id` con
`fk_bloqueo_cancha ... ON DELETE RESTRICT`, `fecha`, `hora_inicio`, `hora_fin` con `CHECK`
`hora_fin > hora_inicio`, `motivo`, y el único `uq_bloqueo_franja` sobre
(`cancha_id`, `fecha`, `hora_inicio`).

**Cubre.** HU-03 (bloqueos de mantenimiento).

**Verificación.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d canchas_db -c "\d bloqueo_mantenimiento"
docker compose exec postgres psql -U admin -d canchas_db -c "INSERT INTO cancha (nombre,deporte,hora_apertura,hora_cierre) VALUES ('C1','PADEL','07:00','22:00')"
docker compose exec postgres psql -U admin -d canchas_db -c "INSERT INTO bloqueo_mantenimiento (cancha_id,fecha,hora_inicio,hora_fin,motivo) VALUES (1,'2026-09-01','08:00','10:00','Pintura')"
docker compose exec postgres psql -U admin -d canchas_db -c "INSERT INTO bloqueo_mantenimiento (cancha_id,fecha,hora_inicio,hora_fin,motivo) VALUES (1,'2026-09-01','08:00','09:00','Repetido')"
docker compose exec postgres psql -U admin -d canchas_db -c "DELETE FROM cancha WHERE cancha_id=1"
```

Esperado: el primer bloqueo entra, el segundo falla por `uq_bloqueo_franja` y el `DELETE`
falla por `fk_bloqueo_cancha`.

---

## T4 — DDL de `reserva` con índices

**Qué hace.** Crea `infra/postgres/04-ddl-reservas.sql` con la tabla `reserva` según design
§3.4: `id` identity, `usuario_id` y `cancha_id` sin clave foránea, `fecha`, `hora_inicio`,
`hora_fin` con `CHECK` `hora_fin = hora_inicio + INTERVAL '1 hour'`, `estado` con `CHECK` de
los tres valores, más los índices `ux_reserva_bloque_confirmada` (único parcial),
`ix_reserva_usuario_estado` e `ix_reserva_cancha_fecha`. Abre con `\c reservas_db` y
`SET ROLE reservas_user;`. Monta el archivo en `docker-compose.yml`.

**Cubre.** HU-04, HU-05, HU-06, RN-01, RN-02, RN-05, RN-08, decisiones D-02, D-03, D-04.

**Verificación.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d reservas_db -c "\d reserva"
docker compose exec postgres psql -U admin -d reservas_db -c "INSERT INTO reserva (usuario_id,cancha_id,fecha,hora_inicio,hora_fin,estado) VALUES (1,1,'2026-09-01','08:00','09:00','CONFIRMADA')"
docker compose exec postgres psql -U admin -d reservas_db -c "INSERT INTO reserva (usuario_id,cancha_id,fecha,hora_inicio,hora_fin,estado) VALUES (2,1,'2026-09-01','08:00','09:00','CONFIRMADA')"
docker compose exec postgres psql -U admin -d reservas_db -c "UPDATE reserva SET estado='CANCELADA' WHERE id=1"
docker compose exec postgres psql -U admin -d reservas_db -c "INSERT INTO reserva (usuario_id,cancha_id,fecha,hora_inicio,hora_fin,estado) VALUES (2,1,'2026-09-01','08:00','09:00','CONFIRMADA')"
docker compose exec postgres psql -U admin -d reservas_db -c "INSERT INTO reserva (usuario_id,cancha_id,fecha,hora_inicio,hora_fin,estado) VALUES (3,1,'2026-09-02','08:00','10:00','CONFIRMADA')"
```

Esperado, en orden: primer `INSERT` entra; el segundo falla por
`ux_reserva_bloque_confirmada` (RN-02); tras cancelar, el tercero entra (RN-05); el último
falla por `ck_reserva_bloque_una_hora` (RN-01).

---

## T5 — Datos semilla

**Qué hace.** Crea `infra/postgres/05-seed.sql`, idempotente con `ON CONFLICT DO NOTHING`
(D-09): un usuario `ADMIN` y uno `USUARIO` con hash BCrypt real, y tres canchas activas
(`PADEL`, `TENIS`, `BASQUET`) con `07:00`–`22:00`. Repite `\c` + `SET ROLE` por cada base en
el orden `usuarios_db` → `canchas_db`. Monta el archivo en `docker-compose.yml` y da de baja
el `infra/postgres/seed.sql` vacío que quedó huérfano.

**Cubre.** HU-01, HU-02 (criterios de seed) y HU-08 (idempotencia).

**Verificación.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d usuarios_db -c "SELECT rol, count(*) FROM usuario GROUP BY rol"
docker compose exec postgres psql -U admin -d usuarios_db -c "SELECT left(password_hash,4) FROM usuario"
docker compose exec postgres psql -U admin -d canchas_db -c "SELECT deporte, hora_apertura, hora_cierre, activa FROM cancha ORDER BY cancha_id"
docker compose exec postgres psql -U admin -d postgres -f /docker-entrypoint-initdb.d/05-seed.sql
docker compose exec postgres psql -U admin -d canchas_db -c "SELECT count(*) FROM cancha"
```

Esperado: un `ADMIN` y al menos un `USUARIO`; los hashes empiezan con `$2`; tres canchas
activas 07:00–22:00; la segunda ejecución del seed no falla y el conteo de canchas sigue en
tres. El `-d postgres` de la reejecución es obligatorio: sin `-d`, psql asume una base con el
nombre del usuario (`admin`) y falla con *database "admin" does not exist*. La base inicial
da igual porque el script trae su propio `\c`.

---

## T6 — Verificación final integral

**Qué hace.** No modifica archivos. Tiene dos partes:

**Parte A — arranque limpio con los cuatro scripts montados a la vez.** Las tareas T1 a T5
verifican cada script recién creado, y cada una borra el volumen, así que ninguna comprueba
que los cinco archivos (`01` a `05`) convivan y corran en orden desde volumen vacío. Esta
parte lo hace: un solo `down -v` + `up -d` y la revisión de que las cuatro tablas existan en
sus tres bases con el seed cargado, sin errores en el log de arranque.

**Parte B — independencia de datos y propiedad de tablas**, según design §8. Si algo falla,
se corrige la tarea que lo originó antes de cerrar la spec.

**Cubre.** HU-05 (independencia de datos), HU-06 (propiedad de tablas) y la convivencia de
todos los entregables E-01 a E-04.

**Verificación — parte A.**

```bash
docker compose down -v
docker compose up -d postgres
docker compose logs --tail=50 postgres
docker compose exec postgres psql -U admin -d usuarios_db -c "\dt"
docker compose exec postgres psql -U admin -d canchas_db  -c "\dt"
docker compose exec postgres psql -U admin -d reservas_db -c "\dt"
docker compose exec postgres psql -U admin -d usuarios_db -c "SELECT count(*) FROM usuario"
docker compose exec postgres psql -U admin -d canchas_db  -c "SELECT count(*) FROM cancha"
```

Esperado: el log no muestra `ERROR` ni `FATAL`; `usuarios_db` tiene `usuario`, `canchas_db`
tiene `cancha` y `bloqueo_mantenimiento`, `reservas_db` tiene `reserva`; el seed dejó dos
usuarios y tres canchas.

**Verificación — parte B.**

```bash
docker compose exec postgres psql -U admin -d usuarios_db -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public'"
docker compose exec postgres psql -U admin -d canchas_db  -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public'"
docker compose exec postgres psql -U admin -d reservas_db -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public'"
docker compose exec postgres psql -U reservas_user -d canchas_db -c "SELECT 1 FROM cancha"
docker compose exec postgres psql -U admin -d reservas_db -c "SELECT conname FROM pg_constraint WHERE contype='f' AND conrelid='reserva'::regclass"
```

Esperado: ningún `tableowner` es `admin`; la consulta de `reservas_user` sobre `canchas_db`
falla por permiso o autenticación; `reserva` no tiene ninguna clave foránea.

---

## T7 — Registro en la bitácora

**Qué hace.** Llena las tres filas C1, C2 y C3 de la sección "Spec 01 — Modelo de datos y
contratos" de `docs/bitacora.md` con fecha, intención del prompt, aceptación y correcciones
aplicadas en cada compuerta.

**Cubre.** Cierre documental de la spec (entregable E5 del alcance).

**Verificación.**

```powershell
type docs\bitacora.md
```

Esperado: las filas C1, C2 y C3 de la sección "Spec 01" ya no están vacías.

**Nota.** `docs/bitacora.md` está fuera de `.claude/specs/01-modelo-y-contratos/`. El
responsable autorizó expresamente esta excepción a CLAUDE.md §0.4 el 23/08/2026, solo para
este archivo y esta tarea.
