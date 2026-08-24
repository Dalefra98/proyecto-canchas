# Spec 05 — ms-reportes · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 05").
La compuerta C2 (`design.md`) sigue pendiente: no se escribe código de producción hasta que
el diseño esté aprobado por escrito.

Las doce preguntas abiertas (P-01 a P-12) fueron **resueltas por el responsable el
23/08/2026** y ya están incorporadas a este documento. Las decisiones quedan registradas en
§9 con su motivo, para la defensa del proyecto.

Tres decisiones obligaron a modificar archivos fuera de esta spec, y el cambio **ya está
aplicado**:

- **P-01** obligó a modificar `docs/contratos/README.md` (tabla de rutas que aceptan el
  token `SERVICIO`) y a dejar constancia en la HU-08 de la spec 04, ya cerrada. Además
  obliga a un cambio de código en `ms-reservas`, que **ejecuta esta spec** y está acotado en
  §8.1.
- **P-03 a P-09** agregaron las "Notas de las rutas de reportes" al contrato y el `500` a
  las tres rutas.
- **P-12** agregó la capa `client` a `CLAUDE.md` §4.

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.5, 3.4, 4.2, 4.3,
6, 7), `.claude/specs/01-modelo-y-contratos/`, `.claude/specs/02-ms-usuarios/`,
`.claude/specs/03-ms-canchas/` y `.claude/specs/04-ms-reservas/` (las cuatro cerradas),
`docker-compose.yml`, `infra/postgres/05-seed.sql`, `docs/bitacora.md`.

## 1. Objetivo

Implementar el microservicio `ms-reportes`: los **tres** reportes de solo lectura ya
congelados en `docs/contratos/README.md` para el dominio `reportes` — ocupación por cancha,
reservas por período y cancelaciones por período. Ni uno más.

Es el único microservicio **sin base de datos propia** (`CLAUDE.md` §3): no tiene entidades
JPA, ni repositorios, ni `spring.jpa.hibernate.ddl-auto`, ni usuario de PostgreSQL. Toda su
información la obtiene por HTTP de `ms-canchas` y `ms-reservas`. Está **prohibido** que lea
`canchas_db` o `reservas_db` directamente.

Es también el único servicio que **agrega** datos de dos orígenes distintos en una sola
respuesta, y el único cuyos tres endpoints son exclusivos de `ADMIN`.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto Maven `backend/ms-reportes`, Spring Boot 3.5.3, Java 21, sin dependencia de JPA ni de PostgreSQL |
| E-02 | `GET /api/reportes/ocupacion?desde&hasta` — `ReporteOcupacionResponse` |
| E-03 | `GET /api/reportes/reservas?desde&hasta` — `ReporteReservasResponse` |
| E-04 | `GET /api/reportes/cancelaciones?desde&hasta` — `ReporteCancelacionesResponse` |
| E-05 | Emisor del token de servicio: JWT HS256 con `JWT_SECRET`, `rol = SERVICIO`, sin `sub`, `exp` 5 minutos, generado en cada llamada saliente (mismo mecanismo D-01 de la spec 04) |
| E-06 | Cliente HTTP hacia `ms-canchas` (`GET /api/canchas`) con el token de servicio de E-05 |
| E-07 | Cliente HTTP hacia `ms-reservas` (`GET /api/reservas`) con el token de servicio de E-05 |
| E-08 | Filtro JWT que valida la firma localmente con `JWT_SECRET` y exige `rol = ADMIN` en los tres endpoints |
| E-09 | `@RestControllerAdvice` con el formato de error congelado, incluida la traducción del fallo de una dependencia HTTP a `500 ERROR_INTERNO` y el `404` de ruta inexistente |
| E-10 | Documentación `springdoc-openapi` con los códigos de error de cada endpoint |
| E-11 | `Dockerfile` según el patrón oficial de `CLAUDE.md` §1 y servicio `ms-reportes` agregado a `docker-compose.yml`, puerto `8085:8080` |
| E-12 | Modificación acotada de `ms-reservas` para que acepte `rol = SERVICIO` en `GET /api/reservas` (§8.1) |

## 3. Contexto técnico fijado (no se vuelve a decidir)

