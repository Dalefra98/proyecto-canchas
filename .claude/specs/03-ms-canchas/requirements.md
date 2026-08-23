# Spec 03 — ms-canchas · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 03").
La compuerta C2 (`design.md`) sigue pendiente: no se escribe codigo de produccion hasta
que el diseno este aprobado por escrito.

Las seis preguntas abiertas (P-01 a P-06) fueron resueltas por el responsable el
23/08/2026 y ya estan incorporadas a este documento. Las decisiones P-01, P-02, P-05 y
P-06 obligaron a modificar `docs/contratos/README.md`: dos codigos de error nuevos
(`NOMBRE_DUPLICADO`, `BLOQUEO_DUPLICADO`), el parametro opcional `fecha` en la ruta de
bloqueos y la nota de filtrado por rol del catalogo. Ese cambio ya esta aplicado y
registrado en el "Registro de cambios" del contrato.

El unico punto que sigue abierto es **A-01** (§6.1): el mecanismo concreto de credenciales
de servicio para las llamadas internas entre microservicios. No bloquea esta spec — se
define en la spec 04 y no se implementa aqui.

Fuentes leidas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.4, 3.4, 4.2,
4.3), `.claude/specs/01-modelo-y-contratos/` y `.claude/specs/02-ms-usuarios/`
(aprobadas), `infra/postgres/03-ddl-canchas.sql` y `05-seed.sql` (ya aplicados),
`docker-compose.yml`.

## 1. Objetivo

Implementar el microservicio `ms-canchas`: catalogo de canchas (crear, editar, consultar,
inactivar) y bloqueos de mantenimiento (crear, listar, eliminar). Es el unico servicio
dueno de `canchas_db` y la unica fuente de verdad del nombre, deporte, horario de atencion
y estado de cada cancha, y de los bloqueos de mantenimiento.

El alcance funcional son exactamente los ocho endpoints ya congelados en
`docs/contratos/README.md` para el dominio `canchas`. Ni uno mas.

`ms-reservas` y `ms-reportes` consumiran este servicio por HTTP para calcular
disponibilidad y ocupacion; esta spec no implementa esos consumidores.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto Maven `backend/ms-canchas` con Java 21 + Spring Boot 3.5.3, capas `controller` -> `service` -> `repository` -> `entity`, DTOs y mapper manual |
| E-02 | Entidades JPA `Cancha` y `BloqueoMantenimiento` que validan contra las tablas existentes con `spring.jpa.hibernate.ddl-auto=validate` |
| E-03 | Los ocho endpoints congelados de `/api/canchas` con sus codigos de respuesta |
| E-04 | Validacion **local** del JWT HS256 emitido por `ms-usuarios` (mismo `JWT_SECRET`), leyendo `sub` y `rol`, sin llamar a `ms-usuarios` ni leer `usuarios_db` |
| E-05 | `@RestControllerAdvice` que traduce toda excepcion al formato `{ "codigo", "mensaje" }` |
| E-06 | Documentacion `springdoc-openapi` con los codigos de error de cada endpoint |
| E-07 | `Dockerfile` segun el patron oficial de `CLAUDE.md` §1 y servicio `ms-canchas` agregado a `docker-compose.yml` |

### 2.1 Coordenadas Maven y paquete (decision P-03)

| Dato | Valor |
|---|---|
| `groupId` | `ec.ups.dae` |
| `artifactId` | `ms-canchas` |
| Paquete raiz | `ec.ups.dae.canchas` |
| Java | 21 |
| Spring Boot | 3.5.3 (`CLAUDE.md` §3) |
| springdoc | `springdoc-openapi-starter-webmvc-ui` 2.8.6 |
| jjwt | 0.12.6 |

### 2.2 Restriccion de construccion del esqueleto

El esqueleto Maven se genera con **Spring Initializr por URL** (`start.spring.io`),
descargando el `.zip`, y el `<parent>` se corrige a mano a 3.5.3 porque Initializr ya solo
entrega la rama 4.x (`CLAUDE.md` §3). **Nunca** se ejecuta `mvn` en el host: en esta
maquina no hay Maven (`CLAUDE.md` §1). La compilacion se hace solo dentro del contenedor
`maven:3.9-eclipse-temurin-21` con el volumen `m2repo`, y el `Dockerfile` usa el cache
mount de BuildKit del patron oficial.

### 2.3 Configuracion en `docker-compose.yml` (decision P-04)

| Dato | Valor |
|---|---|
| Nombre del servicio | `ms-canchas` |
| Puerto interno | `8080` |
| Puerto publicado | `8083:8080` — **temporal**, solo para probar con `curl.exe`; se elimina cuando exista el gateway Nginx (`8082` ya lo ocupa `ms-usuarios`) |
| `depends_on` | `postgres` con `condition: service_healthy` |

