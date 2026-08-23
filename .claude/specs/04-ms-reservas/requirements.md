# Spec 04 — ms-reservas · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 04").
La compuerta C2 (`design.md`) sigue pendiente: no se escribe codigo de produccion hasta
que el diseno este aprobado por escrito.

Las once preguntas abiertas (P-01 a P-11) fueron resueltas por el responsable el
23/08/2026 y ya estan incorporadas a este documento. Las decisiones P-01 y P-10 obligaron a
modificar `docs/contratos/README.md`: el tercer valor `SERVICIO` del campo `rol` y el
codigo de error `RESERVA_NO_CANCELABLE`. Ese cambio ya esta aplicado y registrado en el
"Registro de cambios" del contrato.

La decision P-01 **obliga ademas a modificar `ms-canchas`**, un microservicio ya cerrado
(spec 03). Ese cambio esta acotado a su filtro JWT y se detalla en §8.

Fuentes leidas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.1, 3.3.2, 3.3.3,
3.4, 4.2, 4.3, 7), `.claude/specs/01-modelo-y-contratos/`, `.claude/specs/02-ms-usuarios/`
y `.claude/specs/03-ms-canchas/` (las tres cerradas), `infra/postgres/init.sql`,
`04-ddl-reservas.sql` y `05-seed.sql` (ya aplicados), `docker-compose.yml`,
`docs/bitacora.md`.

## 1. Objetivo

Implementar el microservicio `ms-reservas`: consulta de disponibilidad, creacion de
reservas, historial propio, listado global y cancelacion. Es el unico servicio dueno de
`reservas_db` y la unica fuente de verdad del estado de una reserva.

El alcance funcional son exactamente los cinco endpoints ya congelados en
`docs/contratos/README.md` para el dominio `reservas`. Ni uno mas.

Es el primer microservicio que **consume otro por HTTP**: lee de `ms-canchas` el horario de
atencion de la cancha y sus bloqueos de mantenimiento para armar `DisponibilidadResponse`.
Nunca lee `canchas_db` ni `usuarios_db` (`CLAUDE.md` §3).

Aqui se resuelve el asunto abierto **A-01** que la spec 03 dejo pendiente (§6.1 de
`03-ms-canchas/requirements.md`): el mecanismo de credenciales de servicio para las
llamadas internas es el **token de servicio** de la decision D-01.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto Maven `backend/ms-reservas` con Java 21 + Spring Boot 3.5.3, capas `controller` -> `service` -> `repository` -> `entity`, DTOs y mapper manual |
| E-02 | Entidad JPA `Reserva` que valida contra la tabla existente con `spring.jpa.hibernate.ddl-auto=validate` |
| E-03 | Los cinco endpoints congelados de `/api/reservas` con sus codigos de respuesta |
| E-04 | Cliente HTTP hacia `ms-canchas` (`GET /api/canchas/{canchaId}` y `GET /api/canchas/{canchaId}/bloqueos?fecha`) autenticado con el token de servicio de D-01, con timeouts de 2 s de conexion y 5 s de lectura, sin reintentos |
| E-05 | Emisor del token de servicio: JWT HS256 con `JWT_SECRET`, `rol = SERVICIO`, sin `sub`, `exp` 5 minutos, generado en cada llamada saliente |
| E-06 | Validacion **local** del JWT HS256 emitido por `ms-usuarios` (mismo `JWT_SECRET`), leyendo `sub` y `rol`, sin llamar a `ms-usuarios` ni leer `usuarios_db` |
| E-07 | `@RestControllerAdvice` que traduce toda excepcion al formato `{ "codigo", "mensaje" }` |
| E-08 | Documentacion `springdoc-openapi` con los codigos de error de cada endpoint |
| E-09 | `Dockerfile` segun el patron oficial de `CLAUDE.md` §1 y servicio `ms-reservas` agregado a `docker-compose.yml` |
| E-10 | Modificacion acotada del filtro JWT de `ms-canchas` para aceptar el rol `SERVICIO` (§8) |

### 2.1 Coordenadas Maven y paquete (decision D-11 / supuesto S-11)

| Dato | Valor |
|---|---|
| `groupId` | `ec.ups.dae` |
| `artifactId` | `ms-reservas` |
| Paquete raiz | `ec.ups.dae.reservas` |
| Java | 21 |
| Spring Boot | 3.5.3 (`CLAUDE.md` §3) |
| springdoc | `springdoc-openapi-starter-webmvc-ui` 2.8.6 |
| jjwt | 0.12.6 |

Mismo criterio que la spec 03: el esqueleto se genera con Spring Initializr por URL y el
`<parent>` se corrige a mano a 3.5.3, porque Initializr ya solo entrega la rama 4.x
(`CLAUDE.md` §3). **Nunca** se ejecuta `mvn` en el host; la compilacion va dentro del
contenedor `maven:3.9-eclipse-temurin-21` con el volumen `m2repo`, y el `Dockerfile` usa el
cache mount de BuildKit del patron oficial. Spring Boot 3.5.3, el `Dockerfile` y el filtro
JWT **no se redeciden**: se reutilizan tal cual estan fijados en `CLAUDE.md` §1 y §3 y en
las specs 02 y 03.

### 2.2 Configuracion en `docker-compose.yml`

| Dato | Valor |
|---|---|
| Nombre del servicio | `ms-reservas` |
| Puerto interno | `8080` |
| Puerto publicado | `8084:8080` — **temporal**, solo para probar con `curl.exe` (`8081` adminer, `8082` ms-usuarios, `8083` ms-canchas); se elimina cuando exista el gateway Nginx |
| `depends_on` | `postgres` con `condition: service_healthy`, y `ms-canchas` |

Variables de entorno:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/reservas_db` |
| `SPRING_DATASOURCE_USERNAME` | `reservas_user` |
| `SPRING_DATASOURCE_PASSWORD` | `reservas_pass` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `JWT_SECRET` | la misma de `.env` que usan `ms-usuarios` y `ms-canchas`; con ella se valida el token entrante y se firma el token de servicio saliente |
| `MS_CANCHAS_URL` | `http://ms-canchas:8080` — nombre de contenedor, porque es una llamada servidor a servidor, no del navegador |
| `RESERVAS_MAX_ACTIVAS` | `3` — limite de RN-06, tomado de `.env` (decision D-04) |

## 3. Historias de usuario

### HU-01 — Consultar la disponibilidad de una cancha en una fecha (RN-01)

Como usuario autenticado, necesito ver que bloques de una hora estan libres en una cancha
y una fecha, para elegir cual reservar.

- **CUANDO** un ADMIN o un USUARIO envie
  `GET /api/reservas/disponibilidad?canchaId=1&fecha=2026-08-24` con token valido,
  **ENTONCES** la respuesta sera `200` con `canchaId`, `fecha`, `horaApertura`, `horaCierre`
  y `bloques`, exactamente como el payload `DisponibilidadResponse` congelado.
