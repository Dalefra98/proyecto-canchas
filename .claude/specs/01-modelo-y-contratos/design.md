# Spec 01 — Modelo de datos y contratos · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 01").
Base: `requirements.md` aprobado el 23/08/2026 y `docs/contratos/README.md`.

## 1. Alcance del diseño

Este documento define:

- El DDL de las tres bases (columnas, tipos, restricciones y nombres de constraint).
- La forma de los DTOs y sus validaciones, y el mapeo endpoint → DTO.
- La traducción de excepciones de negocio a códigos HTTP.

Los nombres de campo, los payloads, las rutas, los roles y los códigos de error **no se
copian aquí**: viven en `docs/contratos/README.md`, única fuente de verdad. La sección 4 es
la única que los repite, y solo como comprobación de que el modelo los cubre.

Los entregables ejecutables de esta spec siguen siendo **solo SQL y documentación**. Las
tablas de DTOs y endpoints son el contrato que consumirán las specs de cada microservicio;
aquí no se escribe Java.

## 2. Distribución física

| Base | Usuario dueño | Archivo DDL | Tablas |
|---|---|---|---|
| `usuarios_db` | `usuarios_user` | `infra/postgres/02-ddl-usuarios.sql` | `usuario` |
| `canchas_db` | `canchas_user` | `infra/postgres/03-ddl-canchas.sql` | `cancha`, `bloqueo_mantenimiento` |
| `reservas_db` | `reservas_user` | `infra/postgres/04-ddl-reservas.sql` | `reserva` |
| — | cada usuario | `infra/postgres/05-seed.sql` | datos semilla |

Cada archivo abre con `\c <base>` y `SET ROLE <usuario>;` (requirements §2.1). El seed
repite el par por cada base que toca, en el orden `usuarios_db` → `canchas_db` →
`reservas_db`.

Convención de nombres: tablas y columnas en `snake_case` singular, sin tildes. Los campos
JSON congelados se mapean en la columna "Campo JSON" de cada tabla; el mapeo entidad ↔ DTO
es manual (CLAUDE.md §3).

## 3. Modelo de datos

### 3.1 `usuarios_db.usuario`

| Columna | Tipo | Nulo | Restricción | Campo JSON |
|---|---|---|---|---|
| `usuario_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | no | `pk_usuario` | `usuarioId` |
| `nombre` | `VARCHAR(80)` | no | — | `nombre` |
| `email` | `VARCHAR(120)` | no | `uq_usuario_email` | `email` |
| `password_hash` | `VARCHAR(72)` | no | `ck_usuario_password_bcrypt`: empieza con `$2` | — (nunca se serializa) |
| `rol` | `VARCHAR(8)` | no | `ck_usuario_rol`: `ADMIN` \| `USUARIO` | `rol` |
| `activo` | `BOOLEAN` | no | `DEFAULT TRUE` | `activo` |

`password_hash` no tiene campo JSON de salida: el campo `password` del contrato existe solo
en el request. BCrypt produce exactamente **60 caracteres**; se declara `VARCHAR(72)` como
margen por si a futuro se cambia de algoritmo, no porque BCrypt llegue a esa longitud.

### 3.2 `canchas_db.cancha`

| Columna | Tipo | Nulo | Restricción | Campo JSON |
|---|---|---|---|---|
| `cancha_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | no | `pk_cancha` | `canchaId` |
| `nombre` | `VARCHAR(80)` | no | `uq_cancha_nombre` | `nombre` |
| `deporte` | `VARCHAR(8)` | no | `ck_cancha_deporte`: `PADEL` \| `TENIS` \| `BASQUET` | `deporte` |
| `hora_apertura` | `TIME` | no | `ck_cancha_horario`: `hora_cierre > hora_apertura` | `horaApertura` |
| `hora_cierre` | `TIME` | no | (misma restricción) | `horaCierre` |
| `activa` | `BOOLEAN` | no | `DEFAULT TRUE` | `activa` |

### 3.3 `canchas_db.bloqueo_mantenimiento`