Variables de entorno:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/canchas_db` |
| `SPRING_DATASOURCE_USERNAME` | `canchas_user` |
| `SPRING_DATASOURCE_PASSWORD` | `canchas_pass` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `JWT_SECRET` | la misma de `.env` que usa `ms-usuarios` |

## 3. Historias de usuario

### HU-01 — Listar el catalogo de canchas

Como usuario autenticado, necesito ver las canchas con su deporte, horario de atencion y
estado, para elegir donde reservar.

- **CUANDO** un ADMIN o un USUARIO envie `GET /api/canchas` con un token valido,
  **ENTONCES** la respuesta sera `200` con un arreglo de canchas, cada una con `canchaId`,
  `nombre`, `deporte`, `horaApertura`, `horaCierre` y `activa`.
- **CUANDO** se devuelva una cancha, **ENTONCES** `horaApertura` y `horaCierre` tendran
  formato `HH:mm` y `deporte` sera uno de `PADEL`, `TENIS`, `BASQUET`.
- **CUANDO** quien llame sea un `ADMIN`, **ENTONCES** el listado incluira **todas** las
  canchas, activas e inactivas, cada una con su `activa` real (decision P-05).
- **CUANDO** quien llame sea un `USUARIO`, **ENTONCES** el listado incluira **solo** las
  canchas con `activa = true`; las inactivas no aparecen (decision P-05).
- **CUANDO** se filtre por rol, **ENTONCES** se hara **sin parametro de consulta**: la
  decision sale del claim `rol` del token, no de la peticion. El contrato no gana ningun
  parametro en esta ruta.
- **CUANDO** no exista ninguna cancha visible para ese rol, **ENTONCES** la respuesta sera
  `200` con un arreglo vacio, nunca `404`.
- **SI** la peticion no trae token, o el token es invalido, esta vencido o fue firmado con
  otro secreto, **ENTONCES** la respuesta sera `401` con `codigo = NO_AUTENTICADO`.

### HU-02 — Consultar una cancha por su identificador

Como consumidor autenticado (persona o `ms-reservas`), necesito obtener una cancha
concreta con su horario de atencion, para calcular sus bloques horarios.

- **CUANDO** se envie `GET /api/canchas/{canchaId}` con token valido y la cancha exista,
  **ENTONCES** la respuesta sera `200` con `canchaId`, `nombre`, `deporte`, `horaApertura`,
  `horaCierre` y `activa`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** el `canchaId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **CUANDO** la cancha este inactiva y quien llame sea `ADMIN`, **ENTONCES** se devuelve
  `200` con `activa = false` (decision P-05).
- **SI** la cancha esta inactiva y quien llama es `USUARIO`, **ENTONCES** la respuesta sera
  `404` con `codigo = NO_ENCONTRADO`, por coherencia con HU-01: lo que el USUARIO no ve en
  el listado, tampoco existe para el en el detalle (decision P-05, ver consecuencia C-01).

### HU-03 — Crear una cancha (RN-07)

Como administrador, necesito registrar una cancha nueva con su deporte y horario de
atencion, para que los usuarios puedan reservarla.

- **CUANDO** un ADMIN envie `POST /api/canchas` con `nombre`, `deporte`, `horaApertura` y
  `horaCierre` validos, **ENTONCES** la respuesta sera `201` con la cancha creada, con su
  `canchaId` asignado y `activa = true`.
- **SI** falta cualquiera de los cuatro campos, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `deporte` no es `PADEL`, `TENIS` ni `BASQUET`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `horaApertura` u `horaCierre` no tienen formato `HH:mm`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `horaCierre` no es estrictamente mayor que `horaApertura`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`. Esta validacion se hace en el servicio antes de llegar a la
  base; la restriccion `ck_cancha_horario` del DDL es la red de seguridad, no el mecanismo.
- **SI** `nombre` esta vacio o supera 80 caracteres, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS` (longitud tomada del DDL, supuesto S-04).
- **SI** el `nombre` ya pertenece a otra cancha, **ENTONCES** la respuesta sera `409` con
  `codigo = NOMBRE_DUPLICADO` (decision P-01; codigo agregado al contrato el 23/08/2026).