- **CUANDO** se arme `bloques`, **ENTONCES** contendra un elemento por cada franja de una
  hora entre `horaApertura` y `horaCierre` de esa cancha, en orden ascendente, cada uno con
  `horaInicio`, `horaFin` y `disponible`.
- **CUANDO** `horaApertura` sea `07:00` y `horaCierre` sea `22:00`, **ENTONCES** habra 15
  bloques, el primero `07:00`–`08:00` y el ultimo `21:00`–`22:00`.
- **CUANDO** el bloque tenga una reserva en estado `CONFIRMADA` en esa cancha y fecha,
  **ENTONCES** `disponible` sera `false` (RN-02).
- **CUANDO** el bloque caiga dentro de un bloqueo de mantenimiento de esa cancha y fecha,
  **ENTONCES** `disponible` sera `false`. Un bloqueo `10:00`–`12:00` marca ocupados los
  bloques `10:00`–`11:00` y `11:00`–`12:00`; tocarse en un extremo no ocupa.
- **CUANDO** la reserva del bloque este en estado `CANCELADA`, **ENTONCES** `disponible`
  sera `true` (RN-05).
- **CUANDO** ninguna de las dos condiciones anteriores se cumpla, **ENTONCES** `disponible`
  sera `true`.
- **CUANDO** se lea el horario de atencion y los bloqueos, **ENTONCES** se obtendran de
  `ms-canchas` por HTTP (`GET /api/canchas/{canchaId}` y
  `GET /api/canchas/{canchaId}/bloqueos?fecha=...`) con el token de servicio de HU-07,
  nunca de `canchas_db`.
- **CUANDO** la cancha consultada tenga `activa = false`, **ENTONCES** la respuesta sera
  `200` con **todos** los bloques en `disponible = false` (decision D-05). No es `404`: la
  consulta es informativa y el ADMIN necesita ver la grilla de una cancha retirada.
- **CUANDO** la `fecha` consultada ya haya pasado, **ENTONCES** la consulta se permite y
  responde `200` con normalidad (decision D-03): es informativa y `ms-reportes` puede
  necesitarla. Los bloques ya ocupados por reservas pasadas siguen marcados
  `disponible = false`.
- **SI** falta `canchaId` o `fecha`, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.
- **SI** `fecha` no tiene formato `AAAA-MM-DD` o `canchaId` no es un numero, **ENTONCES**
  `400` con `codigo = DATOS_INVALIDOS`.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** la cancha no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** `ms-canchas` no responde, responde `5xx` o vence el timeout, **ENTONCES** `500`
  con `codigo = ERROR_INTERNO` y `mensaje = "No se pudo consultar el catalogo de canchas"`
  (decision D-06).

### HU-02 — Crear una reserva (RN-01, RN-02, RN-06)

Como usuario autenticado, necesito reservar una cancha en una fecha y un bloque de una
hora, para asegurar mi turno.

- **CUANDO** se envie `POST /api/reservas` con `canchaId`, `fecha` y `horaInicio` validos
  sobre un bloque disponible, **ENTONCES** la respuesta sera `201` con la reserva creada:
  `id`, `usuarioId`, `canchaId`, `fecha`, `horaInicio`, `horaFin` y `estado = CONFIRMADA`.
- **CUANDO** el cuerpo se valide, **ENTONCES** sera exactamente
  `{ canchaId, fecha, horaInicio }` (decision D-11); `horaFin` no se envia.
- **CUANDO** se cree la reserva, **ENTONCES** `horaFin` sera exactamente `horaInicio` mas
  una hora, calculado por el servicio (RN-01, restriccion `ck_reserva_bloque_una_hora`).
- **CUANDO** se cree la reserva, **ENTONCES** `usuarioId` saldra del claim `sub` del token,
  nunca del cuerpo de la peticion; un `usuarioId` enviado en el cuerpo se ignora.
- **CUANDO** se cree la reserva, **ENTONCES** `estado` sera `CONFIRMADA`; un `estado` o un
  `id` enviados en el cuerpo se ignoran.
- **CUANDO** quien llame tenga `rol = ADMIN`, **ENTONCES** la reserva se crea igual que
  para un `USUARIO`, con `201` y con su propio `usuarioId` (decision D-08): un ADMIN
  tambien es una persona que puede reservar. No hay `403` en esta ruta.
- **SI** falta `canchaId`, `fecha` u `horaInicio`, **ENTONCES** `400` con
  `codigo = DATOS_INVALIDOS`.
- **SI** `fecha` no tiene formato `AAAA-MM-DD` o `horaInicio` no tiene formato `HH:mm`,
  **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.
- **SI** `horaInicio` no cae en una hora en punto (minutos distintos de `00`), **ENTONCES**
  `400` con `codigo = DATOS_INVALIDOS`: los bloques son franjas fijas de una hora (RN-01).
- **SI** el bloque no cabe completo dentro del horario de atencion de la cancha
  (`horaInicio` menor que `horaApertura`, o `horaInicio + 1h` mayor que `horaCierre`),
  **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`.
- **SI** la `fecha` y `horaInicio` pedidas ya ocurrieron respecto de la fecha y hora del
  servidor, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS` (decision D-03, mismo
  criterio que P-02.c de la spec 03). Un bloque que empieza hoy mas tarde si se admite.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** la cancha no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** la cancha existe pero tiene `activa = false`, **ENTONCES** `404` con
  `codigo = NO_ENCONTRADO` (decision D-05): es la misma respuesta que un USUARIO ve en
  `ms-canchas` para una cancha inactiva.
- **SI** ya existe una reserva `CONFIRMADA` de esa cancha, esa fecha y esa `horaInicio`,
  **ENTONCES** `409` con `codigo = BLOQUE_OCUPADO` (RN-02).
- **CUANDO** se valide el bloque ocupado, **ENTONCES** se hara con la misma doble barrera
  de las specs 02 y 03: comprobacion previa en el servicio mas traduccion de la violacion
  del indice `ux_reserva_bloque_confirmada` al mismo `409 BLOQUE_OCUPADO`, para dos
  peticiones simultaneas sobre el mismo bloque.
- **CUANDO** el bloque tenga una reserva `CANCELADA`, **ENTONCES** la nueva reserva se crea
  con `201`: el indice unico es parcial y una cancelacion libera el bloque (RN-05).
- **SI** el bloque cae dentro de un bloqueo de mantenimiento de esa cancha y fecha,
  **ENTONCES** `409` con `codigo = BLOQUE_OCUPADO`, el mismo codigo del bloque ya reservado
  (decision D-07): para el cliente es el mismo problema y la accion correctiva es
  identica. No se crea un codigo nuevo ni se toca el contrato.
- **SI** el usuario ya tiene `RESERVAS_MAX_ACTIVAS` reservas activas, **ENTONCES** `409`
  con `codigo = LIMITE_RESERVAS` (RN-06).
