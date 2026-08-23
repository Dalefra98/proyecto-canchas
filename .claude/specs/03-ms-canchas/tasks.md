# Spec 03 — ms-canchas · tasks.md

Base: `requirements.md` (C1 aprobado 23/08/2026) y `design.md` (C2 aprobado 23/08/2026).

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado y se espera aprobacion. Ninguna tarea encadena la
siguiente. Cada tarea deja el proyecto compilando y el servicio arrancando.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay JDK, Maven ni psql: todo pasa por Docker
(`CLAUDE.md` §1). Se usa `curl.exe`, no `curl`.

Atajo usado en las tareas siguientes — compilar el microservicio. El volumen `m2repo`
montado en `/root/.m2` cachea las dependencias entre corridas; sin el, la descarga completa
desde `repo.maven.apache.org` corta el handshake TLS (`CLAUDE.md` §1):

```powershell
docker run --rm -v "${PWD}/backend/ms-canchas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
```

## Regla de reutilizacion desde `ms-usuarios` (T1, T3 y T4)

Cinco archivos **se copian** desde `backend/ms-usuarios`, no se reescriben desde cero. Si
los dos servicios validan el mismo JWT con codigo distinto, la diferencia solo aparece en la
integracion y es dificil de rastrear (design D-15). Se copian con `Copy-Item`, nunca `cp`
(`CLAUDE.md` §1).

| Archivo origen | Destino | Tarea | Adaptacion permitida |
|---|---|---|---|
| `backend/ms-usuarios/Dockerfile` | `backend/ms-canchas/Dockerfile` | T1 | Solo el nombre del `.jar`: `ms-canchas-0.0.1-SNAPSHOT.jar` |
| `config/FiltroToken.java` | `config/FiltroToken.java` | T4 | Solo el paquete y los `import` |
| `service/TokenService.java` | `service/TokenService.java` | T4 | Paquete e `import`, **mas** quitar lo que no aplica (ver abajo) |
| `config/SeguridadConfig.java` | `config/SeguridadConfig.java` | T4 | Paquete e `import`, **mas** la matriz de rutas y quitar el `PasswordEncoder` (ver abajo) |
| `exception/ManejadorExcepciones.java` | `exception/ManejadorExcepciones.java` | T3 | Paquete e `import`, **mas** sustituir las excepciones de dominio (ver abajo) |

`FiltroToken` y el `Dockerfile` son copia literal salvo lo indicado. Los otros tres se copian
como punto de partida y se adaptan **solo** en lo siguiente, dejando intacto todo lo demas:

- **`TokenService`**: se conservan `validar`, `usuarioIdDe` y `rolDe` **identicos, sin tocar
  una linea**, porque son la validacion compartida. Se eliminan el metodo `emitir`, el
  `import` de la entidad `Usuario` y el parametro `jwt.vigencia-horas` del constructor:
  `ms-canchas` no emite tokens (design §5). La construccion de la `SecretKey` a partir de
  `jwt.secret` se conserva tal cual.
- **`SeguridadConfig`**: se conservan la cadena `STATELESS`, CSRF/`httpBasic`/`formLogin`
  desactivados, el `permitAll()` de las tres rutas de springdoc, el
  `addFilterBefore(filtroToken, ...)` y los dos manejadores que escriben
  `401 NO_AUTENTICADO` y `403 SIN_PERMISO` como `ErrorResponse`. Se reemplaza la matriz de
  `requestMatchers` por la tabla del design §5 (las ocho rutas de canchas: `GET` a cualquier
  rol, escritura con `hasRole("ADMIN")`) y se **elimina** el `@Bean PasswordEncoder`:
  `ms-canchas` no maneja contrasenas.
- **`ManejadorExcepciones`**: se conservan la estructura, el formato `ErrorResponse` y los
  manejadores comunes —`MethodArgumentNotValidException`, `HttpMessageNotReadableException`,
  `MethodArgumentTypeMismatchException`, `HttpRequestMethodNotSupportedException`,
  `HttpMediaTypeNotSupportedException` y `Exception` como `500 ERROR_INTERNO`—. Se sustituyen
  las excepciones de dominio de usuarios por las de canchas de la tabla del design §7, y se
  agregan las dos `DataIntegrityViolationException` distinguidas por el nombre de la
  restriccion.

