# Spec 04 — ms-reservas · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 04").
**Spec 04 CERRADA** el 23/08/2026: las diez tareas de `tasks.md` fueron ejecutadas y
verificadas con salida real (ver `docs/bitacora.md`). Se implemento tal como esta descrito
aqui, mas las decisiones D-19 y D-20, agregadas durante la ejecucion y aprobadas por el
responsable.

Base: `.claude/specs/04-ms-reservas/requirements.md` (C1 aprobado el 23/08/2026, con las
once decisiones D-01 a D-11 y las consecuencias C-02 y C-03), `docs/contratos/README.md`
(con los dos cambios del 23/08/2026: el valor `SERVICIO` del campo `rol` y el codigo
`RESERVA_NO_CANCELABLE`), `infra/postgres/04-ddl-reservas.sql` y `05-seed.sql`, y los
disenos aprobados de las specs 02 y 03 para reutilizar lo ya fijado.

Lo que **no** se vuelve a decidir aqui, por estar fijado en `CLAUDE.md` §1 y §3 y en las
specs 02 y 03: Spring Boot 3.5.3, springdoc 2.8.6, jjwt 0.12.6, el patron de `Dockerfile`
con cache mount de BuildKit y el patron de filtro JWT HS256 validado localmente.

## 1. Verificacion campo por campo contra el contrato

Comparacion de los nombres JSON congelados con las columnas del DDL ya aplicado. **No se
renombra nada**: la columna es interna y el campo JSON es el del contrato.

### Reserva