- **CUANDO** se cuenten las reservas activas, **ENTONCES** se contaran solo las que esten
  en estado `CONFIRMADA` **y** cuya `fecha` + `horaInicio` aun no hayan ocurrido
  (decision D-04). Las pasadas y las canceladas no cuentan: contarlas dejaria a un usuario
  bloqueado para siempre tras tres reservas.
- **CUANDO** se lea el limite, **ENTONCES** saldra de la variable de entorno
  `RESERVAS_MAX_ACTIVAS` (valor `3`), no de una constante en codigo (RN-06,
  "configurable").
- **SI** `ms-canchas` no responde, responde `5xx` o vence el timeout, **ENTONCES** `500`
  con `codigo = ERROR_INTERNO` y `mensaje = "No se pudo consultar el catalogo de canchas"`
  (decision D-06). La reserva **no** se crea.

### HU-03 — Consultar mi historial de reservas (RN-03)

Como usuario autenticado, necesito ver mis reservas con su estado, para saber que tengo
reservado y que cancele.

- **CUANDO** se envie `GET /api/reservas/mias` con token valido, **ENTONCES** la respuesta
  sera `200` con un arreglo de las reservas propias, cada una con `id`, `usuarioId`,
  `canchaId`, `fecha`, `horaInicio`, `horaFin` y `estado`.
- **CUANDO** se arme el listado, **ENTONCES** contendra **solo** las reservas cuyo
  `usuarioId` coincida con el claim `sub` del token, en cualquier estado: es un historial,
  no una lista de reservas vigentes.
- **CUANDO** quien llame tenga `rol = ADMIN`, **ENTONCES** recibe `200` con **sus propias**
  reservas, igual que un `USUARIO` (decision D-08). No hay `403` en esta ruta; el listado
  global es otro endpoint (HU-04).
- **CUANDO** una reserva este `CONFIRMADA` en la base y su `fecha` + `horaFin` ya hayan
  pasado, **ENTONCES** se devolvera con `estado = FINALIZADA` (decision D-02, RN-08). El
  calculo es de solo lectura: la columna sigue en `CONFIRMADA`.
- **CUANDO** el usuario no tenga reservas, **ENTONCES** `200` con arreglo vacio, nunca
  `404`.
- **CUANDO** se devuelva una reserva, **ENTONCES** `fecha` tendra formato `AAAA-MM-DD` y
  `horaInicio` / `horaFin` formato `HH:mm`.
- **CUANDO** se ordene el listado, **ENTONCES** sera por `fecha` descendente y, dentro de
  la misma fecha, por `horaInicio` descendente: lo mas reciente primero (decision D-09).
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.

### HU-04 — Consultar todas las reservas del sistema

Como administrador, necesito el listado global de reservas, para poder cancelar cualquiera
y revisar el uso del sistema.

- **CUANDO** un ADMIN envie `GET /api/reservas` con token valido, **ENTONCES** la respuesta
  sera `200` con un arreglo de **todas** las reservas del sistema, de todos los usuarios y
  en todos los estados, cada una con `id`, `usuarioId`, `canchaId`, `fecha`, `horaInicio`,
  `horaFin` y `estado`.
- **CUANDO** una reserva este `CONFIRMADA` en la base y su `fecha` + `horaFin` ya hayan
  pasado, **ENTONCES** se devolvera con `estado = FINALIZADA`, con el mismo calculo de
  lectura de HU-03 (decision D-02, RN-08).
- **CUANDO** no exista ninguna reserva, **ENTONCES** `200` con arreglo vacio, nunca `404`.
- **CUANDO** se ordene el listado, **ENTONCES** sera por `fecha` descendente y luego por
  `horaInicio` descendente (decision D-09).
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama tiene `rol = USUARIO`, **ENTONCES** `403` con `codigo = SIN_PERMISO`.
- **CUANDO** se devuelva una reserva, **ENTONCES** no incluira el nombre del usuario ni el
  nombre de la cancha: esos datos viven en otros servicios y el contrato no los declara en
  esta respuesta. El frontend los resuelve consultando `ms-usuarios` y `ms-canchas`.
- **CUANDO** se llame esta ruta, **ENTONCES** no acepta parametros de filtrado ni de
  paginacion: el contrato congelado no declara ninguno.

### HU-05 — Cancelar una reserva (RN-03, RN-04, RN-05)

Como usuario final necesito cancelar una reserva mia que aun no ocurre, y como
administrador necesito poder cancelar cualquier reserva del sistema.

- **CUANDO** el dueno de la reserva envie `PATCH /api/reservas/{id}/cancelacion` sobre una
  reserva `CONFIRMADA` cuya fecha y hora de inicio aun no han ocurrido, **ENTONCES** la
  respuesta sera `200` con la reserva actualizada y `estado = CANCELADA` (RN-03).
- **CUANDO** un ADMIN envie el mismo endpoint sobre **cualquier** reserva que cumpla RN-04,
  **ENTONCES** la respuesta sera `200` con `estado = CANCELADA`, sea o no suya (RN-03).
- **CUANDO** una reserva quede `CANCELADA`, **ENTONCES** su bloque volvera a aparecer con
  `disponible = true` en HU-01 y podra reservarse de nuevo en HU-02 (RN-05).
- **CUANDO** se cancele, **ENTONCES** no se borra la fila: es un cambio de estado, para
  conservar la trazabilidad de los reportes (RN-08).
- **CUANDO** el cuerpo de la peticion venga vacio, **ENTONCES** se acepta: la ruta ya
  expresa la operacion y el contrato no declara ningun campo de entrada.
- **SI** no hay token o es invalido, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **SI** quien llama es un `USUARIO` y la reserva pertenece a otro `usuarioId`, **ENTONCES**
  `403` con `codigo = SIN_PERMISO` (RN-03).
- **SI** el `id` no existe, **ENTONCES** `404` con `codigo = NO_ENCONTRADO`.
- **SI** la reserva esta `CONFIRMADA` pero su fecha y hora de inicio ya ocurrieron,
  **ENTONCES** `409` con `codigo = RESERVA_PASADA` (RN-04). La comparacion es contra la
  fecha y hora del servidor (supuesto S-09).
- **SI** la reserva ya esta `CANCELADA`, **ENTONCES** `409` con
  `codigo = RESERVA_NO_CANCELABLE` (decision D-10; codigo agregado al contrato el
  23/08/2026). No es idempotente: cancelar dos veces no devuelve `200`.
- **CUANDO** se combinen D-02 y D-10, **ENTONCES** se aplica la precedencia de la
  consecuencia **C-02** (§6.1): una reserva que se ve `FINALIZADA` en HU-03 y HU-04 esta
  `CONFIRMADA` en la base y ya paso, asi que su cancelacion responde `409 RESERVA_PASADA`,
  no `RESERVA_NO_CANCELABLE`. `RESERVA_NO_CANCELABLE` queda reservado para el unico estado
  persistido que no es cancelable: `CANCELADA`.
- **CUANDO** una reserva se cancele, **ENTONCES** `ms-reservas` no avisa a nadie: no hay
  notificaciones (prohibido por `CLAUDE.md` §2).