- **CUANDO** se valide el nombre, **ENTONCES** se hara con la misma estrategia de doble
  barrera que `ms-usuarios`: verificacion previa con `existsByNombre` en el servicio, mas
  traduccion de la violacion de `uq_cancha_nombre` al mismo `409 NOMBRE_DUPLICADO` por si
  dos peticiones simultaneas pasan la verificacion a la vez (decision P-01).
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO` (RN-07).
- **SI** la peticion incluye `canchaId` o `activa`, **ENTONCES** se ignoran: el
  identificador lo genera la base y toda cancha nueva nace activa (supuesto S-02).

### HU-04 — Editar una cancha (RN-07)

Como administrador, necesito corregir el nombre, el deporte o el horario de atencion de
una cancha, para mantener el catalogo al dia.

- **CUANDO** un ADMIN envie `PUT /api/canchas/{canchaId}` con `nombre`, `deporte`,
  `horaApertura` y `horaCierre` validos, **ENTONCES** la respuesta sera `200` con la cancha
  actualizada.
- **CUANDO** la edicion sea exitosa, **ENTONCES** el campo `activa` **no** cambia: el
  estado se maneja solo con `PATCH /api/canchas/{canchaId}/estado` (supuesto S-03).
- **SI** el cuerpo incumple cualquiera de las validaciones de HU-03, **ENTONCES** `400`
  con `codigo = DATOS_INVALIDOS`.
- **SI** el `canchaId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO` (RN-07).
- **SI** el nuevo `nombre` ya pertenece a **otra** cancha, **ENTONCES** `409` con
  `codigo = NOMBRE_DUPLICADO`, con la misma doble barrera de HU-03 (decision P-01).
- **CUANDO** el `nombre` enviado sea el que ya tenia esa misma cancha, **ENTONCES** la
  edicion procede con `200`: no es un duplicado consigo misma.
- **SI** el nuevo horario deja fuera bloqueos o reservas ya existentes, **ENTONCES** la
  edicion **no** se bloquea: `ms-canchas` no conoce las reservas y esta spec no agrega esa
  validacion cruzada (ver §7 y supuesto S-06).

### HU-05 — Activar o inactivar una cancha (RN-07)

Como administrador, necesito inactivar una cancha sin borrarla, para retirarla de la
oferta conservando su historial.

- **CUANDO** un ADMIN envie `PATCH /api/canchas/{canchaId}/estado` con el cuerpo
  `{ "activa": false }`, **ENTONCES** la respuesta sera `200` con la cancha actualizada y
  `activa = false`.
- **CUANDO** un ADMIN envie el mismo endpoint con `{ "activa": true }`, **ENTONCES** la
  cancha vuelve a estar activa.
- **CUANDO** se cambie el estado, **ENTONCES** no se borra ninguna fila: ni la cancha ni
  sus bloqueos de mantenimiento.
- **SI** el cuerpo no trae el campo `activa` o no es booleano, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** el `canchaId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO` (RN-07).
- **CUANDO** una cancha quede inactiva, **ENTONCES** `ms-canchas` no cancela ni toca
  reservas existentes: eso es responsabilidad de `ms-reservas` y esta fuera de esta spec.

### HU-06 — Listar los bloqueos de mantenimiento de una cancha

Como usuario autenticado o como `ms-reservas`, necesito conocer los bloqueos de
mantenimiento de una cancha, para no ofrecer bloques que no estan disponibles.

- **CUANDO** un ADMIN o un USUARIO envie `GET /api/canchas/{canchaId}/bloqueos` con token
  valido y la cancha exista, **ENTONCES** la respuesta sera `200` con un arreglo de
  bloqueos, cada uno con `bloqueoId`, `canchaId`, `fecha`, `horaInicio`, `horaFin` y
  `motivo`.
- **CUANDO** la cancha no tenga bloqueos, **ENTONCES** la respuesta sera `200` con un
  arreglo vacio.
- **CUANDO** se devuelva un bloqueo, **ENTONCES** `fecha` tendra formato `AAAA-MM-DD` y
  `horaInicio` / `horaFin` formato `HH:mm`.
- **CUANDO** la peticion incluya el parametro opcional `?fecha=AAAA-MM-DD`, **ENTONCES** la
  respuesta contendra solo los bloqueos de esa cancha en ese dia (decision P-06; parametro
  agregado al contrato el 23/08/2026).
- **CUANDO** la peticion **no** incluya `fecha`, **ENTONCES** se devuelven todos los
  bloqueos de la cancha, sin filtro.
- **SI** `fecha` viene presente pero no tiene formato `AAAA-MM-DD`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **CUANDO** no haya bloqueos en la `fecha` pedida, **ENTONCES** `200` con arreglo vacio,
  nunca `404`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** el `canchaId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.

### HU-07 — Registrar un bloqueo de mantenimiento

Como administrador, necesito bloquear una franja horaria de una cancha por mantenimiento,
para que nadie pueda reservarla en ese periodo.

- **CUANDO** un ADMIN envie `POST /api/canchas/{canchaId}/bloqueos` con `fecha`,
  `horaInicio`, `horaFin` y `motivo` validos, **ENTONCES** la respuesta sera `201` con el
  bloqueo creado, incluyendo su `bloqueoId` y el `canchaId` de la ruta.
- **SI** falta cualquiera de los cuatro campos, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `fecha` no tiene formato `AAAA-MM-DD`, o `horaInicio` / `horaFin` no tienen
  formato `HH:mm`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.