| Campo JSON (contrato) | Tipo contrato | Columna en `reserva` | Tipo columna | Coincide |
|---|---|---|---|---|
| `id` | number | `id` | `BIGINT` identity | Si |
| `usuarioId` | number | `usuario_id` | `BIGINT` | Si |
| `canchaId` | number | `cancha_id` | `BIGINT` | Si |
| `fecha` | string `AAAA-MM-DD` | `fecha` | `DATE` | Si — la columna guarda la fecha, el DTO la serializa como `AAAA-MM-DD` |
| `horaInicio` | string `HH:mm` | `hora_inicio` | `TIME` | Si |
| `horaFin` | string `HH:mm` | `hora_fin` | `TIME` | Si |
| `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | `estado` | `VARCHAR(12)` con `ck_reserva_estado` | Si — `FINALIZADA` (10 caracteres) cabe, aunque nunca se escribe (D-02) |

### `DisponibilidadResponse`

| Campo JSON (contrato) | Tipo contrato | Origen | Coincide |
|---|---|---|---|
| `canchaId` | number | parametro de la peticion, eco en la respuesta | Si |
| `fecha` | string `AAAA-MM-DD` | parametro de la peticion, eco en la respuesta | Si |
| `horaApertura` | string `HH:mm` | `GET /api/canchas/{canchaId}` de `ms-canchas` | Si |
| `horaCierre` | string `HH:mm` | `GET /api/canchas/{canchaId}` de `ms-canchas` | Si |
| `bloques` | arreglo de objetos | calculado | Si |
| `bloques[].horaInicio` | string `HH:mm` | calculado | Si |
| `bloques[].horaFin` | string `HH:mm` | calculado | Si |
| `bloques[].disponible` | boolean | calculado | Si |

### Campos que se consumen de `ms-canchas`

| Campo JSON | Ruta de origen | Uso en `ms-reservas` |
|---|---|---|
| `canchaId`, `horaApertura`, `horaCierre`, `activa` | `GET /api/canchas/{canchaId}` | horario de atencion, existencia y estado de la cancha |
| `fecha`, `horaInicio`, `horaFin` de cada bloqueo | `GET /api/canchas/{canchaId}/bloqueos?fecha` | marcar bloques ocupados por mantenimiento |
| `bloqueoId`, `motivo` | misma ruta | **no se usan**: se deserializan y se descartan |

### Campo `rol`

| Campo JSON (contrato) | Valores | Uso |
|---|---|---|
| `rol` | `ADMIN` \| `USUARIO` \| `SERVICIO` | claim del token. `ms-reservas` acepta `ADMIN` y `USUARIO` en la entrada, y **emite** `SERVICIO` en el token saliente hacia `ms-canchas` (D-01, S-12) |

**Sin discrepancias.** Ningun campo del contrato falta y ningun campo nuevo se agrega.
Nota ya registrada en el requirements §5: la ruta congelada de la cancelacion usa `{id}`,
igual que el campo del cuerpo; no es `reservaId`. Se implementa literal.

## 2. Modelo de datos

Fuente unica: `infra/postgres/04-ddl-reservas.sql`. **Esta spec no modifica el DDL.** La
entidad se adapta a la tabla, nunca al contrario.

### Tabla `reserva` (base `reservas_db`, propietario `reservas_user`)

| Columna | Tipo | Nulo | Restriccion |
|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | No | `pk_reserva` PRIMARY KEY |
| `usuario_id` | `BIGINT` | No | Sin FK: el usuario vive en `usuarios_db` y la integracion es por REST |
| `cancha_id` | `BIGINT` | No | Sin FK: la cancha vive en `canchas_db` y la integracion es por REST |
| `fecha` | `DATE` | No | — |
| `hora_inicio` | `TIME` | No | — |
| `hora_fin` | `TIME` | No | `ck_reserva_bloque_una_hora`: `hora_fin = hora_inicio + INTERVAL '1 hour'` (RN-01) |
| `estado` | `VARCHAR(12)` | No | `ck_reserva_estado`: `IN ('CONFIRMADA', 'CANCELADA', 'FINALIZADA')` (RN-08) |

Indices y restricciones adicionales ya existentes:

| Objeto | Definicion | Para que |
|---|---|---|
| `ux_reserva_bloque_confirmada` | UNIQUE sobre `(cancha_id, fecha, hora_inicio)` **WHERE `estado = 'CONFIRMADA'`** | RN-02 y RN-05: un bloque ocupado no se vuelve a reservar, pero una reserva `CANCELADA` lo libera porque el indice es parcial |
| `ix_reserva_usuario_estado` | INDEX sobre `(usuario_id, estado)` | Historial propio (HU-03) y conteo de activas (RN-06) |
| `ix_reserva_cancha_fecha` | INDEX sobre `(cancha_id, fecha)` | Consulta de disponibilidad (HU-01) |

### Mapeo de la entidad `Reserva` (paquete `ec.ups.dae.reservas.entity`)

| Atributo Java | Tipo Java | Columna | Notas |
|---|---|---|---|
| `id` | `Long` | `id` | `@Id` con `GenerationType.IDENTITY` |
| `usuarioId` | `Long` | `usuario_id` | Columna simple, no asociacion |
| `canchaId` | `Long` | `cancha_id` | Columna simple, no asociacion |
| `fecha` | `LocalDate` | `fecha` | — |
| `horaInicio` | `LocalTime` | `hora_inicio` | — |
| `horaFin` | `LocalTime` | `hora_fin` | Lo calcula el servicio como `horaInicio + 1h` |
| `estado` | `EstadoReserva` | `estado` | `@Enumerated(EnumType.STRING)`, longitud 12 |

`EstadoReserva` es un enum en `entity` con los tres valores de `ck_reserva_estado`:
`CONFIRMADA`, `CANCELADA`, `FINALIZADA`. El servicio solo **escribe** los dos primeros
(D-02); `FINALIZADA` figura para que el enum cubra el dominio completo de la columna y para
que una fila cargada con ese valor no reviente el mapeo (D-04).

Sin Lombok, sin `@Data`: constructor vacio protegido para JPA, constructor con argumentos
y getters/setters explicitos, igual que en las specs 02 y 03.

### Independencia de datos

`ms-reservas` accede **unicamente** a la tabla `reserva` de `reservas_db`, con el usuario
`reservas_user`. No existe ninguna consulta, vista, `JOIN`, FK ni datasource secundario
hacia `usuarios_db` o `canchas_db`; el propio `init.sql` revoca los permisos cruzados. Todo
dato de otra base entra por HTTP (§5).

### Repositorio `ReservaRepository`

Interfaz `JpaRepository<Reserva, Long>` en `ec.ups.dae.reservas.repository`. Todas las
consultas son sobre `reserva`.

| Metodo | Uso | Indice que aprovecha |
|---|---|---|
| `findById(Long id)` | Cancelacion (HU-05) | `pk_reserva` |
| `findByCanchaIdAndFechaAndEstado(Long canchaId, LocalDate fecha, EstadoReserva estado)` | Disponibilidad: reservas `CONFIRMADA` del dia (HU-01) | `ix_reserva_cancha_fecha` |
| `existsByCanchaIdAndFechaAndHoraInicioAndEstado(...)` | Primera barrera de RN-02 en el alta (HU-02) | `ux_reserva_bloque_confirmada` |
| `contarActivas(Long usuarioId, LocalDate hoy, LocalTime ahora)` — `@Query` JPQL | RN-06: cuenta `CONFIRMADA` con `fecha > :hoy` o (`fecha = :hoy` y `horaInicio > :ahora`) (D-04) | `ix_reserva_usuario_estado` |
| `findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId)` | Historial propio (HU-03, D-09) | `ix_reserva_usuario_estado` |
| `findAllByOrderByFechaDescHoraInicioDesc()` | Listado global (HU-04, D-09) | — |

`contarActivas` es el unico `@Query`: la condicion de RN-06 mezcla dos comparaciones sobre
columnas distintas y no se expresa como consulta derivada legible (D-05).

## 3. DTOs y validaciones

Paquete `ec.ups.dae.reservas.dto`. Sin Lombok. Validacion con `jakarta.validation`.

Fechas y horas viajan como `String` con `@Pattern`, y el mapper hace el parseo estricto:
mismo criterio que D-03 y D-04 de la spec 03, para que la salida sea exactamente `HH:mm` y
`AAAA-MM-DD` y para que un valor mal formado produzca un `400 DATOS_INVALIDOS` limpio.

### `ReservaRequest` — cuerpo de `POST /api/reservas`

| Campo | Tipo | Validaciones | Notas |
|---|---|---|---|
| `canchaId` | `Long` | `@NotNull`, `@Positive` | — |
| `fecha` | `String` | `@NotBlank`, `@Pattern` `^[0-9]{4}-[0-9]{2}-[0-9]{2}$` | Parseo estricto en el mapper |
| `horaInicio` | `String` | `@NotBlank`, `@Pattern` `^[0-9]{2}:[0-9]{2}$` | Parseo estricto; la hora en punto se valida en el servicio |

Es exactamente `{ canchaId, fecha, horaInicio }` (D-11). **No** hay `horaFin`, `usuarioId`,
`estado` ni `id`: si llegan, Jackson los ignora (`FAIL_ON_UNKNOWN_PROPERTIES = false`,
como en las specs 02 y 03) y el servicio los reemplaza por los suyos (HU-02).

La cancelacion no tiene DTO de entrada: `PATCH /api/reservas/{id}/cancelacion` no lee
cuerpo (HU-05).

### `ReservaResponse` — respuesta del alta, del historial, del listado global y de la cancelacion

| Campo | Tipo | Origen |
|---|---|---|
| `id` | `Long` | `reserva.id` |
| `usuarioId` | `Long` | `reserva.usuario_id` |
| `canchaId` | `Long` | `reserva.cancha_id` |
| `fecha` | `String` `AAAA-MM-DD` | `reserva.fecha` formateada |
| `horaInicio` | `String` `HH:mm` | `reserva.hora_inicio` formateada |
| `horaFin` | `String` `HH:mm` | `reserva.hora_fin` formateada |
| `estado` | `String` | **Calculado** por el mapper (D-02): ver §6.6 |

### `DisponibilidadResponse` — respuesta de `GET /api/reservas/disponibilidad`

| Campo | Tipo | Origen |
|---|---|---|
| `canchaId` | `Long` | eco del parametro |
| `fecha` | `String` `AAAA-MM-DD` | eco del parametro |
| `horaApertura` | `String` `HH:mm` | `ms-canchas` |
| `horaCierre` | `String` `HH:mm` | `ms-canchas` |
| `bloques` | `List<BloqueResponse>` | calculado |

### `BloqueResponse` — cada elemento de `bloques`

| Campo | Tipo | Origen |
|---|---|---|
| `horaInicio` | `String` `HH:mm` | calculado |
| `horaFin` | `String` `HH:mm` | `horaInicio + 1h` |
| `disponible` | `boolean` | calculado (§6.1) |

### `CanchaExterna` y `BloqueoExterno` — respuestas de `ms-canchas`

DTOs de **entrada** del cliente HTTP, no se serializan nunca hacia el cliente final.
Reproducen solo los campos que `ms-reservas` necesita, con los nombres exactos del
contrato.

| DTO | Campos leidos | Campos ignorados |
|---|---|---|
| `CanchaExterna` | `canchaId`, `horaApertura`, `horaCierre`, `activa` | `nombre`, `deporte` |
| `BloqueoExterno` | `fecha`, `horaInicio`, `horaFin` | `bloqueoId`, `canchaId`, `motivo` |

### `ErrorResponse` — cuerpo de todo error

| Campo | Tipo | Notas |
|---|---|---|
| `codigo` | `String` | Uno de la tabla "Formato de error" del contrato |
| `mensaje` | `String` | Texto en español, sin datos internos |

Misma clase y mismo contrato que en `ms-usuarios` y `ms-canchas`.

## 4. Endpoints

| Verbo | Ruta | Rol requerido | Exito | Errores | HU |
|---|---|---|---|---|---|
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | token valido, `ADMIN` o `USUARIO` | 200 `DisponibilidadResponse` | 400, 401, 404, 500 | HU-01 |
| POST | `/api/reservas` | token valido, `ADMIN` o `USUARIO` (D-08) | 201 `ReservaResponse` | 400, 401, 404, 409, 500 | HU-02 |
| GET | `/api/reservas` | token valido + `rol = ADMIN` | 200 `List<ReservaResponse>` | 401, 403 | HU-04 |
| GET | `/api/reservas/mias` | token valido, `ADMIN` o `USUARIO` (D-08) | 200 `List<ReservaResponse>` | 401 | HU-03 |
| PATCH | `/api/reservas/{id}/cancelacion` | token valido, `ADMIN` o `USUARIO` | 200 `ReservaResponse` | 401, 403, 404, 409 | HU-05 |

El `500 ERROR_INTERNO` de las dos primeras filas es el fallo de dependencia de D-06; no
figura en la tabla del contrato porque `ERROR_INTERNO` es la red de seguridad comun a los
cuatro microservicios, declarada aparte en "Formato de error".

Las cinco rutas quedan **literalmente** como estan congeladas. La columna "Rol" de
`POST /api/reservas` y `GET /api/reservas/mias` dice `USUARIO` en el contrato porque
describe al consumidor tipico; la decision D-08 confirmo que el `ADMIN` tambien las usa y
que **no** se agrega `403` (requirements §6.2).

### Reparto de responsabilidades

| Clase | Paquete | Responsabilidad |
|---|---|---|
| `ReservaController` | `controller` | Las cinco rutas, `@Valid`, codigos HTTP y documentacion OpenAPI |
| `ReservaService` | `service` | Reglas RN-01 a RN-06 y RN-08, orquestacion del alta y la cancelacion |
| `DisponibilidadService` | `service` | Armado de `DisponibilidadResponse`: bloques, reservas y bloqueos (HU-01) |
| `CanchasClient` | `service` | Unico punto que llama a `ms-canchas` por HTTP, con timeouts y traduccion de fallos (D-06) |
| `TokenService` | `service` | Validacion **local** del JWT entrante, igual que en `ms-canchas` |
| `EmisorTokenServicio` | `service` | Emision del token `rol = SERVICIO` para las llamadas salientes (D-01) |
| `ReservaRepository` | `repository` | Acceso a la tabla `reserva` y solo a ella |
| `ReservaMapper` | `mapper` | Entidad a DTO, formateo `HH:mm` / `AAAA-MM-DD` y calculo de `estado` (D-02) |
| `FiltroToken`, `SeguridadConfig`, `OpenApiConfig`, `ClienteHttpConfig` | `config` | Cadena de filtros, rutas publicas, documentacion y el `RestClient` con sus timeouts |
| `ManejadorExcepciones` y las excepciones de negocio | `exception` | Traduccion a `ErrorResponse` (§7) |

`CanchasClient` y `EmisorTokenServicio` viven en `service`, no en un paquete `client`
nuevo: `CLAUDE.md` §4 congela la lista de paquetes y esta spec no la amplia (D-06).

## 5. Autenticacion, autorizacion e integracion

### 5.1 Token entrante

`ms-reservas` **no emite** tokens de sesion: solo los valida. Mecanismo identico al de las
specs 02 y 03, reutilizado sin cambios.

| Aspecto | Definicion |
|---|---|
| Formato | JWT firmado, algoritmo `HS256` |
| Secreto | variable de entorno `JWT_SECRET`, la misma de `ms-usuarios` y `ms-canchas` |
| Claim `sub` | `usuarioId` |
| Claim `rol` | `ADMIN` o `USUARIO` |
| Claim `exp` | verificado en cada peticion |
| Transporte | encabezado `Authorization: Bearer <token>` |
| Validacion | **local**, sin llamada HTTP a `ms-usuarios` y sin leer `usuarios_db` |
| Libreria | `io.jsonwebtoken` (jjwt) `0.12.6`, los tres artefactos |

Un token entrante con `rol = SERVICIO` se rechaza con `401 NO_AUTENTICADO` (S-12): ningun
endpoint de este servicio se consume asi, y una operacion sin `sub` no tiene dueno con el
que comprobar RN-03.

### 5.2 Token de servicio saliente (D-01, resuelve A-01)

| Aspecto | Definicion |
|---|---|
| Formato | JWT `HS256` firmado con el **mismo** `JWT_SECRET` |
| Claim `rol` | `SERVICIO` |
| Claim `sub` | **ausente** |
| Claim `exp` | 5 minutos desde la emision |
| Emision | uno nuevo en **cada** llamada saliente; no se cachea ni se persiste |
| Transporte | `Authorization: Bearer <token>` hacia `ms-canchas` |
| Alcance | solo lectura; en escritura `ms-canchas` responde `403 SIN_PERMISO` |

El token del usuario final **nunca** se reenvia a `ms-canchas` (decision C-01 de la
spec 03).

### 5.3 Reglas de acceso por ruta

| Ruta | Regla |
|---|---|
| `GET /api/reservas/disponibilidad` | token valido, cualquier rol |
| `POST /api/reservas` | token valido, cualquier rol (D-08) |
| `GET /api/reservas` | token valido + `rol = ADMIN` |
| `GET /api/reservas/mias` | token valido, cualquier rol (D-08) |
| `PATCH /api/reservas/{id}/cancelacion` | token valido, cualquier rol; la propiedad se comprueba en el servicio (RN-03) |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | publicas |

Sesiones `STATELESS`, sin formulario de login, sin `Basic`, sin CSRF. `GET /api/reservas`
es la unica ruta con restriccion de rol en la cadena de filtros; el `403` de la cancelacion
es una regla de negocio y se decide en el servicio (D-07).

### 5.4 Cliente HTTP hacia `ms-canchas`

| Aspecto | Definicion |
|---|---|
| Implementacion | `RestClient` de Spring Framework 6 — ya viene con `spring-boot-starter-web`, sin dependencia nueva |
| URL base | `MS_CANCHAS_URL` = `http://ms-canchas:8080` |
| Timeout de conexion | 2 s (D-06) |
| Timeout de lectura | 5 s (D-06) |
| Reintentos | **ninguno** (D-06) |
| Llamada 1 | `GET /api/canchas/{canchaId}` -> `CanchaExterna` |
| Llamada 2 | `GET /api/canchas/{canchaId}/bloqueos?fecha=AAAA-MM-DD` -> `List<BloqueoExterno>` |
| `404` recibido | se traduce a `CanchaNoEncontradaException` (404 del propio servicio), **no** es fallo de dependencia |
| `5xx`, timeout, error de conexion | `CatalogoNoDisponibleException` -> `500 ERROR_INTERNO` con mensaje fijo |
| `401` / `403` recibidos | tambien `CatalogoNoDisponibleException`: significa que el token de servicio esta mal configurado, no que el cliente final se equivoco (D-08) |