### HU-06 — Estado FINALIZADA calculado al leer (RN-08)

Como equipo, necesito que el estado `FINALIZADA` del contrato tenga un origen definido, sin
tarea programada ni endpoint nuevo.

- **CUANDO** se lea cualquier reserva en HU-03 o HU-04, **ENTONCES** su `estado` se
  calculara asi: si la columna dice `CANCELADA`, se devuelve `CANCELADA`; si dice
  `CONFIRMADA` y su `fecha` + `horaFin` ya pasaron respecto del servidor, se devuelve
  `FINALIZADA`; en cualquier otro caso, `CONFIRMADA` (decision D-02).
- **CUANDO** se calcule `FINALIZADA`, **ENTONCES** **no se escribe en la base**: la columna
  `estado` sigue en `CONFIRMADA`. En `reservas_db` solo existen dos valores reales,
  `CONFIRMADA` y `CANCELADA`, aunque `ck_reserva_estado` admita los tres.
- **CUANDO** una reserva se vea `FINALIZADA`, **ENTONCES** su bloque **no** vuelve a estar
  disponible: la fila sigue `CONFIRMADA` y el indice parcial
  `ux_reserva_bloque_confirmada` la sigue reservando. Es correcto: el bloque ya ocurrio y
  nadie puede reservar el pasado (D-03).
- **CUANDO** se cuenten las reservas activas de RN-06, **ENTONCES** una reserva vista como
  `FINALIZADA` no cuenta, porque su `horaInicio` ya paso (coherente con D-04).
- **CUANDO** `ms-reportes` (spec 05) consuma estos listados, **ENTONCES** recibira
  `FINALIZADA` ya calculada y no necesita replicar la regla.
- **CUANDO** se documente el endpoint en `springdoc-openapi`, **ENTONCES** se dejara
  constancia de que `FINALIZADA` es un estado derivado, no persistido.

### HU-07 — Token de servicio para llamar a `ms-canchas` (A-01)

Como equipo, necesito que `ms-reservas` consulte `ms-canchas` sin propagar el token del
usuario final, porque necesita ver tambien las canchas inactivas (decision C-01 de la
spec 03).

- **CUANDO** `ms-reservas` vaya a llamar a `ms-canchas`, **ENTONCES** emitira un **token de
  servicio**: JWT HS256 firmado con el mismo `JWT_SECRET`, con claim `rol = SERVICIO`,
  **sin** claim `sub` y con `exp` de 5 minutos (decision D-01).
- **CUANDO** emita el token, **ENTONCES** lo generara en cada llamada saliente; no se
  cachea ni se guarda en base.
- **CUANDO** necesite el horario de atencion de una cancha, **ENTONCES** llamara a
  `GET {MS_CANCHAS_URL}/api/canchas/{canchaId}` con `Authorization: Bearer <token de
  servicio>`.
- **CUANDO** necesite los bloqueos de un dia, **ENTONCES** llamara a
  `GET {MS_CANCHAS_URL}/api/canchas/{canchaId}/bloqueos?fecha=AAAA-MM-DD`, usando el
  parametro opcional congelado por la decision P-06 de la spec 03.
- **CUANDO** `ms-canchas` reciba un token con `rol = SERVICIO`, **ENTONCES** lo aceptara en
  las rutas de lectura y respondera con la vista completa del catalogo, canchas inactivas
  incluidas, igual que a un `ADMIN` (D-01; cambio detallado en §8).
- **SI** un token con `rol = SERVICIO` llega a una ruta de escritura de `ms-canchas`
  (`POST`, `PUT`, `PATCH`, `DELETE`), **ENTONCES** `403` con `codigo = SIN_PERMISO`: el rol
  `SERVICIO` nunca escribe (D-01, RN-07).
- **CUANDO** se propague el token del usuario final, **ENTONCES** es un error: `ms-reservas`
  **nunca** lo reenvia a `ms-canchas` (decision C-01 de la spec 03).
- **CUANDO** se configure el cliente HTTP, **ENTONCES** usara **2 segundos de timeout de
  conexion y 5 de lectura, sin reintentos** (decision D-06).
- **SI** `ms-canchas` no responde, responde `5xx` o vence cualquiera de los dos timeouts,
  **ENTONCES** `ms-reservas` responde `500` con `codigo = ERROR_INTERNO` y
  `mensaje = "No se pudo consultar el catalogo de canchas"`, sin propagar el error interno
  ni el cuerpo recibido (decision D-06).
- **SI** `ms-canchas` responde `404` para el `canchaId` pedido, **ENTONCES** eso **no** es
  un fallo de dependencia: se traduce al `404 NO_ENCONTRADO` de HU-01 y HU-02.

### HU-08 — Autorizacion por rol con el token de `ms-usuarios`

Como equipo, necesito que `ms-reservas` decida quien puede hacer que usando el mismo token
emitido por `ms-usuarios`, para cumplir RN-03 sin acoplar los servicios.

- **CUANDO** llegue una peticion con `Authorization: Bearer <token>`, **ENTONCES**
  `ms-reservas` validara la firma **localmente** con `JWT_SECRET` y leera `sub`
  (`usuarioId`) y `rol` de los claims, sin llamar a `ms-usuarios` por HTTP y sin consultar
  `usuarios_db`.
- **SI** falta el encabezado, el token esta vencido, la firma no coincide o el token esta
  mal formado, **ENTONCES** `401` con `codigo = NO_AUTENTICADO`.
- **CUANDO** se identifique al llamante, **ENTONCES** el `usuarioId` con el que se crea y
  se comprueba la propiedad de una reserva sera siempre el del claim `sub`, nunca uno
  enviado por el cliente.
- **SI** el claim `rol` es `USUARIO` y la operacion es `GET /api/reservas`, **ENTONCES**
  `403` con `codigo = SIN_PERMISO`. Es la unica restriccion por rol de este servicio.
- **SI** el token entrante trae `rol = SERVICIO`, **ENTONCES** `ms-reservas` lo rechaza con
  `401 NO_AUTENTICADO`: ningun endpoint de este servicio se consume con token de servicio,
  y las operaciones sin `sub` no tienen dueno. `ms-reservas` **emite** tokens `SERVICIO`,
  no los acepta.
- **CUANDO** se consulte la documentacion `springdoc-openapi`, **ENTONCES** sus rutas
  estaran abiertas sin token, igual que en `ms-usuarios` y `ms-canchas`.

### HU-09 — Errores uniformes y sin stacktrace

Como consumidor de la API, necesito que todos los errores tengan la misma forma, para
mostrar mensajes sin adivinar.

- **CUANDO** cualquier endpoint falle, **ENTONCES** el cuerpo sera exactamente
  `{ "codigo": "...", "mensaje": "..." }` con un `codigo` de la tabla "Formato de error"
  del contrato.
- **CUANDO** llegue un cuerpo mal formado, un tipo de contenido no soportado o un metodo no
  permitido, **ENTONCES** `400` con `codigo = DATOS_INVALIDOS`, igual que en `ms-usuarios`
  y `ms-canchas`.