| Aspecto | Valor |
|---|---|
| Nombre del servicio | `ms-reportes` |
| Ruta en el repo | `backend/ms-reportes` |
| Paquete raíz | `ec.ups.dae.reportes` |
| Spring Boot | 3.5.3 (`CLAUDE.md` §3) |
| Java | 21 |
| Base de datos | **ninguna** (`CLAUDE.md` §3) |
| Puerto interno | 8080 |
| Puerto publicado | `8085:8080`, temporal hasta el gateway (P-10) |
| Capas | `controller` -> `service` -> `client` (`CLAUDE.md` §4, P-12) |
| Mapper | manual, sin Lombok ni MapStruct |
| Inyección | por constructor |
| Errores | `{ "codigo": ..., "mensaje": ... }` según la tabla del contrato |
| `JWT_SECRET` | la misma de `.env` que usan los otros tres microservicios |
| `MS_CANCHAS_URL` | `http://ms-canchas:8080` |
| `MS_RESERVAS_URL` | `http://ms-reservas:8080` |
| Timeouts salientes | 2 s de conexión, 5 s de lectura, sin reintentos (D-06 de la spec 04) |

La capa `client` sustituye a `repository` y `entity`: es la única que hace HTTP saliente,
igual que `repository` era la única que tocaba la base en los otros tres servicios. Quedó
incorporada a `CLAUDE.md` §4 el 23/08/2026 (P-12) para que no vuelva a preguntarse.

## 4. Historias de usuario y criterios de aceptación

### HU-01 — Reporte de ocupación por cancha (PDF §3.3.5)

Como administrador, necesito ver el porcentaje de ocupación de cada cancha en un rango de
fechas, para saber cuáles se usan y cuáles no.

- **CUANDO** un ADMIN envíe `GET /api/reportes/ocupacion?desde=2026-08-01&hasta=2026-08-31`
  con token válido, **ENTONCES** la respuesta será `200` con
  `{ "desde": "2026-08-01", "hasta": "2026-08-31", "items": [...] }`, donde `desde` y
  `hasta` repiten literalmente los parámetros recibidos.
- **CUANDO** se arme cada elemento de `items`, **ENTONCES** tendrá exactamente los campos
  `canchaId`, `nombre`, `deporte`, `horasReservadas`, `horasDisponibles` y
  `porcentajeOcupacion`, sin ninguno de más y sin renombrar (payload congelado).
- **CUANDO** se calcule `horasDisponibles`, **ENTONCES** será
  `(horaCierre − horaApertura) × número de días del rango`, contando **todos** los días,
  pasados y futuros por igual, y **sin restar** los bloqueos de mantenimiento (P-03).
- **CUANDO** se calcule `horasReservadas`, **ENTONCES** será el número de reservas de esa
  cancha con `fecha` dentro del rango y `estado` en `CONFIRMADA` o `FINALIZADA`; las
  `CANCELADA` no cuentan (P-04). Cada reserva vale exactamente una hora (RN-01).
- **CUANDO** se calcule `porcentajeOcupacion`, **ENTONCES** será
  `horasReservadas / horasDisponibles × 100`, redondeado a **un decimal** con `HALF_UP`
  (P-06), y estará entre 0 y 100.
- **CUANDO** `horasDisponibles` sea `0`, **ENTONCES** `porcentajeOcupacion` será `0` y nunca
  habrá división por cero.
- **CUANDO** una cancha no tenga reservas en el rango, **ENTONCES** igual aparecerá en
  `items` con `horasReservadas = 0` y `porcentajeOcupacion = 0` (P-09).
- **CUANDO** una cancha tenga `activa = false`, **ENTONCES** también aparecerá en `items`:
  sus reservas históricas son parte del reporte (P-09).
- **SI** no hay token o es inválido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama tiene `rol = USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** falta `desde` o falta `hasta`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.

Nota para la defensa: los valores del ejemplo del contrato (`horasReservadas: 12`,
`horasDisponibles: 45`) son **ilustrativos** y no corresponden al seed, cuyas tres canchas
abren 15 horas diarias (07:00–22:00). Con la fórmula de P-03, un rango de un día sobre
`Padel 1` da `horasDisponibles = 15`, no 45.

### HU-02 — Reporte de reservas por período (PDF §3.3.5)

Como administrador, necesito el número de reservas por cancha y por deporte en un rango de
fechas, para dimensionar la demanda.

- **CUANDO** un ADMIN envíe `GET /api/reportes/reservas?desde=...&hasta=...` con token
  válido, **ENTONCES** la respuesta será `200` con la misma envoltura
  `{ "desde", "hasta", "items" }`.
- **CUANDO** se arme cada elemento de `items`, **ENTONCES** tendrá exactamente `canchaId`,
  `nombre`, `deporte` y `totalReservas`.
- **CUANDO** se cuente `totalReservas`, **ENTONCES** se contarán las reservas de esa cancha
  con `fecha` dentro del rango y `estado` en `CONFIRMADA` o `FINALIZADA`; las `CANCELADA`
  quedan excluidas y tienen su propio reporte (P-04).
- **CUANDO** no exista ninguna reserva en el rango, **ENTONCES** `200` con todas las canchas
  en `items` y `totalReservas = 0`, nunca `404` ni un arreglo vacío (P-09).
- **SI** no hay token o es inválido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama tiene `rol = USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** falta `desde` o falta `hasta`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.