| Columna | Tipo | Nulo | Restricción | Campo JSON |
|---|---|---|---|---|
| `bloqueo_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | no | `pk_bloqueo` | `bloqueoId` |
| `cancha_id` | `BIGINT` | no | `fk_bloqueo_cancha` → `cancha(cancha_id)` `ON DELETE RESTRICT` | `canchaId` |
| `fecha` | `DATE` | no | — | `fecha` |
| `hora_inicio` | `TIME` | no | `ck_bloqueo_rango`: `hora_fin > hora_inicio` | `horaInicio` |
| `hora_fin` | `TIME` | no | (misma restricción) | `horaFin` |
| `motivo` | `VARCHAR(200)` | no | — | `motivo` |

`uq_bloqueo_franja` sobre (`cancha_id`, `fecha`, `hora_inicio`) — HU-03.
La clave foránea es legítima: ambas tablas viven en `canchas_db`.

### 3.4 `reservas_db.reserva`

| Columna | Tipo | Nulo | Restricción | Campo JSON |
|---|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | no | `pk_reserva` | `id` |
| `usuario_id` | `BIGINT` | no | sin FK (otra base) | `usuarioId` |
| `cancha_id` | `BIGINT` | no | sin FK (otra base) | `canchaId` |
| `fecha` | `DATE` | no | — | `fecha` |
| `hora_inicio` | `TIME` | no | `ck_reserva_bloque_una_hora`: `hora_fin = hora_inicio + INTERVAL '1 hour'` | `horaInicio` |
| `hora_fin` | `TIME` | no | (misma restricción) | `horaFin` |
| `estado` | `VARCHAR(12)` | no | `ck_reserva_estado`: `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | `estado` |

Índices:

| Nombre | Definición | Regla |
|---|---|---|
| `ux_reserva_bloque_confirmada` | `UNIQUE (cancha_id, fecha, hora_inicio) WHERE estado = 'CONFIRMADA'` | RN-02 / RN-05 |
| `ix_reserva_usuario_estado` | `(usuario_id, estado)` | historial propio y conteo de activas (RN-06) |
| `ix_reserva_cancha_fecha` | `(cancha_id, fecha)` | consulta de disponibilidad |

El índice único es **parcial**: al pasar una fila a `CANCELADA` sale del índice y el bloque
queda libre sin borrar el registro histórico.

## 4. Verificación campo por campo contra `docs/contratos/README.md`

| Campo del contrato | Aparece en | Coincide |
|---|---|---|
| `id` | `reserva.id` | sí |
| `estado` | `reserva.estado` | sí |
| `fecha` | `reserva.fecha`, `bloqueo_mantenimiento.fecha` | sí |
| `horaInicio` | `reserva.hora_inicio`, `bloqueo_mantenimiento.hora_inicio` | sí |
| `horaFin` | `reserva.hora_fin`, `bloqueo_mantenimiento.hora_fin` | sí |
| `canchaId` | `cancha.cancha_id`, `bloqueo_mantenimiento.cancha_id`, `reserva.cancha_id` | sí |
| `nombre` | `cancha.nombre`, `usuario.nombre` | sí |
| `deporte` | `cancha.deporte` | sí |
| `horaApertura` | `cancha.hora_apertura` | sí |
| `horaCierre` | `cancha.hora_cierre` | sí |
| `activa` | `cancha.activa` | sí |
| `bloqueoId` | `bloqueo_mantenimiento.bloqueo_id` | sí |
| `motivo` | `bloqueo_mantenimiento.motivo` | sí |
| `usuarioId` | `usuario.usuario_id`, `reserva.usuario_id` | sí |
| `email` | `usuario.email` | sí |
| `password` | solo en DTO de request; columna `password_hash` | sí |
| `rol` | `usuario.rol` | sí |
| `activo` | `usuario.activo` | sí |
| `token` | `LoginResponse.token` | sí |
| `usuario` | `LoginResponse.usuario` | sí |
| `bloques` | `DisponibilidadResponse.bloques` | sí |
| `disponible` | `BloqueDisponibilidad.disponible` | sí |
| `desde` / `hasta` / `items` | envoltura de los tres reportes | sí |
| `horasReservadas` / `horasDisponibles` | `ReporteOcupacionItem` | sí |
| `totalReservas` | `ReporteReservasItem` | sí |
| `totalCancelaciones` | `ReporteCancelacionesItem` | sí |
| `porcentajeOcupacion` | `ReporteOcupacionItem`, calculado en `ms-reportes`, sin columna | sí |