- **CUANDO** falle la llamada a `ms-canchas`, **ENTONCES** `500` con
  `codigo = ERROR_INTERNO` y el mensaje fijo de D-06, nunca la excepcion del cliente HTTP.
- **CUANDO** ocurra una excepcion no prevista, **ENTONCES** `500` con
  `codigo = ERROR_INTERNO`, sin stacktrace, sin nombre de clase Java y sin consulta SQL.

### HU-10 — Arranque validado contra el esquema existente

Como equipo, necesito que el servicio arranque solo si la entidad calza con el DDL ya
versionado, para detectar desalineaciones al instante.

- **CUANDO** se levante `docker compose up -d --build ms-reservas` con `postgres` en estado
  `healthy`, **ENTONCES** el servicio quedara arriba con
  `spring.jpa.hibernate.ddl-auto=validate` sin errores.
- **SI** la entidad `Reserva` no calza con `infra/postgres/04-ddl-reservas.sql`,
  **ENTONCES** se corrige la entidad, nunca el DDL.
- **CUANDO** el servicio este arriba, **ENTONCES** `GET /api/reservas` con token de ADMIN
  devolvera `200` con arreglo vacio: el seed no carga reservas.
- **CUANDO** el servicio este arriba, **ENTONCES**
  `GET /api/reservas/disponibilidad?canchaId=1&fecha=<hoy>` devolvera los 15 bloques de
  `Padel 1` (`07:00`–`22:00`), y con `canchaId=4` (`Padel 2`, `08:00`–`21:00`) devolvera 13
  bloques: eso comprueba que la disponibilidad respeta el horario de cada cancha y no uno
  fijo (dato conservado a proposito en la bitacora de la spec 03).
- **CUANDO** el servicio este arriba, **ENTONCES** expondra su documentacion
  `springdoc-openapi` con los cinco endpoints y sus codigos de error declarados.
- **CUANDO** se reconstruya `ms-canchas` con el cambio de §8, **ENTONCES** sus ocho
  endpoints seguiran comportandose igual para `ADMIN` y `USUARIO`: el rol `SERVICIO` se
  suma, no reemplaza a ninguno.

## 4. Reglas de negocio cubiertas

| RN | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | **La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora** | **Cubierta por completo** — HU-01 y HU-02. Los bloques se derivan del horario de atencion de la cancha en franjas de una hora que empiezan en hora en punto; `horaFin = horaInicio + 1h` lo calcula el servicio y lo respalda `ck_reserva_bloque_una_hora` |
| RN-02 | **No se puede reservar un bloque ya ocupado en la misma cancha** | **Cubierta por completo** — HU-02, con doble barrera: comprobacion en el servicio mas el indice parcial `ux_reserva_bloque_confirmada`. Duplicado responde `409 BLOQUE_OCUPADO`, y lo mismo un bloque bajo mantenimiento (D-07). El reflejo de lectura es `disponible = false` en HU-01 |
| RN-03 | **El usuario solo cancela sus propias reservas; el admin cancela cualquiera** | **Cubierta por completo** — HU-05 y HU-08. Un USUARIO sobre reserva ajena recibe `403 SIN_PERMISO`; el ADMIN cancela cualquiera |
| RN-04 | **Solo se cancela una reserva cuya fecha y hora de inicio no hayan ocurrido** | **Cubierta por completo** — HU-05. Reserva ya ocurrida responde `409 RESERVA_PASADA`, contra la fecha y hora del servidor. Ademas, D-03 impide crear reservas en el pasado, asi que el caso no se genera desde el propio sistema |
| RN-05 | **Cancelar libera el bloque para otro usuario** | **Cubierta por completo** — HU-05 y HU-01. El indice unico es parcial sobre `estado = 'CONFIRMADA'`, asi que una reserva `CANCELADA` no bloquea la franja y vuelve a aparecer `disponible = true` |
| RN-06 | **Limite configurable de reservas activas simultaneas por usuario (default 3)** | **Cubierta por completo** — HU-02. El limite se lee de `RESERVAS_MAX_ACTIVAS` (`3`), y "activa" es toda reserva `CONFIRMADA` cuya `fecha` + `horaInicio` aun no han ocurrido (D-04). Superarlo responde `409 LIMITE_RESERVAS` |
| RN-07 | Solo el admin crea, edita o inactiva canchas y define su horario | No aplica — implementada en `ms-canchas` (spec 03). `ms-reservas` solo **consume** el horario de atencion por HTTP, y su token `SERVICIO` es de solo lectura, asi que el cambio de §8 no debilita RN-07 |
| RN-08 | **Estados CONFIRMADA / CANCELADA / FINALIZADA** | **Cubierta por completo** — HU-02 escribe `CONFIRMADA`, HU-05 escribe `CANCELADA` y HU-06 deriva `FINALIZADA` al leer (D-02). En base solo existen dos valores; `FINALIZADA` es un estado calculado, nunca persistido |

Reglas propias de este microservicio, derivadas del contrato, del DDL congelado y del
documento de alcance (§3.3.1, §3.3.2, §3.3.3):

- Un bloque es de exactamente una hora y empieza en hora en punto — HU-01, HU-02.
- Todo bloque reservable cae completo dentro del horario de atencion de su cancha — HU-02.
- Un bloque dentro de un bloqueo de mantenimiento no esta disponible (HU-01) y su alta se
  rechaza con `409 BLOQUE_OCUPADO` (HU-02, D-07).
- No se reserva el pasado: `400 DATOS_INVALIDOS` — HU-02, D-03.
- No se reserva en una cancha inactiva: `404 NO_ENCONTRADO` — HU-02, D-05.
- La disponibilidad si se consulta para fechas pasadas y para canchas inactivas, que
  devuelven todos los bloques ocupados — HU-01, D-03, D-05.
- El `usuarioId` de una reserva sale del token, nunca del cuerpo — HU-02, HU-08.
- Cancelar no borra la fila — HU-05.
- Una reserva `CANCELADA` no se vuelve a cancelar: `409 RESERVA_NO_CANCELABLE` — HU-05,
  D-10.
- Los dos listados ordenan por `fecha` y `horaInicio` descendente — HU-03, HU-04, D-09.
- Una reserva pertenece a un unico `usuarioId` y a una unica `cancha_id`; ninguno de los dos
  tiene clave foranea, porque viven en otras bases y la integracion es por REST — DDL.

## 5. Contrato REST

Los nombres de campo, rutas, roles y codigos de error son los de
`docs/contratos/README.md`. Esta spec no los redefine: los usa tal cual.

| Verbo | Ruta | Rol | Respuestas |
|---|---|---|---|
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | ADMIN, USUARIO | 200, 400, 401, 404 |
| POST | `/api/reservas` | USUARIO | 201, 400, 401, 404, 409 |
| GET | `/api/reservas` | ADMIN | 200, 401, 403 |
| GET | `/api/reservas/mias` | USUARIO | 200, 401 |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | 200, 401, 403, 404, 409 |