El agrupamiento "por deporte" del PDF §3.3.5 se satisface con el campo `deporte` de cada
fila: el contrato **no** declara un segundo bloque de totales por deporte, así que no se
crea. Agregarlo sería ampliar el alcance (`CLAUDE.md` §0.2).

### HU-03 — Reporte de cancelaciones por período (PDF §3.3.5)

Como administrador, necesito el número de cancelaciones por cancha en un rango de fechas,
para detectar problemas de operación.

- **CUANDO** un ADMIN envíe `GET /api/reportes/cancelaciones?desde=...&hasta=...` con token
  válido, **ENTONCES** la respuesta será `200` con la misma envoltura
  `{ "desde", "hasta", "items" }`.
- **CUANDO** se arme cada elemento de `items`, **ENTONCES** tendrá exactamente `canchaId`,
  `nombre` y `totalCancelaciones`. **No lleva `deporte`**: el payload congelado no lo
  declara y no se agrega (`CLAUDE.md` §5).
- **CUANDO** se cuente `totalCancelaciones`, **ENTONCES** se contarán únicamente las
  reservas con `estado = CANCELADA` y `fecha` dentro del rango.
- **CUANDO** una cancha no tenga cancelaciones, **ENTONCES** aparecerá con
  `totalCancelaciones = 0` (P-09).
- **SI** no hay token o es inválido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama tiene `rol = USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** falta `desde` o falta `hasta`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.

La fecha por la que se filtra una cancelación es la `fecha` de la reserva, no la fecha en
que se canceló: `reservas_db` no guarda fecha de cancelación y el contrato no declara ese
campo. Queda escrito en el contrato y aquí, de forma explícita, para la defensa.

### HU-04 — Validación del rango `desde` / `hasta`

Como consumidor de la API, necesito que un rango mal formado falle claro y temprano, para
no recibir un reporte silenciosamente vacío.

- **SI** falta `desde` o falta `hasta`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`:
  ambos son obligatorios en los tres endpoints.
- **SI** `desde` o `hasta` no tienen el formato `AAAA-MM-DD`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `desde` es posterior a `hasta`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.
- **CUANDO** el rango sea válido, **ENTONCES** ambos extremos serán **inclusivos**: un rango
  de `2026-08-01` a `2026-08-31` cubre 31 días (P-07).
- **CUANDO** `desde` sea igual a `hasta`, **ENTONCES** el reporte cubrirá ese único día y
  responderá `200`.
- **CUANDO** el rango incluya fechas futuras o sea muy amplio, **ENTONCES** se procesará
  igual, sin error: no hay rango máximo ni restricción de fecha tope (P-07).

### HU-05 — Consumo de `ms-canchas` y `ms-reservas` por HTTP

Como equipo, necesito que `ms-reportes` obtenga sus datos exclusivamente por REST, para no
romper la independencia de datos que exige `CLAUDE.md` §3 y el PDF §4.3.

- **CUANDO** se solicite cualquiera de los tres reportes, **ENTONCES** `ms-reportes` pedirá
  a `ms-canchas` el catálogo completo (`GET /api/canchas`) y a `ms-reservas` el listado
  global (`GET /api/reservas`), y cruzará ambos por `canchaId` **en memoria**.
- **CUANDO** haga una llamada saliente, **ENTONCES** emitirá un token de servicio nuevo: JWT
  HS256 firmado con `JWT_SECRET`, con `rol = SERVICIO`, sin `sub` y con `exp` de 5 minutos,
  y lo enviará en `Authorization: Bearer <token de servicio>` (P-01, mecanismo D-01 de la
  spec 04).