Ningún nombre se renombró, abrevió ni tradujo. Ya no queda ningún campo sin congelar: los
que faltaban (disponibilidad, reportes y login) se agregaron al contrato el 23/08/2026 y se
diseñan en §5.4 y §5.5.

## 5. DTOs y validaciones

Reglas comunes: un DTO de request por operación, uno de response por recurso; mapper manual;
validación con `jakarta.validation`; formato `HH:mm` validado con `@Pattern` sobre
`^([01]\d|2[0-3]):[0-5]\d$` y `fecha` con `@JsonFormat`/`@Pattern` sobre `AAAA-MM-DD`.

### 5.1 `ms-usuarios`

| DTO | Campo | Validación |
|---|---|---|
| `LoginRequest` | `email` | `@NotBlank`, `@Email`, `@Size(max=120)` |
| | `password` | `@NotBlank`, `@Size(max=100)` |
| `RegistroUsuarioRequest` | `nombre` | `@NotBlank`, `@Size(max=80)` |
| | `email` | `@NotBlank`, `@Email`, `@Size(max=120)` |
| | `password` | `@NotBlank`, `@Size(min=8, max=100)` |
| `EstadoUsuarioRequest` | `activo` | `@NotNull` |
| `UsuarioResponse` | `usuarioId`, `nombre`, `email`, `rol`, `activo` | sin validación; **sin `password`** |
| `LoginResponse` | `token` | string, respuesta de `POST /api/usuarios/sesiones` |
| | `usuario` | objeto `UsuarioResponse` anidado |

`rol` no se acepta en el registro: el endpoint público crea siempre `USUARIO`. Crear un
ADMIN es tarea del seed.

### 5.2 `ms-canchas`

| DTO | Campo | Validación |
|---|---|---|
| `CanchaRequest` | `nombre` | `@NotBlank`, `@Size(max=80)` |
| | `deporte` | `@NotNull`, enum `PADEL` \| `TENIS` \| `BASQUET` |
| | `horaApertura` | `@NotBlank`, patrón `HH:mm` |
| | `horaCierre` | `@NotBlank`, patrón `HH:mm` |
| `EstadoCanchaRequest` | `activa` | `@NotNull` |
| `CanchaResponse` | `canchaId`, `nombre`, `deporte`, `horaApertura`, `horaCierre`, `activa` | — |
| `BloqueoRequest` | `fecha` | `@NotNull`, patrón `AAAA-MM-DD` |
| | `horaInicio` / `horaFin` | `@NotBlank`, patrón `HH:mm` |
| | `motivo` | `@NotBlank`, `@Size(max=200)` |
| `BloqueoResponse` | `bloqueoId`, `canchaId`, `fecha`, `horaInicio`, `horaFin`, `motivo` | — |

`canchaId` del bloqueo viene de la ruta, no del cuerpo. La coherencia
`horaCierre > horaApertura` y `horaFin > horaInicio` se valida en el servicio y la respalda
la restricción de base.

### 5.3 `ms-reservas`

| DTO | Campo | Validación |
|---|---|---|
| `ReservaRequest` | `canchaId` | `@NotNull`, `@Positive` |
| | `fecha` | `@NotNull`, patrón `AAAA-MM-DD` |
| | `horaInicio` | `@NotBlank`, patrón `HH:mm` |
| `ReservaResponse` | `id`, `usuarioId`, `canchaId`, `fecha`, `horaInicio`, `horaFin`, `estado` | — |

`horaFin` no viaja en el request: el servicio lo deriva sumando una hora (RN-01) y la
restricción `ck_reserva_bloque_una_hora` lo garantiza. `usuarioId` sale del token, nunca del
cuerpo (RN-03).