### 5.5 Cambio en `ms-canchas` (requirements §8)

Alcance exacto, dentro de esta spec y de ninguna otra:

| Archivo | Cambio |
|---|---|
| `config/FiltroToken.java` | Aceptar `rol = SERVICIO` como autoridad valida y no exigir el claim `sub` cuando el rol es `SERVICIO` |
| `config/SeguridadConfig.java` | Las tres rutas `GET` admiten `ADMIN`, `USUARIO` o `SERVICIO`; las cinco de escritura siguen exigiendo `ADMIN`, asi que un `SERVICIO` recibe `403 SIN_PERMISO` |
| `service/CanchaService.java` | El filtrado por rol de P-05 trata a `SERVICIO` como al `ADMIN`: ve todas las canchas y una inactiva responde `200`, no `404` |

No se toca ningun endpoint, entidad, DTO, mapper, repositorio ni regla de negocio de
`ms-canchas`. El comportamiento para `ADMIN` y `USUARIO` queda identico.

## 6. Flujos

### 6.1 Disponibilidad (HU-01)

1. El filtro valida el token; sin token valido, `401`.
2. Se validan `canchaId` y `fecha`: ausentes o mal formados, `400 DATOS_INVALIDOS`
   (parseo estricto de la fecha, D-04 de la spec 03).