- **SI** `horaFin` no es estrictamente mayor que `horaInicio`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS` (coherente con `ck_bloqueo_rango` del DDL).
- **SI** `motivo` esta vacio o supera 200 caracteres, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS` (longitud tomada del DDL, supuesto S-04).
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** el `canchaId` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** ya existe un bloqueo de esa cancha con la misma `fecha` y la misma `horaInicio`,
  **ENTONCES** `409` con `codigo = BLOQUEO_DUPLICADO` (decision P-02.a; codigo agregado al
  contrato el 23/08/2026).
- **SI** la franja nueva **se solapa parcialmente** con la de otro bloqueo de la misma
  cancha y la misma `fecha`, **ENTONCES** `409` con `codigo = BLOQUEO_DUPLICADO`
  (decision P-02.d). Dos franjas se solapan cuando `horaInicio` de una es menor que
  `horaFin` de la otra y `horaFin` de la primera es mayor que `horaInicio` de la segunda;
  tocarse en un extremo (`09:00`–`11:00` junto a `11:00`–`12:00`) **no** es solaparse.
  Ejemplo del criterio: con `09:00`–`11:00` ya registrado, un `10:00`–`12:00` se rechaza.
- **CUANDO** se valide el solapamiento, **ENTONCES** se hara **en la capa de servicio**,
  consultando los bloqueos de esa cancha y fecha antes de insertar. **No se modifica el
  DDL**: el esquema quedo congelado en la spec 01 y el indice `uq_bloqueo_franja` no
  alcanza, porque solo compara `hora_inicio` exacta.
- **CUANDO** se valide el duplicado exacto, **ENTONCES** se mantiene ademas la doble
  barrera de HU-03: la violacion de `uq_bloqueo_franja` se traduce al mismo
  `409 BLOQUEO_DUPLICADO`, como red de seguridad ante dos peticiones simultaneas. La
  comprobacion de solapamiento parcial no tiene respaldo en la base y vive solo en el
  servicio.
- **CUANDO** se rechace por duplicado exacto o por solapamiento parcial, **ENTONCES** el
  `codigo` sera el mismo `BLOQUEO_DUPLICADO`: no se crea un codigo adicional.
- **SI** `horaInicio` es menor que `horaApertura` de la cancha, o `horaFin` es mayor que su
  `horaCierre`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`: el bloqueo debe caer
  completo dentro del horario de atencion, porque una franja fuera de horario no se puede
  reservar y bloquearla no tiene efecto (decision P-02.b).
- **SI** la `fecha` del bloqueo es anterior a la fecha actual del servidor, **ENTONCES**
  `400` con `codigo = DATOS_INVALIDOS`: bloquear el pasado no tiene efecto y ensucia los
  reportes (decision P-02.c). La fecha de hoy si se admite.
- **CUANDO** se valide contra el horario de atencion, **ENTONCES** se leera de la propia
  cancha en `canchas_db`, no de otro servicio.
- **CUANDO** la cancha tenga `activa = false`, **ENTONCES** el bloqueo **si** se puede
  crear: el alta comprueba que la cancha exista, no su estado. Una cancha inactiva puede
  estar precisamente en mantenimiento y el ADMIN necesita registrar el bloqueo. El horario
  de atencion se valida igual, este la cancha activa o no.

### HU-08 — Eliminar un bloqueo de mantenimiento

Como administrador, necesito eliminar un bloqueo que ya no aplica, para liberar la franja.

- **CUANDO** un ADMIN envie `DELETE /api/canchas/{canchaId}/bloqueos/{id}` y el bloqueo
  exista y pertenezca a esa cancha, **ENTONCES** la respuesta sera `204` sin cuerpo.
- **CUANDO** el bloqueo se elimine, **ENTONCES** dejara de aparecer en
  `GET /api/canchas/{canchaId}/bloqueos` y la franja quedara nuevamente disponible para
  `ms-reservas`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es `USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **SI** el `canchaId` no existe, el `{id}` no existe, o el bloqueo existe pero pertenece a
  otra cancha, **ENTONCES** `404` con `codigo = NO_ENCONTRADO` (supuesto S-05).
- **CUANDO** se elimine un bloqueo ya eliminado, **ENTONCES** la respuesta sera `404`, no
  `204`.

### HU-09 — Autorizacion por rol con el token de `ms-usuarios`

Como equipo, necesito que `ms-canchas` decida quien puede escribir en el catalogo usando
el mismo token emitido por `ms-usuarios`, para cumplir RN-07 sin acoplar los servicios.

- **CUANDO** llegue una peticion con `Authorization: Bearer <token>`, **ENTONCES**
  `ms-canchas` validara la firma **localmente** con `JWT_SECRET` y leera `sub`
  (`usuarioId`) y `rol` de los claims, sin llamar a `ms-usuarios` por HTTP y sin consultar
  `usuarios_db`.