- **CUANDO** llame con token de servicio, **ENTONCES** `ms-canchas` le devolverá la vista
  completa del catálogo, incluidas las canchas con `activa = false`, que HU-01 necesita.
- **CUANDO** llame a `ms-canchas` o a `ms-reservas`, **ENTONCES** usará las URLs de
  contenedor de `MS_CANCHAS_URL` y `MS_RESERVAS_URL`, nunca `localhost` ni una URL fija en
  el código.
- **CUANDO** haga una llamada saliente, **ENTONCES** aplicará 2 s de conexión y 5 s de
  lectura, **sin reintentos** (D-06 de la spec 04).
- **SI** `ms-canchas` o `ms-reservas` no responden, responden `5xx`, responden `401`/`403`, o
  agotan el tiempo de espera, **ENTONCES** `500` con `codigo = ERROR_INTERNO` y un mensaje
  propio, sin propagar el cuerpo recibido ni un stacktrace, y **sin** devolver un reporte
  parcial que parezca completo (P-05).
- **CUANDO** se lea una reserva devuelta por `ms-reservas`, **ENTONCES** su `estado` será el
  que ese servicio calcula al leer (`FINALIZADA` no se persiste, decisión D-02 de la spec
  04); `ms-reportes` **no** recalcula estados ni deduce `FINALIZADA` por su cuenta.
- **CUANDO** se filtre por rango, **ENTONCES** se hará en memoria sobre el listado global:
  `GET /api/reservas` no acepta parámetros y no se le agregan (P-02).
- **CUANDO** se necesiten los bloqueos de mantenimiento, **ENTONCES** no se consultan: P-03
  decidió que no restan de `horasDisponibles`, así que `ms-reportes` **no** llama a
  `GET /api/canchas/{canchaId}/bloqueos`.

### HU-06 — Autorización: los tres reportes son solo de ADMIN (PDF §3.1)

Como equipo, necesito que ningún usuario final vea los reportes, porque el PDF §3.1 y el
contrato congelado marcan los tres endpoints como exclusivos de `ADMIN`.

- **CUANDO** llegue una petición con `Authorization: Bearer <token>`, **ENTONCES**
  `ms-reportes` validará la firma **localmente** con `JWT_SECRET` y leerá el claim `rol`,
  sin llamar a `ms-usuarios` por HTTP y sin consultar `usuarios_db`.
