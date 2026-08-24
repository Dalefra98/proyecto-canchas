# Spec 04 — ms-reservas · tasks.md

Base: `requirements.md` (C1 aprobado 23/08/2026) y `design.md` (C2 aprobado 23/08/2026).

**Estado: las diez tareas (T1 a T10) fueron ejecutadas y verificadas con salida real el
23/08/2026. Spec 04 cerrada** — ver `docs/bitacora.md` para la traza de iteraciones y el
estado en que quedo el entorno.

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado y se espera aprobacion. Ninguna tarea encadena la
siguiente. Cada tarea deja el proyecto compilando y el servicio arrancando.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay JDK, Maven ni psql: todo pasa por Docker
(`CLAUDE.md` §1). Se usa `curl.exe`, no `curl`, y `Copy-Item`, no `cp`.

Atajo usado en todas las tareas — compilar el microservicio. El volumen `m2repo` montado en
`/root/.m2` cachea las dependencias entre corridas; sin el, la descarga completa desde
`repo.maven.apache.org` corta el handshake TLS (`CLAUDE.md` §1):

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
```

## Regla de reutilizacion desde `ms-canchas` (T1, T3 y T4)

Siete archivos **se copian** desde `backend/ms-canchas`, no se reescriben desde cero. Si
tres servicios validan el mismo JWT con codigo distinto, la diferencia solo aparece en la
integracion y es dificil de rastrear (design D-17).

| Archivo origen (en `backend/ms-canchas`) | Destino | Tarea | Adaptacion permitida |
|---|---|---|---|
| `Dockerfile` | `backend/ms-reservas/Dockerfile` | T1 | Solo el nombre del `.jar`: `ms-reservas-0.0.1-SNAPSHOT.jar` |
| `config/OpenApiConfig.java` | `config/OpenApiConfig.java` | T1 | Paquete, `import` y el titulo del documento |
| `dto/ErrorResponse.java` | `dto/ErrorResponse.java` | T3 | Solo el paquete |
| `exception/ManejadorExcepciones.java` | `exception/ManejadorExcepciones.java` | T3 | Paquete, `import` y las excepciones de dominio (ver abajo) |
| `config/FiltroToken.java` | `config/FiltroToken.java` | T4 | Paquete, `import` y el rechazo de `rol = SERVICIO` (ver abajo) |
| `service/TokenService.java` | `service/TokenService.java` | T4 | Solo el paquete y los `import` |
| `config/SeguridadConfig.java` | `config/SeguridadConfig.java` | T4 | Paquete, `import` y la matriz de rutas (ver abajo) |

`TokenService` y el `Dockerfile` son copia literal salvo lo indicado. Los otros se copian
como punto de partida y se adaptan **solo** en lo siguiente, dejando intacto todo lo demas:

- **`ManejadorExcepciones`**: se conservan la estructura, el formato `ErrorResponse` y los
  manejadores comunes —`MethodArgumentNotValidException`,
  `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`,
  `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException` y
  `Exception` como `500 ERROR_INTERNO`—. Se sustituyen las excepciones de dominio de canchas
  por las de reservas de la tabla del design §7, se agrega
  `MissingServletRequestParameterException` como `400 DATOS_INVALIDOS`, y la
  `DataIntegrityViolationException` se reconoce ahora por `ux_reserva_bloque_confirmada`.
- **`FiltroToken`**: se conserva la validacion completa del token. Se agrega **una sola
  regla nueva**: un token entrante con `rol = SERVICIO` se rechaza con
  `401 NO_AUTENTICADO` (design §5.1, supuesto S-12).
- **`SeguridadConfig`**: se conservan la cadena `STATELESS`, CSRF/`httpBasic`/`formLogin`
  desactivados, el `permitAll()` de las tres rutas de springdoc, el
  `addFilterBefore(filtroToken, ...)` y los dos manejadores que escriben
  `401 NO_AUTENTICADO` y `403 SIN_PERMISO` como `ErrorResponse`. Se reemplaza la matriz de
  `requestMatchers` por la tabla del design §5.3: `GET /api/reservas` con
  `hasRole("ADMIN")` y las otras cuatro rutas con token valido de cualquier rol.

Comando de copia, desde la raiz del repositorio:

```powershell
Copy-Item backend/ms-canchas/Dockerfile backend/ms-reservas/Dockerfile
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/config/OpenApiConfig.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/config/
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/dto/ErrorResponse.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/dto/
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/exception/ManejadorExcepciones.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/exception/
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/config/FiltroToken.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/config/
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/service/TokenService.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/service/
Copy-Item backend/ms-canchas/src/main/java/ec/ups/dae/canchas/config/SeguridadConfig.java backend/ms-reservas/src/main/java/ec/ups/dae/reservas/config/
```

**Prohibido** en estas tareas: escribir desde cero cualquiera de los siete archivos, o
cambiar el algoritmo, el parseo o el manejo de errores del token. No se modifica nada dentro
de `backend/ms-usuarios`, y de `backend/ms-canchas` **solo** lo que autoriza T5.

---

**Precondicion comun de T5 a T9 — `postgres` en estado `healthy`.** El healthcheck solo pasa
cuando el ultimo script del init ya cargo el seed. Antes de lanzar cualquier `curl`:

```powershell
docker compose ps
```

Esperado: `postgres` en `Up (healthy)`, y `ms-usuarios`, `ms-canchas` y `ms-reservas` en
`Up`. Si `postgres` aparece como `starting` o `unhealthy`, se espera y se repite.

**Tokens de ADMIN y de USUARIO.** A partir de T5 hacen falta tokens reales, que emite
`ms-usuarios` con las claves documentadas en la cabecera de `infra/postgres/05-seed.sql`:

```powershell
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"usuario@canchas.ec\",\"password\":\"Usuario123\"}"
```

En los comandos de verificacion, `<TOKEN_ADMIN>` y `<TOKEN_USUARIO>` se reemplazan por el
campo `token` de cada respuesta. Los tres servicios comparten `JWT_SECRET`, asi que
`ms-reservas` valida localmente el token emitido por `ms-usuarios` (design §5.1).

**Fechas de prueba.** Donde un comando diga `<HOY>` o `<MANANA>` se escribe la fecha real en
formato `AAAA-MM-DD`. Las reservas se prueban con `<MANANA>` porque D-03 prohibe reservar el
pasado.

---

## T1 — Esqueleto Maven, imagen y servicio en compose

**Que hace.** Descarga el esqueleto de Spring Initializr por URL (nunca `mvn` en el host,
§2.1 del requirements) en `backend/ms-reservas` con `groupId` `ec.ups.dae`, `artifactId`
`ms-reservas`, paquete `ec.ups.dae.reservas`, Java 21 y los starters `web`, `data-jpa`,
`validation`, `security` y el driver `postgresql`. Corrige el `<parent>` a **Spring Boot
3.5.3** y agrega a mano `springdoc-openapi-starter-webmvc-ui` 2.8.6 y los tres artefactos
`io.jsonwebtoken` 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Escribe la configuracion
de la tabla del design §8: datasource de `reservas_db`, `ddl-auto=validate`,
`server.port=8080`, `jwt.secret`, `mscanchas.url`, los dos timeouts, `reservas.max-activas`
y la duracion del token de servicio. **Copia** el `Dockerfile` y `OpenApiConfig` desde
`backend/ms-canchas` segun la regla de reutilizacion, y agrega el servicio `ms-reservas` a
`docker-compose.yml` con `8084:8080` temporal, `depends_on: postgres` con
`condition: service_healthy` y `ms-canchas`, y las variables `MS_CANCHAS_URL` y
`RESERVAS_MAX_ACTIVAS`.

**Instruccion literal.** El `Dockerfile` **se copia** de `backend/ms-canchas/Dockerfile` y se
adapta **solo** en el nombre del `.jar`. **No se reescribe desde cero.** `RESERVAS_MAX_ACTIVAS`
ya existe en `.env` y `.env.example` con valor `3` (S-13): no se crea ni se cambia su valor.

**Cubre.** E-01, E-09; §2.1 y §2.2 del requirements; §8 del design.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
docker compose logs --tail=50 ms-reservas
```