Las cinco filas quedan **exactamente como estaban congeladas**: la decision D-08 confirma
que ni `POST /api/reservas` ni `GET /api/reservas/mias` suman `403`, porque el ADMIN
tambien las usa. La columna "Rol" de esas dos filas describe al consumidor tipico del
documento de alcance, no una restriccion tecnica (ver §6.2).

Parametros de consulta: `canchaId` y `fecha`, **ambos obligatorios**, solo en
`GET /api/reservas/disponibilidad`. Ninguna otra ruta acepta parametros.

Campos JSON usados, con los nombres exactos del contrato:

| Concepto | Campo | Tipo / valores | Uso |
|---|---|---|---|
| Identificador de reserva | `id` | number | respuesta y path de la cancelacion |
| Estado de la reserva | `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | respuesta de reserva; `FINALIZADA` es derivado (D-02) |
| Fecha de la reserva | `fecha` | string `AAAA-MM-DD` | request y respuesta de reserva; parametro de disponibilidad |
| Hora de inicio | `horaInicio` | string `HH:mm` | request y respuesta de reserva; bloque de disponibilidad |
| Hora de fin | `horaFin` | string `HH:mm` | respuesta de reserva; bloque de disponibilidad. **No** se envia en el request (D-11) |
| Identificador de cancha | `canchaId` | number | request y respuesta de reserva; parametro y respuesta de disponibilidad |
| Identificador de usuario | `usuarioId` | number | respuesta de reserva |
| Hora de apertura de la cancha | `horaApertura` | string `HH:mm` | respuesta de disponibilidad |
| Hora de cierre de la cancha | `horaCierre` | string `HH:mm` | respuesta de disponibilidad |
| Lista de bloques del dia | `bloques` | arreglo de objetos | respuesta de disponibilidad |
| Bloque libre | `disponible` | boolean | cada elemento de `bloques` |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` \| `SERVICIO` | claim del token; `SERVICIO` solo en el token saliente hacia `ms-canchas` (D-01) |

Payload congelado que produce esta spec — `DisponibilidadResponse`:

```json
{
  "canchaId": 1,
  "fecha": "2026-08-24",
  "horaApertura": "07:00",
  "horaCierre": "22:00",
  "bloques": [
    { "horaInicio": "07:00", "horaFin": "08:00", "disponible": true },
    { "horaInicio": "08:00", "horaFin": "09:00", "disponible": false }
  ]
}
```

Codigos de error usados: `DATOS_INVALIDOS` (400), `NO_AUTENTICADO` (401), `SIN_PERMISO`
(403), `NO_ENCONTRADO` (404), `BLOQUE_OCUPADO` (409), `LIMITE_RESERVAS` (409),
`RESERVA_PASADA` (409), `RESERVA_NO_CANCELABLE` (409), `ERROR_INTERNO` (500). Los tres
primeros codigos `409` estan congelados desde la spec 01;
`RESERVA_NO_CANCELABLE` se agrego al contrato el 23/08/2026 por la decision D-10. Los
cuatro son de uso exclusivo de `ms-reservas`.

Nota literal sobre la ruta congelada: la cancelacion usa `{id}`, no `{reservaId}`, y el
campo del cuerpo tambien es `id`, no `reservaId`. Se implementa tal cual esta congelado.

## 6. Dependencias, consecuencias y contradicciones resueltas

| Servicio | Endpoint consumido | Para que | Credencial |
|---|---|---|---|
| `ms-canchas` | `GET /api/canchas/{canchaId}` | `horaApertura`, `horaCierre`, `activa` y existencia de la cancha | token de servicio `rol = SERVICIO` (D-01) |
| `ms-canchas` | `GET /api/canchas/{canchaId}/bloqueos?fecha` | marcar bloques ocupados por mantenimiento | token de servicio `rol = SERVICIO` (D-01) |

`ms-reservas` no consume `ms-usuarios`: valida el token localmente y no necesita mas datos
del usuario que su `usuarioId`.

`ms-reportes` (spec 05) consumira `ms-reservas`, pero esta spec no implementa nada para el
ni le abre endpoints nuevos. Si `ms-reportes` necesitara llamar con token de servicio, el
mecanismo de D-01 ya esta congelado en el contrato y no se vuelve a decidir.

### 6.1 C-02 — Precedencia entre `RESERVA_PASADA` y `RESERVA_NO_CANCELABLE`

Consecuencia de combinar D-02 y D-10. Como `FINALIZADA` no se persiste, una reserva que el
cliente vio como `FINALIZADA` esta en realidad `CONFIRMADA` en la base y su hora ya paso.
Ese caso lo cubre RN-04, asi que su cancelacion responde `409 RESERVA_PASADA`.

`RESERVA_NO_CANCELABLE` queda entonces para un unico caso real: la reserva ya `CANCELADA`.
Se mantiene el codigo igual, porque el mensaje al usuario es distinto y porque deja el
comportamiento explicito si alguna vez `FINALIZADA` llegara a persistirse.

**Confirmado por el responsable el 23/08/2026:** RN-04 tiene precedencia y
`RESERVA_NO_CANCELABLE` queda solo para la reserva ya `CANCELADA`.

### 6.2 C-03 — Contradiccion resuelta entre el documento de alcance y el contrato

El PDF §3.1 marca "No" para el administrador en "Crear una reserva" y en "Consultar
historial de reservas propias", pero el contrato congelado **no declara `403`** en
`POST /api/reservas` ni en `GET /api/reservas/mias`.

**Resolucion del responsable (23/08/2026, decision D-08): manda el contrato.** El ADMIN
puede crear reservas y tiene historial propio, sin `403`. Un ADMIN tambien es una persona
que puede reservar; la tabla del PDF describe el reparto tipico de funciones por modulo, no
una prohibicion, y ninguna RN de §3.4 respalda el bloqueo. Agregar `403` seria inventar una
regla.

Queda escrito aqui de forma explicita para la defensa del proyecto: la diferencia con el
PDF es deliberada y esta justificada, no es un descuido.

### 6.3 A-02 — Una ruta inexistente responde `500`, no `404` (asunto abierto)

Detectado al verificar T4 el 23/08/2026 con salida real: una peticion **autenticada** a una
ruta que no existe no llega a ningun controlador, Spring lanza `NoResourceFoundException` y
la red de seguridad `@ExceptionHandler(Exception.class)` la convierte en
`500 ERROR_INTERNO`. Deberia ser `404 NO_ENCONTRADO`.

No es un defecto que introduzca esta spec: `ms-canchas`, ya cerrado, hace exactamente lo
mismo, y se comprobo en la misma verificacion. Es un hueco comun a los tres microservicios,
heredado del `ManejadorExcepciones` que las specs 02 y 03 congelaron.