Comando de copia, desde la raiz del repositorio:

```powershell
Copy-Item backend/ms-usuarios/Dockerfile backend/ms-canchas/Dockerfile
Copy-Item backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/config/FiltroToken.java backend/ms-canchas/src/main/java/ec/ups/dae/canchas/config/
Copy-Item backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/config/SeguridadConfig.java backend/ms-canchas/src/main/java/ec/ups/dae/canchas/config/
Copy-Item backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/service/TokenService.java backend/ms-canchas/src/main/java/ec/ups/dae/canchas/service/
Copy-Item backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/exception/ManejadorExcepciones.java backend/ms-canchas/src/main/java/ec/ups/dae/canchas/exception/
```

**Prohibido** en estas tres tareas: escribir desde cero cualquiera de los cinco archivos, o
cambiar el algoritmo, el parseo o el manejo de errores del token. Tampoco se modifica nada
dentro de `backend/ms-usuarios`: la copia va en un solo sentido.

---

**Precondicion comun de T5 a T8 — `postgres` en estado `healthy`.** El healthcheck solo pasa
cuando el ultimo script del init ya cargo el seed, y `ms-canchas` depende de esa condicion.
Antes de lanzar cualquier `curl`:

```powershell
docker compose ps
```

Esperado: `postgres` en `Up (healthy)` y `ms-canchas` en `Up`. Si `postgres` aparece como
`starting` o `unhealthy`, se espera y se repite; no se ejecutan los `curl` hasta que este
`healthy`.

**Token de ADMIN y de USUARIO.** A partir de T5 hacen falta tokens reales, que emite
`ms-usuarios` (ya implementado en la spec 02) con las claves documentadas en la cabecera de
`infra/postgres/05-seed.sql`:

```powershell
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"usuario@canchas.ec\",\"password\":\"Usuario123\"}"
```

En los comandos de verificacion, `<TOKEN_ADMIN>` y `<TOKEN_USUARIO>` se reemplazan por el
campo `token` de cada respuesta. Ambos servicios comparten `JWT_SECRET`, asi que
`ms-canchas` valida localmente el token emitido por `ms-usuarios` (design §5).

---

## T1 — Esqueleto Maven, imagen y servicio en compose

**Que hace.** Descarga el esqueleto de Spring Initializr por URL (nunca `mvn` en el host,
§2.2 del requirements) en `backend/ms-canchas` con `groupId` `ec.ups.dae`, `artifactId`
`ms-canchas`, paquete `ec.ups.dae.canchas`, Java 21 y los starters `web`, `data-jpa`,
`validation`, `security` y el driver `postgresql`. Corrige el `<parent>` a **Spring Boot
3.5.3** y agrega a mano `springdoc-openapi-starter-webmvc-ui` 2.8.6 y los tres artefactos
`io.jsonwebtoken` 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Escribe
`application.properties` con el datasource de `canchas_db`, `ddl-auto=validate`,
`server.port=8080` y `jwt.secret` desde `JWT_SECRET`. **Copia** el `Dockerfile` desde
`backend/ms-usuarios` segun la regla de reutilizacion de arriba, cambiando unicamente el
`.jar` a `ms-canchas-0.0.1-SNAPSHOT.jar`, y agrega el servicio `ms-canchas` a
`docker-compose.yml`
con `8083:8080` temporal y `depends_on: postgres` con `condition: service_healthy`.

**Instruccion literal.** El `Dockerfile` **se copia** de `backend/ms-usuarios/Dockerfile` y
se adapta **solo** en el nombre del `.jar` (`ms-usuarios-0.0.1-SNAPSHOT.jar` ->
`ms-canchas-0.0.1-SNAPSHOT.jar`). **No se reescribe desde cero.**