Esperado: el `package` termina sin error y el log muestra el arranque de Spring Boot en el
puerto 8080.

---

## T2 — Entidad, enum y repositorio

**Que hace.** Crea `entity/Reserva` mapeada a la tabla existente segun la tabla de mapeo del
design §2, con `usuarioId` y `canchaId` como columnas `Long` simples y sin asociaciones
(D-02); el enum `entity/EstadoReserva` con los tres valores de `ck_reserva_estado`
persistido como `STRING` (D-04); y `repository/ReservaRepository` con los seis metodos del
design §2, incluido el unico `@Query` JPQL `contarActivas` (D-05). **No modifica ningun
archivo de `infra/postgres/`.**

**Cubre.** HU-10, E-02; RN-01, RN-02, RN-06, RN-08 en su parte de persistencia; decisiones
D-02, D-04, D-05.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
docker compose logs --tail=50 ms-reservas
docker compose exec postgres psql -U reservas_user -d reservas_db -c "\d reserva"
```

Esperado: arranque sin `SchemaManagementException` y la descripcion de la tabla coincidiendo
con el mapeo. Si `ddl-auto=validate` se queja, se corrige la entidad, **nunca el DDL**.

---

## T3 — DTOs, mapper manual y manejo de excepciones

**Que hace.** Crea `dto/ReservaRequest`, `dto/ReservaResponse`, `dto/DisponibilidadResponse`,
`dto/BloqueResponse`, `dto/CanchaExterna` y `dto/BloqueoExterno` con las validaciones
`jakarta.validation` de las tablas del design §3 (fechas y horas como `String` con
`@Pattern`, D-11); `mapper/ReservaMapper` manual con formateo y parseo estricto de `HH:mm` y
`AAAA-MM-DD` y con el **calculo de `estado`** de la tabla del design §6.6 (D-15); las
excepciones `ReservaNoEncontradaException`, `ReservaAjenaException`,
`CanchaNoEncontradaException`, `BloqueOcupadoException`, `LimiteReservasException`,
`ReservaPasadaException`, `ReservaNoCancelableException`, `BloqueInvalidoException`,
`FechaPasadaException`, `FormatoInvalidoException` y `CatalogoNoDisponibleException`; y
**copia** `dto/ErrorResponse` y `exception/ManejadorExcepciones` desde `backend/ms-canchas`
segun la regla de reutilizacion, adaptando el manejador a la tabla completa del design §7.
Sin controladores todavia.

**Instruccion literal.** `ErrorResponse` y `ManejadorExcepciones` **se copian**; no se
reescriben desde cero. Los codigos `409` usados son exactamente `BLOQUE_OCUPADO`,
`LIMITE_RESERVAS`, `RESERVA_PASADA` y `RESERVA_NO_CANCELABLE`, los del contrato.

**Cubre.** HU-06, HU-09, E-07; RN-08; decisiones D-11, D-15, D-16.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
docker compose logs --tail=50 ms-reservas
```