- **SI** falta el encabezado, el token está vencido, la firma no coincide o está mal
  formado, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** el claim `rol` es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO` en los
  tres endpoints.
- **SI** el token entrante trae `rol = SERVICIO`, **ENTONCES** `401` con
  `codigo = NO_AUTENTICADO` (P-11): ningún servicio consume `ms-reportes`. Emitir tokens de
  servicio no obliga a aceptarlos, y así quedó escrito en el contrato.
- **CUANDO** se consulte la documentación `springdoc-openapi`, **ENTONCES** sus rutas
  estarán abiertas sin token, igual que en los otros tres microservicios.

### HU-07 — Errores uniformes y sin stacktrace

Como consumidor de la API, necesito que todos los errores tengan la misma forma que los de
los otros tres microservicios.

- **CUANDO** cualquier endpoint falle, **ENTONCES** el cuerpo será exactamente
  `{ "codigo": "...", "mensaje": "..." }` con un `codigo` de la tabla "Formato de error".
- **CUANDO** falle una dependencia HTTP, **ENTONCES** `500` con `codigo = ERROR_INTERNO`
  (P-05). Las tres rutas declaran `500` en el contrato desde el 23/08/2026.
- **CUANDO** ocurra una excepción no prevista, **ENTONCES** `500` con
  `codigo = ERROR_INTERNO`, sin stacktrace en la respuesta.
- **CUANDO** se pida una ruta que no existe, **ENTONCES** `404` con
  `codigo = NO_ENCONTRADO`, con el mismo manejador de `NoResourceFoundException` que la
  tarea T10 de la spec 04 dejó en los otros tres servicios.
- **CUANDO** se traduzca cualquier error, **ENTONCES** el mensaje estará en español, sin
  tildes, como en los otros tres servicios.

### HU-08 — El servicio arranca en Docker Compose

Como equipo, necesito `ms-reportes` levantado junto al resto del entorno con un solo
comando.

- **CUANDO** se ejecute `docker compose up -d --build ms-reportes` y `ms-canchas` y
  `ms-reservas` estén arriba, **ENTONCES** el servicio quedará arriba y responderá en el
  puerto `8085` del host (P-10).
- **CUANDO** el servicio arranque, **ENTONCES** **no** intentará conectarse a PostgreSQL: el
  `pom.xml` no incluye `spring-boot-starter-data-jpa` ni el driver, así que no hay
  autoconfiguración de `DataSource` que excluir.
- **CUANDO** el servicio esté arriba, **ENTONCES**
  `GET /api/reportes/ocupacion?desde&hasta` con token de ADMIN devolverá las **cuatro**
  canchas que hoy tiene `canchas_db`: las tres del seed (`Padel 1`, `Tenis 1`, `Basquet 1`,
  todas `07:00`–`22:00`) y `Padel 2` (`canchaId = 4`, `08:00`–`21:00`), que la spec 04 dejó
  activa a propósito para tener un horario distinto. Esa cuarta cancha es la que permite
  verificar que `horasDisponibles` usa el horario real de cada cancha y no un valor fijo.
- **CUANDO** el servicio esté arriba, **ENTONCES** expondrá su documentación
  `springdoc-openapi` con los tres endpoints y sus códigos de error.
- **CUANDO** se aplique el cambio de §8.1 a `ms-reservas`, **ENTONCES** sus cinco endpoints
  seguirán comportándose igual para `ADMIN` y `USUARIO`: el rol `SERVICIO` se suma en
  `GET /api/reservas`, no sustituye a ninguno.

## 5. Reglas de negocio cubiertas

| ID | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora | **Consumida, no implementada** — que cada reserva valga exactamente una hora es lo que permite que `horasReservadas` sea un conteo de reservas (HU-01). `ms-reportes` no valida la duración: la garantiza `ms-reservas` con `ck_reserva_bloque_una_hora` |
| RN-02 | No se puede reservar un bloque ya ocupado | No aplica — es de `ms-reservas` (spec 04). `ms-reportes` no crea reservas |
| RN-03 | El usuario cancela solo las suyas; el admin cualquiera | No aplica — es de `ms-reservas` (spec 04). `ms-reportes` es de solo lectura |
| RN-04 | Solo se cancela una reserva que aún no ha ocurrido | No aplica — es de `ms-reservas` (spec 04) |
| RN-05 | Cancelar libera el bloque | **Cubierta como coherencia de lectura** — una reserva `CANCELADA` no cuenta en `horasReservadas` ni en `totalReservas` (P-04), así que el reporte no contradice la disponibilidad real que muestra `ms-reservas` |
| RN-06 | Límite configurable de reservas activas por usuario | No aplica — es de `ms-reservas` (spec 04) |
| RN-07 | Solo el admin gestiona canchas y su horario | **Consumida, no implementada** — `ms-reportes` **lee** `horaApertura` y `horaCierre` para calcular `horasDisponibles`, y nunca escribe en `ms-canchas`: su token `SERVICIO` es de solo lectura y toda ruta de escritura le responde `403` |
| RN-08 | Estados `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | **Cubierta por completo** — los tres reportes clasifican por `estado`: `CONFIRMADA` + `FINALIZADA` en HU-01 y HU-02, `CANCELADA` en HU-03. `ms-reportes` toma el `estado` tal cual lo devuelve `ms-reservas` y no lo recalcula |

`ms-reportes` no implementa ninguna regla de negocio nueva: es un módulo de solo lectura
(PDF §3.3.5). Las reglas propias de esta spec son de **cálculo**, no de negocio, y quedaron
fijadas en P-03, P-04 y P-06.

## 6. Contrato REST

Nombres tomados literalmente de `docs/contratos/README.md`. No se renombra, no se abrevia,
no se agrega nada.

### 6.1 Rutas