- **SI** falta el encabezado, el token esta vencido, la firma no coincide o el token esta
  mal formado, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** el claim `rol` es `USUARIO` y la operacion es de escritura (`POST`, `PUT`,
  `PATCH`, `DELETE`), **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **CUANDO** la operacion sea de lectura (`GET`), **ENTONCES** ambos roles la ejecutan.
- **CUANDO** se consulte la documentacion `springdoc-openapi`, **ENTONCES** sus rutas
  estaran abiertas sin token, igual que en `ms-usuarios`.

### HU-10 — Errores uniformes y sin stacktrace

Como consumidor de la API, necesito que todos los errores tengan la misma forma, para
mostrar mensajes sin adivinar.

- **CUANDO** cualquier endpoint falle, **ENTONCES** el cuerpo sera exactamente
  `{ "codigo": "...", "mensaje": "..." }` con un `codigo` de la tabla "Formato de error"
  del contrato.
- **CUANDO** llegue un cuerpo mal formado, un tipo de contenido no soportado o un metodo
  no permitido, **ENTONCES** la respuesta sera `400` con `codigo = DATOS_INVALIDOS`, igual
  que en `ms-usuarios`.
- **CUANDO** ocurra una excepcion no prevista, **ENTONCES** la respuesta sera `500` con
  `codigo = ERROR_INTERNO` y el cliente no recibira stacktrace, nombre de clase Java ni
  consulta SQL.

### HU-11 — Arranque validado contra el esquema existente

Como equipo, necesito que el servicio arranque solo si las entidades calzan con el DDL ya
versionado, para detectar desalineaciones al instante.

- **CUANDO** se levante `docker compose up -d --build ms-canchas` con `postgres` en estado
  `healthy`, **ENTONCES** el servicio quedara arriba con
  `spring.jpa.hibernate.ddl-auto=validate` sin errores.
- **SI** una entidad no calza con `infra/postgres/03-ddl-canchas.sql`, **ENTONCES** se
  corrige la entidad, nunca el DDL.
- **CUANDO** el servicio este arriba, **ENTONCES** `GET /api/canchas` devolvera las tres
  canchas del seed (`Padel 1`, `Tenis 1`, `Basquet 1`, todas de `07:00` a `22:00` y
  activas).
- **CUANDO** el servicio este arriba, **ENTONCES** expondra su documentacion
  `springdoc-openapi` con los ocho endpoints y sus codigos de error declarados.

## 4. Reglas de negocio cubiertas

| RN | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | Reserva = cancha + fecha + bloque de 1 hora | No aplica — `ms-canchas` no crea reservas. Aporta el horario de atencion del que `ms-reservas` deriva los bloques (HU-02) |
| RN-02 | No reservar un bloque ocupado | No aplica — la validacion de solapamiento de reservas vive en `ms-reservas`. `ms-canchas` solo aporta los bloqueos de mantenimiento (HU-06) |
| RN-03 | Usuario cancela lo propio; admin cualquiera | No aplica |
| RN-04 | Solo se cancela una reserva no ocurrida | No aplica |
| RN-05 | Cancelar libera el bloque | No aplica |
| RN-06 | Limite configurable de reservas activas | No aplica |
| RN-07 | **Solo el admin crea, edita o inactiva canchas y define su horario de atencion** | **Cubierta por completo** — HU-03, HU-04, HU-05 y HU-09. Toda escritura exige `rol = ADMIN`; el USUARIO recibe `403 SIN_PERMISO`. El horario de atencion (`horaApertura` / `horaCierre`) solo se define y edita por ADMIN |
| RN-08 | Estados CONFIRMADA / CANCELADA / FINALIZADA | No aplica — `ms-canchas` no conoce el estado de las reservas |

Reglas propias de este microservicio, derivadas del contrato, del DDL congelado y del
documento de alcance (§3.3.4 "Gestion de canchas"):

- El `nombre` de cancha es unico (`uq_cancha_nombre`): duplicado responde
  `409 NOMBRE_DUPLICADO` — HU-03, HU-04.
- `horaCierre > horaApertura` en toda cancha (`ck_cancha_horario`) — HU-03, HU-04.
- `deporte` solo puede ser `PADEL`, `TENIS` o `BASQUET` (`ck_cancha_deporte`) — HU-03.
- `horaFin > horaInicio` en todo bloqueo (`ck_bloqueo_rango`) — HU-07.
- No hay dos bloqueos de la misma cancha con igual `fecha` y `horaInicio`
  (`uq_bloqueo_franja`): duplicado responde `409 BLOQUEO_DUPLICADO` — HU-07.
- Dos bloqueos de la misma cancha y fecha **no pueden solaparse ni parcialmente**:
  `409 BLOQUEO_DUPLICADO`. Se valida en el servicio, no en la base — HU-07.
- Todo bloqueo cae dentro del horario de atencion de su cancha y en una fecha no pasada —
  HU-07.