3. `CanchasClient` pide la cancha con token de servicio. `404` de `ms-canchas` -> `404
   NO_ENCONTRADO`; fallo de dependencia -> `500 ERROR_INTERNO`.
4. Se generan los bloques de una hora desde `horaApertura` hasta `horaCierre`, descartando
   un resto final menor a una hora (S-04).
5. **Si la cancha tiene `activa = false`**, todos los bloques salen con
   `disponible = false` y **no se llama** al endpoint de bloqueos: la respuesta ya esta
   determinada (D-05, decision D-09 de este diseno).
6. Si esta activa, se piden los bloqueos de esa cancha y fecha con el parametro `?fecha`.
7. Se consultan las reservas `CONFIRMADA` de esa cancha y fecha en `reserva`.
8. Cada bloque queda `disponible = false` si coincide con una reserva `CONFIRMADA` por
   `horaInicio`, o si se solapa con algun bloqueo: `bloque.horaInicio < bloqueo.horaFin` y
   `bloque.horaFin > bloqueo.horaInicio`. Tocarse en un extremo no ocupa.
9. Se responde `200` con el payload congelado.

Una fecha pasada se procesa con normalidad (D-03): la consulta es informativa.

### 6.2 Alta de reserva (HU-02)

Orden de validaciones — todas las de `400` antes de cualquier `409`, mismo criterio que
D-10 de la spec 03:

