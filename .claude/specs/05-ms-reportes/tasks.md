# Spec 05 — ms-reportes · tasks.md

Base: `requirements.md` (C1 aprobado 23/08/2026) y `design.md` (C2 aprobado 23/08/2026).

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado literal y se espera aprobacion. Ninguna tarea encadena
la siguiente. Cada tarea deja el proyecto compilando y los servicios arrancando.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay JDK, Maven ni psql: todo pasa por Docker
(`CLAUDE.md` §1). Se usa `curl.exe`, no `curl`, y `Copy-Item`, no `cp`.

Atajo usado en todas las tareas — compilar el microservicio. El volumen `m2repo` montado en
`/root/.m2` cachea las dependencias entre corridas:

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
```

## Regla de reutilizacion desde `ms-reservas` (T1, T2 y T3)

Cinco archivos **se copian** desde `backend/ms-reservas`, no se reescriben desde cero. Es la
misma regla que la spec 04 aplico sobre `ms-canchas` (design D-17 de esa spec): si cuatro
servicios validan el mismo JWT con codigo distinto, la diferencia solo aparece en la
integracion.

| Archivo origen (en `backend/ms-reservas`) | Destino | Tarea | Adaptacion permitida |
|---|---|---|---|
| `Dockerfile` | `backend/ms-reportes/Dockerfile` | T1 | Solo el nombre del `.jar`: `ms-reportes-0.0.1-SNAPSHOT.jar` |
| `config/OpenApiConfig.java` | `config/OpenApiConfig.java` | T1 | Paquete, `import` y el titulo del documento |
| `dto/ErrorResponse.java` | `dto/ErrorResponse.java` | T2 | Solo el paquete |
| `exception/ManejadorExcepciones.java` | `exception/ManejadorExcepciones.java` | T2 | Paquete, `import` y la tabla de excepciones (ver abajo) |
| `service/TokenService.java` | `service/TokenService.java` | T3 | Solo el paquete y los `import` |
| `config/FiltroToken.java` | `config/FiltroToken.java` | T3 | Solo el paquete y los `import`: el rechazo del rol `SERVICIO` se conserva **tal cual** (P-11) |
| `config/SeguridadConfig.java` | `config/SeguridadConfig.java` | T3 | Paquete, `import` y la matriz de rutas |
| `service/EmisorTokenServicio.java` | `service/EmisorTokenServicio.java` | T5 | Paquete, `import` y el nombre de la propiedad de duracion |
| `config/ClienteHttpConfig.java` | `config/ClienteHttpConfig.java` | T5 | Paquete, `import` y el segundo bean `RestClient` (D-13) |

Adaptaciones permitidas, dejando intacto todo lo demas:

- **`ManejadorExcepciones`**: se conservan la estructura, el formato `ErrorResponse` y los
  manejadores de `MethodArgumentTypeMismatchException`,
  `HttpRequestMethodNotSupportedException`, `MissingServletRequestParameterException`,
  `NoResourceFoundException` y `Exception`. Se **eliminan** los manejadores de las
  excepciones de dominio de reservas y el de `DataIntegrityViolationException` (no hay base
  de datos), y se **agregan** `RangoInvalidoException`, `CatalogoNoDisponibleException` y
  `ReservasNoDisponiblesException` segun la tabla del design §6.
- **`SeguridadConfig`**: se conservan la cadena `STATELESS`, CSRF/`httpBasic`/`formLogin`
  desactivados, el `permitAll()` de las tres rutas de springdoc, el `addFilterBefore` y los
  dos manejadores que escriben `401 NO_AUTENTICADO` y `403 SIN_PERMISO`. La matriz de rutas
  se reduce a una sola regla: `/api/reportes/**` con `hasRole("ADMIN")`.
- **`ClienteHttpConfig`**: se duplica el bean para tener `clienteCanchas` y
  `clienteReservas`, cada uno con su `baseUrl` (design D-13).

**Prohibido** en estas tareas: escribir desde cero cualquiera de esos archivos, o cambiar el
algoritmo, el parseo o el manejo de errores del token. De `backend/ms-usuarios` y
`backend/ms-canchas` **no se toca nada**; de `backend/ms-reservas`, solo lo que autoriza T4.

---

**Precondicion comun de T3 en adelante — `postgres` en estado `healthy`.**

```powershell
docker compose ps
```

Esperado: `postgres` en `Up (healthy)` y `ms-usuarios`, `ms-canchas` y `ms-reservas` en `Up`.

**Tokens de ADMIN y de USUARIO.** Se emiten con las claves documentadas en la cabecera de
`infra/postgres/05-seed.sql`:

```powershell
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"usuario@canchas.ec\",\"password\":\"Usuario123\"}"
```

En los comandos, `<TOKEN_ADMIN>` y `<TOKEN_USUARIO>` se reemplazan por el campo `token` de
cada respuesta. Los cuatro servicios comparten `JWT_SECRET`.

**Fechas.** Donde un comando diga `<HOY>` o `<MANANA>` se escribe la fecha real en formato
`AAAA-MM-DD`.

**Estado del entorno al empezar.** Segun `docs/bitacora.md`: la tabla `reserva` esta
**vacia**, `bloqueo_mantenimiento` tambien, y `canchas_db` tiene **cuatro** canchas: las tres
del seed (`07:00`–`22:00`) y `Padel 2` (`canchaId = 4`, `08:00`–`21:00`), activa. Esa cuarta
cancha es la que prueba que `horasDisponibles` usa el horario real de cada una.

---

## T1 — Esqueleto Maven, imagen y servicio en compose

**Que hace.** Descarga el esqueleto de Spring Initializr por URL (nunca `mvn` en el host) en
`backend/ms-reportes` con `groupId` `ec.ups.dae`, `artifactId` `ms-reportes`, paquete
`ec.ups.dae.reportes`, Java 21 y los starters **`web` y `security` unicamente**. Corrige el
`<parent>` a **Spring Boot 3.5.3** y agrega a mano `springdoc-openapi-starter-webmvc-ui`
2.8.6 y los tres artefactos `io.jsonwebtoken` 0.12.6. **Sin** `data-jpa`, **sin** el driver
`postgresql` y **sin** `validation` (design D-01 y §4.3). Escribe
`application.properties` con la tabla del design §9: `server.port=8080`, `jwt.secret`,
`reportes.token-servicio.duracion`, las dos URLs y los cuatro timeouts, **sin ninguna
propiedad `spring.datasource.*` ni `spring.jpa.*`**. Copia el `Dockerfile` y `OpenApiConfig`
segun la regla de reutilizacion, y agrega el servicio `ms-reportes` a `docker-compose.yml`
con `8085:8080`, las tres variables de entorno y `depends_on` de `ms-canchas` y
`ms-reservas` con `condition: service_started`, **sin** `depends_on` de `postgres`.

**Cubre.** HU-08, E-01, E-11; decision D-01, P-10.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
docker compose logs --tail=50 ms-reportes
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8085/v3/api-docs
```

Esperado: compila; el contenedor queda `Up`; y el log **no** menciona `HikariPool` ni
`DataSource` —prueba de que no hay base de datos, que es lo que esta tarea demuestra
(D-01)—.

`/v3/api-docs` responde **`401`**, no `200`. **No es un defecto**: quien abre esa ruta sin
token es el `permitAll()` de `SeguridadConfig`, que se copia en **T3**. Mientras esa clase no
exista, Spring Security protege todo por defecto, y el log lo confirma con la linea
`Using generated security password: ...`. El `200` sobre `/v3/api-docs` se verifica en T3,
donde ya es la prueba de que springdoc quedo abierto.

El log muestra ademas un `INFO` de `OptionalValidatorFactoryBean` diciendo que no encontro un
proveedor de Bean Validation. Tambien es lo esperado: `spring-boot-starter-validation` se
excluyo a proposito porque no hay cuerpo de peticion que validar (design §4.3).

---

## T2 — DTOs, excepciones y manejador de errores

**Que hace.** Crea los siete DTOs de salida del design §4.1 (`ReporteOcupacionResponse`,
`OcupacionItem`, `ReporteReservasResponse`, `ReservasItem`, `ReporteCancelacionesResponse`,
`CancelacionesItem`, `ErrorResponse`) y los dos de entrada del §4.2 (`CanchaExterna`,
`ReservaExterna`), todos como `record` sin Lombok y con los nombres verificados en el design
§4.4. Crea las tres excepciones propias (`RangoInvalidoException`,
`CatalogoNoDisponibleException`, `ReservasNoDisponiblesException`) y copia y adapta
`ManejadorExcepciones` segun la regla de reutilizacion.

**Cubre.** HU-07; contrato §6.2, §6.3 y §6.4; decisiones D-11, D-12.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
docker compose logs --tail=20 ms-reportes
curl.exe -s -w "`n%{http_code}`n" http://localhost:8085/api/reportes/inexistente
```

Esperado: compila y el contenedor arranca con los nueve DTOs y el `@RestControllerAdvice`
cargados.

La ruta inexistente responde **`401`**, no `404`, **por la misma razon que en T1**: sin
`SeguridadConfig` —que llega en T3— Spring Security exige autenticacion en todo, y la
peticion ni siquiera alcanza al `ManejadorExcepciones`. La prueba real del
`404 NO_ENCONTRADO` con el cuerpo `{"codigo":"NO_ENCONTRADO","mensaje":"El recurso
solicitado no existe"}` **se difiere a T3**, donde una ruta inexistente con token de ADMIN ya
atraviesa la cadena y llega al manejador.

Esta tarea, por tanto, se verifica por compilacion y arranque limpio. Es la consecuencia de
copiar la seguridad despues de las excepciones; el orden inverso obligaria a tocar
`SeguridadConfig` dos veces.

---

## T3 — Seguridad: solo ADMIN entra

**Que hace.** Copia `TokenService`, `FiltroToken` y `SeguridadConfig` desde `ms-reservas`
segun la regla de reutilizacion. `FiltroToken` conserva **sin cambios** el rechazo del token
con `rol = SERVICIO` (P-11). `SeguridadConfig` deja `permitAll()` en las tres rutas de
springdoc y una unica regla de negocio: `/api/reportes/**` con `hasRole("ADMIN")`.

**Cubre.** HU-06, E-08; design §5.2.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8085/v3/api-docs
curl.exe -s -w "`n%{http_code}`n" http://localhost:8085/api/reportes/ocupacion
curl.exe -s -w "`n%{http_code}`n" http://localhost:8085/api/reportes/ocupacion -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8085/api/reportes/ocupacion -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: `/v3/api-docs` sigue en `200` sin token; sin token, `401 NO_AUTENTICADO`; con
token de USUARIO, `403 SIN_PERMISO`; con token de ADMIN, `404 NO_ENCONTRADO`, porque la
cadena ya lo deja pasar pero el controlador todavia no existe. Ese `404` es la prueba de que
el ADMIN atraviesa la seguridad.

---

## T4 — Abrir `GET /api/reservas` al rol `SERVICIO` en `ms-reservas`

**Que hace.** Unico cambio de codigo fuera de `backend/ms-reportes`, con el alcance exacto
del design §8, que **esta prohibido ampliar**. En `backend/ms-reservas`:

- `config/FiltroToken.java`: deja de descartar el token con `rol = SERVICIO`; lo autentica
  con la authority `ROLE_SERVICIO` y **principal nulo**, porque no trae claim `sub`.
- `config/SeguridadConfig.java`: `GET /api/reservas` pasa de `hasRole("ADMIN")` a
  `hasAnyRole("ADMIN", "SERVICIO")`, y las **otras cuatro** rutas pasan de `.authenticated()`
  a `hasAnyRole("ADMIN", "USUARIO")`. Este segundo cambio es obligatorio: sin el, un token
  `SERVICIO` recien autenticado atravesaria `.authenticated()` y
  `POST /api/reservas` crearia una reserva con `usuarioId` nulo.

No se toca ninguna entidad, DTO, mapper, repositorio, regla de negocio ni endpoint de
`ms-reservas`. `ms-canchas` no se toca en absoluto.

**Cubre.** Decision P-01 del requirements §8.1 y design §8 y D-16; entregable E-12.

**Verificacion.** Primero la regresion: `ADMIN` y `USUARIO` deben comportarse **igual que
antes**.

```powershell
docker run --rm -v "${PWD}/backend/ms-reservas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reservas
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" http://localhost:8084/api/reservas/mias -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8084/api/reservas/disponibilidad?canchaId=1&fecha=<MANANA>" -H "Authorization: Bearer <TOKEN_USUARIO>"
```

Esperado, identico a como cerro la spec 04: ADMIN `200` con arreglo vacio, USUARIO `403` en
el listado global, `200` en `/mias` y `200` con los 15 bloques en disponibilidad.

**Limitacion declarada.** Las cuatro comprobaciones propias del rol `SERVICIO` —`200` en
`GET /api/reservas` y `403 SIN_PERMISO` en las otras cuatro rutas— **no se pueden ejecutar
aqui**: no hay `java` ni `mvn` en el host para acuñar un token `SERVICIO`, y `ms-reportes`
todavia no tiene quien lo emita. Se verifican en **T5**, que es la primera tarea que dispara
una llamada real con ese token.

Si se quiere adelantar la comprobacion, el token se puede acuñar dentro del contenedor con
`openssl`, como se hizo en la T5 de la spec 04, sin que el secreto salga de ahi. Si ese
camino falla dos veces, **no se insiste**: la verificacion queda para T5.

---

## T5 — Clientes HTTP y reporte de reservas (HU-02)

**Que hace.** Copia y adapta `EmisorTokenServicio` y `ClienteHttpConfig` segun la regla de
reutilizacion, este ultimo con los **dos** beans `RestClient` (`clienteCanchas` y
`clienteReservas`), cada uno con su `baseUrl` y con 2 s / 5 s sin reintentos. Crea
`client/CanchasClient` (`GET /api/canchas`) y `client/ReservasClient` (`GET /api/reservas`),
unicos puntos que hacen HTTP saliente, que adjuntan el token de servicio y traducen `5xx`,
`401`, `403` y timeout a `CatalogoNoDisponibleException` y `ReservasNoDisponiblesException`
(D-08). Crea `ReporteService` con el parseo estricto del rango (D-04), el filtro por rango y
por estado, el cruce por `canchaId` (D-15), `ReporteMapper`, y `ReporteController` con
**solo** `GET /api/reportes/reservas`.

**Cubre.** HU-02, HU-04, HU-05, E-05, E-06, E-07; RN-01, RN-08; decisiones D-02, D-03, D-04,
D-06, D-07, D-09, D-10, D-12, D-13, D-14, D-15.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=<HOY>&hasta=<HOY>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=<HOY>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=2026-08-31&hasta=2026-08-01" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=31-08-2026&hasta=2026-08-31" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=2026-02-30&hasta=2026-03-01" -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: la primera responde `200` con `desde` y `hasta` iguales a lo enviado y las
**cuatro** canchas en `items`, todas con `totalReservas: 0` (la tabla esta vacia) — prueba de
P-09. Las cuatro siguientes, `400 DATOS_INVALIDOS`: parametro ausente, `desde` posterior a
`hasta`, formato invertido y fecha inexistente.

**Verificacion diferida de T4**, ya con el token de servicio circulando de verdad: que la
primera llamada responda `200` **es** la prueba de que `ms-reservas` acepta el rol `SERVICIO`
en `GET /api/reservas`. Falta el otro lado del cambio:

```powershell
docker compose logs --tail=30 ms-reservas
```

Esperado: el log muestra la peticion `GET /api/reservas` atendida, sin `401` ni `403`. Para
comprobar que las otras cuatro rutas siguen cerradas al rol `SERVICIO` se acuña el token
dentro del contenedor con `openssl` y se prueban `POST /api/reservas` y
`GET /api/reservas/mias`, que deben responder `403 SIN_PERMISO`.

---

## T6 — Reporte de ocupacion (HU-01)

**Que hace.** Crea `service/CalculadoraOcupacion` con las tres formulas del design §3:
`diasDelRango` inclusivo, `horasDisponibles` por cancha con su propio horario de atencion y
sin restar bloqueos, y `porcentajeOcupacion` como `BigDecimal` con escala 1 y `HALF_UP`,
incluido el corto circuito de `horasDisponibles <= 0`. Agrega
`GET /api/reportes/ocupacion` al `ReporteController`. No se agrega ninguna llamada HTTP
nueva: reutiliza los dos clientes de T5.

**Cubre.** HU-01, E-02; RN-01, RN-05, RN-07; decisiones D-05, D-06, D-10.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/ocupacion?desde=<HOY>&hasta=<HOY>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/ocupacion?desde=<HOY>&hasta=<HOY+1>" -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado, con la tabla `reserva` vacia: la primera responde `200` con las cuatro canchas,
`horasReservadas: 0`, `porcentajeOcupacion: 0.0`, y `horasDisponibles: 15` en las tres del
seed y **13** en `Padel 2` — prueba de que el horario sale de cada cancha y no de un valor
fijo. La segunda, con un rango de dos dias, duplica ambas cifras a `30` y `26`: prueba de que
los dos extremos son inclusivos (P-07).

---

## T7 — Reporte de cancelaciones y documentacion OpenAPI (HU-03)

**Que hace.** Agrega `GET /api/reportes/cancelaciones` al `ReporteController`, contando solo
`estado = CANCELADA` y devolviendo `CancelacionesItem` **sin** el campo `deporte`, tal como
lo congela el contrato. Completa las anotaciones `@ApiResponse` de springdoc en los **tres**
endpoints, con sus codigos `400`, `401`, `403` y `500`.

**Cubre.** HU-03, HU-07, E-03, E-10; RN-08.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-reportes:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-reportes
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/cancelaciones?desde=<HOY>&hasta=<HOY>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s http://localhost:8085/v3/api-docs
```

Esperado: `200` con las cuatro canchas, `totalCancelaciones: 0`, y **ningun** campo
`deporte` en las filas. El documento OpenAPI declara las tres rutas de `/api/reportes` con
sus cuatro codigos de error cada una, y ninguna ruta de mas.

---

## T8 — Evidencia de integracion y de independencia

**Que hace.** Ninguna linea de codigo nuevo. Es la tarea de evidencia para la demo, analoga a
la T9 de la spec 04: demuestra que los tres reportes reflejan datos reales y que un fallo de
dependencia se comporta como manda el diseño.

Primero se crean reservas por API con el token de USUARIO —dos confirmadas y una que luego se
cancela, todas sobre `<MANANA>`— y se comprueban los tres reportes. Despues se apaga
`ms-canchas` y se comprueba el `500 ERROR_INTERNO`. Al terminar **se restaura el entorno**:
se vuelve a levantar `ms-canchas` y se borran las reservas de prueba, para dejar la tabla
`reserva` vacia como estaba.

**Cubre.** HU-01, HU-02, HU-03, HU-05, HU-07; RN-05, RN-08; decisiones D-05, D-08.

**Verificacion, parte 1 — datos reales:**

```powershell
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"09:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":1,\"fecha\":\"<MANANA>\",\"horaInicio\":\"10:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X POST http://localhost:8084/api/reservas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"canchaId\":4,\"fecha\":\"<MANANA>\",\"horaInicio\":\"09:00\"}"
curl.exe -s -w "`n%{http_code}`n" -X PATCH http://localhost:8084/api/reservas/3/cancelacion -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/ocupacion?desde=<MANANA>&hasta=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=<MANANA>&hasta=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/cancelaciones?desde=<MANANA>&hasta=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: `Padel 1` con `horasReservadas: 2`, `horasDisponibles: 15` y
`porcentajeOcupacion: 13.3` —que es `2/15 = 13.33…` redondeado a un decimal con `HALF_UP`,
la prueba de P-06—; `totalReservas: 2` en `Padel 1` y `0` en `Padel 2`, porque su unica
reserva se cancelo; y `totalCancelaciones: 1` en `Padel 2`. El `id` de la tercera reserva
puede no ser `3`: se toma el que devuelva su alta.

**Verificacion, parte 2 — dependencia caida:**

```powershell
docker compose stop ms-canchas
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/ocupacion?desde=<MANANA>&hasta=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
docker compose logs --tail=20 ms-reportes
docker compose start ms-canchas
```

Esperado: `500` con el cuerpo
`{"codigo":"ERROR_INTERNO","mensaje":"No se pudo consultar el catalogo de canchas"}`, **sin**
stacktrace y **sin** un reporte parcial con ceros (D-08). El detalle real de la causa aparece
solo en el log.

**Restauracion del entorno:**

```powershell
docker compose exec postgres psql -U reservas_user -d reservas_db -c "DELETE FROM reserva"
docker compose exec postgres psql -U reservas_user -d reservas_db -c "SELECT COUNT(*) FROM reserva"
curl.exe -s -w "`n%{http_code}`n" "http://localhost:8085/api/reportes/reservas?desde=<MANANA>&hasta=<MANANA>" -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado: `COUNT` en `0` y el reporte vuelve a mostrar las cuatro canchas en cero. Es la
unica tarea que escribe SQL directo, y solo para limpiar datos de prueba que la propia API
creo: no se modifica ningun esquema.
