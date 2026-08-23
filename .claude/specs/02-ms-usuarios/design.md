# Spec 02 — ms-usuarios · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 02").
Falta `tasks.md`: el codigo de produccion se escribe tarea por tarea, una a la vez.

Base: `.claude/specs/02-ms-usuarios/requirements.md` (C1 aprobado el 23/08/2026),
`docs/contratos/README.md` y `infra/postgres/02-ddl-usuarios.sql`.

## 1. Verificacion campo por campo contra el contrato

Comparacion de los nombres JSON congelados con las columnas del DDL ya aplicado. **No se
renombra nada**: la columna es interna y el campo JSON es el del contrato.

| Campo JSON (contrato) | Tipo contrato | Columna en `usuario` | Tipo columna | Coincide |
|---|---|---|---|---|
| `usuarioId` | number | `usuario_id` | `BIGINT` identity | Si |
| `nombre` | string | `nombre` | `VARCHAR(80)` | Si |
| `email` | string | `email` | `VARCHAR(120)` unico | Si |
| `password` | string, **solo request** | `password_hash` | `VARCHAR(72)` | Si — el request recibe la clave en claro y la columna guarda el hash BCrypt; el campo `password` nunca se serializa en respuesta |
| `rol` | `ADMIN` \| `USUARIO` | `rol` | `VARCHAR(8)` con `CHECK` | Si |
| `activo` | boolean | `activo` | `BOOLEAN` default `TRUE` | Si |
| `token` | string | — | — | Si — no se persiste, se calcula (JWT) |
| `usuario` | objeto `UsuarioResponse` | — | — | Si — envoltura de respuesta de inicio de sesion |

Sin discrepancias. Ningun campo del contrato falta y ningun campo nuevo se agrega.

## 2. Modelo de datos

Fuente unica: `infra/postgres/02-ddl-usuarios.sql`. **Esta spec no modifica el DDL.** La
entidad se adapta a la tabla, nunca al contrario.

### Tabla `usuario` (base `usuarios_db`, propietario `usuarios_user`)