**Decision del responsable (23/08/2026):** se corrige — una ruta inexistente debe responder
`404 NO_ENCONTRADO` en los tres servicios — pero **no ahora**. Motivo: toca dos servicios ya
cerrados y es un cambio transversal; hacerlo dentro de la implementacion de `ms-reservas`
mezclaria ambas cosas y ensuciaria la trazabilidad de la spec.

Queda como **tarea T10 de `tasks.md`**, la ultima, despues de T9. Alcance: agregar el
manejador de `NoResourceFoundException` al `ManejadorExcepciones` de `ms-usuarios`,
`ms-canchas` y `ms-reservas`, con la propiedad
`spring.mvc.throw-exception-if-no-handler-found` o su equivalente segun la version.

Hasta que T10 se ejecute, el comportamiento actual sigue vigente y esta documentado aqui a
proposito. No afecta a las cinco rutas congeladas de `ms-reservas`: todas tendran handler al
terminar T8.

## 7. Fuera de alcance de esta spec

- `ms-usuarios` (ya implementado) y `ms-reportes` (spec 05).
- `ms-canchas`, salvo el unico cambio de §8 que D-01 obliga a hacer en su filtro JWT.
  Ningun endpoint, entidad, DTO ni regla de negocio de `ms-canchas` se toca.
- Todo el frontend: `shell`, `mf-reservas`, `mf-administracion`, `mf-reportes` y Module
  Federation.
- Cualquier operacion de reservas que no este en las cinco rutas congeladas: editar o
  reprogramar una reserva, borrarla fisicamente, filtrar o paginar los listados, buscar por
  cancha, por deporte, por estado o por rango de fechas, reservar varios bloques en una sola
  peticion.
- Persistir `FINALIZADA`, y con ello cualquier tarea programada, `@Scheduled`, job o
  endpoint de cierre de reservas: D-02 lo resuelve calculando el estado al leer.
- Devolver en la respuesta de una reserva el nombre del usuario, el nombre de la cancha o
  el deporte: el contrato no los declara ahi y traerlos obligaria a llamadas cruzadas en
  cada fila.
- Cache de las respuestas de `ms-canchas`, reintentos, circuit breaker o cualquier politica
  de resiliencia mas alla de los timeouts de D-06.
- Cache o reutilizacion del token de servicio: se emite uno por llamada (D-01).
- Modificar `infra/postgres/*.sql`. El esquema quedo congelado en la spec 01: si la entidad
  no calza, se corrige la entidad. En particular, **no** se agrega ninguna restriccion nueva
  para el limite de RN-06 ni para los bloqueos de mantenimiento, y **no** se toca
  `ck_reserva_estado` aunque `FINALIZADA` nunca se escriba.
- Cargar reservas de ejemplo en `05-seed.sql`.
- Modificar `docs/contratos/README.md` mas alla de los dos cambios ya autorizados el
  23/08/2026 (el valor `SERVICIO` del campo `rol` y el codigo `RESERVA_NO_CANCELABLE`).
- Redecidir Spring Boot 3.5.3, el patron de `Dockerfile` con cache mount ni el filtro JWT:
  estan fijados en `CLAUDE.md` §1 y §3 y en las specs 02 y 03, y se reutilizan tal cual.
- Emision de tokens de sesion: `ms-reservas` solo los valida; el emisor es `ms-usuarios`.
  Lo unico que `ms-reservas` emite es el token de servicio de D-01, que no identifica a
  ninguna persona.
- El gateway Nginx que enrutara `/api`: mientras no exista, el puerto publicado es la unica
  via de prueba y es temporal.
- Generar el esqueleto con `mvn` en el host: prohibido por `CLAUDE.md` §1.
- Prohibido por `CLAUDE.md` §2: pagos, notificaciones, reservas recurrentes, torneos, app
  movil nativa, reportes BI.
- Prohibido por `CLAUDE.md` §3: Lombok, MapStruct, `@Autowired` en campos, `@Data`, clases
  `Util` genericas.

## 8. Cambio obligado en `ms-canchas` (spec 03, ya cerrada)

La decision D-01 no se puede implementar solo en `ms-reservas`: `ms-canchas` hoy reconoce
unicamente `ADMIN` y `USUARIO`, asi que un token con `rol = SERVICIO` seria tratado como
rol desconocido.

Alcance exacto del cambio, que se ejecuta como una tarea de **esta** spec:

| Que | Detalle |
|---|---|
| Que se toca | Solo el filtro JWT y la resolucion de autoridades de `ms-canchas`, mas la anotacion de rol de sus rutas |
| Lectura | Un token con `rol = SERVICIO` es valido en `GET /api/canchas`, `GET /api/canchas/{canchaId}` y `GET /api/canchas/{canchaId}/bloqueos`, y recibe la **vista completa** del catalogo: canchas activas e inactivas, igual que un `ADMIN`. Una cancha inactiva responde `200`, no `404` |
| Escritura | `POST`, `PUT`, `PATCH` y `DELETE` siguen exigiendo `rol = ADMIN`. Un token `SERVICIO` en cualquiera de ellas responde `403 SIN_PERMISO` |
| Sin `sub` | El token de servicio no trae `sub`; el filtro de `ms-canchas` no debe exigirlo cuando el rol es `SERVICIO` |
| Que NO se toca | Ningun endpoint, entidad, DTO, mapper, regla de negocio ni respuesta de `ms-canchas`. El comportamiento para `ADMIN` y `USUARIO` queda identico, incluido el filtrado por rol de la decision P-05 de la spec 03 |
| Verificacion | Los cuatro comandos de verificacion ya ejecutados en la spec 03 deben seguir dando el mismo resultado, mas dos nuevos: token `SERVICIO` sobre cancha inactiva devuelve `200`, y token `SERVICIO` sobre `POST /api/canchas` devuelve `403 SIN_PERMISO` |

Esto cierra el asunto abierto **A-01** que la spec 03 dejo registrado en su §6.1 y §7.

Cambios ya aplicados a `docs/contratos/README.md` el 23/08/2026 por estas decisiones: el
tercer valor `SERVICIO` en la fila `rol` de "Campos acordados", la nota de uso que define
el token de servicio y su restriccion de solo lectura, el codigo de error
`RESERVA_NO_CANCELABLE` (409) en la tabla "Formato de error", y las dos lineas
correspondientes en el registro de cambios.

## 9. Datos que faltaron y hubo que suponer

Sin supuestos abiertos. Las once preguntas fueron resueltas por el responsable el
23/08/2026 y sus decisiones ya estan incorporadas a este documento.

### 9.1 Decisiones tomadas

