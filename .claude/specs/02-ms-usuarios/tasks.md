# Spec 02 — ms-usuarios · tasks.md

Base: `requirements.md` (C1 aprobado 23/08/2026) y `design.md` (C2 aprobado 23/08/2026).

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado y se espera aprobacion. Ninguna tarea encadena la
siguiente.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay JDK, Maven ni psql: todo pasa por Docker
(`CLAUDE.md` §1).

Atajo usado en las tareas siguientes — compilar el microservicio:

```powershell
docker run --rm -v "${PWD}/backend/ms-usuarios:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
```

---

## T1 — Esqueleto Maven, imagen y servicio en compose

**Que hace.** Descarga el esqueleto de Spring Initializr por URL (nunca `mvn` en el host,
§2.2 del requirements) en `backend/ms-usuarios` con `groupId` `ec.ups.dae`, `artifactId`
`ms-usuarios`, paquete `ec.ups.dae.usuarios`, Java 21 y los starters `web`, `data-jpa`,
`validation`, `security` y el driver `postgresql`. Agrega a mano al `pom.xml`
`springdoc-openapi-starter-webmvc-ui` y los tres artefactos `io.jsonwebtoken` `0.12.x`
(`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Escribe `application.properties` con el
datasource, `ddl-auto=validate`, `server.port=8080`, `jwt.secret` desde `JWT_SECRET` y
`jwt.vigencia-horas=8`. Crea el `Dockerfile` multietapa (compilacion con
`maven:3.9-eclipse-temurin-21`, ejecucion con JRE 21) y agrega el servicio `ms-usuarios` a
`docker-compose.yml` con `8082:8080` temporal y `depends_on: postgres` con
`condition: service_healthy`.

**Cubre.** E-01, E-07, §2.1, §2.2, §2.3 del requirements; §8 del design.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-usuarios:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-usuarios
docker compose logs --tail=50 ms-usuarios
```

Esperado: el `package` termina sin error y el log muestra el arranque de Spring Boot en el
puerto 8080.

---

## T2 — Entidad, enum y repositorio

**Que hace.** Crea `entity/Usuario` mapeada a la tabla existente `usuario` segun la tabla de
mapeo del design §2, el enum `entity/Rol` (`ADMIN`, `USUARIO`) persistido como `STRING`, y
`repository/UsuarioRepository` con `findByEmail`, `existsByEmail`, `findAll` y `findById`.
No modifica ningun archivo de `infra/postgres/`.

**Cubre.** HU-07, E-02, RN-03 y RN-07 en su parte de identidad; decision D-10.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-usuarios:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-usuarios
docker compose logs --tail=50 ms-usuarios
```

Esperado: arranque sin errores de `SchemaManagementException`. Si `ddl-auto=validate` se
queja, se corrige la entidad, nunca el DDL.

---

## T3 — DTOs, mapper manual y manejo de excepciones

**Que hace.** Crea `dto/RegistroRequest`, `dto/LoginRequest`, `dto/CambioEstadoRequest`,
`dto/UsuarioResponse`, `dto/LoginResponse` y `dto/ErrorResponse` con las validaciones
`jakarta.validation` de la tabla del design §3; `mapper/UsuarioMapper` manual; las
excepciones `EmailDuplicadoException`, `CredencialesInvalidasException`,
`UsuarioNoEncontradoException`, `AutoInactivacionException`; y
`exception/ManejadorExcepciones` con `@RestControllerAdvice` cubriendo la tabla completa del
design §7, incluidos `HttpRequestMethodNotSupportedException` y
`HttpMediaTypeNotSupportedException` como `400 DATOS_INVALIDOS` y `Exception` como
`500 ERROR_INTERNO`. Sin controladores todavia.

**Cubre.** HU-06, E-05; decisiones D-05, D-06, D-07, D-11.

**Verificacion.**

```powershell
docker run --rm -v "${PWD}/backend/ms-usuarios:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests
docker compose up -d --build ms-usuarios
docker compose logs --tail=50 ms-usuarios
```

Esperado: compila y arranca. `UsuarioResponse` no declara ningun campo de contrasena.

---

## T4 — Seguridad base: BCrypt, TokenService y cadena de filtros

**Que hace.** Crea `service/TokenService` (emision y validacion de JWT HS256 con
`JWT_SECRET`, claims `sub` = `usuarioId`, `rol` y `exp` a 8 horas), el filtro que lee
`Authorization: Bearer` y `config/SeguridadConfig` con el `BCryptPasswordEncoder`, sesion
`STATELESS`, CSRF desactivado, rutas publicas (`POST /api/usuarios`,
`POST /api/usuarios/sesiones`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`),
`anyRequest().authenticated()` y los manejadores que devuelven `401 NO_AUTENTICADO` y
`403 SIN_PERMISO` en formato `ErrorResponse`. Todavia sin endpoints.