- El catalogo que ve un `USUARIO` contiene solo canchas activas — HU-01, HU-02.
- Inactivar una cancha no borra filas: es un cambio de estado (HU-05).
- Un bloqueo pertenece a una y solo una cancha (`fk_bloqueo_cancha`) — HU-08.

## 5. Contrato REST

Los nombres de campo, rutas, roles y codigos de error son los de
`docs/contratos/README.md`. Esta spec no los redefine: los usa tal cual.

| Verbo | Ruta | Rol | Respuestas |
|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | 200, 401 |
| GET | `/api/canchas/{canchaId}` | ADMIN, USUARIO | 200, 401, 404 |
| POST | `/api/canchas` | ADMIN | 201, 400, 401, 403, 409 |
| PUT | `/api/canchas/{canchaId}` | ADMIN | 200, 400, 401, 403, 404, 409 |
| PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | 200, 400, 401, 403, 404 |
| GET | `/api/canchas/{canchaId}/bloqueos?fecha` | ADMIN, USUARIO | 200, 400, 401, 404 |
| POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | 201, 400, 401, 403, 404, 409 |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | 204, 401, 403, 404 |

Parametros de consulta: solo `fecha` (`AAAA-MM-DD`, **opcional**) en
`GET /api/canchas/{canchaId}/bloqueos`. El filtrado del catalogo por rol no usa parametro:
sale del claim `rol` del token.

Campos JSON usados, con los nombres exactos del contrato:

| Concepto | Campo | Tipo / valores | Uso |
|---|---|---|---|
| Identificador de cancha | `canchaId` | number | respuesta, path de cancha y bloqueos |
| Nombre de cancha | `nombre` | string | request y respuesta de cancha |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | request y respuesta de cancha |
| Hora de apertura de la cancha | `horaApertura` | string `HH:mm` | request y respuesta de cancha |
| Hora de cierre de la cancha | `horaCierre` | string `HH:mm` | request y respuesta de cancha |
| Cancha activa | `activa` | boolean | request del PATCH de estado y respuesta de cancha |
| Identificador de bloqueo | `bloqueoId` | number | respuesta de bloqueo |
| Fecha | `fecha` | string `AAAA-MM-DD` | request y respuesta de bloqueo |
| Hora de inicio | `horaInicio` | string `HH:mm` | request y respuesta de bloqueo |
| Hora de fin | `horaFin` | string `HH:mm` | request y respuesta de bloqueo |
| Motivo del bloqueo | `motivo` | string | request y respuesta de bloqueo |

Composicion de un bloqueo, segun las "Notas de uso" del contrato: `bloqueoId`, `canchaId`,
`fecha`, `horaInicio`, `horaFin` y `motivo`.

Codigos de error usados: `DATOS_INVALIDOS` (400), `NO_AUTENTICADO` (401), `SIN_PERMISO`
(403), `NO_ENCONTRADO` (404), `NOMBRE_DUPLICADO` (409), `BLOQUEO_DUPLICADO` (409),
`ERROR_INTERNO` (500). Los dos codigos `409` se agregaron al contrato el 23/08/2026 por
las decisiones P-01 y P-02 y son de uso exclusivo de `ms-canchas`.

Nota literal sobre la ruta congelada: el ultimo endpoint usa `{id}`, no `{bloqueoId}`. Se
implementa tal cual esta congelado; el campo del cuerpo sigue siendo `bloqueoId`.

## 6. Consumidores previstos (contexto, no alcance)

`ms-reservas` usara `GET /api/canchas/{canchaId}` (horario de atencion) y
`GET /api/canchas/{canchaId}/bloqueos?fecha=...` para armar `DisponibilidadResponse`, y
`ms-reportes` usara `GET /api/canchas` para el nombre y el deporte de cada fila. Por eso
ambas rutas de lectura estan abiertas a `ADMIN` y `USUARIO` (cambio del 23/08/2026 en el
contrato) y por eso el filtro `fecha` es opcional (decision P-06). Esta spec **no**
implementa ningun cliente HTTP hacia otros servicios.

### 6.1 C-01 — Llamadas internas entre microservicios (decidido aqui)

Consecuencia de P-05: para un `USUARIO`, una cancha inactiva responde `404`. Si
`ms-reservas` propagara el token del usuario final, no podria resolver reservas historicas
sobre una cancha inactivada despues.

**Decision del responsable (23/08/2026):** `ms-reservas` **no propaga** el token del
usuario final al consultar `GET /api/canchas/{canchaId}`; llama con **credenciales de
servicio**, porque necesita ver tambien las canchas inactivas.

Lo que eso significa para `ms-canchas`:

- `ms-canchas` debera **aceptar peticiones internas de otros microservicios sin exigir el
  rol `ADMIN`**, y responder a esas peticiones con la vista completa del catalogo
  (inactivas incluidas), igual que a un `ADMIN`.