**Cubre.** E-01, E-07; §2.1, §2.2, §2.3 del requirements; §8 del design.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-canchas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-canchas
docker compose logs --tail=50 ms-canchas
```

Esperado: el `package` termina sin error y el log muestra el arranque de Spring Boot en el
puerto 8080.

---

## T2 — Entidades, enum y repositorios

**Que hace.** Crea `entity/Cancha` y `entity/BloqueoMantenimiento` mapeadas a las tablas
existentes segun las tablas de mapeo del design §2, el enum `entity/Deporte` (`PADEL`,
`TENIS`, `BASQUET`) persistido como `STRING`, y los dos repositorios con las operaciones del
design §2: `CanchaRepository` (`findAll`, `findByActivaTrue`, `findById`, `existsByNombre`,
`existsByNombreAndCanchaIdNot`, `save`) y `BloqueoRepository` (`findByCanchaId`,
`findByCanchaIdAndFecha`, la consulta derivada de solapamiento,
`findByBloqueoIdAndCanchaId`, `save`, `delete`). `canchaId` del bloqueo es columna `Long`
simple, no asociacion. **No modifica ningun archivo de `infra/postgres/`.**

**Cubre.** HU-11, E-02; decisiones D-02, D-13.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-canchas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-canchas
docker compose logs --tail=50 ms-canchas
docker compose exec postgres psql -U canchas_user -d canchas_db -c "\d cancha"
```

Esperado: arranque sin `SchemaManagementException`; la descripcion de la tabla coincide con
el mapeo. Si `ddl-auto=validate` se queja, se corrige la entidad, **nunca el DDL**.

---

## T3 — DTOs, mappers manuales y manejo de excepciones

**Que hace.** Crea `dto/CanchaRequest`, `dto/CambioEstadoCanchaRequest`,
`dto/BloqueoRequest`, `dto/CanchaResponse`, `dto/BloqueoResponse` y `dto/ErrorResponse` con
las validaciones `jakarta.validation` de las tablas del design §3 (fechas y horas como
`String` con `@Pattern`); `mapper/CanchaMapper` y `mapper/BloqueoMapper` manuales, con
formateo y parseo estricto de `HH:mm` y `AAAA-MM-DD`; las excepciones
`CanchaNoEncontradaException`, `BloqueoNoEncontradoException`, `NombreDuplicadoException`,
`BloqueoDuplicadoException`, `HorarioInvalidoException`, `FueraDeHorarioException`,
`FechaPasadaException`, `FormatoInvalidoException`; y **copia**
`exception/ManejadorExcepciones` desde `backend/ms-usuarios` segun la regla de reutilizacion
de arriba, adaptandolo para cubrir la tabla completa del design §7, incluidos
`HttpRequestMethodNotSupportedException` y `HttpMediaTypeNotSupportedException` como
`400 DATOS_INVALIDOS`, las dos `DataIntegrityViolationException` distinguidas por el nombre
de la restriccion, y `Exception` como `500 ERROR_INTERNO`. Sin controladores todavia.

**Instruccion literal.** `ManejadorExcepciones` **se copia** de
`backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/exception/ManejadorExcepciones.java`
y se adapta **solo** en el paquete y los `import` (`ec.ups.dae.usuarios` ->
`ec.ups.dae.canchas`) y en las excepciones de dominio propias de este servicio. **No se
reescribe desde cero.**