1. Token valido; si no, `401`.
2. `@Valid` sobre `ReservaRequest` y parseo estricto de `fecha` y `horaInicio`; fallo,
   `400 DATOS_INVALIDOS`.
3. `horaInicio` en hora en punto (minutos `00`); si no, `400`.
4. La fecha y hora del bloque no pueden haber ocurrido (D-03); si ya pasaron, `400`.
5. Se pide la cancha a `ms-canchas` con token de servicio. No existe, `404
   NO_ENCONTRADO`; `activa = false`, tambien `404 NO_ENCONTRADO` (D-05); fallo de
   dependencia, `500 ERROR_INTERNO` y **no se crea nada**.
6. El bloque cabe completo en el horario de atencion: `horaInicio >= horaApertura` y
   `horaInicio + 1h <= horaCierre`; si no, `400`.
7. RN-02, primera barrera — **consulta local**:
   `existsByCanchaIdAndFechaAndHoraInicioAndEstado(..., CONFIRMADA)`; si existe,
   `409 BLOQUE_OCUPADO`.
8. RN-06 — **consulta local**: se cuentan las activas del `usuarioId` del token con
   `contarActivas`; si son `RESERVAS_MAX_ACTIVAS` o mas, `409 LIMITE_RESERVAS`.
9. Mantenimiento — **llamada HTTP**: se piden los bloqueos de esa cancha y fecha; si el
   bloque se solapa con alguno, `409 BLOQUE_OCUPADO` (D-07).
10. Se persiste con `usuarioId` del claim `sub`, `horaFin = horaInicio + 1h` y
    `estado = CONFIRMADA`.
11. RN-02, segunda barrera: una `DataIntegrityViolationException` sobre
    `ux_reserva_bloque_confirmada` se traduce al mismo `409 BLOQUE_OCUPADO` (D-03 de este
    diseno).
12. Se responde `201` con `ReservaResponse`.

Los pasos 7 y 9 comparten codigo de error a proposito (D-07 del requirements).

Los tres `409` van en ese orden por **D-19**: las dos comprobaciones locales se resuelven
antes que la que exige red, porque el codigo de respuesta es el mismo y el caso mas
frecuente de rechazo —el bloque ya reservado— se contesta sin gastar una llamada a
`ms-canchas`.

El paso 5 **no** se mueve: devuelve `404`, no `409`, y ademas aporta el horario de atencion
que necesita el paso 6.

### 6.3 Historial propio (HU-03)

Token valido -> `findByUsuarioIdOrderByFechaDescHoraInicioDesc(sub)` -> mapeo con el
`estado` calculado -> `200`. Sin reservas, arreglo vacio. Un `ADMIN` recibe **sus** propias
reservas (D-08).

### 6.4 Listado global (HU-04)

Token valido + `rol = ADMIN` -> `findAllByOrderByFechaDescHoraInicioDesc()` -> mapeo con el
`estado` calculado -> `200`. Un `USUARIO` recibe `403 SIN_PERMISO` desde la cadena de
filtros. Sin filtros ni paginacion.

### 6.5 Cancelacion (HU-05)

1. Token valido; si no, `401`.
2. `findById(id)`; no existe, `404 NO_ENCONTRADO`.
3. RN-03: si el rol es `USUARIO` y `reserva.usuarioId != sub`, `403 SIN_PERMISO`. El
   `ADMIN` pasa siempre.
4. Precedencia de C-02: si el estado persistido es `CONFIRMADA` y la fecha y hora de inicio
   ya ocurrieron, `409 RESERVA_PASADA` (RN-04). Esto cubre tambien la reserva que el cliente
   vio como `FINALIZADA`.
5. Si el estado persistido **no** es `CONFIRMADA` — en la practica, `CANCELADA` —,
   `409 RESERVA_NO_CANCELABLE` (D-10).
6. Se escribe `estado = CANCELADA` y se responde `200`. La fila no se borra (RN-08) y el
   bloque queda libre por el indice parcial (RN-05).

El orden de los pasos 3, 4 y 5 es deliberado: primero quien pregunta, despues si la reserva
aun podia cancelarse (D-10 de este diseno).

### 6.6 Calculo de `estado` al leer (HU-06, D-02)

Lo aplica `ReservaMapper` en toda salida, y **nunca** escribe en la base:

| Estado persistido | Condicion | `estado` devuelto |
|---|---|---|
| `CANCELADA` | — | `CANCELADA` |
| `CONFIRMADA` | `fecha` + `horaFin` ya pasaron | `FINALIZADA` |
| `CONFIRMADA` | aun no pasan | `CONFIRMADA` |
| `FINALIZADA` | caso que el servicio nunca escribe | `FINALIZADA` |