| Verbo | Ruta | Rol | Respuestas |
|---|---|---|---|
| GET | `/api/reportes/ocupacion?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 |
| GET | `/api/reportes/reservas?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 |
| GET | `/api/reportes/cancelaciones?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 |

Los tres declaran los mismos cinco códigos y ninguno más. En particular **no declaran
`404`** como respuesta de negocio (un rango sin datos es `200` con `items`) ni `409`. El
`404 NO_ENCONTRADO` de HU-07 es solo el de una ruta inexistente, transversal a los cuatro
microservicios.

Los parámetros `desde` y `hasta` son los dos únicos que aceptan estas rutas, y ambos son
obligatorios. Ninguna acepta `canchaId`, `deporte`, `estado`, paginación ni ordenamiento.

### 6.2 Campos

| Concepto | Campo | Tipo / valores |
|---|---|---|
| Inicio del rango consultado | `desde` | string `AAAA-MM-DD` |
| Fin del rango consultado | `hasta` | string `AAAA-MM-DD` |
| Lista de filas del reporte | `items` | arreglo de objetos |
| Identificador de cancha | `canchaId` | number |
| Nombre de cancha | `nombre` | string |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` |
| Horas reservadas en el rango | `horasReservadas` | number |
| Horas disponibles en el rango | `horasDisponibles` | number |
| Total de reservas en el rango | `totalReservas` | number |
| Total de cancelaciones en el rango | `totalCancelaciones` | number |
| Porcentaje de ocupación | `porcentajeOcupacion` | number (0-100), un decimal, `HALF_UP` |
| Código de error | `codigo` | ver tabla "Formato de error" |
| Mensaje de error | `mensaje` | string |

### 6.3 Payloads de respuesta

`ReporteOcupacionResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horasReservadas": 12, "horasDisponibles": 45, "porcentajeOcupacion": 26.7 }
  ]
}
```

`ReporteReservasResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "totalReservas": 12 }
  ]
}
```

`ReporteCancelacionesResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "totalCancelaciones": 3 }
  ]
}
```

### 6.4 Códigos de error usados

| Situación | HTTP | `codigo` |
|---|---|---|
| `desde`/`hasta` ausente, mal formado o `desde` posterior a `hasta` | 400 | `DATOS_INVALIDOS` |
| Sin token, token inválido o token con `rol = SERVICIO` | 401 | `NO_AUTENTICADO` |
| Rol `USUARIO` | 403 | `SIN_PERMISO` |
| Ruta inexistente | 404 | `NO_ENCONTRADO` |
| Fallo de `ms-canchas` o `ms-reservas`, o error no previsto | 500 | `ERROR_INTERNO` |

## 7. Dependencias hacia otros servicios

| Servicio | Endpoint consumido | Para qué | Credencial |
|---|---|---|---|
| `ms-canchas` | `GET /api/canchas` | `canchaId`, `nombre`, `deporte`, `horaApertura`, `horaCierre` y `activa` de **todas** las canchas | token de servicio `rol = SERVICIO` |
| `ms-reservas` | `GET /api/reservas` | listado global con `canchaId`, `fecha`, `horaInicio`, `horaFin` y `estado` | token de servicio `rol = SERVICIO` (habilitado por P-01) |

`ms-reportes` **no** consume `ms-usuarios`: valida el token localmente y ningún reporte
lleva datos de usuario. Tampoco consume `GET /api/canchas/{canchaId}/bloqueos`, porque P-03
decidió que los bloqueos no restan de `horasDisponibles`.

### 7.1 D-02 — Filtrado en memoria: limitación asumida

`GET /api/reservas` está congelado sin parámetros y así se queda (P-02). `ms-reportes` trae
**todas** las reservas del sistema en cada petición y filtra por `fecha` en memoria.

Queda escrito como decisión de diseño consciente, no como descuido: **esto no escala**. Con
decenas de miles de reservas la respuesta crecería sin límite y el filtrado se haría en el
lado equivocado. Es aceptable para el alcance académico de este proyecto, cuyo seed tiene
tres canchas, y evita propagar un cambio de contrato a la spec 04 (cerrada) y a
`mf-administracion`, que ya consume esa ruta tal cual está.

Si alguna vez hiciera falta, la corrección conocida es agregar `?desde&hasta` a
`GET /api/reservas`. No se hace ahora.

## 8. Consecuencias sobre servicios ya cerrados

### 8.1 Cambio autorizado en `ms-reservas` (decisión P-01)

Único cambio de código fuera de `backend/ms-reportes` que esta spec ejecuta. Alcance
**exacto**, y está prohibido ampliarlo:

| Archivo | Cambio |
|---|---|
| `FiltroToken` de `ms-reservas` | Acepta un token con `rol = SERVICIO` **sin exigir el claim `sub`** |
| `SeguridadConfig` de `ms-reservas` | Admite `SERVICIO` **únicamente** en `GET /api/reservas` |