**Cubre.** HU-10, E-05; decisiones D-03, D-04, D-08, D-09, D-14.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-canchas:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-canchas
docker compose logs --tail=50 ms-canchas
```

Esperado: compila y arranca. Los dos codigos `409` usados son exactamente
`NOMBRE_DUPLICADO` y `BLOQUEO_DUPLICADO`, los del contrato.

---

## T4 — Seguridad: validacion del token y reglas de acceso

**Que hace.** **Copia** `service/TokenService`, `config/FiltroToken` y
`config/SeguridadConfig` desde `backend/ms-usuarios` segun la regla de reutilizacion de
arriba. `FiltroToken` cambia solo de paquete. `TokenService` conserva `validar`,
`usuarioIdDe` y `rolDe` sin tocar una linea, y pierde `emitir` y `jwt.vigencia-horas`: este
servicio **no emite** tokens. `SeguridadConfig` conserva la cadena `STATELESS`, las rutas de
documentacion publicas, el `addFilterBefore` y los manejadores de `401 NO_AUTENTICADO` y
`403 SIN_PERMISO`; se le reemplaza la matriz de rutas por la tabla del design §5 (escritura
solo `ADMIN`) y se le quita el `PasswordEncoder`. Todavia sin endpoints propios.

**Instruccion literal.** Los tres archivos **se copian** de
`backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/` y se adaptan **solo** en el paquete
y los `import` (`ec.ups.dae.usuarios` -> `ec.ups.dae.canchas`) y en las rutas especificas de
este servicio. **No se reescriben desde cero**, y en particular el algoritmo, el parseo y el
manejo de errores del token quedan byte por byte como en `ms-usuarios`.

**Cubre.** HU-09, E-04, RN-07 en su parte de autorizacion; decisiones D-15 y §5 del design.

**Verificacion.**

```powershell
docker compose up -d --build ms-canchas
curl.exe -i http://localhost:8083/v3/api-docs
curl.exe -i http://localhost:8083/api/canchas
curl.exe -i http://localhost:8083/api/canchas -H "Authorization: Bearer token-falso"
```

Esperado: `200` en `/v3/api-docs`; `401` con `{"codigo":"NO_AUTENTICADO",...}` sin token y
tambien con el token invalido.

---

## T5 — Lectura del catalogo con filtrado por rol

**Que hace.** Crea `service/CanchaService` con el listado y el detalle, y
`controller/CanchaController` con `GET /api/canchas` y `GET /api/canchas/{canchaId}`. El
`ADMIN` recibe todas las canchas; el `USUARIO`, solo las `activa = true`, sin parametro de
consulta: el rol sale del `SecurityContext`. Una cancha inexistente da `404 NO_ENCONTRADO`,
y una inactiva vista por un `USUARIO` da el mismo `404`, con la misma excepcion.

**Cubre.** HU-01, HU-02, E-03 (primeros endpoints); decisiones P-05, D-05, D-06.

**Verificacion.** Las tres canchas del seed estan activas, asi que primero se inactiva una
por SQL para poder distinguir los dos roles. Reemplazar los marcadores por los tokens.

```powershell
docker compose up -d --build ms-canchas
docker compose exec postgres psql -U canchas_user -d canchas_db -c "UPDATE cancha SET activa = FALSE WHERE nombre = 'Basquet 1'"
curl.exe -i http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i http://localhost:8083/api/canchas/3 -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i http://localhost:8083/api/canchas/3 -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i http://localhost:8083/api/canchas/9999 -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado, en orden: `200` con las tres canchas y `horaApertura` en formato `07:00` (no
`07:00:00`); `200` con solo dos; `200` con `Basquet 1` y `activa = false`; `404
NO_ENCONTRADO`; `404 NO_ENCONTRADO`. Al terminar se restaura el seed:

```powershell
docker compose exec postgres psql -U canchas_user -d canchas_db -c "UPDATE cancha SET activa = TRUE WHERE nombre = 'Basquet 1'"
```

---

## T6 — Escritura del catalogo: alta, edicion y cambio de estado

**Que hace.** Agrega al servicio y al controlador `POST /api/canchas`,
`PUT /api/canchas/{canchaId}` y `PATCH /api/canchas/{canchaId}/estado`, todos solo `ADMIN`.
Incluye la validacion `horaCierre > horaApertura`, la doble barrera del nombre duplicado
(`existsByNombre` / `existsByNombreAndCanchaIdNot` mas traduccion de `uq_cancha_nombre`) y la
regla de que el `PUT` no toca `activa`.

**Cubre.** HU-03, HU-04, HU-05, RN-07, E-03; supuestos S-02, S-03, S-04; decisiones P-01,
D-08, D-11.

**Verificacion.**

```powershell
docker compose up -d --build ms-canchas
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 2\",\"deporte\":\"PADEL\",\"horaApertura\":\"08:00\",\"horaCierre\":\"21:00\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 2\",\"deporte\":\"PADEL\",\"horaApertura\":\"08:00\",\"horaCierre\":\"21:00\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 3\",\"deporte\":\"PADEL\",\"horaApertura\":\"22:00\",\"horaCierre\":\"08:00\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 3\",\"deporte\":\"FUTBOL\",\"horaApertura\":\"08:00\",\"horaCierre\":\"21:00\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 4\",\"deporte\":\"PADEL\",\"horaApertura\":\"08:00\",\"horaCierre\":\"21:00\"}"
curl.exe -i -X PUT http://localhost:8083/api/canchas/1 -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Padel 1\",\"deporte\":\"PADEL\",\"horaApertura\":\"06:00\",\"horaCierre\":\"23:00\"}"
curl.exe -i -X PUT http://localhost:8083/api/canchas/1 -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"nombre\":\"Tenis 1\",\"deporte\":\"PADEL\",\"horaApertura\":\"06:00\",\"horaCierre\":\"23:00\"}"
curl.exe -i -X PATCH http://localhost:8083/api/canchas/1/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activa\":false}"
curl.exe -i -X PATCH http://localhost:8083/api/canchas/1/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{}"
curl.exe -i -X PATCH http://localhost:8083/api/canchas/9999/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activa\":false}"
```

