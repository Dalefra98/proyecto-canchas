# Spec 02 — ms-usuarios · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 02").
La compuerta C2 (`design.md`) sigue pendiente: no se escribe codigo de produccion hasta
que el diseno este aprobado por escrito.

Las cuatro preguntas abiertas (P-01 a P-04) fueron resueltas por el responsable el
23/08/2026 y ya estan incorporadas a este documento; los supuestos S-01 a S-07 quedaron
confirmados tal como se escribieron.

Fuentes leidas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf`, `.claude/specs/01-modelo-y-contratos/`
(aprobada) e `infra/postgres/02-ddl-usuarios.sql` + `05-seed.sql` (ya aplicados).

## 1. Objetivo

Implementar el microservicio `ms-usuarios`: registro de usuarios, inicio de sesion con
emision de token, listado de usuarios y activacion/inactivacion de usuarios por parte del
ADMIN. Es el unico servicio dueno de `usuarios_db` y la unica fuente de identidad y rol
para el resto del sistema.

El alcance funcional son exactamente los cuatro endpoints ya congelados en
`docs/contratos/README.md` para el dominio `usuarios`. Ni uno mas.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto Maven `backend/ms-usuarios` con Java 21 + Spring Boot, capas `controller` -> `service` -> `repository` -> `entity`, DTOs y mapper manual |
| E-02 | Entidad JPA que valida contra la tabla `usuario` existente con `spring.jpa.hibernate.ddl-auto=validate` |
| E-03 | Los cuatro endpoints congelados de `/api/usuarios` con sus codigos de respuesta |
| E-04 | Emision y validacion de token JWT HS256, y hash BCrypt de contrasenas |
| E-05 | `@RestControllerAdvice` que traduce toda excepcion al formato `{ "codigo", "mensaje" }` |
| E-06 | Documentacion `springdoc-openapi` con los codigos de error de cada endpoint |
| E-07 | Servicio `ms-usuarios` agregado a `docker-compose.yml` |

### 2.1 Coordenadas Maven y paquete (decision P-03)

| Dato | Valor |
|---|---|
| `groupId` | `ec.ups.dae` |
| `artifactId` | `ms-usuarios` |
| Paquete raiz | `ec.ups.dae.usuarios` |
| Java | 21 |

### 2.2 Restriccion de construccion del esqueleto

El esqueleto Maven se genera con **Spring Initializr por URL** (`start.spring.io`),
descargando el `.zip` del proyecto. **Nunca** se genera con `mvn` ni `mvn archetype` en el
host: en esta maquina no hay Maven instalado (`CLAUDE.md` §1). La compilacion posterior se
hace unicamente dentro del contenedor `maven:3.9-eclipse-temurin-21`.

### 2.3 Configuracion en `docker-compose.yml` (decision P-02)

| Dato | Valor |
|---|---|
| Nombre del servicio | `ms-usuarios` |
| Puerto interno | `8080` |
| Puerto publicado | `8082:8080` — **temporal**, solo para probar con `curl.exe` durante el desarrollo; se elimina cuando exista el gateway Nginx |
| `depends_on` | `postgres` con `condition: service_healthy` |

Variables de entorno:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/usuarios_db` |
| `SPRING_DATASOURCE_USERNAME` | `usuarios_user` |
| `SPRING_DATASOURCE_PASSWORD` | `usuarios_pass` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `JWT_SECRET` | tomada de `.env` (ya existente) |

## 3. Historias de usuario

### HU-01 — Registro de usuario

Como visitante, necesito registrarme con nombre, correo y contrasena, para poder iniciar
sesion y reservar canchas.

- **CUANDO** se envie `POST /api/usuarios` con `nombre`, `email` y `password` validos,
  **ENTONCES** la respuesta sera `201` con el objeto del usuario creado, cuyo `rol` sera
  `USUARIO` y `activo` sera `true`.
- **CUANDO** el registro sea exitoso, **ENTONCES** el cuerpo de la respuesta **no**
  contendra el campo `password` ni ninguna representacion del hash.
- **CUANDO** el registro sea exitoso, **ENTONCES** la columna `password_hash` de la fila
  creada empezara con `$2` (hash BCrypt) y nunca contendra la contrasena en claro.
- **SI** falta `nombre`, `email` o `password`, o `email` no tiene formato de correo,
  **ENTONCES** la respuesta sera `400` con `codigo = DATOS_INVALIDOS`.
- **SI** `password` tiene menos de 8 o mas de 100 caracteres, **ENTONCES** la respuesta
  sera `400` con `codigo = DATOS_INVALIDOS`. No se exigen mayusculas ni simbolos
  (decision P-04).