Las otras cuatro rutas siguen rechazando `SERVICIO` con `401 NO_AUTENTICADO`:
`POST /api/reservas` y `PATCH /api/reservas/{id}/cancelacion` escriben, y
`GET /api/reservas/mias` necesita un `sub` que el token de servicio no trae.
`GET /api/reservas/disponibilidad` tampoco lo acepta: ningún servicio interno la consume.

Ninguna entidad, DTO, regla de negocio, mapper ni consulta de `ms-reservas` se toca. La
HU-08 de la spec 04 quedó actualizada el 23/08/2026 con la nota de revisión que explica el
cambio y por qué se descartó la alternativa.

### 8.2 Archivos ya modificados por las respuestas a P-01 a P-12

| Archivo | Cambio | Pregunta |
|---|---|---|
| `docs/contratos/README.md` | Tabla de rutas que aceptan el token `SERVICIO`; regla de no propagar el token del usuario final; emitir no obliga a aceptar | P-01, P-11 |
| `docs/contratos/README.md` | `500` en las tres rutas de reportes y "Notas de las rutas de reportes" | P-03 a P-09 |
| `CLAUDE.md` §4 | Capa `client` para microservicios sin base propia | P-12 |
| `.claude/specs/04-ms-reservas/requirements.md` | Nota de revisión en HU-08 y corrección de la nota de §6 | P-01 |

## 9. Decisiones tomadas (P-01 a P-12, respondidas el 23/08/2026)

**P-01 — Credencial de `ms-reportes` hacia `ms-reservas`. Salida (a):** `ms-reservas` acepta
`rol = SERVICIO` en `GET /api/reservas`, solo lectura. Se descartó la salida (b) —reenviar
el token del ADMIN que pide el reporte— porque acopla `ms-reportes` a la sesión de quien
pregunta y contradice C-01 de la spec 03, que ya estableció que las llamadas internas no
propagan el token del usuario final. Mantener un único mecanismo en todo el sistema vale más
que no tocar una spec cerrada. Alcance del cambio en §8.1.

**P-02 — Filtrado del rango.** Se trae el listado global y se filtra en memoria; el contrato
no se modifica. Agregar parámetros a una ruta congelada propagaría el cambio a la spec 04 y
a `mf-administracion`, y el volumen del proyecto no lo justifica. Limitación asumida por
escrito en §7.1.

**P-03 — `horasDisponibles`.** `(horaCierre − horaApertura) × número de días del rango`. No
se restan los bloqueos de mantenimiento: obligaría a una llamada HTTP por día y por cancha.
Se cuentan todos los días del rango, pasados y futuros por igual. El `45` del ejemplo del
contrato es ilustrativo y no corresponde al seed.

**P-04 — Estados que cuentan.** `horasReservadas` y `totalReservas` cuentan `CONFIRMADA` y
`FINALIZADA`, y excluyen `CANCELADA`. Una reserva que ya ocurrió ocupó la cancha igual;
contar solo `CONFIRMADA` daría cero en todo rango pasado, que es justo lo que un reporte
necesita mostrar. Las canceladas tienen su propio reporte.

**P-05 — Fallo de una dependencia HTTP.** `500 ERROR_INTERNO`, mismo criterio que D-06 de la
spec 04. `ERROR_INTERNO` ya estaba en la tabla "Formato de error" como código transversal,
así que no se agrega ningún código nuevo: solo se suma el `500` a las tres rutas.

**P-06 — `porcentajeOcupacion`.** Un decimal, redondeo `HALF_UP`.

**P-07 — Rango.** Ambos extremos inclusivos. Sin rango máximo ni restricción de fechas
futuras.

**P-08 — Canchas de mayor y menor demanda.** Confirmado: queda fuera de esta spec. Se
resuelve ordenando `items` en el frontend, sin endpoint nuevo.

**P-09 — Canchas en `items`.** Toda cancha aparece, con ceros si no tuvo actividad. Las
inactivas también: sus reservas históricas siguen siendo parte del reporte.

**P-10 — Puerto.** `8085:8080`, temporal hasta que exista el gateway.

**P-11 — Token `SERVICIO` entrante.** Confirmado: `ms-reportes` lo rechaza con
`401 NO_AUTENTICADO`.

**P-12 — Capa `client`.** Aceptada, y agregada a `CLAUDE.md` §4 como capa válida para
microservicios sin base de datos propia, para que no vuelva a preguntarse.

