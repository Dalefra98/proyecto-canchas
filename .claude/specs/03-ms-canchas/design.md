# Spec 03 — ms-canchas · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 03").
Falta `tasks.md`: el codigo de produccion se escribe tarea por tarea, una a la vez.

Base: `.claude/specs/03-ms-canchas/requirements.md` (C1 aprobado el 23/08/2026),
`docs/contratos/README.md` (con los cuatro cambios del 23/08/2026 por P-01, P-02, P-05 y
P-06), `infra/postgres/03-ddl-canchas.sql` y `05-seed.sql`, y
`.claude/specs/02-ms-usuarios/design.md` para reutilizar lo ya fijado (JWT, manejo de
excepciones, `Dockerfile`, versiones).

Lo que **no** se vuelve a decidir aqui, por estar fijado en `CLAUDE.md` §1 y §3 y en la
spec 02: Spring Boot 3.5.3, springdoc 2.8.6, jjwt 0.12.6, el patron de `Dockerfile` con
cache mount de BuildKit y el patron de filtro JWT HS256 validado localmente.

## 1. Verificacion campo por campo contra el contrato

Comparacion de los nombres JSON congelados con las columnas del DDL ya aplicado. **No se
renombra nada**: la columna es interna y el campo JSON es el del contrato.

### Cancha

| Campo JSON (contrato) | Tipo contrato | Columna en `cancha` | Tipo columna | Coincide |
|---|---|---|---|---|
| `canchaId` | number | `cancha_id` | `BIGINT` identity | Si |
| `nombre` | string | `nombre` | `VARCHAR(80)` unico | Si |
| `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | `deporte` | `VARCHAR(8)` con `CHECK` | Si |
| `horaApertura` | string `HH:mm` | `hora_apertura` | `TIME` | Si — la columna guarda la hora, el DTO la serializa como `HH:mm` |
| `horaCierre` | string `HH:mm` | `hora_cierre` | `TIME` | Si |
| `activa` | boolean | `activa` | `BOOLEAN` default `TRUE` | Si |

### Bloqueo de mantenimiento

| Campo JSON (contrato) | Tipo contrato | Columna en `bloqueo_mantenimiento` | Tipo columna | Coincide |
|---|---|---|---|---|
| `bloqueoId` | number | `bloqueo_id` | `BIGINT` identity | Si |
| `canchaId` | number | `cancha_id` | `BIGINT` con FK | Si |
| `fecha` | string `AAAA-MM-DD` | `fecha` | `DATE` | Si |
| `horaInicio` | string `HH:mm` | `hora_inicio` | `TIME` | Si |
| `horaFin` | string `HH:mm` | `hora_fin` | `TIME` | Si |
| `motivo` | string | `motivo` | `VARCHAR(200)` | Si |

Sin discrepancias. Ningun campo del contrato falta y ningun campo nuevo se agrega. La
composicion del bloqueo es la de las "Notas de uso" del contrato: `bloqueoId`, `canchaId`,
`fecha`, `horaInicio`, `horaFin`, `motivo`.

Nota ya registrada en el requirements §5: la ruta congelada del DELETE usa `{id}`, no
`{bloqueoId}`. Se implementa literal; el campo del cuerpo sigue siendo `bloqueoId`.

## 2. Modelo de datos

Fuente unica: `infra/postgres/03-ddl-canchas.sql`. **Esta spec no modifica el DDL.** Las
entidades se adaptan a las tablas, nunca al contrario.

### Tabla `cancha` (base `canchas_db`, propietario `canchas_user`)

| Columna | Tipo | Nulo | Restriccion |
|---|---|---|---|
| `cancha_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | No | `pk_cancha` PRIMARY KEY |
| `nombre` | `VARCHAR(80)` | No | `uq_cancha_nombre` UNIQUE |
| `deporte` | `VARCHAR(8)` | No | `ck_cancha_deporte` CHECK IN (`PADEL`, `TENIS`, `BASQUET`) |
| `hora_apertura` | `TIME` | No | — |
| `hora_cierre` | `TIME` | No | `ck_cancha_horario` CHECK `hora_cierre > hora_apertura` |
| `activa` | `BOOLEAN` | No | DEFAULT `TRUE` |

### Tabla `bloqueo_mantenimiento` (misma base)