- **SI** el `email` ya existe en la tabla `usuario`, **ENTONCES** la respuesta sera `409`
  con `codigo = EMAIL_DUPLICADO`.
- **SI** la peticion incluye un campo `rol`, **ENTONCES** se ignora: el registro publico
  siempre crea `USUARIO` (ver supuesto S-03).

### HU-02 — Inicio de sesion

Como usuario registrado, necesito iniciar sesion con mi correo y contrasena, para obtener
un token y que el shell sepa quien soy y cual es mi rol.

- **CUANDO** se envie `POST /api/usuarios/sesiones` con `email` y `password` correctos de
  un usuario con `activo = true`, **ENTONCES** la respuesta sera `200` con el payload
  congelado `LoginResponse`: `token` y `usuario` con `usuarioId`, `nombre`, `email`, `rol`
  y `activo`.
- **CUANDO** se inicie sesion con `admin@canchas.ec`, **ENTONCES** el `rol` devuelto sera
  `ADMIN`; con `usuario@canchas.ec` sera `USUARIO`.
- **SI** falta `email` o `password` en el cuerpo, **ENTONCES** la respuesta sera `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** el `email` no existe o la contrasena no coincide con el hash BCrypt almacenado,
  **ENTONCES** la respuesta sera `401` con `codigo = NO_AUTENTICADO` y el mismo mensaje en
  ambos casos, sin revelar si el correo existe.
- **SI** el usuario tiene `activo = false`, **ENTONCES** la respuesta sera `401` con
  `codigo = NO_AUTENTICADO` (ver supuesto S-04).
- **CUANDO** la respuesta sea `200`, **ENTONCES** el cuerpo no contendra `password`.

### HU-03 — Listado de usuarios

Como administrador, necesito ver todos los usuarios con su rol y estado, para poder
administrarlos.

- **CUANDO** un ADMIN envie `GET /api/usuarios` con su token, **ENTONCES** la respuesta
  sera `200` con un arreglo de usuarios, cada uno con `usuarioId`, `nombre`, `email`,
  `rol` y `activo`, sin `password`.
- **CUANDO** el listado incluya usuarios inactivos, **ENTONCES** apareceran igual, con
  `activo = false`; el listado no oculta ni borra registros.
- **SI** la peticion no trae token o el token es invalido o expirado, **ENTONCES** la
  respuesta sera `401` con `codigo = NO_AUTENTICADO`.
- **SI** el token corresponde a un `USUARIO`, **ENTONCES** la respuesta sera `403` con
  `codigo = SIN_PERMISO`.

### HU-04 — Activar o inactivar un usuario

Como administrador, necesito activar o inactivar usuarios, para bloquear el acceso sin
borrar su historial de reservas.

- **CUANDO** un ADMIN envie `PATCH /api/usuarios/{usuarioId}/estado` con el cuerpo
  `{ "activo": false }`, **ENTONCES** la respuesta sera `200` con el usuario actualizado y
  `activo = false`.
- **CUANDO** el usuario quede inactivo, **ENTONCES** un intento posterior de
  `POST /api/usuarios/sesiones` con sus credenciales devolvera `401` (HU-02).
- **CUANDO** un ADMIN envie el mismo endpoint con `{ "activo": true }`, **ENTONCES** el
  usuario vuelve a poder iniciar sesion.
- **SI** el cuerpo no trae el campo `activo` o no es booleano, **ENTONCES** la respuesta
  sera `400` con `codigo = DATOS_INVALIDOS`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** el `usuarioId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** el ADMIN intenta inactivarse a si mismo, **ENTONCES** la respuesta sera `400` con
  `codigo = DATOS_INVALIDOS` (ver supuesto S-05).

### HU-05 — Identidad para el resto del sistema

Como equipo, necesito que el token emitido por `ms-usuarios` permita a los demas
microservicios conocer el `usuarioId` y el `rol` de quien llama, para aplicar RN-03 y
RN-07 sin leer `usuarios_db`.

- **CUANDO** `ms-usuarios` emita un token, **ENTONCES** sera un **JWT firmado con HS256**
  usando el secreto de la variable de entorno `JWT_SECRET`, con los claims `sub` =
  `usuarioId`, `rol` y `exp`.
- **CUANDO** se emita el token, **ENTONCES** su vigencia sera de **8 horas** desde la
  emision.
- **CUANDO** otro microservicio reciba una peticion con ese token, **ENTONCES** lo validara
  **localmente** con el mismo `JWT_SECRET` y leera `usuarioId` y `rol` de los claims, sin
  llamar a `ms-usuarios` por HTTP y sin consultar `usuarios_db`.
- **SI** un token esta vencido, tiene firma invalida o no fue emitido con el mismo secreto,
  **ENTONCES** la respuesta a esa peticion sera `401` con `codigo = NO_AUTENTICADO`.