## 10. Fuera de alcance de esta spec

- Los otros tres microservicios (`ms-usuarios`, `ms-canchas`, `ms-reservas`), **salvo** el
  cambio de §8.1 en `ms-reservas`, que P-01 obliga a hacer y está acotado a `FiltroToken` y
  `SeguridadConfig`. Ninguna entidad, DTO, regla ni endpoint suyo se toca por otra razón.
- Todo el frontend: `shell`, `mf-reservas`, `mf-administracion`, `mf-reportes` y Module
  Federation. La pantalla de reportes es de otra spec.
- **"Listado de las canchas con mayor y menor demanda"** (PDF §3.3.5, cuarta viñeta): el
  contrato no declara endpoint ni payload para eso, y P-08 confirmó que se resuelve
  ordenando `items` en el cliente. Crear un cuarto reporte sería ampliar el alcance.
- Cualquier reporte, filtro, agregación o campo que no esté en los tres payloads congelados:
  reservas por usuario, ranking de usuarios, ingresos, series temporales, comparativas entre
  períodos, totales generales fuera de `items`, o un bloque de totales agrupados por
  `deporte`.
- Restar los bloqueos de mantenimiento de `horasDisponibles`, y con ello el consumo de
  `GET /api/canchas/{canchaId}/bloqueos`: P-03 lo descartó.
- Agregar `?desde&hasta` a `GET /api/reservas`: P-02 lo descartó.
- Base de datos propia, tablas de agregados, vistas materializadas, esquema en PostgreSQL,
  DDL nuevo o modificación de `infra/postgres/*.sql`. `ms-reportes` no persiste nada y no
  se cargan reservas de ejemplo en `05-seed.sql`.
- Lectura directa de `reservas_db` o `canchas_db` (prohibido por `CLAUDE.md` §3 y PDF §4.3),
  aunque el PDF §4.2 diga "Lectura desde reservas_db / canchas_db" en su tabla: el propio
  PDF §4.3 aclara que el módulo consulta *"mediante llamadas a los microservicios"*, y
  `CLAUDE.md` §3 lo prohíbe sin matices. Manda `CLAUDE.md`.
- Caché de las respuestas de `ms-canchas` y `ms-reservas`, reintentos, circuit breaker o
  cualquier política de resiliencia más allá de los timeouts heredados de D-06.
- Caché o reutilización del token de servicio: se emite uno por llamada, igual que en la
  spec 04.
- Exportación a CSV, Excel, PDF o cualquier formato analítico, y gráficos: prohibido
  explícitamente por `CLAUDE.md` §2 y PDF §3.5 ("reportes BI o exportación analítica").
- Paginación y ordenamiento configurable de `items`.
- Redecidir Spring Boot 3.5.3, el patrón de `Dockerfile` con cache mount, el formato de
  error o el mecanismo del filtro JWT: están fijados en `CLAUDE.md` §1 y §3 y en las specs
  02, 03 y 04, y se reutilizan tal cual.
- El gateway Nginx que enrutará `/api`: mientras no exista, el puerto `8085` publicado es la
  única vía de prueba y es temporal.
- Generar el esqueleto con `mvn` en el host: prohibido por `CLAUDE.md` §1.

## 11. Supuestos

Sin supuestos. Los doce huecos detectados fueron respondidos por el responsable el
23/08/2026 y están en §9. Los tres puntos siguientes no son supuestos, sino lecturas del
material existente que quedan explícitas para la defensa del proyecto:

1. **`ms-reportes` no tiene base de datos.** Lo dice `CLAUDE.md` §3 de forma literal; el PDF
   §4.2 dice "Lectura desde reservas_db / canchas_db" en su tabla, y esa contradicción se
   resuelve a favor de `CLAUDE.md`, que es la fuente única de verdad y además coincide con
   el propio PDF §4.3. Señalado en §10.
2. **`horasReservadas` se cuenta como "una reserva = una hora"**, porque RN-01 y la
   restricción `ck_reserva_bloque_una_hora` de la spec 04 garantizan que todo bloque dura
   exactamente una hora.
3. **La fecha de una cancelación es la `fecha` de la reserva**, no la fecha en que se
   canceló, porque `reservas_db` no almacena esa segunda fecha y el contrato no la declara.
   Señalado en HU-03 y en el contrato.