### 5.4 Disponibilidad — `ms-reservas`

| DTO | Campo | Tipo / regla |
|---|---|---|
| `DisponibilidadResponse` | `canchaId` | number, eco del query param |
| | `fecha` | string `AAAA-MM-DD`, eco del query param |
| | `horaApertura` / `horaCierre` | string `HH:mm`, obtenidos por HTTP de `GET /api/canchas/{canchaId}` |
| | `bloques` | arreglo de `BloqueDisponibilidad`, uno por hora entre apertura y cierre |
| `BloqueDisponibilidad` | `horaInicio` / `horaFin` | string `HH:mm`, `horaFin = horaInicio + 1h` |
| | `disponible` | boolean |

Cálculo de `disponible` (se implementa en la spec de `ms-reservas`, aquí solo se congela la
regla): es `false` si existe una reserva en estado `CONFIRMADA` para esa cancha, fecha y
`horaInicio`, **o** si el bloque cae dentro de un bloqueo de mantenimiento devuelto por
`GET /api/canchas/{canchaId}/bloqueos`; `true` en cualquier otro caso.

Los bloqueos y el horario de atención viven en `canchas_db`: `ms-reservas` los obtiene por
HTTP, nunca por SQL (§8).

### 5.5 Reportes — `ms-reportes`

Los tres reportes comparten la envoltura `{ desde, hasta, items }`, donde `desde` y `hasta`
son el eco del rango consultado en formato `AAAA-MM-DD`.

| DTO | Campos de cada elemento de `items` |
|---|---|
| `ReporteOcupacionResponse` | `canchaId`, `nombre`, `deporte`, `horasReservadas`, `horasDisponibles`, `porcentajeOcupacion` |
| `ReporteReservasResponse` | `canchaId`, `nombre`, `deporte`, `totalReservas` |
| `ReporteCancelacionesResponse` | `canchaId`, `nombre`, `totalCancelaciones` |

`porcentajeOcupacion` = `horasReservadas / horasDisponibles * 100`, acotado a 0-100.
`nombre` y `deporte` provienen de `ms-canchas` por HTTP; los conteos, de `ms-reservas`.
`ms-reportes` no tiene base propia ni datasource.

## 6. Endpoints y DTOs

El **rol requerido y los códigos de respuesta de cada endpoint viven en
`docs/contratos/README.md`**, sección "Rutas REST congeladas"; no se repiten aquí. Esta
tabla solo agrega el mapeo endpoint → DTO, que el contrato no cubre.

| Verbo | Ruta | Request | Response |
|---|---|---|---|
| POST | `/api/usuarios/sesiones` | `LoginRequest` | `LoginResponse` |
| POST | `/api/usuarios` | `RegistroUsuarioRequest` | `UsuarioResponse` |
| GET | `/api/usuarios` | — | `UsuarioResponse[]` |
| PATCH | `/api/usuarios/{usuarioId}/estado` | `EstadoUsuarioRequest` | `UsuarioResponse` |
| GET | `/api/canchas` | — | `CanchaResponse[]` |
| GET | `/api/canchas/{canchaId}` | — | `CanchaResponse` |
| POST | `/api/canchas` | `CanchaRequest` | `CanchaResponse` |
| PUT | `/api/canchas/{canchaId}` | `CanchaRequest` | `CanchaResponse` |
| PATCH | `/api/canchas/{canchaId}/estado` | `EstadoCanchaRequest` | `CanchaResponse` |
| GET | `/api/canchas/{canchaId}/bloqueos` | — | `BloqueoResponse[]` |
| POST | `/api/canchas/{canchaId}/bloqueos` | `BloqueoRequest` | `BloqueoResponse` |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | — | sin cuerpo |
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | query | `DisponibilidadResponse` |
| POST | `/api/reservas` | `ReservaRequest` | `ReservaResponse` |
| GET | `/api/reservas` | — | `ReservaResponse[]` |
| GET | `/api/reservas/mias` | — | `ReservaResponse[]` |
| PATCH | `/api/reservas/{id}/cancelacion` | — | `ReservaResponse` |
| GET | `/api/reportes/ocupacion?desde&hasta` | query | `ReporteOcupacionResponse` |
| GET | `/api/reportes/reservas?desde&hasta` | query | `ReporteReservasResponse` |
| GET | `/api/reportes/cancelaciones?desde&hasta` | query | `ReporteCancelacionesResponse` |