Consecuencia registrada: en `reservas_db` solo existen `CONFIRMADA` y `CANCELADA`. Una
reserva vista como `FINALIZADA` sigue ocupando su bloque en el indice parcial, lo cual es
correcto porque ese bloque ya ocurrio y D-03 impide reservar el pasado.

## 7. Excepciones a codigos HTTP

`ManejadorExcepciones` en `ec.ups.dae.reservas.exception`, anotado con
`@RestControllerAdvice`. Todo error sale como `ErrorResponse`; nunca un stacktrace (HU-09).

| Excepcion | HTTP | `codigo` | Origen |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `DATOS_INVALIDOS` | `@Valid` sobre `ReservaRequest` |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | JSON malformado o cuerpo ausente donde se espera |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | `{id}` o `canchaId` no numericos |
| `MissingServletRequestParameterException` | 400 | `DATOS_INVALIDOS` | Falta `canchaId` o `fecha` en la disponibilidad |
| `HttpRequestMethodNotSupportedException` | 400 | `DATOS_INVALIDOS` | Verbo equivocado sobre una ruta existente (evita el 405) |
| `HttpMediaTypeNotSupportedException` | 400 | `DATOS_INVALIDOS` | `Content-Type` distinto de `application/json` (evita el 415) |
| `FormatoInvalidoException` | 400 | `DATOS_INVALIDOS` | Parseo estricto fallido de `fecha` o `horaInicio` |
| `BloqueInvalidoException` | 400 | `DATOS_INVALIDOS` | `horaInicio` fuera de hora en punto, o bloque fuera del horario de atencion |
| `FechaPasadaException` | 400 | `DATOS_INVALIDOS` | Alta sobre un bloque que ya ocurrio (D-03) |
| Token ausente, expirado, con firma invalida o con `rol = SERVICIO` | 401 | `NO_AUTENTICADO` | Punto de entrada de autenticacion del filtro (S-12) |
| Rol insuficiente en `GET /api/reservas` | 403 | `SIN_PERMISO` | Punto de acceso denegado de la cadena de filtros |
| `ReservaAjenaException` | 403 | `SIN_PERMISO` | `USUARIO` cancelando una reserva de otro (RN-03) |
| `CanchaNoEncontradaException` | 404 | `NO_ENCONTRADO` | `canchaId` inexistente, o inactiva en el alta (D-05) |
| `ReservaNoEncontradaException` | 404 | `NO_ENCONTRADO` | `{id}` inexistente en la cancelacion |
| `BloqueOcupadoException` | 409 | `BLOQUE_OCUPADO` | Bloque con reserva `CONFIRMADA` (RN-02) o bajo mantenimiento (D-07) |
| `LimiteReservasException` | 409 | `LIMITE_RESERVAS` | Limite `RESERVAS_MAX_ACTIVAS` alcanzado (RN-06) |
| `ReservaPasadaException` | 409 | `RESERVA_PASADA` | Cancelar una `CONFIRMADA` que ya ocurrio (RN-04, C-02) |
| `ReservaNoCancelableException` | 409 | `RESERVA_NO_CANCELABLE` | Cancelar una reserva ya `CANCELADA` (D-10) |
| `DataIntegrityViolationException` sobre `ux_reserva_bloque_confirmada` | 409 | `BLOQUE_OCUPADO` | Carrera entre dos altas simultaneas sobre el mismo bloque |
| `CatalogoNoDisponibleException` | 500 | `ERROR_INTERNO` | `ms-canchas` caido, `5xx`, `401`/`403` o timeout. Mensaje fijo: `"No se pudo consultar el catalogo de canchas"` (D-06) |
| `Exception` (resto) | 500 | `ERROR_INTERNO` | Red de seguridad |

Ningun codigo fuera de la tabla "Formato de error" del contrato. `405` y `415` no se
devuelven nunca: se traducen a `400 DATOS_INVALIDOS`, declarados explicitamente para que no
terminen como `500`.

La `DataIntegrityViolationException` se reconoce por el nombre de la restriccion en la
causa (`ux_reserva_bloque_confirmada`); si no se reconoce, cae en `500 ERROR_INTERNO`,
igual que en la spec 03.

Las respuestas `401` y `403` de la cadena de filtros usan los mismos manejadores de
autenticacion y acceso denegado de las specs 02 y 03, para conservar el formato
`{ "codigo", "mensaje" }`.

## 8. Configuracion

| Propiedad / variable | Valor | Origen |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/reservas_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | `reservas_user` | entorno |
| `spring.datasource.password` | `reservas_pass` | entorno |
| `spring.jpa.hibernate.ddl-auto` | `validate` | entorno |
| `server.port` | `8080` | interno del contenedor |
| `jwt.secret` | — | `JWT_SECRET` de `.env`, la misma de `ms-usuarios` y `ms-canchas` |
| `mscanchas.url` | `http://ms-canchas:8080` | `MS_CANCHAS_URL` |
| `mscanchas.timeout.conexion` | `2s` | fijo en `application.yml` (D-06) |
| `mscanchas.timeout.lectura` | `5s` | fijo en `application.yml` (D-06) |
| `reservas.max-activas` | `3` | `RESERVAS_MAX_ACTIVAS`, ya presente en `.env` y `.env.example` (S-13) |
| `reservas.token-servicio.duracion` | `5m` | fijo en `application.yml` (D-01) |