Esperado: compila y arranca sin error.

---

## T4 — Seguridad: validacion del token entrante y emisor del token de servicio

**Que hace.** **Copia** `config/FiltroToken`, `service/TokenService` y
`config/SeguridadConfig` desde `backend/ms-canchas` segun la regla de reutilizacion, con las
tres adaptaciones ya descritas: rechazo de `rol = SERVICIO` en la entrada (S-12) y la matriz
de rutas del design §5.3. Ademas crea `service/EmisorTokenServicio`, que emite el JWT `HS256`
con `rol = SERVICIO`, **sin** claim `sub` y con `exp` de 5 minutos, firmado con el mismo
`JWT_SECRET`, uno nuevo por llamada y sin cache (D-01, D-13). Todavia no hay controladores ni
cliente HTTP: el emisor queda listo para T6.

**Cubre.** HU-08, E-05, E-06; §5.1, §5.2 y §5.3 del design; decisiones D-13, D-14, D-17;
supuesto S-12.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
docker compose logs --tail=50 ms-reservas
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8084/v3/api-docs
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas
```

Esperado: `200` en `/v3/api-docs` (ruta publica) y `401` con
`{"codigo":"NO_AUTENTICADO",...}` en `/api/reservas` sin token. Las rutas todavia no existen
como controladores: lo que se comprueba aqui es la cadena de filtros.

---

## T5 — `ms-canchas` acepta el rol `SERVICIO` (solo lectura)

**Que hace.** Unica tarea que toca `backend/ms-canchas`, con el alcance exacto del design
§5.5 y el requirements §8: `config/FiltroToken` acepta `rol = SERVICIO` como autoridad valida
y no exige el claim `sub` para ese rol; `config/SeguridadConfig` admite `SERVICIO` en las tres
rutas `GET` y mantiene `hasRole("ADMIN")` en las cinco de escritura, de modo que un `SERVICIO`
recibe `403 SIN_PERMISO`; y el filtrado por rol de `service/CanchaService` trata a `SERVICIO`
como al `ADMIN`: ve todas las canchas y una inactiva responde `200`, no `404`. **No se toca
ningun endpoint, entidad, DTO, mapper, repositorio ni regla de negocio de `ms-canchas`.**

**Cubre.** HU-07, E-10; cierra el asunto A-01 de la spec 03; decisiones D-01 y D-18.

**Verificacion.** Regresion de la spec 03: el comportamiento de `ADMIN` y `USUARIO` debe
quedar identico.

```powershell
docker compose up -d --build ms-canchas
docker compose logs --tail=50 ms-canchas
curl.exe -s -w "`n%{http_code}`n" http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"nombre\":\"X\",\"deporte\":\"PADEL\",\"horaApertura\":\"07:00\",\"horaCierre\":\"22:00\"}"
```

Esperado: el ADMIN sigue viendo las cuatro canchas, el USUARIO solo las activas, y el
USUARIO sigue recibiendo `403 SIN_PERMISO` en el alta.

**Limitacion declarada.** Las dos comprobaciones propias del rol `SERVICIO` —`200` sobre una
cancha inactiva y `403 SIN_PERMISO` en una ruta de escritura— **no se pueden ejecutar en esta
tarea**: no hay forma de emitir un token `SERVICIO` sin `mvn` ni `java` en el host, y el
emisor de T4 todavia no tiene quien lo invoque. Ambas se verifican en T6, que es la primera
tarea que dispara una llamada real con ese token.

---

## T6 — Cliente HTTP hacia `ms-canchas` y disponibilidad (HU-01)

**Que hace.** Crea `config/ClienteHttpConfig` con el `RestClient` apuntando a
`MS_CANCHAS_URL`, con 2 s de timeout de conexion y 5 s de lectura y **sin reintentos**
(D-06, D-12); `service/CanchasClient`, unico punto que llama a `ms-canchas`, que adjunta el
token de `EmisorTokenServicio`, deserializa `CanchaExterna` y `BloqueoExterno`, traduce el
`404` recibido a `CanchaNoEncontradaException` y todo `5xx`, `401`, `403` o timeout a
`CatalogoNoDisponibleException` (D-08); `service/DisponibilidadService` con el algoritmo de
bloques del design §6.1, incluido el corto circuito de la cancha inactiva (D-09); y el
`ReservaController` con **solo** `GET /api/reservas/disponibilidad`.

**Cubre.** HU-01, HU-07, E-04; RN-01, RN-02, RN-05 en su parte de lectura; decisiones D-06,
D-08, D-09, D-12.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=4&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=99&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=24-08-2026" -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: `canchaId=1` devuelve `200` con 15 bloques de `07:00` a `22:00`, todos
`disponible: true`; `canchaId=4` (`Padel 2`, `08:00`–`21:00`) devuelve 13 bloques, lo que
prueba que el horario sale de cada cancha y no de un valor fijo; `canchaId=99` responde
`404 NO_ENCONTRADO`; y la fecha mal formada, `400 DATOS_INVALIDOS`.

**Verificacion diferida de T5.** En esta tarea se comprueba ademas, ya con el token de
servicio circulando de verdad:

```powershell
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8083/api/canchas/4/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activa\":false}"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=4&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8083/api/canchas/4/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activa\":true}"
```

Esperado: con `Padel 2` inactiva, la disponibilidad responde `200` con los 13 bloques en
`disponible: false` — prueba de que el token `SERVICIO` ve una cancha que el `USUARIO` no
veria (D-05, D-09). La tercera llamada devuelve la cancha a su estado original.

---

## T7 — Alta de reserva (HU-02)

**Que hace.** Implementa `POST /api/reservas` en `ReservaController` y `ReservaService` con
el flujo del design §6.2 en su orden exacto: validaciones `400` primero —hora en punto,
fecha no pasada (D-03), bloque dentro del horario de atencion—, luego `404` de cancha
inexistente o inactiva (D-05), y por ultimo los tres `409` en el orden de **D-19**: bloque
ocupado (consulta local, RN-02), limite `RESERVAS_MAX_ACTIVAS` (consulta local, RN-06) y
bloqueo de mantenimiento (llamada HTTP, D-07). Incluye la segunda barrera de RN-02:
`DataIntegrityViolationException` sobre `ux_reserva_bloque_confirmada` traducida al mismo
`409 BLOQUE_OCUPADO` (D-03 del design). El `usuarioId` sale del claim `sub` y `horaFin` lo
calcula el servicio.

**Cubre.** HU-02; RN-01, RN-02, RN-06; decisiones D-03, D-07, D-08, D-19 del design y D-03,
D-04, D-05, D-07, D-11 del requirements.

**Verificacion.**

```powershell
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"09:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"09:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"09:30\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"2020-01-01\",\"horaInicio\":\"09:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"22:00\"}"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
```

Esperado, en orden: `201` con `estado: "CONFIRMADA"` y `horaFin: "10:00"`; `409
BLOQUE_OCUPADO` en el mismo bloque (RN-02); `400 DATOS_INVALIDOS` por hora fuera de punto;
`400 DATOS_INVALIDOS` por fecha pasada (D-03); `400 DATOS_INVALIDOS` por bloque fuera del
horario de atencion; y la disponibilidad con `09:00`–`10:00` ya en `disponible: false`.

El limite de RN-06 se comprueba creando tres reservas futuras del mismo usuario en bloques
distintos y verificando que la cuarta responde `409 LIMITE_RESERVAS`.

---

## T8 — Listados, cancelacion y documentacion OpenAPI (HU-03, HU-04, HU-05)

**Que hace.** Implementa las tres rutas restantes con los flujos del design §6.3, §6.4 y
§6.5: `GET /api/reservas/mias` (historial propio, D-09 del requirements),
`GET /api/reservas` (listado global, solo `ADMIN`) y
`PATCH /api/reservas/{id}/cancelacion` con el orden de comprobaciones de **D-10 del
design**: propiedad (`403 SIN_PERMISO`, RN-03), reserva pasada (`409 RESERVA_PASADA`, RN-04)
y estado no cancelable (`409 RESERVA_NO_CANCELABLE`, D-10 del requirements). Ambos listados
ordenan por `fecha` y `horaInicio` descendente y devuelven el `estado` calculado de HU-06.
Completa la documentacion `springdoc-openapi` de los cinco endpoints con sus codigos de
error, dejando constancia de que `FINALIZADA` es un estado derivado y no persistido.

**Cubre.** HU-03, HU-04, HU-05, HU-06, E-03, E-08; RN-03, RN-04, RN-05, RN-08; consecuencia
C-02; decisiones D-09 y D-10 del requirements y D-10 y D-15 del design.

**Verificacion.** Con la reserva creada en T7 y su `id` en `<ID>`:

```powershell
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas/mias -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8084/api/reservas/<ID>/cancelacion -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8084/api/reservas/<ID>/cancelacion -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8084/api/reservas/999999/cancelacion -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8084/v3/api-docs
```

Esperado, en orden: el historial del USUARIO con sus reservas ordenadas de la mas reciente a
la mas antigua; el listado global con `200` para el ADMIN; `403 SIN_PERMISO` para el USUARIO
en el listado global; `200` con `estado: "CANCELADA"` en la primera cancelacion (RN-03);
`409 RESERVA_NO_CANCELABLE` al repetirla (D-10); `404 NO_ENCONTRADO` en un `id` inexistente;
la disponibilidad con el bloque `09:00`–`10:00` otra vez en `disponible: true` (RN-05); y
`200` en el documento OpenAPI con los cinco endpoints.

RN-03 se comprueba ademas cancelando con `<TOKEN_USUARIO>` una reserva creada por otro
usuario: debe responder `403 SIN_PERMISO`. RN-04 y la precedencia C-02 se comprueban sobre
una reserva ya ocurrida, que debe responder `409 RESERVA_PASADA`.

---

## T9 — Fallo de dependencia y prueba end-to-end de RN-02 / RN-05

**Que hace.** **No escribe codigo.** Ejecuta y documenta con salida real dos escenarios que
ninguna tarea anterior cubre completa, y deja la base limpia. Es la tarea de evidencia para
la demo en vivo.

**Cubre.** D-06 (fallo de dependencia), RN-02, RN-05, HU-01, HU-02, HU-05, y la evidencia de
**independencia entre microservicios** exigida por el documento de alcance §4.2.

### a) Fallo de dependencia (D-06)

```powershell
docker compose stop ms-canchas
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"11:00\"}"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas/mias -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_ADMIN>"
docker compose start ms-canchas
docker compose ps
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
```

Esperado, en orden:

| Llamada | Codigo | Cuerpo |
|---|---|---|
| Disponibilidad con `ms-canchas` caido | `500` | `{"codigo":"ERROR_INTERNO","mensaje":"No se pudo consultar el catalogo de canchas"}` |
| Alta con `ms-canchas` caido | `500` | El mismo cuerpo; **no** se crea ninguna fila |
| `GET /api/reservas/mias` | `200` | Historial normal: **no depende de `ms-canchas`** |
| `GET /api/reservas` (ADMIN) | `200` | Listado global normal |
| Disponibilidad tras `docker compose start` | `200` | Los 15 bloques otra vez |

Las dos filas `200` son las importantes: demuestran que `ms-reservas` sigue operando
parcialmente con una dependencia caida, que es la evidencia de independencia entre
microservicios para la demo en vivo. Los dos `500` demuestran que el fallo se traduce al
mensaje fijo de D-06 y nunca deja escapar la excepcion del cliente HTTP.

Antes de repetir la disponibilidad se comprueba con `docker compose ps` que `ms-canchas`
volvio a `Up`; si aparece como `starting`, se espera y se repite. El alta con la dependencia
caida debe confirmarse tambien contra la base: la fila no existe.

```powershell
docker compose exec postgres psql -U reservas_user -d reservas_db -c "SELECT count(*) FROM reserva WHERE hora_inicio = '11:00'"
```

Esperado: `0`.

### b) Ciclo completo RN-02 / RN-05 por API, no por SQL

Cada paso va acompanado de la disponibilidad para ver el bloque cambiar de estado. Se usa
el bloque `15:00`–`16:00` de `canchaId=1` en `<MANANA>`, libre hasta aqui.

```powershell
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"15:00\"}"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"15:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8084/api/reservas/<ID_15>/cancelacion -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"15:00\"}"
```

`<ID_15>` es el `id` devuelto por el primer `201`. Esperado, en orden:

| Paso | Resultado |
|---|---|
| Disponibilidad inicial | `200`, bloque `15:00`–`16:00` con `disponible: true` |
| Alta sobre bloque libre | `201`, `estado: "CONFIRMADA"`, `horaFin: "16:00"` |
| Disponibilidad | `200`, el mismo bloque ahora en `disponible: false` (RN-02) |
| Alta sobre el mismo bloque | `409 BLOQUE_OCUPADO` (RN-02) |
| Cancelacion | `200`, `estado: "CANCELADA"` |
| Disponibilidad | `200`, el bloque vuelve a `disponible: true` (RN-05) |
| Alta sobre el mismo bloque | `201`, `estado: "CONFIRMADA"` — el bloque quedo realmente liberado (RN-05) |

Todo el ciclo se ejecuta **por la API**, nunca con `INSERT` ni `UPDATE` en la base: lo que
se prueba es la regla del servicio, no la del DDL.

### c) Estado final documentado y limpieza

Se documenta el estado de `reservas_db` antes de limpiar, para que la evidencia quede en la
bitacora:

```powershell
docker compose exec postgres psql -U reservas_user -d reservas_db -c "SELECT id, usuario_id, cancha_id, fecha, hora_inicio, hora_fin, estado FROM reserva ORDER BY id"
docker compose exec postgres psql -U reservas_user -d reservas_db -c "SELECT estado, count(*) FROM reserva GROUP BY estado"
```

Se espera que en la columna `estado` aparezcan **solo** `CONFIRMADA` y `CANCELADA`: ninguna
fila con `FINALIZADA`, que es la comprobacion en base de la decision D-02.

Limpieza de las reservas de prueba creadas en T7 y T9:

```powershell
docker compose exec postgres psql -U reservas_user -d reservas_db -c "DELETE FROM reserva"
docker compose exec postgres psql -U reservas_user -d reservas_db -c "SELECT count(*) FROM reserva"
```

Esperado: `0`. La tabla `reserva` queda vacia, igual que la deja el seed.

**No se toca `Padel 2` (`canchaId = 4`)**: la bitacora de la spec 03 la conserva a proposito
por su horario distinto, y T6 la devolvio a `activa = true`. Antes de cerrar la tarea se
confirma:

```powershell
curl.exe -s http://localhost:8083/api/canchas/4 -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: `Padel 2`, `08:00`–`21:00`, `activa: true`.