**Cubre.** HU-05, E-04; decisiones D-01, D-02, D-03, D-12, D-13.

**Verificacion.**

```powershell
docker compose up -d --build ms-usuarios
curl.exe -i http://localhost:8082/v3/api-docs
curl.exe -i http://localhost:8082/api/usuarios
```

Esperado: `200` en `/v3/api-docs` y `401` con `{"codigo":"NO_AUTENTICADO",...}` en
`/api/usuarios` sin token.

---

## T5 — Registro de usuario

**Que hace.** Crea `service/UsuarioService` con el registro (hash BCrypt, `rol = USUARIO`,
`activo = true`, `existsByEmail` mas traduccion de la violacion de `uq_usuario_email`) y
`controller/UsuarioController` con `POST /api/usuarios` devolviendo `201` y
`UsuarioResponse`.

**Cubre.** HU-01, E-03 (primer endpoint); supuestos S-01, S-03, S-06; decisiones D-08, P-04.

**Precondicion — postgres `healthy`.** El healthcheck de `postgres` solo pasa cuando el
ultimo script del init ya cargo el usuario ADMIN del seed, y `ms-usuarios` depende de esa
condicion. Tras un `docker compose down -v` el init vuelve a correr y el arranque tarda, asi
que antes de lanzar cualquier `curl` hay que confirmar el estado:

```powershell
docker compose ps
```

Esperado: la fila de `postgres` en estado `Up (healthy)` y la de `ms-usuarios` en `Up`. Si
`postgres` aparece como `starting` o `unhealthy`, se espera y se repite el comando; no se
ejecutan los `curl` hasta que este `healthy`. Esta precondicion aplica igual a T6, T7 y T8.

**Verificacion.**

```powershell
docker compose up -d --build ms-usuarios
curl.exe -i -X POST http://localhost:8082/api/usuarios -H "Content-Type: application/json" -d "{\"nombre\":\"Ana\",\"email\":\"ana@demo.ec\",\"password\":\"Clave123\"}"
curl.exe -i -X POST http://localhost:8082/api/usuarios -H "Content-Type: application/json" -d "{\"nombre\":\"Ana\",\"email\":\"ana@demo.ec\",\"password\":\"Clave123\"}"
curl.exe -i -X POST http://localhost:8082/api/usuarios -H "Content-Type: application/json" -d "{\"nombre\":\"Ana\",\"email\":\"mal\",\"password\":\"123\"}"
docker compose exec postgres psql -U usuarios_user -d usuarios_db -c "SELECT email, rol, activo, left(password_hash,4) FROM usuario WHERE email = 'ana@demo.ec'"
```

Esperado: `201` sin campo `password` en el cuerpo; `409 EMAIL_DUPLICADO` en el segundo;
`400 DATOS_INVALIDOS` en el tercero; la fila muestra `USUARIO`, `activo = t` y un hash que
empieza con `$2`.

---

## T6 — Inicio de sesion

**Que hace.** Crea `service/AutenticacionService` que verifica credenciales con BCrypt,
rechaza usuario inexistente, clave incorrecta y `activo = false` con el mismo
`401 NO_AUTENTICADO`, y pide el token a `TokenService`. Agrega
`POST /api/usuarios/sesiones` al controlador devolviendo `200` y `LoginResponse`.

**Cubre.** HU-02, E-03; supuestos S-01, S-04; decisiones D-01, D-04.

**Verificacion previa — contrasena real del seed.** `infra/postgres/05-seed.sql` guarda
hashes, no claves en claro, pero su cabecera **si documenta** la correspondencia. Confirmar
que sigue ahi antes de lanzar los `curl`:

```powershell
docker compose exec postgres psql -U usuarios_user -d usuarios_db -c "SELECT email, left(password_hash,7) FROM usuario ORDER BY usuario_id"
```