- Ese llamador interno no es un `USUARIO` ni un `ADMIN`: es un tercer tipo de consumidor,
  y el filtrado por rol de HU-01 y HU-02 no le aplica.

**Asunto abierto A-01, a definir en la spec 04 (`ms-reservas`):** el mecanismo concreto de
las credenciales de servicio — que es (token de servicio firmado con `JWT_SECRET` y un
claim propio, cabecera compartida, u otra cosa), quien lo emite, como se configura y como
lo verifica `ms-canchas`.

**No se implementa nada de esto en la spec 03.** Los ocho endpoints se implementan tal como
estan descritos en las HU-01 a HU-09, con el filtrado por rol de P-05. Cuando la spec 04
defina el mecanismo, `ms-canchas` recibira ese cambio como una modificacion posterior,
acordada primero en `docs/contratos/README.md`.

`ms-reportes` no se ve afectado: corre con token de `ADMIN` y ya ve todo.

## 7. Fuera de alcance de esta spec

- `ms-usuarios` (ya implementado), `ms-reservas`, `ms-reportes` y sus endpoints.
- Todo el frontend: `shell`, `mf-reservas`, `mf-administracion`, `mf-reportes` y Module
  Federation.
- Calculo de disponibilidad y de bloques horarios: vive en `ms-reservas`
  (`DisponibilidadResponse`).
- Cualquier operacion de canchas o bloqueos que no este en las ocho rutas congeladas:
  borrar una cancha, editar un bloqueo, listar bloqueos de todas las canchas, filtrar o
  paginar el catalogo, buscar por deporte.
- Validaciones cruzadas contra reservas: `ms-canchas` no consulta `ms-reservas` ni
  `reservas_db`, asi que no impide inactivar una cancha con reservas futuras, ni bloquear
  una franja ya reservada, ni reducir el horario de atencion por debajo de reservas
  existentes.
- Modificar `infra/postgres/*.sql`. El esquema quedo congelado en la spec 01: si la entidad
  no calza, se corrige la entidad.
- Modificar `docs/contratos/README.md` mas alla de los cuatro cambios ya autorizados el
  23/08/2026 (`NOMBRE_DUPLICADO`, `BLOQUEO_DUPLICADO`, el parametro `fecha` y la nota de
  filtrado por rol).
- Redecidir Spring Boot 3.5.3, el patron de `Dockerfile` con cache mount ni el filtro JWT:
  estan fijados en `CLAUDE.md` §1 y §3 y en la spec 02, y se reutilizan tal cual.
- Emision de tokens: `ms-canchas` solo los valida; el emisor es `ms-usuarios`.
- El mecanismo de credenciales de servicio para llamadas internas (asunto abierto A-01 de
  §6.1): se decide en la spec 04. Esta spec deja constancia del requisito, no lo
  implementa; `ms-canchas` sigue reconociendo unicamente `ADMIN` y `USUARIO`.
- El gateway Nginx que enrutara `/api`: mientras no exista, el puerto publicado es la unica
  via de prueba y es temporal.
- Generar el esqueleto con `mvn` en el host: prohibido por `CLAUDE.md` §1 (ver §2.2).
- Prohibido por `CLAUDE.md` §2: pagos, notificaciones, reservas recurrentes, torneos, app
  movil nativa, reportes BI.
- Prohibido por `CLAUDE.md` §3: Lombok, MapStruct, `@Autowired` en campos, `@Data`, clases
  `Util` genericas.

## 8. Datos que faltaron y hubo que suponer

Sin supuestos abiertos. Las seis preguntas fueron resueltas por el responsable el
23/08/2026 y sus decisiones ya estan incorporadas a este documento.

### 8.1 Decisiones tomadas