- **CUANDO** se revise esta spec, **ENTONCES** no existira ningun endpoint de validacion de
  token: no se agrega a las rutas congeladas (decision P-01). La justificacion de la
  decision se documenta en `design.md`.

### HU-06 — Errores uniformes y sin stacktrace

Como consumidor de la API, necesito que todos los errores tengan la misma forma, para
poder mostrar mensajes al usuario sin adivinar.

- **CUANDO** cualquier endpoint falle, **ENTONCES** el cuerpo sera exactamente
  `{ "codigo": "...", "mensaje": "..." }` con un `codigo` de la tabla "Formato de error"
  del contrato.
- **CUANDO** ocurra una excepcion no prevista, **ENTONCES** el cliente no recibira
  stacktrace, nombre de clase Java ni consulta SQL.

### HU-07 — Arranque validado contra el esquema existente

Como equipo, necesito que el servicio arranque solo si la entidad calza con el DDL ya
versionado, para detectar desalineaciones al instante.

- **CUANDO** se levante `ms-usuarios` con `docker compose up -d --build ms-usuarios` y las
  bases ya creadas, **ENTONCES** el servicio quedara arriba con
  `spring.jpa.hibernate.ddl-auto=validate` sin errores.
- **SI** la entidad no calza con `infra/postgres/02-ddl-usuarios.sql`, **ENTONCES** se
  corrige la entidad, nunca el DDL.
- **CUANDO** el servicio este arriba, **ENTONCES** expondra su documentacion
  `springdoc-openapi` con los cuatro endpoints y sus codigos de error declarados.

## 4. Reglas de negocio cubiertas

| RN | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | Reserva = cancha + fecha + bloque de 1 hora | No aplica |
| RN-02 | No reservar un bloque ocupado | No aplica |
| RN-03 | Usuario cancela lo propio; admin cualquiera | Parcial — `ms-usuarios` provee la identidad y el `rol` que `ms-reservas` usara para decidir (HU-02, HU-05) |
| RN-04 | Solo se cancela una reserva no ocurrida | No aplica |
| RN-05 | Cancelar libera el bloque | No aplica |
| RN-06 | Limite configurable de reservas activas | No aplica |
| RN-07 | Solo el admin gestiona canchas y horarios | Parcial — el token identifica al ADMIN; la autorizacion sobre canchas vive en `ms-canchas` (HU-05) |
| RN-08 | Estados CONFIRMADA / CANCELADA / FINALIZADA | No aplica |

Reglas propias de este microservicio, derivadas del contrato y del documento de alcance:
`email` unico (HU-01); `password` solo en request y persistido como hash BCrypt (HU-01,
HU-02); solo el ADMIN lista y cambia el estado de usuarios (HU-03, HU-04); un usuario
inactivo no inicia sesion (HU-02).

## 5. Contrato REST

Los nombres de campo, rutas, roles y codigos de error son los de
`docs/contratos/README.md`. Esta spec no los redefine: los usa tal cual.

| Verbo | Ruta | Rol | Respuestas |
|---|---|---|---|
| POST | `/api/usuarios/sesiones` | publico | 200, 400, 401 |
| POST | `/api/usuarios` | publico | 201, 400, 409 |
| GET | `/api/usuarios` | ADMIN | 200, 401, 403 |
| PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | 200, 400, 401, 403, 404 |

Campos JSON usados, con los nombres exactos del contrato:

| Concepto | Campo | Tipo / valores | Uso |
|---|---|---|---|
| Identificador de usuario | `usuarioId` | number | respuesta y path del PATCH |
| Nombre de usuario | `nombre` | string | request y respuesta |
| Correo de acceso | `email` | string | request y respuesta |
| Contrasena | `password` | string | **solo en request** de registro e inicio de sesion |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | respuesta |
| Usuario activo | `activo` | boolean | request del PATCH y respuesta |
| Token de sesion | `token` | string | respuesta de inicio de sesion |
| Usuario de la sesion | `usuario` | objeto `UsuarioResponse` | respuesta de inicio de sesion |

Payload congelado de respuesta de inicio de sesion (`LoginResponse`):

```json
{
  "token": "...",
  "usuario": { "usuarioId": 1, "nombre": "Ana", "email": "ana@demo.ec", "rol": "USUARIO", "activo": true }
}
```

Codigos de error usados: `DATOS_INVALIDOS` (400), `NO_AUTENTICADO` (401), `SIN_PERMISO`
(403), `NO_ENCONTRADO` (404), `EMAIL_DUPLICADO` (409).

## 6. Fuera de alcance de esta spec