Esperado, en orden: `201`; `409 NOMBRE_DUPLICADO`; `400 DATOS_INVALIDOS` (cierre antes de
apertura); `400 DATOS_INVALIDOS` (deporte fuera del enum); `403 SIN_PERMISO`; `200` con el
horario nuevo y **`activa` sin cambiar**; `409 NOMBRE_DUPLICADO` (nombre de otra cancha);
`200` con `activa = false`; `400 DATOS_INVALIDOS`; `404 NO_ENCONTRADO`. Al terminar se
restaura `Padel 1`:

```powershell
docker compose exec postgres psql -U canchas_user -d canchas_db -c "UPDATE cancha SET activa = TRUE, hora_apertura = '07:00', hora_cierre = '22:00' WHERE cancha_id = 1"
```

---

## T7 — Bloqueos de mantenimiento

**Que hace.** Crea `service/BloqueoService` y `controller/BloqueoController` con
`GET /api/canchas/{canchaId}/bloqueos` (filtro opcional `?fecha`, abierto a ambos roles),
`POST /api/canchas/{canchaId}/bloqueos` y
`DELETE /api/canchas/{canchaId}/bloqueos/{id}` (ambos solo `ADMIN`). Aplica, en el orden del
design §6: `400` de forma, `404` de cancha, `horaFin > horaInicio`, fecha no pasada, franja
dentro del horario de atencion, y por ultimo `409 BLOQUEO_DUPLICADO` por duplicado exacto o
solapamiento parcial. El alta **se permite sobre una cancha inactiva** (D-16). El DELETE
exige que el bloqueo pertenezca a esa cancha.

**Cubre.** HU-06, HU-07, HU-08, E-03 (endpoints restantes); supuestos S-05, S-09;
decisiones P-02.a a P-02.d, P-06, D-07, D-09, D-10, D-12, D-16.

**Verificacion.** `Padel 1` atiende de `07:00` a `22:00`. Las fechas usadas son futuras
respecto del 23/08/2026.