`GET /api/canchas/{canchaId}` y `GET /api/canchas/{canchaId}/bloqueos` son las dos lecturas
que `ms-reservas` consume para calcular disponibilidad: la primera aporta `horaApertura` y
`horaCierre` y confirma que la cancha existe; la segunda, los bloques a excluir. El contrato les asigna rol
ADMIN y USUARIO, y por eso no declaran `403` (ver D-11).

El token de `USUARIO` en `PATCH /api/reservas/{id}/cancelacion` solo pasa si la reserva es
propia (RN-03); un ADMIN pasa siempre.

## 7. Excepciones a códigos HTTP

Traducción centralizada en `@RestControllerAdvice` por microservicio. Nunca sale un
stacktrace.

| Excepción | HTTP | `codigo` | Origen típico |
|---|---|---|---|
| `MethodArgumentNotValidException` (jakarta.validation) | 400 | `DATOS_INVALIDOS` | DTO inválido |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | JSON malformado, `HH:mm` ilegible |
| `DatosInvalidosException` | 400 | `DATOS_INVALIDOS` | coherencia de horario, rango de fechas |
| `NoAutenticadoException` | 401 | `NO_AUTENTICADO` | token ausente, inválido o usuario `activo = false` |
| `SinPermisoException` | 403 | `SIN_PERMISO` | rol insuficiente, cancelar reserva ajena (RN-03) |
| `RecursoNoEncontradoException` | 404 | `NO_ENCONTRADO` | id inexistente en cualquier dominio |
| `BloqueOcupadoException` | 409 | `BLOQUE_OCUPADO` | RN-02, incluida la violación de `ux_reserva_bloque_confirmada` |
| `LimiteReservasException` | 409 | `LIMITE_RESERVAS` | RN-06, `RESERVAS_MAX_ACTIVAS` |
| `ReservaPasadaException` | 409 | `RESERVA_PASADA` | RN-04 |
| `EmailDuplicadoException` | 409 | `EMAIL_DUPLICADO` | violación de `uq_usuario_email` en el registro |
| `DataIntegrityViolationException` no mapeada | 409 | `DATOS_INVALIDOS` | red de seguridad; se registra en log |

`EMAIL_DUPLICADO` se agregó al contrato el 23/08/2026; ya no se reutiliza `DATOS_INVALIDOS`
para ese caso.

## 8. Independencia de datos

| Mecanismo | Cómo se cumple |
|---|---|
| Sin FK entre bases | `reserva.usuario_id` y `reserva.cancha_id` son `BIGINT` sin FK (§3.4) |
| Sin SQL cruzado | cada base pertenece a un usuario distinto y `01-init.sql` ya hizo `REVOKE ALL ... FROM PUBLIC` |
| Sin tabla compartida | ninguna tabla se repite en dos bases |
| `ms-reportes` | no tiene base ni datasource; consulta `ms-canchas` y `ms-reservas` por HTTP |
| Validar que la cancha existe al reservar | `ms-reservas` llama `GET /api/canchas` por HTTP, no consulta `canchas_db` |