- `ms-canchas`, `ms-reservas`, `ms-reportes` y sus endpoints.
- Todo el frontend: `shell`, `mf-reservas`, `mf-administracion`, `mf-reportes`, Module
  Federation y la pantalla de inicio de sesion.
- Cualquier operacion de usuarios que no este en las cuatro rutas congeladas: editar
  nombre o correo, cambiar o recuperar contrasena, borrar usuario, cerrar sesion en el
  servidor, cambiar el `rol` de un usuario existente, consultar el perfil propio.
- Modificar `infra/postgres/*.sql`. El esquema quedo congelado en la spec 01.
- Refresco o revocacion de tokens, sesiones persistidas en base, SSO.
- Endpoint de validacion de token para otros microservicios: no se crea (decision P-01).
- El gateway Nginx que enrutara `/api` hacia los microservicios: mientras no exista, el
  puerto `8082` publicado es la unica via de prueba y es temporal.
- Generar el esqueleto con `mvn` en el host: prohibido por `CLAUDE.md` §1 (ver §2.2).
- Prohibido por `CLAUDE.md` §2: pagos, notificaciones (correo/SMS/push), reservas
  recurrentes, torneos, app movil nativa, reportes BI.
- Prohibido por `CLAUDE.md` §3: Lombok, MapStruct, `@Autowired` en campos, clases `Util`
  genericas.

## 7. Datos que faltaron y hubo que suponer

Sin supuestos abiertos. Las cuatro preguntas fueron resueltas por el responsable el
23/08/2026 y sus decisiones ya estan incorporadas a este documento.

### Decisiones tomadas

| # | Decision | Donde quedo |
|---|---|---|
| P-01 | El `token` es un JWT firmado con HS256 y el secreto `JWT_SECRET` de `.env`. Claims: `sub` = `usuarioId`, `rol`, `exp`. Vigencia 8 horas. Cada microservicio lo valida **localmente**; no se crea endpoint de validacion ni se llama a `ms-usuarios` por HTTP. La justificacion (el PDF admite autenticacion basica con roles y la validacion local evita acoplar los cuatro servicios a la disponibilidad de `ms-usuarios`, respetando la independencia entre microservicios) se documenta como decision de diseno en `design.md` | HU-05, E-04, §2.3, §6 |
| P-02 | Servicio `ms-usuarios`, puerto interno `8080`, publicado `8082:8080` **temporalmente** para pruebas con `curl.exe` hasta que exista el gateway Nginx. Variables de datasource hacia `usuarios_db` con `usuarios_user` / `usuarios_pass`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` y `JWT_SECRET` desde `.env`. `depends_on: postgres` con `condition: service_healthy` | §2.3, §6, HU-07 |
| P-03 | `groupId` `ec.ups.dae`, `artifactId` `ms-usuarios`, paquete raiz `ec.ups.dae.usuarios` | §2.1 |
| P-04 | `password`: minimo 8 y maximo 100 caracteres, sin exigir mayusculas ni simbolos | HU-01 |
| Extra | El esqueleto Maven se genera con Spring Initializr por URL, nunca con `mvn` en el host | §2.2, §6 |

### Supuestos confirmados por el responsable el 23/08/2026

| # | Supuesto | Base |
|---|---|---|
| S-01 | El cuerpo de `POST /api/usuarios` es `{ nombre, email, password }` y el de `POST /api/usuarios/sesiones` es `{ email, password }`. El contrato congela los campos, no la forma del request | `docs/contratos/README.md`, "Campos acordados" |
| S-02 | El cuerpo de `PATCH /api/usuarios/{usuarioId}/estado` es `{ "activo": <boolean> }`, por simetria con el campo congelado `activo` | Contrato + nombre de la ruta |
| S-03 | El registro publico crea siempre `rol = USUARIO` y `activo = true`. El contrato no expone ningun endpoint para crear un ADMIN y el ADMIN inicial ya viene en `05-seed.sql` | Alcance funcional: el registro es del usuario final |
| S-04 | Un usuario con `activo = false` no puede iniciar sesion y el rechazo es `401 NO_AUTENTICADO`. El contrato no define un codigo especifico para "usuario inactivo" | Solo `activo` tiene sentido como bloqueo de acceso |
| S-05 | Un ADMIN no puede inactivarse a si mismo y ese intento responde `400 DATOS_INVALIDOS`. **Se mantiene por decision del responsable** | Evita dejar el sistema sin administrador; ninguna RN lo dice |
| S-06 | Longitudes maximas de entrada: `nombre` 80 y `email` 120 caracteres, tomadas del DDL ya aplicado | `infra/postgres/02-ddl-usuarios.sql` |
| S-07 | `GET /api/usuarios` devuelve todos los usuarios sin paginacion ni filtros, porque el contrato no congela ningun parametro de consulta | "Rutas REST congeladas" |