Servicio `ms-reservas` en `docker-compose.yml`, `depends_on: postgres` con
`condition: service_healthy` y `ms-canchas`, puerto `8084:8080` **temporal** para pruebas
con `curl.exe` hasta que exista el gateway Nginx (S-10).

Imagen: el `Dockerfile` copia el patron oficial de `CLAUDE.md` §1 sin cambios, ajustando
solo el nombre del `.jar` a `ms-reservas-0.0.1-SNAPSHOT.jar`. El esqueleto se descarga de
Spring Initializr por URL y se le corrige el `<parent>` a 3.5.3; nunca se ejecuta `mvn` en
el host (§2.1 del requirements).

### Dependencias

Las mismas de `ms-canchas`, con las mismas versiones, para que los tres servicios validen
el mismo token de forma identica.

| Dependencia | Origen |
|---|---|
| `spring-boot-starter-web` | Spring Initializr — aporta tambien el `RestClient` del cliente HTTP |
| `spring-boot-starter-data-jpa` | Spring Initializr |
| `spring-boot-starter-validation` | Spring Initializr |
| `spring-boot-starter-security` | Spring Initializr |
| `postgresql` (driver, `runtime`) | Spring Initializr |
| `springdoc-openapi-starter-webmvc-ui` `2.8.6` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-api` `0.12.6` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-impl` `0.12.6` (`runtime`) | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-jackson` `0.12.6` (`runtime`) | agregada a mano al `pom.xml` |

No se agrega ningun cliente HTTP externo, ni libreria de resiliencia, ni `spring-cloud`.

## 9. Decisiones de diseno

| # | Decision | Alternativa descartada | Por que |
|---|---|---|---|
| D-01 | Un solo `ReservaController` con las cinco rutas, y dos servicios: `ReservaService` (escritura y listados) y `DisponibilidadService` (solo HU-01) | Un unico servicio con todo, o un controlador por endpoint | Las cinco rutas son un mismo agregado, asi que dividir el controlador seria artificial. Pero la disponibilidad no toca la escritura, tiene sus dos llamadas HTTP y su propio algoritmo de bloques: separarla evita que `ReservaService` cargue con dos responsabilidades muy distintas |
| D-02 | `usuarioId` y `canchaId` son columnas `Long` simples, sin asociacion JPA | Mapearlas como `@ManyToOne` a entidades espejo de usuario y cancha | Esas filas viven en otras bases y `CLAUDE.md` §3 prohibe leerlas. Una entidad espejo obligaria a un segundo datasource o a duplicar datos; el DDL ya declara que no hay FK justamente por eso |
| D-03 | Doble barrera en RN-02: `exists...` en el servicio **mas** traduccion de `ux_reserva_bloque_confirmada` al mismo `409 BLOQUE_OCUPADO` | Solo la verificacion previa, o solo la restriccion de base | Dos altas simultaneas sobre el mismo bloque pasan la verificacion y la segunda reventaria como `500`. Es el patron ya probado en las specs 02 y 03 |
| D-04 | El enum `EstadoReserva` declara los tres valores de `ck_reserva_estado`, aunque el servicio solo escriba dos | Un enum de dos valores, `CONFIRMADA` y `CANCELADA` | Con dos valores, una fila con `FINALIZADA` — cargada a mano o por una version futura — reventaria el mapeo al leer. El enum debe cubrir el dominio de la columna; que el servicio no lo escriba es otra cosa |
| D-05 | `contarActivas` es un `@Query` JPQL; el resto son consultas derivadas | Nombre de metodo derivado, o filtrar en memoria | La condicion de D-04 del requirements mezcla `fecha > hoy` con `fecha = hoy AND horaInicio > ahora`: derivarla produce un nombre de metodo ilegible. Filtrar en memoria traeria todo el historial del usuario para contar tres filas |
| D-06 | `CanchasClient` y `EmisorTokenServicio` van en el paquete `service` | Un paquete `client` o `integration` nuevo | `CLAUDE.md` §4 congela la lista de paquetes: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `exception`. Inventar un paquete seria ampliar la estructura sin permiso |
| D-07 | El `403` de la cancelacion se decide en el servicio, no en la cadena de filtros; solo `GET /api/reservas` restringe por rol en la configuracion de seguridad | Expresar RN-03 con anotaciones de seguridad a nivel de metodo | La propiedad de la reserva no se conoce hasta cargarla de la base: es una regla de negocio, no de ruta. Resolverla en el filtro obligaria a consultar la base desde la cadena de seguridad |
| D-08 | Un `401` o `403` recibido **de `ms-canchas`** se trata como fallo de dependencia (`500 ERROR_INTERNO`), no se propaga al cliente | Reenviar el mismo codigo al cliente final | Un `401` de `ms-canchas` significa que el token de servicio esta mal firmado o vencido: es un defecto de configuracion nuestro, no un error del usuario. Devolverle `401` a un cliente que si estaba autenticado seria enganoso |
| D-09 | Con la cancha inactiva, la disponibilidad **no** llama al endpoint de bloqueos y devuelve todos los bloques ocupados | Llamar igual y calcular normalmente | El resultado ya esta determinado por D-05 del requirements: todos los bloques salen `false`. La segunda llamada HTTP no cambiaria nada y solo agrega latencia y una via mas de fallo |
| D-10 | En la cancelacion, el orden es propiedad (`403`) -> reserva pasada (`409 RESERVA_PASADA`) -> estado no cancelable (`409 RESERVA_NO_CANCELABLE`) | Comprobar primero el estado, o el `409` antes que el `403` | Responder `409` a quien no es dueno de la reserva le revelaria informacion sobre una reserva ajena. Y la precedencia de RN-04 sobre el estado es la consecuencia C-02 ya confirmada |
| D-11 | Fechas y horas viajan como `String` en los DTOs, con `@Pattern` mas parseo estricto en el mapper | `LocalDate` / `LocalTime` con `@JsonFormat` | Con `LocalTime` y el serializador por defecto Jackson emite `07:00:00` y rompe el `HH:mm` congelado; y un valor invalido fallaria en el deserializador, antes de `@Valid`, con un mensaje ajeno al contrato. Es el mismo D-03 de la spec 03 |
| D-12 | Cliente HTTP con `RestClient` de Spring Framework 6 y timeouts explicitos | `RestTemplate`, `WebClient` o un cliente generado | `RestTemplate` esta en mantenimiento; `WebClient` arrastraria WebFlux entero a un servicio MVC. `RestClient` ya viene en `spring-boot-starter-web` y es sincrono, que es lo que pide el documento de alcance §4.2 |
| D-13 | El token de servicio se emite en **cada** llamada, sin cache | Emitir uno y reutilizarlo hasta que expire | Firmar un JWT corto es despreciable frente a la llamada HTTP que lo acompaña. Cachearlo obligaria a manejar expiracion, concurrencia y renovacion para no ahorrar nada medible |
| D-14 | El rol `SERVICIO` se reutiliza sobre el mismo `JWT_SECRET` y el mismo filtro | Una clave compartida en cabecera propia, o un par de claves aparte | Reutiliza un mecanismo ya probado en tres servicios, no agrega infraestructura ni variables nuevas, y el `exp` de 5 minutos limita el dano si el token se filtra (decision D-01 del responsable) |
| D-15 | `FINALIZADA` se calcula en el `ReservaMapper`, no en el servicio ni en una consulta | Calcularlo con `CASE` en SQL, o persistirlo con una tarea programada | En el mapper la regla vive en un solo sitio y se aplica a las tres salidas. Un `CASE` en SQL la duplicaria en cada consulta, y persistirlo exigiria un `@Scheduled` que D-02 descarto explicitamente |
| D-16 | Mapper manual con metodos explicitos | MapStruct o reflexion generica | Prohibido por `CLAUDE.md` §3; ademas el formateo `HH:mm` de D-11 y el calculo de estado de D-15 necesitan conversion explicita |
| D-17 | Se reutilizan sin cambios el filtro JWT, el `ManejadorExcepciones`, el `ErrorResponse` y el patron de `Dockerfile` de las specs 02 y 03 | Rediseñarlos para este servicio | Estan fijados en `CLAUDE.md` §1 y §3 y en dos specs aprobadas; redecidirlos arriesga que tres servicios validen el mismo token de forma distinta |
| D-18 | El cambio en `ms-canchas` se limita a `FiltroToken`, `SeguridadConfig` y el filtrado por rol de `CanchaService` | Abrir un endpoint interno nuevo en `ms-canchas`, o un `ms-gateway` que resuelva la identidad | Un endpoint interno duplicaria los dos `GET` que ya existen y habria que congelarlo en el contrato. El gateway no existe todavia y crearlo aqui seria ampliar el alcance de la spec |

| D-19 | Entre validaciones que devuelven **el mismo codigo de respuesta**, primero las que se resuelven con una consulta local y despues las que exigen una llamada de red. En el alta: bloque ocupado (local) -> limite RN-06 (local) -> bloqueo de mantenimiento (HTTP) | Mantener el orden narrativo de las reglas: mantenimiento, limite y bloque ocupado | Los tres devuelven `409`, asi que el orden entre ellos no cambia el contrato ni ningun criterio de aceptacion. Con el orden anterior, un alta sobre un bloque ya reservado —el rechazo mas frecuente— gastaba una llamada HTTP a `ms-canchas` antes de mirar una fila que ya estaba en la propia base. La regla **no** alcanza al paso 5: ese devuelve `404`, no `409`, y ademas aporta el horario de atencion que necesita la validacion siguiente |
| D-20 | Una reserva cuyo `horaInicio` coincide con el instante actual **no** cuenta como activa para RN-06: la comparacion de `contarActivas` es `horaInicio > :ahora`, con `>` estricto | Usar `>=` y contar tambien el bloque que arranca justo ahora | Ese bloque ya esta ocurriendo: no es un turno futuro que el usuario pueda usar para acaparar horarios, que es lo que RN-06 quiere evitar. Ademas es coherente con D-03, que tampoco permite **crear** una reserva en ese mismo instante: si el sistema no deja reservarlo, tampoco tiene sentido que ocupe cupo. Borde verificado con el predicado ejecutado contra Postgres antes de T3. **El mismo criterio se aplica al otro extremo del bloque** (confirmado el 23/08/2026): en el instante exacto de `horaFin` la reserva ya se ve `FINALIZADA` en HU-03 y HU-04, porque `estadoVisible` compara con `fin.isAfter(ahora)` estricto. Los dos bordes tratan el bloque en curso igual: empezado ya no es futuro, terminado ya no es vigente |
## 10. Puntos pendientes

Ninguno que bloquee la implementacion.

El asunto abierto **A-01** de la spec 03 queda **cerrado** por D-01 del requirements y por
§5.2 y §5.5 de este diseño.

Registrado para la spec 05: si `ms-reportes` necesitara consultar `ms-canchas` o
`ms-reservas` sin token de persona, el mecanismo de token de servicio ya esta congelado en
el contrato y no se vuelve a decidir. Este diseño no implementa nada para `ms-reportes`.

Todo el diseño se apoya en campos, rutas y codigos congelados en el contrato, verificados
uno a uno en §1.