| Columna | Tipo | Nulo | Restriccion |
|---|---|---|---|
| `usuario_id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | No | `pk_usuario` PRIMARY KEY |
| `nombre` | `VARCHAR(80)` | No | — |
| `email` | `VARCHAR(120)` | No | `uq_usuario_email` UNIQUE |
| `password_hash` | `VARCHAR(72)` | No | `ck_usuario_password_bcrypt` CHECK `LIKE '$2%'` |
| `rol` | `VARCHAR(8)` | No | `ck_usuario_rol` CHECK IN (`ADMIN`, `USUARIO`) |
| `activo` | `BOOLEAN` | No | DEFAULT `TRUE` |

### Mapeo de la entidad `Usuario` (paquete `ec.ups.dae.usuarios.entity`)

| Atributo | Tipo Java | Columna | Notas de mapeo |
|---|---|---|---|
| `usuarioId` | `Long` | `usuario_id` | `@Id` con generacion `IDENTITY` |
| `nombre` | `String` | `nombre` | `length = 80`, `nullable = false` |
| `email` | `String` | `email` | `length = 120`, `nullable = false`, `unique = true` |
| `passwordHash` | `String` | `password_hash` | `length = 72`, `nullable = false` |
| `rol` | `Rol` (enum) | `rol` | `@Enumerated(EnumType.STRING)`, `length = 8` |
| `activo` | `boolean` | `activo` | `nullable = false` |

Enum `Rol`: `ADMIN`, `USUARIO`. Valores identicos al `CHECK` de la tabla y al contrato.

Con `spring.jpa.hibernate.ddl-auto=validate` cualquier desalineacion de nombre, tipo o
nulabilidad detiene el arranque (HU-07).

### Independencia de datos

- `ms-usuarios` solo abre conexion a `usuarios_db` con el usuario `usuarios_user`.
- La unica tabla consultada es `usuario`. No hay consultas nativas, ni `JOIN`, ni esquemas
  cruzados hacia `canchas_db` o `reservas_db`.
- El servicio no consulta reservas para decidir nada: la relacion usuario–reserva vive en
  `ms-reservas` como columna numerica sin clave foranea.

### Repositorio `UsuarioRepository`

| Operacion | Uso |
|---|---|
| `findByEmail(String email)` | Inicio de sesion (HU-02) y deteccion de duplicado (HU-01) |
| `existsByEmail(String email)` | Verificacion previa del registro (HU-01) |
| `findAll()` | Listado del ADMIN (HU-03), sin paginacion (S-07) |
| `findById(Long usuarioId)` | Cambio de estado (HU-04) |
| `save(Usuario usuario)` | Registro y cambio de estado |

## 3. DTOs y validaciones

Todos en `ec.ups.dae.usuarios.dto`. Conversion con **mapper manual**
(`UsuarioMapper`, paquete `mapper`): sin Lombok, sin MapStruct. La entidad nunca sale del
paquete `service` hacia el controlador.

### `RegistroRequest` — cuerpo de `POST /api/usuarios`

| Campo | Tipo | Validaciones `jakarta.validation` |
|---|---|---|
| `nombre` | String | `@NotBlank`, `@Size(max = 80)` |
| `email` | String | `@NotBlank`, `@Email`, `@Size(max = 120)` |
| `password` | String | `@NotBlank`, `@Size(min = 8, max = 100)` |

No declara `rol` ni `activo`: el registro publico siempre crea `USUARIO` con
`activo = true` (S-03). Un `rol` enviado por el cliente se ignora porque el DTO no lo tiene.

### `LoginRequest` — cuerpo de `POST /api/usuarios/sesiones`

| Campo | Tipo | Validaciones |
|---|---|---|
| `email` | String | `@NotBlank`, `@Email` |
| `password` | String | `@NotBlank` |

Aqui no se valida longitud de `password`: una clave que no cumple la politica actual debe
producir `401 NO_AUTENTICADO`, no `400`, para no revelar politicas ni existencia de cuentas.

### `CambioEstadoRequest` — cuerpo de `PATCH /api/usuarios/{usuarioId}/estado`

| Campo | Tipo | Validaciones |
|---|---|---|
| `activo` | `Boolean` (objeto, no primitivo) | `@NotNull` |

Se usa `Boolean` y no `boolean` para que un cuerpo sin el campo se detecte como `null` y
responda `400 DATOS_INVALIDOS` en vez de asumir `false` (HU-04).

### `UsuarioResponse` — respuesta de registro, listado y cambio de estado

| Campo | Tipo | Origen |
|---|---|---|
| `usuarioId` | Long | entidad |
| `nombre` | String | entidad |
| `email` | String | entidad |
| `rol` | String (`ADMIN` \| `USUARIO`) | entidad |
| `activo` | boolean | entidad |

**No** contiene `password` ni `passwordHash`. El mapper no tiene ninguna via para
copiarlos, asi que la omision es estructural y no depende de una anotacion.

### `LoginResponse` — respuesta de inicio de sesion

| Campo | Tipo | Origen |
|---|---|---|
| `token` | String | JWT emitido |
| `usuario` | `UsuarioResponse` | entidad autenticada |

Forma identica al payload congelado del contrato.

### `ErrorResponse` — cuerpo de todo error

| Campo | Tipo |
|---|---|
| `codigo` | String |
| `mensaje` | String |

## 4. Endpoints

Controlador `UsuarioController` (paquete `controller`), raiz `/api/usuarios`.

| Verbo | Ruta | Rol requerido | Cuerpo entrada | Respuesta 2xx | Errores |
|---|---|---|---|---|---|
| POST | `/api/usuarios/sesiones` | publico | `LoginRequest` | `200` + `LoginResponse` | 400, 401 |
| POST | `/api/usuarios` | publico | `RegistroRequest` | `201` + `UsuarioResponse` | 400, 409 |
| GET | `/api/usuarios` | ADMIN | — | `200` + lista de `UsuarioResponse` | 401, 403 |
| PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | `CambioEstadoRequest` | `200` + `UsuarioResponse` | 400, 401, 403, 404 |

Rutas, roles y codigos son exactamente los de la tabla "Rutas REST congeladas". No se
agrega ningun endpoint mas, ni de validacion de token (decision P-01).

### Reparto de responsabilidades

| Capa | Clase | Responsabilidad |
|---|---|---|
| `controller` | `UsuarioController` | Recibe, valida con `@Valid`, delega, devuelve el codigo HTTP. Sin logica de negocio |
| `service` | `UsuarioService` | Registro, listado y cambio de estado. Aplica S-03, S-04 y S-05 |
| `service` | `AutenticacionService` | Verifica credenciales con BCrypt y pide el token |
| `service` | `TokenService` | Emite y valida el JWT HS256 |
| `repository` | `UsuarioRepository` | Acceso a la tabla `usuario` |
| `mapper` | `UsuarioMapper` | Entidad -> `UsuarioResponse`, `RegistroRequest` -> entidad |
| `config` | `SeguridadConfig` | `PasswordEncoder` BCrypt, filtro de token, reglas de acceso por ruta |
| `config` | `OpenApiConfig` | Metadatos `springdoc-openapi` |
| `exception` | jerarquia + `ManejadorExcepciones` | Traduccion a `ErrorResponse` |

## 5. Autenticacion y autorizacion

### Token (decision P-01)

| Aspecto | Definicion |
|---|---|
| Formato | JWT firmado, algoritmo `HS256` |
| Secreto | variable de entorno `JWT_SECRET`, la misma para los cuatro microservicios |
| Claim `sub` | `usuarioId` |
| Claim `rol` | `ADMIN` o `USUARIO` |
| Claim `exp` | emision + 8 horas |
| Transporte | encabezado `Authorization: Bearer <token>` |
| Validacion | **local** en cada microservicio, con el mismo secreto. Sin llamada HTTP a `ms-usuarios` |

El `rol` viaja en el token para que `ms-canchas` y `ms-reservas` apliquen RN-03 y RN-07 sin
consultar `usuarios_db`. Consecuencia aceptada: si un ADMIN inactiva a un usuario, el token
ya emitido sigue siendo valido hasta su `exp`; no hay revocacion (fuera de alcance por §6
del requirements). El bloqueo se materializa cuando el usuario intenta iniciar sesion de
nuevo.

### Reglas de acceso por ruta

| Ruta | Regla |
|---|---|
| `POST /api/usuarios/sesiones` | publica |
| `POST /api/usuarios` | publica |
| `GET /api/usuarios` | token valido + `rol = ADMIN` |
| `PATCH /api/usuarios/{usuarioId}/estado` | token valido + `rol = ADMIN` |
| `/v3/api-docs/**` | publica |
| `/swagger-ui/**` | publica |
| `/swagger-ui.html` | publica |

Las tres rutas de documentacion se declaran `permitAll()` de forma explicita en el
`SecurityFilterChain` y el filtro de token las deja pasar sin validar nada. Si no se abren,
`springdoc-openapi` responde `401` y el entregable E-03 no se puede demostrar.

Sesiones sin estado (`SessionCreationPolicy.STATELESS`), sin formulario de login de Spring
Security, sin `Basic` y sin CSRF (API consumida por el frontend con token).

### Contrasenas

- Se codifican con `BCryptPasswordEncoder` de Spring Security antes de persistir.
- La verificacion en el inicio de sesion usa `matches` sobre `password_hash`.
- El hash resultante empieza con `$2`, condicion que el `CHECK` de la tabla exige.
- `VARCHAR(72)` alcanza para los 60 caracteres del hash BCrypt.

## 6. Flujos

### Registro (HU-01)

1. `@Valid` sobre `RegistroRequest`; si falla -> `400 DATOS_INVALIDOS`.
2. `existsByEmail`; si existe -> `EmailDuplicadoException` -> `409 EMAIL_DUPLICADO`.
3. Se codifica la clave con BCrypt y se arma la entidad con `rol = USUARIO`,
   `activo = true`.
4. `save`. Si la restriccion `uq_usuario_email` salta por una carrera entre dos peticiones
   simultaneas, la violacion de integridad tambien se traduce a `409 EMAIL_DUPLICADO`.
5. Respuesta `201` con `UsuarioResponse`.

### Inicio de sesion (HU-02)

1. `@Valid` sobre `LoginRequest`; si falla -> `400 DATOS_INVALIDOS`.
2. `findByEmail`. Si no existe, si la clave no coincide o si `activo = false` ->
   `CredencialesInvalidasException` -> `401 NO_AUTENTICADO`, con **el mismo mensaje** en
   los tres casos.
3. Se emite el JWT con `sub`, `rol` y `exp`.
4. Respuesta `200` con `LoginResponse`.

### Listado (HU-03)

1. El filtro valida el token; sin token o invalido -> `401 NO_AUTENTICADO`.
2. Si el `rol` del token no es `ADMIN` -> `403 SIN_PERMISO`.
3. `findAll` y mapeo a lista de `UsuarioResponse`, incluidos los inactivos.

### Cambio de estado (HU-04)

1. Filtro: token valido y `rol = ADMIN`; en su defecto `401` o `403`.
2. `@Valid` sobre `CambioEstadoRequest`; `activo` ausente -> `400 DATOS_INVALIDOS`.
3. `findById`; si no existe -> `UsuarioNoEncontradoException` -> `404 NO_ENCONTRADO`.
4. Si el `usuarioId` del path es igual al `sub` del token y `activo = false` ->
   `AutoInactivacionException` -> `400 DATOS_INVALIDOS` (S-05).
5. Se actualiza `activo`, se guarda y se responde `200` con `UsuarioResponse`.

## 7. Excepciones a codigos HTTP

`ManejadorExcepciones` en `ec.ups.dae.usuarios.exception`, anotado con
`@RestControllerAdvice`. Todo error sale como `ErrorResponse`; nunca un stacktrace (HU-06).

| Excepcion | HTTP | `codigo` | Origen |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `DATOS_INVALIDOS` | `@Valid` de cualquier DTO |
| `HttpMessageNotReadableException` | 400 | `DATOS_INVALIDOS` | JSON malformado o `activo` no booleano |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | `usuarioId` no numerico en el path |
| `HttpRequestMethodNotSupportedException` | 400 | `DATOS_INVALIDOS` | Verbo equivocado sobre una ruta existente (evita que el 405 caiga en el manejador generico) |
| `HttpMediaTypeNotSupportedException` | 400 | `DATOS_INVALIDOS` | `Content-Type` distinto de `application/json` (evita que el 415 caiga en el manejador generico) |
| `AutoInactivacionException` | 400 | `DATOS_INVALIDOS` | El ADMIN intenta inactivarse (S-05) |
| `CredencialesInvalidasException` | 401 | `NO_AUTENTICADO` | Correo inexistente, clave incorrecta o usuario inactivo |
| Token ausente, expirado o con firma invalida | 401 | `NO_AUTENTICADO` | Punto de entrada de autenticacion |
| Rol insuficiente | 403 | `SIN_PERMISO` | Punto de acceso denegado |
| `UsuarioNoEncontradoException` | 404 | `NO_ENCONTRADO` | `PATCH` sobre un `usuarioId` inexistente |
| `EmailDuplicadoException` | 409 | `EMAIL_DUPLICADO` | Registro con correo ya usado |
| `DataIntegrityViolationException` sobre `uq_usuario_email` | 409 | `EMAIL_DUPLICADO` | Carrera entre dos registros simultaneos |
| `Exception` (resto) | 500 | `ERROR_INTERNO` | Red de seguridad (decision D-06). `ERROR_INTERNO` ya esta congelado en el contrato desde el 23/08/2026 |

Ningun codigo fuera de la tabla "Formato de error" del contrato. `405` y `415` no se
devuelven nunca: se traducen a `400 DATOS_INVALIDOS`, para lo cual el manejador debe
declararlos explicitamente (si no, terminan en el manejador de `Exception` y saldrian como
`500`).

Las respuestas `401` y `403` se producen dentro de la cadena de filtros, por lo que se
escriben con los mismos manejadores de autenticacion y acceso denegado para conservar el
formato `{ "codigo", "mensaje" }`.

## 8. Configuracion

| Propiedad / variable | Valor | Origen |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/usuarios_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | `usuarios_user` | entorno |
| `spring.datasource.password` | `usuarios_pass` | entorno |
| `spring.jpa.hibernate.ddl-auto` | `validate` | entorno |
| `server.port` | `8080` | interno del contenedor |
| `jwt.secret` | — | `JWT_SECRET` de `.env` |
| `jwt.vigencia-horas` | `8` | `application.properties` |

Servicio `ms-usuarios` en `docker-compose.yml`, `depends_on: postgres` con
`condition: service_healthy`, puerto `8082:8080` **temporal** para pruebas con `curl.exe`
hasta que exista el gateway Nginx.

Imagen: build multi-etapa. Etapa de compilacion con `maven:3.9-eclipse-temurin-21`, etapa
de ejecucion con una imagen JRE 21. El esqueleto se descarga de Spring Initializr por URL;
nunca se ejecuta `mvn` en el host (§2.2 del requirements).

### Dependencias

| Dependencia | Origen |
|---|---|
| `spring-boot-starter-web` | Spring Initializr |
| `spring-boot-starter-data-jpa` | Spring Initializr |
| `spring-boot-starter-validation` | Spring Initializr |
| `spring-boot-starter-security` | Spring Initializr |
| `postgresql` (driver, `runtime`) | Spring Initializr |
| `springdoc-openapi-starter-webmvc-ui` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-api` `0.12.x` | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-impl` `0.12.x` (`runtime`) | agregada a mano al `pom.xml` |
| `io.jsonwebtoken:jjwt-jackson` `0.12.x` (`runtime`) | agregada a mano al `pom.xml` |

La libreria JWT queda fijada: **`io.jsonwebtoken` (jjwt) version `0.12.x`**, con sus tres
artefactos `jjwt-api`, `jjwt-impl` y `jjwt-jackson`. No esta en el catalogo de Spring
Initializr, asi que se escribe a mano en el `pom.xml`. **Las specs de `ms-canchas`,
`ms-reservas` y `ms-reportes` usan exactamente la misma libreria y version** para validar
el mismo token.

## 9. Decisiones de diseno

| # | Decision | Alternativa descartada | Por que |
|---|---|---|---|
| D-01 | JWT HS256 validado localmente por cada microservicio con `JWT_SECRET` compartido | Token opaco guardado en `usuarios_db` y validado por HTTP contra `ms-usuarios` | El token opaco convierte a `ms-usuarios` en punto unico de falla: si cae, ningun otro servicio autentica. La validacion local mantiene la independencia entre microservicios y el PDF de alcance admite explicitamente una autenticacion basica con roles |
| D-02 | El `rol` viaja como claim del token | Que cada servicio consulte el rol a `ms-usuarios` en cada peticion | Una llamada HTTP por peticion solo para leer el rol agrega latencia y acoplamiento sin aportar nada, ya que el rol casi no cambia |
| D-03 | Sin revocacion ni lista negra de tokens; inactivar un usuario solo bloquea inicios de sesion futuros | Tabla de tokens revocados consultada en cada validacion | Exigiria estado compartido entre los cuatro servicios, justo lo que la arquitectura prohibe. Con 8 horas de vigencia el riesgo es aceptable para el alcance academico |
| D-04 | `401 NO_AUTENTICADO` con mensaje identico para correo inexistente, clave incorrecta y usuario inactivo | Distinguir cada caso con mensajes o codigos propios | Distinguirlos permite enumerar cuentas registradas. Ademas el contrato no define codigo para "usuario inactivo" |
| D-05 | `CambioEstadoRequest.activo` como `Boolean` con `@NotNull` | `boolean` primitivo | Con el primitivo, un cuerpo sin el campo se interpretaria como `false` e inactivaria al usuario en silencio |
| D-06 | Manejador `Exception` -> `500 ERROR_INTERNO`. Resuelto con la opcion (a): `ERROR_INTERNO` se agrego a la tabla "Formato de error" del contrato el 23/08/2026, con su linea en el registro de cambios, porque los cuatro microservicios lo necesitan | Dejar que Spring devuelva su error por defecto | El error por defecto filtra la clase de excepcion y la ruta al cliente, y rompe el formato `{ "codigo", "mensaje" }` |
| D-11 | `405` y `415` se traducen a `400 DATOS_INVALIDOS` declarando `HttpRequestMethodNotSupportedException` y `HttpMediaTypeNotSupportedException` en el manejador | Dejarlos caer en el manejador de `Exception` | Un verbo equivocado o un `Content-Type` que no sea JSON son errores del cliente, no del servidor: reportarlos como `500` es enganoso. No se crean codigos nuevos |
| D-12 | Rutas de `springdoc-openapi` (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) abiertas de forma explicita en el `SecurityFilterChain` | Dejarlas detras del filtro de token | Con la cadena `STATELESS` y `anyRequest().authenticated()`, Swagger responderia `401` y el entregable E-03 no se podria demostrar |
| D-13 | Libreria JWT fijada en `io.jsonwebtoken` (jjwt) `0.12.x`, agregada a mano al `pom.xml` | Dejar la eleccion a cada spec, o usar `spring-boot-starter-oauth2-resource-server` | Si cada microservicio elige libreria o version distinta, la validacion del mismo token se vuelve incoherente. El starter de OAuth2 traeria un modelo de recurso protegido que excede lo que el PDF de alcance pide |
| D-07 | Mapper manual en una clase `UsuarioMapper` con metodos explicitos | MapStruct o reflexion generica | Prohibido por `CLAUDE.md` §3, y el mapeo explicito garantiza que `passwordHash` no pueda filtrarse a una respuesta |
| D-08 | Verificacion previa con `existsByEmail` **y** traduccion de la violacion de `uq_usuario_email` | Solo la verificacion previa | Dos registros simultaneos con el mismo correo pasarian la verificacion y el segundo reventaria como `500` |
| D-09 | El listado devuelve todos los usuarios sin paginar | Paginacion con `page` y `size` | El contrato no congela ningun parametro de consulta; agregarlo seria ampliar el alcance (S-07) |
| D-10 | La entidad se adapta al DDL existente y el enum `Rol` se persiste como `STRING` | Ajustar el DDL a la entidad, u `ORDINAL` | `CLAUDE.md` §3 manda: el esquema lo dicta el DDL versionado. `ORDINAL` guardaria numeros y violaria el `CHECK` de la columna |

## 10. Puntos pendientes

Ninguno. El unico punto abierto (D-06) fue resuelto el 23/08/2026 con la opcion (a):
`ERROR_INTERNO` (HTTP 500) ya figura en la tabla "Formato de error" de
`docs/contratos/README.md` y en su registro de cambios.

Todo el diseno se apoya en campos, rutas y codigos congelados en el contrato.
