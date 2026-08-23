# Spec 01 — Modelo de datos y contratos · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 01").
Las siete decisiones de la sección 7 fueron resueltas por el responsable el 23/08/2026 y
ya están incorporadas a este documento y a `docs/contratos/README.md`.

## 1. Objetivo

Dejar congelados los dos cimientos sobre los que se construirán todas las specs
siguientes:

1. El **modelo de datos** de las tres bases PostgreSQL (`usuarios_db`, `canchas_db`,
   `reservas_db`), versionado como DDL en `infra/postgres/`.
2. El **contrato REST** (rutas, campos JSON y códigos de error) documentado en
   `docs/contratos/README.md`.

Esta spec **no implementa código Java ni React**. Sus entregables son SQL versionado y
documentación de contrato.

## 2. Entregables de la spec

| ID | Entregable | Ubicación |
|---|---|---|
| E-01 | DDL de tablas de `usuarios_db` | `infra/postgres/02-ddl-usuarios.sql` |
| E-02 | DDL de tablas de `canchas_db` | `infra/postgres/03-ddl-canchas.sql` |
| E-03 | DDL de tablas de `reservas_db` | `infra/postgres/04-ddl-reservas.sql` |
| E-04 | Datos semilla mínimos (un ADMIN, un USUARIO, tres canchas) | `infra/postgres/05-seed.sql` |
| E-05 | Campos JSON faltantes y rutas REST congeladas | `docs/contratos/README.md` |

`infra/postgres/01-init.sql` ya existe (usuarios y bases) y **no se modifica** en esta
spec. El archivo `infra/postgres/seed.sql` queda reemplazado por `05-seed.sql`.

### 2.1 Orden y propiedad de los scripts — condición obligatoria

Los scripts de `docker-entrypoint-initdb.d` se ejecutan **conectados a la base `postgres` y
como superusuario**. Por eso, cada archivo DDL y el seed deben comenzar con:

```
\c <base>
SET ROLE <usuario_de_esa_base>;
```

Si se omite, las tablas quedan como propiedad de `admin`, el microservicio no puede leerlas
y `spring.jpa.hibernate.ddl-auto=validate` falla al arrancar.

| Archivo | `\c` | `SET ROLE` |
|---|---|---|
| `02-ddl-usuarios.sql` | `usuarios_db` | `usuarios_user` |
| `03-ddl-canchas.sql` | `canchas_db` | `canchas_user` |
| `04-ddl-reservas.sql` | `reservas_db` | `reservas_user` |
| `05-seed.sql` | cada base que toque | el usuario de cada base |

## 3. Historias de usuario

### HU-01 — Modelo de usuarios y roles

Como equipo de desarrollo, necesito una tabla de usuarios con rol y estado, para que
`ms-usuarios` pueda autenticar y el resto de servicios sepa quién es ADMIN.

- **CUANDO** se aplique `02-ddl-usuarios.sql` sobre `usuarios_db`, **ENTONCES** existirá una
  tabla de usuarios que cubra los campos congelados `usuarioId`, `nombre`, `email`, `rol`,
  `activo` y una columna para el hash de la contraseña.
- **CUANDO** se intente insertar un usuario con un `rol` distinto de `ADMIN` o `USUARIO`,
  **ENTONCES** la base rechazará la fila por restricción de dominio.
- **SI** se intenta insertar dos usuarios con el mismo `email`, **ENTONCES** la base
  rechazará la segunda fila por restricción de unicidad.
- **CUANDO** se consulte la columna de contraseña de cualquier fila del seed, **ENTONCES**
  contendrá un hash BCrypt (prefijo `$2`), nunca texto plano.
- **CUANDO** se consulte la tabla tras el seed, **ENTONCES** habrá exactamente un usuario
  con `rol = ADMIN` y al menos uno con `rol = USUARIO`, ambos con `activo = true`.

### HU-02 — Modelo de canchas y horario de atención

Como administrador, necesito que el catálogo de canchas guarde deporte, horario de atención
y estado, para poder gestionarlo sin borrar registros históricos.

- **CUANDO** se aplique `03-ddl-canchas.sql` sobre `canchas_db`, **ENTONCES** existirá una
  tabla de canchas que cubra los campos congelados `canchaId`, `nombre`, `deporte`,
  `horaApertura`, `horaCierre` y `activa`.
- **CUANDO** se intente insertar un `deporte` distinto de PADEL, TENIS o BASQUET,
  **ENTONCES** la base rechazará la fila por restricción de dominio.
- **SI** `horaCierre` es menor o igual que `horaApertura`, **ENTONCES** la base rechazará la
  fila por restricción de coherencia.
- **CUANDO** se consulte la tabla tras el seed, **ENTONCES** habrá al menos una cancha con
  `activa = true` de cada deporte (PADEL, TENIS, BASQUET), con `horaApertura = 07:00` y
  `horaCierre = 22:00`.