| # | Decision | Donde quedo |
|---|---|---|
| D-01 | **A-01 resuelto.** Credencial interna = **token de servicio**: JWT HS256 firmado con el mismo `JWT_SECRET`, claim `rol = SERVICIO`, **sin** `sub`, `exp` de 5 minutos, emitido por `ms-reservas` en cada llamada. En `ms-canchas` el rol `SERVICIO` habilita **solo lectura** con vista completa del catalogo; en `POST`, `PUT`, `PATCH` y `DELETE` responde `403 SIN_PERMISO`. Motivo: reutiliza el mecanismo ya probado, no agrega infraestructura y el `exp` corto limita el dano si el token se filtra | HU-07, HU-08, §5, §6, §8, contrato |
| D-02 | `FINALIZADA` **se calcula al leer, no se persiste**: una reserva `CONFIRMADA` cuya `fecha` + `horaFin` ya pasaron se devuelve como `FINALIZADA` en HU-03 y HU-04, con la columna intacta. Motivo: sin tarea programada ni endpoint nuevo, no toca el contrato y el indice unico parcial sigue funcionando igual. **Consecuencia: en base solo existen `CONFIRMADA` y `CANCELADA`** | HU-03, HU-04, HU-06, §4, §6.1 |
| D-03 | No se puede reservar en el pasado: `400 DATOS_INVALIDOS`, mismo criterio que P-02.c de la spec 03. La **consulta de disponibilidad** de una fecha pasada **si** se permite: es informativa y `ms-reportes` puede necesitarla | HU-01, HU-02, §4 |
| D-04 | RN-06: limite `3`, leido de la variable de entorno `RESERVAS_MAX_ACTIVAS` que ya existe en `.env`. "Activa" = reserva `CONFIRMADA` cuya `fecha` + `horaInicio` aun **no** han ocurrido. Motivo: contar las pasadas dejaria a un usuario bloqueado para siempre tras tres reservas | HU-02, HU-06, §2.2, §4 |
| D-05 | No se puede reservar en una cancha inactiva: `404 NO_ENCONTRADO`, coherente con lo que el USUARIO ve en `ms-canchas`. La **disponibilidad** de una cancha inactiva **si** se consulta y devuelve todos los bloques con `disponible = false` | HU-01, HU-02, §4 |
| D-06 | Fallo de `ms-canchas` (caido, `5xx` o timeout): `500 ERROR_INTERNO` con `mensaje = "No se pudo consultar el catalogo de canchas"`. Timeouts de **2 s de conexion y 5 s de lectura**, **sin reintentos**. Registrado como decision de diseno | HU-01, HU-02, HU-07, HU-09 |
| D-07 | Reservar sobre un bloque bajo mantenimiento se rechaza con el mismo `409 BLOQUE_OCUPADO`: para el cliente es el mismo problema y la accion correctiva es identica. No se toca el contrato. Mismo criterio que la spec 03 aplico al reutilizar `BLOQUEO_DUPLICADO` para el solapamiento parcial | HU-02, §4, §5 |
| D-08 | **Contradiccion PDF / contrato resuelta: manda el contrato.** El ADMIN puede crear reservas y tiene historial propio, sin `403`. Motivo: un ADMIN tambien es una persona que puede reservar; el PDF §3.1 describe el reparto tipico, no una prohibicion, y agregar `403` seria inventar una regla que ninguna RN respalda. Queda escrito para la defensa | HU-02, HU-03, §5, §6.2 |
| D-09 | Ambos listados ordenan por `fecha` **descendente** y luego `horaInicio` **descendente**: lo mas reciente primero, que es lo que el frontend muestra arriba | HU-03, HU-04, §4 |
| D-10 | Cancelar una reserva que ya no esta `CONFIRMADA` no es `RESERVA_PASADA`: se agrega el codigo **`RESERVA_NO_CANCELABLE` (409)** al contrato. Motivo: son situaciones distintas y el mensaje al usuario tambien. Precedencia frente a D-02 en la consecuencia C-02 | HU-05, §4, §5, §6.1, contrato |
| D-11 | Confirmado el supuesto S-01: el cuerpo de `POST /api/reservas` es `{ canchaId, fecha, horaInicio }` y `horaFin` lo calcula el servicio | HU-02, §5 |

### 9.2 Supuestos aplicados en este documento

| # | Supuesto | Base |
|---|---|---|
| S-01 | Confirmado como decision D-11: el cuerpo del alta es `{ canchaId, fecha, horaInicio }`. El cuerpo de `PATCH /api/reservas/{id}/cancelacion` es vacio | D-11; `ck_reserva_bloque_una_hora` hace redundante enviar `horaFin` |
| S-02 | La respuesta de una reserva es `{ id, usuarioId, canchaId, fecha, horaInicio, horaFin, estado }` | Son exactamente las columnas de la tabla `reserva` y todos los campos existen en el contrato |
| S-03 | Toda reserva nace `CONFIRMADA`; `id`, `usuarioId` y `estado` enviados en el cuerpo se ignoran | RN-08 y el patron ya aplicado en las specs 02 y 03 |
| S-04 | Los bloques de disponibilidad se generan de una hora en punto a la siguiente, desde `horaApertura` hasta `horaCierre`, descartando cualquier resto menor a una hora si el horario no fuera multiplo exacto | RN-01 y el ejemplo del payload congelado |
| S-05 | `GET /api/reservas/mias` devuelve el historial completo del usuario, en todos los estados | El PDF §3.1 lo llama "historial de reservas propias" |
| S-06 | `GET /api/reservas` devuelve todas las reservas sin filtro ni paginacion | El contrato no declara parametros en esa ruta |
| S-07 | La validacion del JWT reutiliza el mecanismo de las specs 02 y 03: HS256, `JWT_SECRET` compartido, claims `sub` y `rol`, validacion local | Spec 02 HU-05 y spec 03 S-07; `CLAUDE.md` §3 |
| S-08 | Las rutas de `springdoc-openapi` quedan publicas, como en `ms-usuarios` y `ms-canchas` | Coherencia con las specs 02 y 03 |
| S-09 | "Ya ocurrio" (RN-04, D-02, D-03, D-04) se evalua contra la fecha y hora del servidor; el contenedor toma la zona horaria del host | Mismo criterio que el supuesto S-09 de la spec 03; ninguna fuente define zona horaria |
| S-10 | Puerto publicado `8084:8080` y `MS_CANCHAS_URL = http://ms-canchas:8080` | `8081`, `8082` y `8083` ya estan tomados en `docker-compose.yml`; la llamada es servidor a servidor, no del navegador |
| S-11 | `groupId` `ec.ups.dae`, `artifactId` `ms-reservas`, paquete raiz `ec.ups.dae.reservas` | Misma convencion de las specs 02 y 03 y de `CLAUDE.md` §4 |
| S-12 | **Confirmado por el responsable el 23/08/2026.** `ms-reservas` **emite** tokens `SERVICIO` pero **no los acepta**: uno entrante responde `401 NO_AUTENTICADO`, porque ningun endpoint suyo se consume asi y las operaciones sin `sub` no tienen dueno | D-01 y confirmacion explicita del responsable |
| S-13 | **Verificado.** `RESERVAS_MAX_ACTIVAS` ya existe en `.env` y en `.env.example` con valor `3`, confirmado por el responsable el 23/08/2026. Esta spec solo la propaga al servicio en `docker-compose.yml`; no la crea ni cambia su valor | D-04 |