## 9. Decisiones de diseño

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D-01 | `estado` y `deporte` como `VARCHAR` + `CHECK` | tipo `ENUM` de PostgreSQL | agregar un valor a un ENUM exige `ALTER TYPE`; el `CHECK` es portable y Hibernate lo valida sin dialecto extra |
| D-02 | Índice único **parcial** sobre `CONFIRMADA` | índice único sobre las tres columnas sin filtro | el filtro es lo que permite RN-05: con el índice total, una reserva cancelada seguiría bloqueando el bloque |
| D-03 | Cancelar = cambiar `estado` a `CANCELADA` | `DELETE` de la fila | RN-08 exige trazabilidad y `ms-reportes` necesita contar cancelaciones |
| D-04 | `hora_inicio`/`hora_fin` como dos `TIME` | `tstzrange` con exclusión GiST | el bloque es fijo de 1 hora; el rango y su índice de exclusión son innecesarios y ocultan el contrato `HH:mm` |
| D-05 | `horaFin` derivada en el servidor | recibirla en `ReservaRequest` | evita que el cliente envíe un bloque de duración distinta y elimina una validación cruzada |
| D-06 | `BIGINT GENERATED ALWAYS AS IDENTITY` | `SERIAL` | `IDENTITY` es el estándar SQL y no deja secuencias huérfanas al borrar la tabla |
| D-07 | Un DDL por base con `\c` + `SET ROLE` | un único script ejecutado como superusuario | sin `SET ROLE` las tablas quedan de `admin` y `ddl-auto=validate` falla al arrancar |
| D-08 | `activo` / `activa` como `BOOLEAN` | borrado físico de usuarios y canchas | RN-07 pide inactivar, y las reservas históricas referencian canchas que ya no se usan |
| D-09 | Seed idempotente con `ON CONFLICT DO NOTHING` | `TRUNCATE` + `INSERT` | HU-08 exige poder ejecutarlo dos veces sin duplicar ni fallar |
| D-10 | `password_hash` con `CHECK` de prefijo `$2` | confiar solo en el código de la aplicación | hace verificable en base la decisión 6 (BCrypt, nunca texto plano) |
| D-11 | `GET /api/canchas/{canchaId}/bloqueos` pasa a rol ADMIN **y** USUARIO | dejarlo solo ADMIN y que `ms-reservas` consulte con un token técnico de administrador | el cálculo de disponibilidad para un usuario final necesita excluir los bloqueos; con rol solo-ADMIN habría que inventar un token privilegiado o duplicar los bloqueos en `reservas_db`, y lo segundo rompe la independencia de datos. La información expuesta (cancha, fecha, franja, motivo) no es sensible: el usuario ya ve el bloque como no disponible |
| D-12 | `GET /api/canchas/{canchaId}` como lectura individual | que `ms-reservas` filtre en memoria el resultado de `GET /api/canchas` | traer el catálogo completo para leer un horario crece con el número de canchas y no distingue "no existe" de "está inactiva"; la lectura individual devuelve `404` limpio |
| D-13 | `LoginResponse` = `{ token, usuario }` | devolver `UsuarioResponse` plano y el token en un header | el shell necesita ambos datos para pasar `usuario={{ id, nombre, rol }}` a los remotes; un solo cuerpo evita que cada microfrontend lea headers |
| D-14 | Reportes con envoltura `{ desde, hasta, items }` | devolver el arreglo desnudo | el eco del rango hace autocontenida la respuesta para la pantalla de reportes y permite agregar totales sin romper el contrato |

## 10. Verificación del diseño

Comandos previstos para las tareas de la spec (CLAUDE.md §1, solo Docker):

```bash
docker compose down -v
docker compose up -d postgres
docker compose exec postgres psql -U admin -d reservas_db -c "\d+ reserva"
docker compose exec postgres psql -U admin -d canchas_db -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public'"
```

Prueba de RN-02 / RN-05: dos `INSERT` `CONFIRMADA` sobre la misma terna (el segundo debe
fallar), luego `UPDATE` a `CANCELADA` y repetir el `INSERT` (debe pasar).

## 11. Fuera de este diseño

- Código Java, React, `pom.xml`, `application.yml` y clases de excepción: specs siguientes.
- Configuración de Spring Security, emisión del token y su formato interno.
- Implementación del cálculo de `disponible` y de los agregados de reportes: aquí solo se
  congela la forma de la respuesta y la regla (§5.4, §5.5).
- Estrategia de paso a `FINALIZADA`.
- `docker-compose.yml`: montar `02` a `05` en `docker-entrypoint-initdb.d` es tarea de esta
  spec, pero cualquier otro cambio al compose queda fuera.