### HU-03 — Modelo de bloqueos de mantenimiento

Como administrador, necesito registrar bloqueos de mantenimiento por cancha, fecha y bloque
horario, para que esos bloques no se ofrezcan como disponibles.

- **CUANDO** se aplique `03-ddl-canchas.sql`, **ENTONCES** existirá una tabla de bloqueos que
  cubra los campos congelados `bloqueoId`, `canchaId`, `fecha`, `horaInicio`, `horaFin` y
  `motivo`.
- **CUANDO** se intente eliminar una cancha referenciada por un bloqueo, **ENTONCES** la base
  impedirá la operación por integridad referencial (la clave foránea es válida: ambas tablas
  viven en `canchas_db`).
- **SI** se intenta registrar dos bloqueos con la misma terna `canchaId` + `fecha` +
  `horaInicio`, **ENTONCES** la base rechazará el segundo por restricción de unicidad.

### HU-04 — Modelo de reservas

Como usuario, necesito que mi reserva quede guardada con cancha, fecha, bloque horario y
estado, para que el sistema pueda validar disponibilidad y mostrarme mi historial.

- **CUANDO** se aplique `04-ddl-reservas.sql` sobre `reservas_db`, **ENTONCES** existirá una
  tabla de reservas que cubra los campos congelados `id`, `usuarioId`, `canchaId`, `fecha`,
  `horaInicio`, `horaFin` y `estado`.
- **CUANDO** se intente insertar un `estado` distinto de CONFIRMADA, CANCELADA o FINALIZADA,
  **ENTONCES** la base rechazará la fila por restricción de dominio (RN-08).
- **CUANDO** exista una reserva CONFIRMADA para una terna `canchaId` + `fecha` +
  `horaInicio`, **ENTONCES** un segundo INSERT con esa misma terna en estado CONFIRMADA será
  rechazado por un índice único parcial (RN-02).
- **SI** la reserva existente está CANCELADA, **ENTONCES** el INSERT de una nueva reserva
  CONFIRMADA sobre la misma terna será aceptado (RN-05).
- **CUANDO** se inserte una reserva, **ENTONCES** `horaFin` deberá ser exactamente una hora
  posterior a `horaInicio` (RN-01), verificado por restricción.

### HU-05 — Independencia de datos entre microservicios

Como equipo, necesito que ninguna base pueda leer las tablas de otra, para respetar la
independencia de datos exigida por la arquitectura.

- **CUANDO** el usuario `reservas_user` intente consultar una tabla de `canchas_db`,
  **ENTONCES** PostgreSQL responderá con error de permiso o de base inexistente.
- **CUANDO** se revise `04-ddl-reservas.sql`, **ENTONCES** `usuarioId` y `canchaId` serán
  columnas numéricas **sin** clave foránea hacia otra base.
- **SI** una spec posterior necesita datos de otro dominio, **ENTONCES** los obtendrá por
  HTTP/REST, nunca por SQL cruzado.

### HU-06 — Propiedad de las tablas verificable

Como equipo, necesito que cada tabla pertenezca al usuario de su microservicio, para que el
arranque con `ddl-auto=validate` no falle por permisos.

- **CUANDO** se levante el entorno desde cero y se consulte `pg_tables` en cada base,
  **ENTONCES** el `tableowner` de toda tabla será `usuarios_user`, `canchas_user` o
  `reservas_user` según la base, y nunca el superusuario `admin`.
- **SI** algún script DDL no declara `\c <base>` y `SET ROLE <usuario>`, **ENTONCES** la
  tarea se considera no cumplida aunque las tablas existan.

### HU-07 — Contrato REST documentado

Como equipo, necesito las rutas y payloads acordados por escrito antes de programar, para que
los cuatro microservicios y los tres microfrontends no se desincronicen.

- **CUANDO** se cierre esta spec, **ENTONCES** `docs/contratos/README.md` contendrá los campos de
  usuario, cancha y bloqueo y la tabla de rutas bajo el título "Rutas REST
  congeladas".
- **CUANDO** cualquier endpoint devuelva una respuesta, **ENTONCES** el campo `password` no
  aparecerá en el cuerpo; solo se acepta en el request de registro y de inicio de sesión.
- **CUANDO** un endpoint devuelva un error de negocio, **ENTONCES** el contrato documentará el
  par HTTP + `codigo` exacto de la tabla de errores ya congelada.
- **SI** se agrega o renombra un campo, **ENTONCES** quedará registrado en la tabla "Registro
  de cambios" de `docs/contratos/README.md` con fecha y specs afectadas.

### HU-08 — Datos semilla reproducibles

Como equipo, necesito datos de prueba cargados al levantar el entorno, para poder verificar
cada spec sin insertar filas a mano.