---

## T10 — `NoResourceFoundException` a `404 NO_ENCONTRADO` en los tres microservicios

**Que hace.** Corrige el asunto abierto **A-02** del requirements §6.3: hoy una peticion
autenticada a una ruta que no existe responde `500 ERROR_INTERNO`, porque
`NoResourceFoundException` no tiene manejador propio y cae en la red de seguridad
`@ExceptionHandler(Exception.class)`. Debe responder `404 NO_ENCONTRADO`.

Agrega el manejador de `NoResourceFoundException` al `ManejadorExcepciones` de
**`ms-usuarios`, `ms-canchas` y `ms-reservas`**, con el codigo `NO_ENCONTRADO` del contrato,
y la propiedad `spring.mvc.throw-exception-if-no-handler-found` —o su equivalente segun la
version de Spring Boot— donde haga falta para que la excepcion llegue al `@RestControllerAdvice`.

**Por que va al final.** Toca dos microservicios ya cerrados (specs 02 y 03) y es un cambio
transversal. Hacerlo antes mezclaria esa correccion con la implementacion de `ms-reservas` y
ensuciaria la trazabilidad de la spec (decision del responsable, 23/08/2026).

**Cubre.** A-02 del requirements §6.3. No cubre ninguna HU ni RN nueva: es coherencia del
formato de error (HU-09 y su equivalente en las specs 02 y 03).

**Alcance exacto.** Solo el manejador nuevo y, si aplica, la propiedad de configuracion. **No
se toca** ningun endpoint, entidad, DTO, mapper, repositorio, regla de negocio ni la matriz
de rutas de ninguno de los tres servicios.

**Verificacion.**

```powershell
docker compose up -d --build ms-usuarios ms-canchas ms-reservas
curl.exe -s -w "`n%{http_code}`n" http://localhost:8082/api/ruta-que-no-existe -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8083/api/ruta-que-no-existe -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/ruta-que-no-existe -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado en los tres puertos: `404` con
`{"codigo":"NO_ENCONTRADO","mensaje":"..."}`, nunca `500`.

**Regresion obligatoria.** Despues del cambio, las rutas que si existen deben seguir
respondiendo igual en los tres servicios:

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>"
```

Esperado: `200`, `200` y `403`.