| # | Decision | Donde quedo |
|---|---|---|
| P-01 | Se agrega el codigo `NOMBRE_DUPLICADO` (HTTP 409) al contrato; `POST /api/canchas` y `PUT /api/canchas/{canchaId}` suman `409`. Deteccion con doble barrera, como en `ms-usuarios`: `existsByNombre` en el servicio mas traduccion de la violacion de `uq_cancha_nombre` para peticiones simultaneas | HU-03, HU-04, §4, §5, contrato |
| P-02.a | Se agrega el codigo `BLOQUEO_DUPLICADO` (HTTP 409) al contrato; `POST /api/canchas/{canchaId}/bloqueos` suma `409`. Misma doble barrera sobre `uq_bloqueo_franja` (misma `fecha` y misma `horaInicio`) | HU-07, §4, §5, contrato |
| P-02.d | El **solapamiento parcial** entre bloqueos de la misma cancha y fecha tambien se rechaza con `409 BLOQUEO_DUPLICADO` (09:00–11:00 rechaza 10:00–12:00; tocarse en un extremo no es solaparse). Se valida **en la capa de servicio** y **no se modifica el DDL**: `uq_bloqueo_franja` solo compara `hora_inicio` exacta. Motivo: un bloqueo duplicable a medias deja huecos que `ms-reservas` ofreceria como disponibles | HU-07, §4 |
| P-02.b | El bloqueo debe caer completo dentro del horario de atencion de la cancha; fuera de el, `400 DATOS_INVALIDOS` | HU-07, §4 |
| P-02.c | No se admite bloquear una fecha pasada: `400 DATOS_INVALIDOS`. La fecha de hoy si se admite | HU-07, §4 |
| P-03 | `groupId` `ec.ups.dae`, `artifactId` `ms-canchas`, paquete raiz `ec.ups.dae.canchas` | §2.1 |
| P-04 | Puerto publicado `8083:8080`, temporal hasta el gateway Nginx; datasource con `canchas_user` / `canchas_pass` sobre `canchas_db` | §2.3 |
| P-05 | El `USUARIO` ve solo canchas `activa = true`; el `ADMIN` ve todas. Se resuelve **por rol, sin parametro de consulta** (lo mas simple: la fuente es el claim `rol` del token). Para un `USUARIO`, una cancha inactiva responde `404 NO_ENCONTRADO` en el detalle | HU-01, HU-02, §4, §5, §6 (C-01), contrato |
| P-06 | `GET /api/canchas/{canchaId}/bloqueos` acepta `?fecha=AAAA-MM-DD` **opcional**; sin el devuelve todos. Formato invalido, `400 DATOS_INVALIDOS`. El endpoint suma `400` a sus respuestas | HU-06, §5, §6, contrato |
| C-01 | `ms-reservas` consultara `ms-canchas` con **credenciales de servicio**, no propagando el token del usuario final, porque necesita ver canchas inactivas para resolver reservas historicas. Queda registrado que `ms-canchas` debera aceptar peticiones internas sin exigir rol `ADMIN` y devolverles la vista completa del catalogo. **No se implementa en esta spec** | §6.1, §7 |

Cambios aplicados a `docs/contratos/README.md` el 23/08/2026 por estas decisiones: dos
codigos de error nuevos (`NOMBRE_DUPLICADO`, `BLOQUEO_DUPLICADO`), los `409` en las tres
rutas de escritura, el parametro `fecha` y el `400` en la ruta de bloqueos, la nota de
filtrado por rol del catalogo, y las cuatro lineas correspondientes en el registro de
cambios.

### 8.2 Supuestos aplicados en este documento

| # | Supuesto | Base |
|---|---|---|
| S-01 | El cuerpo de `POST /api/canchas` y de `PUT /api/canchas/{canchaId}` es `{ nombre, deporte, horaApertura, horaCierre }`, y el de `POST /api/canchas/{canchaId}/bloqueos` es `{ fecha, horaInicio, horaFin, motivo }`. El contrato congela los campos, no la forma del request | `docs/contratos/README.md`, "Campos acordados" y "Notas de uso" |
| S-02 | Toda cancha creada nace con `activa = true`; `canchaId` y `activa` enviados en el cuerpo del `POST` se ignoran | `activa BOOLEAN NOT NULL DEFAULT TRUE` en el DDL |
| S-03 | El `PUT` no cambia `activa`, porque existe un `PATCH .../estado` dedicado. El cuerpo del PATCH es `{ "activa": <boolean> }`, por simetria con `ms-usuarios` | Contrato mas nombre de la ruta |
| S-04 | Longitudes maximas de entrada: `nombre` 80 y `motivo` 200 caracteres | `infra/postgres/03-ddl-canchas.sql` |
| S-05 | Un bloqueo cuyo `{id}` existe pero pertenece a otra cancha responde `404 NO_ENCONTRADO`, no `403`: la ruta identifica el recurso por el par cancha + bloqueo | Ruta anidada; el contrato solo declara `401, 403, 404` para el DELETE |
| S-06 | `ms-canchas` no valida nada contra reservas existentes al editar el horario o inactivar una cancha | `CLAUDE.md` §3: prohibido que un microservicio lea tablas de otro; ninguna RN lo exige |
| S-07 | La validacion del JWT reutiliza el mismo mecanismo decidido en la spec 02: HS256, `JWT_SECRET` compartido, claims `sub` y `rol`, validacion local sin endpoint de verificacion | Spec 02, HU-05 y decision P-01 de esa spec |
| S-08 | Las rutas de `springdoc-openapi` quedan publicas, como en `ms-usuarios` | Coherencia con la spec 02, correccion 2 de la bitacora |
| S-09 | "Fecha pasada" (P-02.c) se evalua contra la fecha del servidor, la misma referencia que usara `ms-reservas` para RN-04 | Ninguna fuente define zona horaria; el contenedor la toma del host |
| S-10 | Anulado el 23/08/2026: el solapamiento parcial **si** se valida. Ver decision P-02.d | — |