- **CUANDO** se levante el entorno desde cero con `docker compose up -d`, **ENTONCES** las tres
  bases quedarán con sus tablas creadas y `05-seed.sql` aplicado.
- **CUANDO** se ejecute el seed dos veces, **ENTONCES** no se duplicarán filas ni fallará la
  carga.

## 4. Reglas de negocio cubiertas

| RN | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | Reserva de cancha + fecha + bloque de 1 hora | Total — columnas y restricción de duración (HU-04) |
| RN-02 | No reservar un bloque ocupado | Total a nivel de datos — índice único parcial sobre CONFIRMADA (HU-04); la validación de servicio va en spec posterior |
| RN-03 | Usuario cancela lo propio; admin cualquiera | Parcial — el modelo guarda `usuarioId` y `rol`; la autorización va en spec posterior |
| RN-04 | Solo se cancela una reserva no ocurrida | Parcial — el modelo guarda `fecha` y `horaInicio`; la validación va en spec posterior |
| RN-05 | Cancelar libera el bloque | Total a nivel de datos — el índice único ignora CANCELADA (HU-04) |
| RN-06 | Límite configurable de reservas activas | Parcial — el `estado` permite contar activas; el valor lo aporta la variable de entorno `RESERVAS_MAX_ACTIVAS` (default 3) y la validación va en la spec de `ms-reservas` |
| RN-07 | Solo el admin gestiona canchas y horarios | Parcial — el modelo guarda `horaApertura`, `horaCierre` y `activa`; la autorización va en spec posterior |
| RN-08 | Estados CONFIRMADA / CANCELADA / FINALIZADA | Total — restricción de dominio (HU-04) |

## 5. Contrato REST

`docs/contratos/README.md` es la **única fuente de verdad** de campos JSON, payloads, rutas,
roles y códigos de error. Esta spec no los copia: los referencia. Ninguna spec del proyecto
vuelve a duplicar el contrato, porque dos copias terminan desincronizándose.

| Qué se necesita | Sección del contrato |
|---|---|
| Campos JSON congelados y sus tipos | "Campos acordados" |
| Payloads `LoginResponse`, `DisponibilidadResponse` y los tres reportes | "Payloads congelados" |
| Rutas, rol requerido y códigos de respuesta por endpoint | "Rutas REST congeladas" |
| Formato de error y catálogo de códigos | "Formato de error" |
| Historial de decisiones sobre el contrato | "Registro de cambios" |

Lo único que esta spec agrega sobre el contrato, y que se verifica en sus tareas:

- La contraseña se persiste como hash **BCrypt** (el de Spring Security); la columna nunca
  guarda texto plano y el campo `password` nunca se serializa en una respuesta.
- El límite de reservas activas se lee de la variable de entorno `RESERVAS_MAX_ACTIVAS`
  (default 3).

## 6. Fuera de alcance de esta spec

- Todo código Java, Spring Boot, React y Webpack. Esta spec entrega SQL y documentación.
- Autenticación, emisión/validación de token y configuración de BCrypt en Spring Security: se
  define en la spec de `ms-usuarios`. Aquí solo se exige que el hash del seed sea BCrypt.
- Lógica de servicio de las reglas RN-02, RN-03, RN-04, RN-06 y RN-07, incluida la lectura de
  `RESERVAS_MAX_ACTIVAS`.
- Transición automática a FINALIZADA (job o cálculo en consulta): se decide en la spec de
  `ms-reservas`.
- Cálculo de `porcentajeOcupacion` y agregaciones de `ms-reportes`.
- Configuración de Module Federation y pantallas de los microfrontends.
- Prohibido por CLAUDE.md §2: pagos, notificaciones, reservas recurrentes, torneos, app móvil
  nativa, reportes BI.

## 7. Datos que faltaron y hubo que suponer

Sin supuestos. Los siete puntos abiertos de la versión anterior fueron resueltos por el
responsable el 23/08/2026 y quedaron incorporados así:

| # | Decisión | Dónde quedó |
|---|---|---|
| 1 | Campos JSON de usuario, cancha y bloqueo congelados; `password` solo en request | `docs/contratos/README.md`, HU-01/02/03 |
| 2 | Rutas REST aprobadas tal como se propusieron | `docs/contratos/README.md`, sección "Rutas REST congeladas" |
| 3 | Un archivo DDL por base, numerados `02` a `05`, cada uno con `\c` + `SET ROLE` | §2, §2.1, HU-06 |
| 4 | Horario 07:00–22:00 solo como dato semilla, no regla | HU-02 |
| 5 | Límite 3 por defecto vía `RESERVAS_MAX_ACTIVAS` | §4 (RN-06), §6 |
| 6 | Hash BCrypt de Spring Security, nunca texto plano | §5, HU-01, §6 |
| 7 | `ms-reportes` sin base propia | §6 |