```
-- Cabecera de infra/postgres/05-seed.sql (spec 01 / T5):
-- Hashes BCrypt de coste 10 generados con htpasswd -nbBC 10 (Spring Security acepta $2y).
--   admin@canchas.ec   -> Admin123
--   usuario@canchas.ec -> Usuario123
```

Por eso T6 usa `Admin123` para `admin@canchas.ec` y `Usuario123` para
`usuario@canchas.ec`. **No hay que regenerar el seed.**

**SI** el primer `curl` de esta tarea devuelve `401` con esas claves, **ENTONCES** el hash
del seed no corresponde a la clave documentada y la unica solucion es regenerar
`infra/postgres/05-seed.sql`, que pertenece a la **spec 01**: en ese caso se detiene la
tarea, se reporta y se espera autorizacion antes de tocar ese archivo.

**Verificacion.**

```powershell
docker compose up -d --build ms-usuarios
curl.exe -i -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
curl.exe -i -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"incorrecta\"}"
curl.exe -i -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"nadie@demo.ec\",\"password\":\"Clave123\"}"
```

Esperado: `200` con `token` y `usuario` (`rol = ADMIN`, sin `password`); `401
NO_AUTENTICADO` con el mismo mensaje en los dos ultimos.

---

## T7 — Listado y cambio de estado (solo ADMIN)

**Que hace.** Agrega al servicio y al controlador `GET /api/usuarios` (lista completa, sin
paginacion, incluidos inactivos) y `PATCH /api/usuarios/{usuarioId}/estado` con
`404 NO_ENCONTRADO` si el id no existe y `400 DATOS_INVALIDOS` si el ADMIN intenta
inactivarse a si mismo. Ambos exigen `rol = ADMIN`.

**Cubre.** HU-03, HU-04, E-03 (endpoints restantes); supuestos S-02, S-05, S-07.

**Verificacion.** Reemplazar `<TOKEN_ADMIN>` y `<TOKEN_USUARIO>` por los tokens obtenidos en
T6, y `<ID>` por el `usuarioId` de `ana@demo.ec`.

```powershell
docker compose up -d --build ms-usuarios
curl.exe -i http://localhost:8082/api/usuarios -H "Authorization: Bearer <TOKEN_ADMIN>"
curl.exe -i http://localhost:8082/api/usuarios -H "Authorization: Bearer <TOKEN_USUARIO>"
curl.exe -i -X PATCH http://localhost:8082/api/usuarios/<ID>/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activo\":false}"
curl.exe -i -X PATCH http://localhost:8082/api/usuarios/9999/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{\"activo\":false}"
curl.exe -i -X PATCH http://localhost:8082/api/usuarios/<ID>/estado -H "Authorization: Bearer <TOKEN_ADMIN>" -H "Content-Type: application/json" -d "{}"
curl.exe -i -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"ana@demo.ec\",\"password\":\"Clave123\"}"
```

Esperado, en orden: `200` con la lista; `403 SIN_PERMISO`; `200` con `activo = false`;
`404 NO_ENCONTRADO`; `400 DATOS_INVALIDOS`; y `401 NO_AUTENTICADO` al intentar iniciar
sesion con el usuario ya inactivo.

---

## T8 — Documentacion OpenAPI y cierre de la spec

**Que hace.** Crea `config/OpenApiConfig` y declara en cada endpoint sus codigos de error con
`springdoc-openapi` (`@ApiResponse` por cada codigo de la tabla del design §4). Verifica
campo por campo que la API responde con los nombres congelados y que ninguna respuesta
serializa `password`. Deja constancia del resultado en `docs/bitacora.md`.

**Cubre.** E-06, HU-06, HU-07; §5 del requirements.

**Verificacion.**

```powershell
docker compose up -d --build ms-usuarios
curl.exe -s http://localhost:8082/v3/api-docs | Select-String -Pattern "usuarioId","activo","EMAIL_DUPLICADO","password"
curl.exe -i -X DELETE http://localhost:8082/api/usuarios
curl.exe -i -X POST http://localhost:8082/api/usuarios -H "Content-Type: text/plain" -d "hola"
```

Esperado: el documento OpenAPI lista los cuatro endpoints con `usuarioId` y `activo` y con
`password` unicamente en los esquemas de request; el `DELETE` responde
`400 DATOS_INVALIDOS` (405 traducido) y el `Content-Type` invalido tambien
`400 DATOS_INVALIDOS` (415 traducido).