| Columna | Tipo | Nulo | Restriccion |
|---|---|---|---|
| `bloqueo_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | No | `pk_bloqueo` PRIMARY KEY |
| `cancha_id` | `BIGINT` | No | `fk_bloqueo_cancha` FK -> `cancha(cancha_id)` `ON DELETE RESTRICT` |
| `fecha` | `DATE` | No | — |
| `hora_inicio` | `TIME` | No | `uq_bloqueo_franja` UNIQUE (`cancha_id`, `fecha`, `hora_inicio`) |
| `hora_fin` | `TIME` | No | `ck_bloqueo_rango` CHECK `hora_fin > hora_inicio` |
| `motivo` | `VARCHAR(200)` | No | — |

La clave foranea es legitima: ambas tablas viven en `canchas_db` y pertenecen a este
microservicio.

### Mapeo de la entidad `Cancha` (paquete `ec.ups.dae.canchas.entity`)

| Atributo | Tipo Java | Columna | Notas de mapeo |
|---|---|---|---|
| `canchaId` | `Long` | `cancha_id` | `@Id`, generacion `IDENTITY` |
| `nombre` | `String` | `nombre` | `length = 80`, `nullable = false`, `unique = true` |
| `deporte` | `Deporte` (enum) | `deporte` | `@Enumerated(EnumType.STRING)`, `length = 8` |
| `horaApertura` | `LocalTime` | `hora_apertura` | `nullable = false` |
| `horaCierre` | `LocalTime` | `hora_cierre` | `nullable = false` |
| `activa` | `boolean` | `activa` | `nullable = false` |

Enum `Deporte`: `PADEL`, `TENIS`, `BASQUET`. Valores identicos al `CHECK` de la tabla y al
contrato.

### Mapeo de la entidad `BloqueoMantenimiento` (mismo paquete)

| Atributo | Tipo Java | Columna | Notas de mapeo |
|---|---|---|---|
| `bloqueoId` | `Long` | `bloqueo_id` | `@Id`, generacion `IDENTITY` |
| `canchaId` | `Long` | `cancha_id` | `@Column(nullable = false)` — columna simple, no asociacion (D-02) |
| `fecha` | `LocalDate` | `fecha` | `nullable = false` |
| `horaInicio` | `LocalTime` | `hora_inicio` | `nullable = false` |
| `horaFin` | `LocalTime` | `hora_fin` | `nullable = false` |
| `motivo` | `String` | `motivo` | `length = 200`, `nullable = false` |

Con `spring.jpa.hibernate.ddl-auto=validate`, cualquier desalineacion de nombre, tipo o
nulabilidad detiene el arranque (HU-11).

### Independencia de datos

- `ms-canchas` solo abre conexion a `canchas_db` con el usuario `canchas_user`.
- Las unicas tablas consultadas son `cancha` y `bloqueo_mantenimiento`, ambas propias.
- No hay consultas nativas, ni `JOIN`, ni referencias a `usuarios_db` ni a `reservas_db`.
- El servicio no consulta reservas para decidir nada (S-06): no impide inactivar una cancha
  con reservas futuras ni bloquear una franja ya reservada.
- La identidad y el rol de quien llama salen del token, no de `usuarios_db` (S-07).

### Repositorio `CanchaRepository`

| Operacion | Uso |
|---|---|
| `findAll()` | Listado del ADMIN (HU-01) |
| `findByActivaTrue()` | Listado del USUARIO, filtrado por rol (HU-01, P-05) |
| `findById(Long canchaId)` | Detalle, edicion, cambio de estado, y verificacion de existencia en las rutas de bloqueos |
| `existsByNombre(String nombre)` | Verificacion previa del alta (HU-03, P-01) |
| `existsByNombreAndCanchaIdNot(String nombre, Long canchaId)` | Verificacion previa de la edicion, para no chocar consigo misma (HU-04) |
| `save(Cancha cancha)` | Alta, edicion y cambio de estado |

### Repositorio `BloqueoRepository`

| Operacion | Uso |
|---|---|
| `findByCanchaId(Long canchaId)` | Listado sin filtro (HU-06) |
| `findByCanchaIdAndFecha(Long canchaId, LocalDate fecha)` | Listado con `?fecha` (HU-06, P-06) |
| `existsByCanchaIdAndFechaAndHoraInicioLessThanAndHoraFinGreaterThan(canchaId, fecha, horaFin, horaInicio)` | Deteccion de solapamiento, duplicado exacto incluido (HU-07, P-02.a y P-02.d) |
| `findByBloqueoIdAndCanchaId(Long bloqueoId, Long canchaId)` | Baja: exige que el bloqueo pertenezca a esa cancha (HU-08, S-05) |
| `save(BloqueoMantenimiento bloqueo)` | Alta |
| `delete(BloqueoMantenimiento bloqueo)` | Baja |

Sobre la consulta de solapamiento: la condicion `inicioA < finB && finA > inicioB` es la
definicion escrita en HU-07, y cubre tambien el duplicado exacto de `uq_bloqueo_franja`.
Dos franjas que solo se tocan en un extremo (`09:00`–`11:00` y `11:00`–`12:00`) no la
cumplen, que es lo pedido. La consulta la ejecuta el repositorio sobre su propia tabla,
pero **la regla la aplica el servicio**: el DDL no cambia (P-02.d).

## 3. DTOs y validaciones

Todos en `ec.ups.dae.canchas.dto`. Conversion con **mapper manual** (`CanchaMapper` y
`BloqueoMapper`, paquete `mapper`): sin Lombok, sin MapStruct. La entidad nunca sale del
paquete `service` hacia el controlador.

Horas y fechas viajan como `String` en los DTOs y se convierten en el mapper (decision
D-03), de modo que el JSON siempre respeta `HH:mm` y `AAAA-MM-DD` exactamente como los
congela el contrato.

### `CanchaRequest` — cuerpo de `POST /api/canchas` y de `PUT /api/canchas/{canchaId}`

| Campo | Tipo | Validaciones `jakarta.validation` |
|---|---|---|
| `nombre` | String | `@NotBlank`, `@Size(max = 80)` |
| `deporte` | String | `@NotBlank`, `@Pattern(regexp = "PADEL\|TENIS\|BASQUET")` |
| `horaApertura` | String | `@NotBlank`, `@Pattern` de `HH:mm` (`^([01]\d\|2[0-3]):[0-5]\d$`) |
| `horaCierre` | String | `@NotBlank`, `@Pattern` de `HH:mm` |

No declara `canchaId` ni `activa`: el identificador lo genera la base y el estado se maneja
solo por `PATCH .../estado` (S-02, S-03). Lo que el DTO no tiene, no se puede enviar.

Regla que `jakarta.validation` no puede expresar sobre dos campos y queda en el servicio:
`horaCierre > horaApertura` -> `400 DATOS_INVALIDOS` (HU-03).

### `CambioEstadoCanchaRequest` — cuerpo de `PATCH /api/canchas/{canchaId}/estado`

| Campo | Tipo | Validaciones |
|---|---|---|
| `activa` | `Boolean` (objeto, no primitivo) | `@NotNull` |

Se usa `Boolean` y no `boolean` por el mismo motivo que en `ms-usuarios` (D-05 de la spec
02): un cuerpo sin el campo debe dar `400`, no inactivar la cancha en silencio.

### `BloqueoRequest` — cuerpo de `POST /api/canchas/{canchaId}/bloqueos`

| Campo | Tipo | Validaciones |
|---|---|---|
| `fecha` | String | `@NotBlank`, `@Pattern` de `AAAA-MM-DD` (`^\d{4}-\d{2}-\d{2}$`) |
| `horaInicio` | String | `@NotBlank`, `@Pattern` de `HH:mm` |
| `horaFin` | String | `@NotBlank`, `@Pattern` de `HH:mm` |
| `motivo` | String | `@NotBlank`, `@Size(max = 200)` |

No declara `canchaId`: viene del path. No declara `bloqueoId`: lo genera la base.

Reglas que quedan en el servicio (HU-07): `horaFin > horaInicio`; la franja dentro de
`horaApertura`–`horaCierre` de la cancha (P-02.b); `fecha` no anterior a hoy (P-02.c);
ausencia de solapamiento (P-02.a y P-02.d). El `@Pattern` acepta `2026-02-31`, asi que el
mapper hace ademas un parseo estricto con `LocalDate.parse`; un fallo de parseo tambien es
`400 DATOS_INVALIDOS`.

### `CanchaResponse` — respuesta de listado, detalle, alta, edicion y cambio de estado

| Campo | Tipo | Origen |
|---|---|---|
| `canchaId` | Long | entidad |
| `nombre` | String | entidad |
| `deporte` | String (`PADEL` \| `TENIS` \| `BASQUET`) | entidad, nombre del enum |
| `horaApertura` | String `HH:mm` | entidad, formateado por el mapper |
| `horaCierre` | String `HH:mm` | entidad, formateado por el mapper |
| `activa` | boolean | entidad |

### `BloqueoResponse` — respuesta de listado y de alta de bloqueos

| Campo | Tipo | Origen |
|---|---|---|
| `bloqueoId` | Long | entidad |
| `canchaId` | Long | entidad |
| `fecha` | String `AAAA-MM-DD` | entidad, formateado por el mapper |
| `horaInicio` | String `HH:mm` | entidad, formateado por el mapper |
| `horaFin` | String `HH:mm` | entidad, formateado por el mapper |
| `motivo` | String | entidad |

### `ErrorResponse` — cuerpo de todo error

| Campo | Tipo |
|---|---|
| `codigo` | String |
| `mensaje` | String |

Misma forma que en `ms-usuarios`: `{ "codigo", "mensaje" }`.

## 4. Endpoints

Controladores en el paquete `controller`: `CanchaController` (raiz `/api/canchas`) y
`BloqueoController` (raiz `/api/canchas/{canchaId}/bloqueos`).

| Verbo | Ruta | Rol requerido | Cuerpo entrada | Respuesta 2xx | Errores |
|---|---|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | — | `200` + lista de `CanchaResponse` | 401 |
| GET | `/api/canchas/{canchaId}` | ADMIN, USUARIO | — | `200` + `CanchaResponse` | 401, 404 |
| POST | `/api/canchas` | ADMIN | `CanchaRequest` | `201` + `CanchaResponse` | 400, 401, 403, 409 |
| PUT | `/api/canchas/{canchaId}` | ADMIN | `CanchaRequest` | `200` + `CanchaResponse` | 400, 401, 403, 404, 409 |
| PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | `CambioEstadoCanchaRequest` | `200` + `CanchaResponse` | 400, 401, 403, 404 |
| GET | `/api/canchas/{canchaId}/bloqueos?fecha` | ADMIN, USUARIO | — (`fecha` opcional) | `200` + lista de `BloqueoResponse` | 400, 401, 404 |
| POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | `BloqueoRequest` | `201` + `BloqueoResponse` | 400, 401, 403, 404, 409 |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | — | `204` sin cuerpo | 401, 403, 404 |

Rutas, roles y codigos son exactamente los de la tabla "Rutas REST congeladas" del contrato,
ya actualizada el 23/08/2026. No se agrega ningun endpoint mas.

El unico parametro de consulta es `fecha` (opcional) en el listado de bloqueos. El filtrado
del catalogo por rol **no usa parametro**: sale del claim `rol` del token (P-05).

### Reparto de responsabilidades

| Capa | Clase | Responsabilidad |
|---|---|---|
| `controller` | `CanchaController` | Recibe, valida con `@Valid`, delega, devuelve el codigo HTTP. Sin logica de negocio |
| `controller` | `BloqueoController` | Idem para las rutas anidadas de bloqueos |
| `service` | `CanchaService` | Alta, edicion, cambio de estado, listado y detalle. Aplica P-01 y P-05 |
| `service` | `BloqueoService` | Alta, listado y baja de bloqueos. Aplica P-02.a a P-02.d y P-06 |
| `service` | `TokenService` | Valida el JWT HS256 y extrae `sub` y `rol` |
| `repository` | `CanchaRepository`, `BloqueoRepository` | Acceso a sus dos tablas propias |
| `mapper` | `CanchaMapper`, `BloqueoMapper` | Entidad <-> DTO, con formateo y parseo de fecha y hora |
| `config` | `SeguridadConfig` | Filtro de token, reglas de acceso por ruta |
| `config` | `OpenApiConfig` | Metadatos `springdoc-openapi` |
| `exception` | jerarquia + `ManejadorExcepciones` | Traduccion a `ErrorResponse` |

## 5. Autenticacion y autorizacion

### Token

`ms-canchas` **no emite** tokens: solo los valida. El mecanismo es el ya fijado en la spec
02 (D-01 y D-13 de aquel diseno) y se reutiliza sin cambios.

| Aspecto | Definicion |
|---|---|
| Formato | JWT firmado, algoritmo `HS256` |
| Secreto | variable de entorno `JWT_SECRET`, la misma que usa `ms-usuarios` |
| Claim `sub` | `usuarioId` |
| Claim `rol` | `ADMIN` o `USUARIO` |
| Claim `exp` | verificado en cada peticion |
| Transporte | encabezado `Authorization: Bearer <token>` |
| Validacion | **local**, sin llamada HTTP a `ms-usuarios` y sin leer `usuarios_db` |
| Libreria | `io.jsonwebtoken` (jjwt) `0.12.6`, los tres artefactos, igual que en `ms-usuarios` |

El filtro convierte el claim `rol` en una autoridad de Spring Security y deja el
`Authentication` en el `SecurityContext`. El servicio lee de ahi si quien llama es `ADMIN`,
que es lo que necesita el filtrado de HU-01 y HU-02 (D-05).

### Reglas de acceso por ruta

| Ruta | Regla |
|---|---|
| `GET /api/canchas` | token valido, cualquier rol |
| `GET /api/canchas/{canchaId}` | token valido, cualquier rol |
| `POST /api/canchas` | token valido + `rol = ADMIN` |
| `PUT /api/canchas/{canchaId}` | token valido + `rol = ADMIN` |
| `PATCH /api/canchas/{canchaId}/estado` | token valido + `rol = ADMIN` |
| `GET /api/canchas/{canchaId}/bloqueos` | token valido, cualquier rol |
| `POST /api/canchas/{canchaId}/bloqueos` | token valido + `rol = ADMIN` |
| `DELETE /api/canchas/{canchaId}/bloqueos/{id}` | token valido + `rol = ADMIN` |
| `/v3/api-docs/**` | publica |
| `/swagger-ui/**` | publica |
| `/swagger-ui.html` | publica |

Las tres rutas de documentacion se declaran `permitAll()` explicito en el
`SecurityFilterChain` y el filtro de token las deja pasar (D-12 de la spec 02, S-08 de este
requirements). Sesiones `STATELESS`, sin formulario de login, sin `Basic`, sin CSRF.

`ms-canchas` no reconoce mas identidades que `ADMIN` y `USUARIO`. El consumidor interno con
credenciales de servicio de C-01 es un **asunto abierto A-01** que define la spec 04: aqui
no se implementa nada de eso.

## 6. Flujos

### Listado del catalogo (HU-01)

1. El filtro valida el token; sin token o invalido -> `401 NO_AUTENTICADO`.
2. El servicio lee el rol del `SecurityContext`.
3. `ADMIN` -> `findAll()`. `USUARIO` -> `findByActivaTrue()` (P-05).
4. Mapeo a lista de `CanchaResponse`; lista vacia se responde `200` con `[]`.

### Detalle de una cancha (HU-02)

1. Token valido; en su defecto `401`.
2. `findById`; si no existe -> `CanchaNoEncontradaException` -> `404 NO_ENCONTRADO`.
3. Si el rol es `USUARIO` y `activa = false` -> la misma
   `CanchaNoEncontradaException` -> `404 NO_ENCONTRADO` (P-05). Se reutiliza la excepcion a
   proposito: la respuesta debe ser indistinguible de la de una cancha inexistente.
4. `200` con `CanchaResponse`.

### Alta de cancha (HU-03)

1. Filtro: token valido y `rol = ADMIN`; en su defecto `401` o `403`.
2. `@Valid` sobre `CanchaRequest`; si falla -> `400 DATOS_INVALIDOS`.
3. El mapper parsea `horaApertura` y `horaCierre`; fallo de parseo -> `400`.
4. `horaCierre > horaApertura`; si no -> `HorarioInvalidoException` -> `400`.
5. `existsByNombre`; si existe -> `NombreDuplicadoException` -> `409 NOMBRE_DUPLICADO`.
6. `save` con `activa = true`. Si `uq_cancha_nombre` salta por dos altas simultaneas, la
   violacion de integridad se traduce al mismo `409 NOMBRE_DUPLICADO` (P-01, doble barrera).
7. `201` con `CanchaResponse`.

### Edicion de cancha (HU-04)

1. Filtro: `ADMIN`; en su defecto `401` o `403`.
2. `@Valid` y parseo, igual que en el alta -> `400` si fallan.
3. `findById`; si no existe -> `404 NO_ENCONTRADO`.
4. `existsByNombreAndCanchaIdNot`; si existe -> `409 NOMBRE_DUPLICADO`. Reenviar el mismo
   nombre que ya tenia esa cancha no es duplicado y la edicion procede.
5. Se actualizan `nombre`, `deporte`, `horaApertura` y `horaCierre`. **`activa` no se
   toca** (S-03).
6. `save`, con la misma traduccion de `uq_cancha_nombre` a `409`.
7. `200` con `CanchaResponse`.

No se valida nada contra bloqueos ni reservas existentes al cambiar el horario (S-06).

### Cambio de estado de cancha (HU-05)

1. Filtro: `ADMIN`; en su defecto `401` o `403`.
2. `@Valid` sobre `CambioEstadoCanchaRequest`; `activa` ausente o no booleano -> `400`.
3. `findById`; si no existe -> `404 NO_ENCONTRADO`.
4. Se actualiza `activa`, se guarda. No se borra ninguna fila, ni la cancha ni sus
   bloqueos.
5. `200` con `CanchaResponse`.

### Listado de bloqueos (HU-06)

1. Token valido; en su defecto `401`.
2. `findById` de la cancha; si no existe -> `404 NO_ENCONTRADO`.
3. Si viene `?fecha`: parseo estricto; formato invalido -> `400 DATOS_INVALIDOS`; luego
   `findByCanchaIdAndFecha`. Sin `fecha`: `findByCanchaId` (P-06).
4. `200` con la lista de `BloqueoResponse`; sin resultados, `[]`.

El filtrado por rol de P-05 **no** aplica aqui: el contrato abre esta ruta a ambos roles
para que `ms-reservas` calcule disponibilidad, y el requirements no la restringe.

### Alta de bloqueo (HU-07)

1. Filtro: `ADMIN`; en su defecto `401` o `403`.
2. `@Valid` sobre `BloqueoRequest`; si falla -> `400 DATOS_INVALIDOS`.
3. Parseo estricto de `fecha`, `horaInicio` y `horaFin`; fallo -> `400`.
4. `findById` de la cancha; si no existe -> `404 NO_ENCONTRADO`. **El estado de la cancha
   no se comprueba: se admite crear bloqueos sobre una cancha con `activa = false`**
   (D-16). El horario de atencion se lee igual, este activa o no.
5. `horaFin > horaInicio`; si no -> `400` (`ck_bloqueo_rango` es la red de seguridad).
6. `fecha` no anterior a `LocalDate.now()`; si lo es -> `FechaPasadaException` -> `400`
   (P-02.c). Hoy si se admite.
7. Franja dentro del horario de atencion de la cancha leida en el paso 4:
   `horaInicio >= horaApertura` y `horaFin <= horaCierre`; si no ->
   `FueraDeHorarioException` -> `400` (P-02.b).
8. Solapamiento con otro bloqueo de esa cancha y fecha (duplicado exacto incluido) ->
   `BloqueoDuplicadoException` -> `409 BLOQUEO_DUPLICADO` (P-02.a, P-02.d).
9. `save`. Si `uq_bloqueo_franja` salta por dos altas simultaneas, la violacion se traduce
   al mismo `409 BLOQUEO_DUPLICADO`. El solapamiento parcial no tiene respaldo en la base:
   vive solo en el paso 8.
10. `201` con `BloqueoResponse`.

El orden importa: primero los `400` de forma y de regla, despues el `409` de conflicto, para
que un cuerpo invalido nunca se reporte como conflicto.

### Baja de bloqueo (HU-08)

1. Filtro: `ADMIN`; en su defecto `401` o `403`.
2. `findByBloqueoIdAndCanchaId`. Si la cancha no existe, el bloqueo no existe, o el bloqueo
   pertenece a otra cancha -> `BloqueoNoEncontradoException` -> `404 NO_ENCONTRADO` (S-05).
3. `delete` y respuesta `204` sin cuerpo. Repetir la baja da `404`, no `204`.

## 7. Excepciones a codigos HTTP

`ManejadorExcepciones` en `ec.ups.dae.canchas.exception`, anotado con
`@RestControllerAdvice`. Todo error sale como `ErrorResponse`; nunca un stacktrace (HU-10).

| Excepcion | HTTP | `codigo` | Origen |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `DATOS_INVALIDOS` | `@Valid` de cualquier DTO |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | JSON malformado, o `activa` no booleano |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | `canchaId` o `{id}` no numericos en el path |
| `HttpRequestMethodNotSupportedException` | 400 | `DATOS_INVALIDOS` | Verbo equivocado sobre una ruta existente (evita que el 405 caiga en el manejador generico) |
| `HttpMediaTypeNotSupportedException` | 400 | `DATOS_INVALIDOS` | `Content-Type` distinto de `application/json` (evita el 415) |
| `FormatoInvalidoException` | 400 | `DATOS_INVALIDOS` | Parseo estricto fallido de `fecha`, `horaInicio`, `horaFin`, `horaApertura` o `horaCierre` (incluye `?fecha` del listado) |
| `HorarioInvalidoException` | 400 | `DATOS_INVALIDOS` | `horaCierre <= horaApertura` en cancha, o `horaFin <= horaInicio` en bloqueo |
| `FueraDeHorarioException` | 400 | `DATOS_INVALIDOS` | Bloqueo fuera del horario de atencion de su cancha (P-02.b) |
| `FechaPasadaException` | 400 | `DATOS_INVALIDOS` | Bloqueo con `fecha` anterior a hoy (P-02.c) |
| Token ausente, expirado o con firma invalida | 401 | `NO_AUTENTICADO` | Punto de entrada de autenticacion del filtro |
| Rol insuficiente | 403 | `SIN_PERMISO` | Punto de acceso denegado (RN-07) |
| `CanchaNoEncontradaException` | 404 | `NO_ENCONTRADO` | `canchaId` inexistente, o inactiva vista por un `USUARIO` (P-05) |
| `BloqueoNoEncontradoException` | 404 | `NO_ENCONTRADO` | `{id}` inexistente o perteneciente a otra cancha (S-05) |
| `NombreDuplicadoException` | 409 | `NOMBRE_DUPLICADO` | Alta o edicion con un nombre ya usado (P-01) |
| `BloqueoDuplicadoException` | 409 | `BLOQUEO_DUPLICADO` | Franja repetida o solapada (P-02.a, P-02.d) |
| `DataIntegrityViolationException` sobre `uq_cancha_nombre` | 409 | `NOMBRE_DUPLICADO` | Carrera entre dos altas o ediciones simultaneas |
| `DataIntegrityViolationException` sobre `uq_bloqueo_franja` | 409 | `BLOQUEO_DUPLICADO` | Carrera entre dos altas de bloqueo simultaneas |
| `Exception` (resto) | 500 | `ERROR_INTERNO` | Red de seguridad |

Ningun codigo fuera de la tabla "Formato de error" del contrato. `405` y `415` no se
devuelven nunca: se traducen a `400 DATOS_INVALIDOS`, declarandolos explicitamente para que
no terminen como `500`.

Las dos `DataIntegrityViolationException` se distinguen por el nombre de la restriccion que
aparece en la causa (`uq_cancha_nombre` o `uq_bloqueo_franja`); si no se reconoce ninguna,
cae en `500 ERROR_INTERNO` (D-08).

Las respuestas `401` y `403` se producen dentro de la cadena de filtros, con los mismos
manejadores de autenticacion y acceso denegado, para conservar el formato
`{ "codigo", "mensaje" }`.

## 8. Configuracion

| Propiedad / variable | Valor | Origen |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/canchas_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | `canchas_user` | entorno |
| `spring.datasource.password` | `canchas_pass` | entorno |
| `spring.jpa.hibernate.ddl-auto` | `validate` | entorno |
| `server.port` | `8080` | interno del contenedor |
| `jwt.secret` | — | `JWT_SECRET` de `.env`, la misma de `ms-usuarios` |

Servicio `ms-canchas` en `docker-compose.yml`, `depends_on: postgres` con
`condition: service_healthy`, puerto `8083:8080` **temporal** para pruebas con `curl.exe`
hasta que exista el gateway Nginx (P-04).

Imagen: el `Dockerfile` copia el patron oficial de `CLAUDE.md` §1 sin cambios, ajustando
solo el nombre del `.jar` a `ms-canchas-0.0.1-SNAPSHOT.jar`. El esqueleto se descarga de
Spring Initializr por URL y se le corrige el `<parent>` a 3.5.3; nunca se ejecuta `mvn` en
el host (§2.2 del requirements).

### Dependencias

Las mismas de `ms-usuarios`, con las mismas versiones, para que ambos servicios validen el
mismo token de forma identica.

| Dependencia | Origen |
|---|---|
| `spring-boot-starter-web` | Spring Initializr |
| `spring-boot-starter-data-jpa` | Spring Initializr |
| `spring-boot-starter-validation` | Spring Initializr |
| `spring-boot-starter-security` | Spring Initializr |
| `postgresql` (driver, `runtime`) | Spring Initializr |
| `springdoc-openapi-starter-webmvc-ui` `2.8.6` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-api` `0.12.6` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-impl` `0.12.6` (`runtime`) | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-jackson` `0.12.6` (`runtime`) | agregada a mano al `pom.xml` |

No se incluye `spring-boot-starter-mail`, ni cliente HTTP hacia otros microservicios:
`ms-canchas` no llama a nadie.

## 9. Decisiones de diseno

| # | Decision | Alternativa descartada | Por que |
|---|---|---|---|
| D-01 | Dos controladores, dos servicios y dos repositorios, uno por agregado (cancha y bloqueo) | Un unico `CanchaController` con las ocho rutas | Las rutas de bloqueos son un recurso anidado con su propio ciclo de vida y sus propias reglas (P-02.b a P-02.d); mezclarlas produce una clase con ocho responsabilidades y un servicio inmanejable |
| D-02 | `BloqueoMantenimiento.canchaId` es una columna `Long` simple, no una asociacion `@ManyToOne` a `Cancha` | Mapear la FK como `@ManyToOne` | El servicio nunca necesita navegar del bloqueo a la cancha: cuando le hace falta el horario, ya cargo la cancha por el `canchaId` del path. Una columna simple evita cargas perezosas, sesiones abiertas en la serializacion y ciclos en el JSON. La FK sigue existiendo en la base y sigue protegiendo la integridad |
| D-03 | Fechas y horas viajan como `String` en los DTOs; el mapper formatea y parsea con `DateTimeFormatter` | `LocalTime` / `LocalDate` en el DTO con `@JsonFormat` | Con `LocalTime` y el serializador por defecto, Jackson emite `07:00:00` y rompe el `HH:mm` congelado; y un valor mal formado produciria `HttpMessageNotReadableException` antes de `@Valid`, con un mensaje del deserializador. Con `String` mas `@Pattern`, el error es un `400 DATOS_INVALIDOS` limpio y el formato de salida es exactamente el del contrato |
| D-04 | Ademas del `@Pattern`, el mapper hace parseo estricto (`LocalDate.parse`, `LocalTime.parse`) | Confiar solo en el `@Pattern` | El patron `^\d{4}-\d{2}-\d{2}$` acepta `2026-02-31`, que no es una fecha. El parseo es la unica verificacion real de validez |
| D-05 | El filtrado por rol de HU-01 y HU-02 se resuelve leyendo el rol del `SecurityContext` en el servicio, sin parametro de consulta | Un parametro `?activa=true` en el contrato, o filtrar en el frontend | El parametro dejaria al `USUARIO` ver inactivas con solo cambiarlo, y filtrar en el frontend expone datos que el rol no deberia recibir. El rol ya viaja en el token: es la fuente mas simple y la unica no manipulable por el cliente (decision P-05 del responsable) |
| D-06 | Una cancha inactiva vista por un `USUARIO` responde `404 NO_ENCONTRADO`, reutilizando la misma excepcion que una cancha inexistente | `403 SIN_PERMISO`, o `200` con la cancha | `403` revelaria que la cancha existe, contradiciendo el listado que ya la oculta; `200` contradiria directamente HU-01. La respuesta debe ser indistinguible |
| D-07 | El solapamiento parcial se valida en el servicio con una consulta derivada sobre la propia tabla | Un indice o `EXCLUDE` con `btree_gist` en el DDL | `CLAUDE.md` §3 y la spec 01 congelan el esquema: la entidad se adapta al DDL, no al reves. Ademas `EXCLUDE` exigiria una extension de PostgreSQL y un cambio del script ya aplicado (instruccion explicita del responsable en P-02.d) |
| D-08 | Doble barrera en los dos conflictos: verificacion previa en el servicio **mas** traduccion de `uq_cancha_nombre` y `uq_bloqueo_franja` a su `409`, distinguidas por el nombre de la restriccion | Solo la verificacion previa | Dos peticiones simultaneas pasan la verificacion y la segunda reventaria como `500`. Es el mismo patron ya probado en `ms-usuarios` (D-08 de la spec 02), pedido explicitamente en P-01 |
| D-09 | El duplicado exacto y el solapamiento parcial comparten el codigo `BLOQUEO_DUPLICADO` | Un codigo adicional, p. ej. `BLOQUEO_SOLAPADO` | Para el cliente son el mismo problema —la franja ya esta bloqueada— y la accion correctiva es identica. Un codigo mas obligaria a tocar el contrato sin aportar nada |
| D-10 | En el alta de bloqueo, todas las validaciones `400` se evaluan antes del `409` | Consultar el solapamiento primero, por ser una unica consulta | Un cuerpo con `horaFin < horaInicio` reportado como `409` seria enganoso: el conflicto solo tiene sentido sobre una franja bien formada |
| D-11 | El `PUT` reemplaza los cuatro campos editables y **no** toca `activa` | Un `PUT` que acepte tambien `activa` | Existe un `PATCH .../estado` dedicado (S-03). Aceptar `activa` en los dos sitios crearia dos caminos para el mismo cambio y un `PUT` sin ese campo inactivaria canchas por omision |
| D-12 | El listado de bloqueos no filtra por rol y expone los bloqueos a `ADMIN` y `USUARIO` | Aplicarle el mismo filtrado por rol del catalogo | El contrato abre esa ruta a ambos roles justamente para que `ms-reservas` calcule disponibilidad, y un bloqueo de mantenimiento no es informacion sensible |
| D-13 | Las entidades se adaptan al DDL y el enum `Deporte` se persiste como `STRING` | Ajustar el DDL a las entidades, o `ORDINAL` | `CLAUDE.md` §3 manda: el esquema lo dicta el DDL versionado. `ORDINAL` guardaria numeros y violaria `ck_cancha_deporte` |
| D-14 | Mapper manual con metodos explicitos, uno por agregado | MapStruct o reflexion generica | Prohibido por `CLAUDE.md` §3; ademas el formateo de `HH:mm` y `AAAA-MM-DD` de D-03 necesita conversion explicita |
| D-15 | Se reutilizan sin cambios el filtro JWT, el `ManejadorExcepciones` y el patron de `Dockerfile` de la spec 02 | Rediseñarlos para este servicio | Estan fijados en `CLAUDE.md` §1 y §3 y en la spec 02 aprobada; redecidirlos arriesga que dos servicios validen el mismo token de forma distinta |
| D-16 | Se **permite** registrar un bloqueo sobre una cancha con `activa = false`: el alta no comprueba el estado de la cancha, solo su existencia | Rechazar el alta con `400 DATOS_INVALIDOS` o `404` cuando la cancha esta inactiva | Una cancha inactiva puede estar precisamente en mantenimiento, que es el caso de uso del bloqueo: prohibirlo dejaria al ADMIN sin poder registrar el motivo justo cuando mas lo necesita. Ademas ninguna RN lo prohibe, y el bloqueo es informacion, no una reserva: no habilita nada que la cancha inactiva no permitiera ya |

## 10. Puntos pendientes

Ninguno que bloquee la implementacion.

Queda registrado el **asunto abierto A-01** (requirements §6.1): el mecanismo de
credenciales de servicio para las llamadas internas de `ms-reservas`, que define la spec 04.
Cuando exista, `ms-canchas` recibira ese cambio como una modificacion posterior, acordada
primero en `docs/contratos/README.md`. Nada de este diseño lo anticipa.

Todo el diseño se apoya en campos, rutas y codigos congelados en el contrato, verificados
uno a uno en §1.