```powershell
docker compose up -d --build ms-canchas
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"09:00\",\"horaFin\":\"11:00\",\"motivo\":\"Cambio de piso\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"09:00\",\"horaFin\":\"11:00\",\"motivo\":\"Repetido exacto\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"10:00\",\"horaFin\":\"12:00\",\"motivo\":\"Solapado parcial\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"11:00\",\"horaFin\":\"12:00\",\"motivo\":\"Pegado, no solapa\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"06:00\",\"horaFin\":\"08:00\",\"motivo\":\"Fuera de horario\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-01-05\",\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\",\"motivo\":\"Fecha pasada\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"15:00\",\"horaFin\":\"14:00\",\"motivo\":\"Fin antes que inicio\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/9999/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-01\",\"horaInicio\":\"15:00\",\"horaFin\":\"16:00\",\"motivo\":\"Cancha inexistente\"}"
curl.exe -i -X POST http://localhost:8083/api/canchas/1/bloqueos -H "Authorization: Bearer <TOKEN_USUARIO>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-02\",\"horaInicio\":\"15:00\",\"horaFin\":\"16:00\",\"motivo\":\"Sin permiso\"}"
curl.exe -i "http://localhost:8083/api/canchas/1/bloqueos" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i "http://localhost:8083/api/canchas/1/bloqueos?fecha=2026-09-01" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i "http://localhost:8083/api/canchas/1/bloqueos?fecha=2026-12-25" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i "http://localhost:8083/api/canchas/1/bloqueos?fecha=01-09-2026" -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i -X DELETE http://localhost:8083/api/canchas/2/bloqueos/1 -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i -X DELETE http://localhost:8083/api/canchas/1/bloqueos/1 -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i -X DELETE http://localhost:8083/api/canchas/1/bloqueos/1 -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Esperado, en orden: `201`; `409 BLOQUEO_DUPLICADO`; `409 BLOQUEO_DUPLICADO`; `201` (tocarse
en un extremo no es solaparse); `400 DATOS_INVALIDOS`; `400 DATOS_INVALIDOS`; `400
DATOS_INVALIDOS`; `404 NO_ENCONTRADO`; `403 SIN_PERMISO`; `200` con dos bloqueos; `200` con
los dos del dia; `200` con `[]`; `400 DATOS_INVALIDOS`; `404 NO_ENCONTRADO` (bloqueo de otra
cancha); `204` sin cuerpo; `404 NO_ENCONTRADO` al repetir la baja.

Verificacion adicional de D-16 — bloqueo sobre cancha inactiva:

```powershell
docker compose exec postgres psql -U canchas_user -d canchas_db -c "UPDATE cancha SET activa = FALSE WHERE cancha_id = 2"
curl.exe -i -X POST http://localhost:8083/api/canchas/2/bloqueos -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"fecha\":\"2026-09-03\",\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\",\"motivo\":\"Mantenimiento mayor\"}"
docker compose exec postgres psql -U canchas_user -d canchas_db -c "UPDATE cancha SET activa = TRUE WHERE cancha_id = 2"
```

Esperado: `201`, porque el alta comprueba que la cancha exista, no su estado.

---

## T8 — Documentacion OpenAPI y cierre de la spec

**Que hace.** Crea `config/OpenApiConfig` y declara en cada endpoint sus codigos de error con
`springdoc-openapi` (`@ApiResponse` por cada codigo de la tabla del design §4, incluidos los
dos `409` nuevos y el `400` del listado de bloqueos). Verifica campo por campo que la API
responde con los nombres congelados del contrato y que `405` y `415` salen como `400`. Deja
constancia del resultado en `docs/bitacora.md`.

**Cubre.** E-06, HU-10, HU-11; §1 y §5 del requirements; decision D-11 de la spec 02.

**Verificacion.**

```powershell
docker compose up -d --build ms-canchas
curl.exe -s http://localhost:8083/v3/api-docs | Select-String -Pattern "canchaId","horaApertura","bloqueoId","NOMBRE_DUPLICADO","BLOQUEO_DUPLICADO"
curl.exe -i -X DELETE http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i -X POST http://localhost:8083/api/canchas -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: text/plain" -d "hola"
docker compose exec postgres psql -U canchas_user -d canchas_db -c "SELECT cancha_id, nombre, deporte, hora_apertura, hora_cierre, activa FROM cancha ORDER BY cancha_id"
```

Esperado: el documento OpenAPI lista los ocho endpoints con `canchaId`, `horaApertura`,
`bloqueoId` y los dos codigos `409`; el `DELETE` sobre la coleccion responde
`400 DATOS_INVALIDOS` (405 traducido); el `Content-Type` invalido tambien
`400 DATOS_INVALIDOS` (415 traducido); y la consulta SQL confirma que las tres canchas del
seed quedaron con su horario y su estado originales.

**Limpieza final de datos de prueba.** Los bloqueos `2` y `3` que dejo T7 se conservan
durante T8 —el documento OpenAPI y las pruebas de esta tarea los usan como datos reales— y
se borran al terminar, para devolver la base al estado del seed. La tabla
`bloqueo_mantenimiento` nace vacia en `05-seed.sql`, asi que debe quedar sin filas:

```powershell
docker compose exec postgres psql -U canchas_user -d canchas_db -c "DELETE FROM bloqueo_mantenimiento WHERE bloqueo_id IN (2, 3)"
docker compose exec postgres psql -U canchas_user -d canchas_db -c "SELECT bloqueo_id, cancha_id, fecha FROM bloqueo_mantenimiento ORDER BY bloqueo_id"
```

Esperado: el `DELETE` reporta `DELETE 2` y el `SELECT` devuelve `(0 rows)`.
