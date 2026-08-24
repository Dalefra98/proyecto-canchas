# Spec 07 — mf-reservas (remote de Module Federation) · requirements.md

Estado: **C1 — APROBADO** el 24/08/2026 ("Apruebo requisitos de la spec 07").
Las nueve preguntas abiertas (P-01 a P-09) fueron **respondidas por el responsable el
24/08/2026** y estan incorporadas al cuerpo de este documento; las decisiones y sus motivos
quedan en §8, para la defensa del proyecto.

La compuerta C2 (`design.md`) sigue **pendiente**: no se escribe codigo de produccion hasta que
el diseño este aprobado por escrito (`CLAUDE.md` §6).

Fuentes leidas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.1, 3.3.2, 3.3.3, 3.4,
3.5, 4.1, 4.4, 7), `.claude/specs/04-ms-reservas/` (decisiones D-01 a D-11),
`.claude/specs/03-ms-canchas/`, `.claude/specs/06-shell-module-federation/` (contrato de props
y decisiones P-01 a P-09, C-1 y C-2), `docker-compose.yml`, `docs/bitacora.md` y
`frontend/shell/` ya entregado.

## 1. Objetivo

Implementar `frontend/mf-reservas`: el **primer remote** de Module Federation del proyecto.
Es el modulo **Reservas** del PDF §3.2, con sus tres pantallas:

| Pantalla del PDF §3.2 | Descripcion del PDF |
|---|---|
| Consulta de disponibilidad | Calendario/grilla de horarios disponibles por cancha y deporte |
| Nueva reserva | Formulario para reservar cancha, fecha y bloque horario |
| Mis reservas | Listado de reservas del usuario con opcion de cancelar |

El remote se llama `mfReservas`, expone `./ReservasApp`, corre en el puerto 3001 y recibe del
shell exactamente las cuatro props del contrato congelado. **No** tiene sesion propia, **no**
pinta cabecera ni menu (eso es del shell, spec 06) y **no** implementa ninguna pantalla de
administracion ni de reportes.

Esta spec es tambien la que **verifica por primera vez la carga real de un remote**: la spec 06
dejo escrito en su P-04 que su HU-06 se verificaba con el borde de error y que la carga real se
comprueba aqui.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto `frontend/mf-reservas`: React 18 + Webpack 5 con `ModuleFederationPlugin`, `name: "mfReservas"`, `exposes` con la clave `"./ReservasApp"`, puerto 3001 |
| E-02 | `src/index.js` que solo hace `import("./bootstrap")`, mas `src/bootstrap.jsx` y el componente expuesto (`CLAUDE.md` §4) |
| E-03 | Pantalla **Consulta de disponibilidad**: selector de `deporte` con opcion "Todos", selector de cancha, fecha (hoy por defecto) y grilla de bloques de una hora con su estado libre/ocupado |
| E-04 | Pantalla **Nueva reserva**: se abre al elegir un bloque libre de la grilla, llega **precargada** con cancha, fecha y bloque, y ofrece confirmar (`POST /api/reservas`) o volver a la grilla |
| E-05 | Pantalla **Mis reservas**: listado completo de `GET /api/reservas/mias`, en todos los estados, en el orden recibido y sin filtros |
| E-06 | Cancelacion contra `PATCH /api/reservas/{id}/cancelacion`, con paso de confirmacion previo y refresco del listado |
| E-07 | Capa `src/api/` como **unica** capa que hace `fetch`, siempre con rutas relativas bajo el `apiBaseUrl` recibido por prop |
| E-08 | Uso del `token` recibido por prop en el encabezado `Authorization: Bearer <token>` de toda llamada; el remote **no** lee `sessionStorage` |
| E-09 | Manejo de los codigos de error del contrato (`400`, `401`, `403`, `404`, `409`, `500`) mostrando el `mensaje` recibido |
| E-10 | Invocacion de la prop `onLogout` ante un `401 NO_AUTENTICADO` |
| E-11 | Navegacion interna entre las tres pantallas por estado de React, **sin enrutador** (coherente con P-05 de la spec 06) |
| E-12 | `shared` con `react` y `react-dom` en `singleton: true`, CSS plano propio, sin libreria de UI y sin TypeScript |
| E-13 | `devServer` con `host: "0.0.0.0"`, `allowedHosts: "all"`, `watchOptions: { poll: 1000, ignored: /node_modules/ }` y `proxy` de los cuatro prefijos de `/api` hacia nombres de contenedor (P-02 de la spec 06) |
| E-14 | Servicio `mf-reservas` en `docker-compose.yml`: `node:20-alpine`, `webpack serve`, volumen del codigo mas volumen anonimo `mf_reservas_node_modules`, `ports: "3001:3001"` y `depends_on` de `ms-canchas` y `ms-reservas` |

## 3. Contexto tecnico fijado (no se vuelve a decidir)

| Aspecto | Valor | Fuente |
|---|---|---|
| Ruta en el repo | `frontend/mf-reservas` | `CLAUDE.md` §4 |
| Nombre Module Federation | `mfReservas` | contrato, "Contrato Module Federation" |
| Modulo expuesto | `./ReservasApp` | contrato |
| Puerto | 3001 | contrato |
| React | 18 (`18.3.1`, la version del shell) | `CLAUDE.md` §3, `frontend/shell/package.json` |
| Empaquetador | Webpack 5 con `ModuleFederationPlugin` | `CLAUDE.md` §3 |
| `shared` | `react` y `react-dom` con `singleton: true` | `CLAUDE.md` §3 |
| Arranque | `src/index.js` -> `import("./bootstrap")` | `CLAUDE.md` §3 |
| Llamadas HTTP | rutas relativas bajo `/api`, unica capa `src/api/` | `CLAUDE.md` §3 y §4 |
| Autenticacion | `token` recibido por prop, nunca leido del almacenamiento del navegador | contrato de props, D-12 de la spec 06 |
| Enrutador | **ninguno**: la pantalla activa es estado de React | P-05 de la spec 06 |
| Pantallas | tres, con paso de la grilla al formulario precargado | P-01 |
| Fecha por defecto de la grilla | **hoy**; el selector no impide fechas pasadas | P-02 |
| Filtro por deporte | selector con opcion "Todos" en la grilla; **ninguno** en Mis reservas | P-03 |
| Ejecucion suelta en `localhost:3001` | **no** hay aplicacion usable: el remote solo publica `remoteEntry.js` | P-04 |
| Estilos | CSS plano, sin librerias de UI externas | `CLAUDE.md` §3 |
| Lenguaje | JavaScript, **sin** TypeScript | `CLAUDE.md` §3 |
| Idioma | identificadores en español sin tildes; textos en español | `CLAUDE.md` §7 |
| Instalacion de dependencias | solo por Docker (`node:20-alpine`) | `CLAUDE.md` §1 |
| Watcher | `watchOptions: { poll: 1000, ignored: /node_modules/ }` | `docs/bitacora.md`: el bind mount de Windows no entrega inotify |
| Destinos del proxy | `http://ms-usuarios:8080`, `ms-canchas`, `ms-reservas`, `ms-reportes` (nombres de contenedor) | P-02 de la spec 06 |
| `depends_on` del servicio | `ms-canchas` y `ms-reservas`, los dos que el remote consume | P-07 |

Props que el remote **recibe** del shell, tal como quedaron congeladas el 23/08/2026:

```jsx
<ReservasApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

**Distincion que ya resolvio la spec 06 y aqui no se vuelve a discutir**: la URL del
`remoteEntry.js` es del **navegador** (`http://localhost:3001/remoteEntry.js`), mientras el
destino del `proxy` de `/api` es un **nombre de contenedor**, porque el proxy lo ejecuta
`webpack serve` dentro de la red de Docker.

Consecuencia practica: cuando el remote se monta dentro del shell, su codigo corre en el origen
del shell (`http://localhost:3000`) y sus rutas relativas `/api/...` las proxya el `devServer`
del **shell**. El `proxy` propio del remote (E-13) es el que responde cuando el remote se sirve
por separado en `http://localhost:3001`, y es lo que la spec 06 dejo escrito: "cada remote
declarara los suyos igual".

## 4. Historias de usuario y criterios de aceptacion

### HU-01 — Consultar la disponibilidad de una cancha en una fecha (PDF §3.3.1, RN-01)

Como usuario autenticado, necesito ver que bloques de una hora estan libres en una cancha y una
fecha, para elegir cual reservar.

- **CUANDO** se abra la pantalla de disponibilidad, **ENTONCES** el remote pedira el catalogo
  con `GET /api/canchas` y ofrecera un selector de `deporte` con la opcion **"Todos"** y un
  selector de cancha que muestra su `nombre` y su `deporte` (P-03).
- **CUANDO** se elija un `deporte`, **ENTONCES** el selector de canchas mostrara solo las de ese
  `deporte`, filtradas **en el navegador**: `GET /api/canchas` no acepta parametro de filtrado y
  no se le inventa uno.
- **CUANDO** se muestren los valores de `deporte`, **ENTONCES** seran exactamente `PADEL`,
  `TENIS` y `BASQUET` (contrato).
- **CUANDO** la pantalla se abra, **ENTONCES** el campo de fecha llegara con la **fecha de hoy**
  tomada del navegador, en formato `AAAA-MM-DD` (P-02).
- **CUANDO** el usuario elija una fecha ya pasada, **ENTONCES** el selector **lo permite** y la
  consulta se hace igual: la disponibilidad de una fecha pasada es informativa y responde `200`
  (D-03 de la spec 04). El rechazo de reservar en el pasado aparece solo al intentar la reserva
  (HU-02) y lo aplica `ms-reservas`.
- **CUANDO** el usuario elija una cancha y una `fecha`, **ENTONCES** el remote llamara a
  `GET /api/reservas/disponibilidad?canchaId=<canchaId>&fecha=<AAAA-MM-DD>`, con los nombres de
  parametro exactos del contrato.
- **CUANDO** la respuesta sea `200`, **ENTONCES** la grilla mostrara un elemento por cada objeto
  de `bloques`, en el orden recibido, con su `horaInicio`, su `horaFin` y su estado visual segun
  `disponible`.
- **CUANDO** un bloque tenga `disponible = true`, **ENTONCES** se mostrara como libre y
  seleccionable; **CUANDO** tenga `disponible = false`, **ENTONCES** se mostrara como ocupado y
  **no** sera seleccionable.
- **CUANDO** el usuario elija un bloque libre, **ENTONCES** el remote pasara a la pantalla de
  nueva reserva con la cancha, la `fecha` y el bloque ya cargados (HU-02, P-01).
- **CUANDO** se muestre el horario de atencion, **ENTONCES** se usaran `horaApertura` y
  `horaCierre` de la respuesta, nunca un rango fijo escrito en el codigo.
- **CUANDO** el remote pinte la grilla, **ENTONCES** **no** recalculara `disponible` por su
  cuenta ni consultara bloqueos de mantenimiento: la disponibilidad la resuelve `ms-reservas`
  (HU-01 de la spec 04) y el remote solo la presenta.
- **CUANDO** la consulta este en curso, **ENTONCES** se mostrara un aviso de carga y el boton de
  consultar quedara deshabilitado.
- **CUANDO** se consulte una cancha con `activa = false`, **ENTONCES** la respuesta llega con
  todos los bloques en `disponible = false` (D-05 de la spec 04) y el remote la pinta tal cual,
  sin agregar un mensaje que la API no devolvio. Un `USUARIO` no vera esa cancha en el selector,
  porque `GET /api/canchas` le devuelve solo las `activa = true`; un `ADMIN` si.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrara el
  `mensaje` recibido junto al formulario de consulta, sin borrar la seleccion.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrara el
  `mensaje` recibido y la grilla quedara vacia.
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se mostrara el
  `mensaje` recibido y se permitira reintentar la consulta sin recargar la pagina. Es el caso
  previsto por D-06 de la spec 04 (`ms-canchas` caido o con timeout) y, segun la bitacora, el
  que puede aparecer una o dos veces justo despues de reiniciar un contenedor.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-06.

### HU-02 — Crear una reserva (PDF §3.3.2, RN-01, RN-02, RN-06)

Como usuario autenticado, necesito reservar el bloque que elegi en la grilla, para asegurar mi
turno.

- **CUANDO** se abra la pantalla de nueva reserva, **ENTONCES** llegara **precargada** con el
  `nombre` y el `deporte` de la cancha, la `fecha` y el bloque (`horaInicio`–`horaFin`) elegidos
  en la grilla, y el usuario no volvera a escribir ninguno de esos datos (P-01).
- **CUANDO** la pantalla se muestre, **ENTONCES** ofrecera exactamente dos acciones: **confirmar
  la reserva** y **volver a la grilla** (P-01).
- **CUANDO** el usuario pulse volver, **ENTONCES** se regresara a la grilla con la misma cancha
  y fecha consultadas, sin crear nada.
- **CUANDO** el usuario confirme, **ENTONCES** el remote hara `POST /api/reservas` con el cuerpo
  exacto `{ "canchaId": ..., "fecha": "AAAA-MM-DD", "horaInicio": "HH:mm" }`.
- **CUANDO** se arme el cuerpo, **ENTONCES** **no** incluira `horaFin`, `usuarioId`, `id` ni
  `estado`: `ms-reservas` calcula `horaFin`, toma el `usuarioId` del token y fija
  `estado = CONFIRMADA` (HU-02 de la spec 04).
- **CUANDO** la respuesta sea `201`, **ENTONCES** el remote mostrara el aviso de reserva creada
  con los datos devueltos (`id`, `fecha`, `horaInicio`, `horaFin`, `estado`) y volvera a la
  grilla, consultando de nuevo la disponibilidad de esa cancha y fecha, de modo que el bloque
  aparezca ya ocupado.
- **CUANDO** solo se llegue a esta pantalla desde un bloque con `disponible = true`, **ENTONCES**
  el remote nunca ofrecera confirmar sobre un bloque ocupado.
- **CUANDO** la peticion este en curso, **ENTONCES** el boton de confirmar quedara
  deshabilitado, para que un doble clic no intente dos veces el mismo bloque.
- **CUANDO** el usuario en sesion tenga `rol = ADMIN`, **ENTONCES** puede crear reservas igual
  que un `USUARIO`, sin `403` (decision D-08 de la spec 04, corregida como C-2 en la spec 06).
- **SI** la respuesta es `409` con `codigo = BLOQUE_OCUPADO`, **ENTONCES** se mostrara el
  `mensaje` recibido y se refrescara la grilla: alguien reservo ese bloque antes, o cae bajo un
  bloqueo de mantenimiento (RN-02, D-07 de la spec 04).
- **SI** la respuesta es `409` con `codigo = LIMITE_RESERVAS`, **ENTONCES** se mostrara el
  `mensaje` recibido. El remote **no** cuenta reservas activas por su cuenta ni deshabilita el
  boton por adelantado: el limite lo aplica `ms-reservas` con `RESERVAS_MAX_ACTIVAS` (RN-06,
  D-04 de la spec 04) y el contrato no declara ninguna ruta para consultar el contador.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrara el
  `mensaje` recibido. Es el caso de la fecha y hora ya pasadas (D-03 de la spec 04) —el unico
  lugar donde ese rechazo aparece, porque el selector de fecha no lo impide (P-02)— y del bloque
  que no cabe en el horario de atencion.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrara el
  `mensaje` recibido: la cancha no existe o esta inactiva (D-05 de la spec 04).
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se mostrara el
  `mensaje` recibido y se permitira reintentar; la reserva no se creo.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-06.

### HU-03 — Ver mi historial de reservas (PDF §3.2 "Mis reservas", RN-08)

Como usuario autenticado, necesito ver mis reservas con su estado, para saber que tengo
reservado y que puedo cancelar.

- **CUANDO** se abra la pantalla de Mis reservas, **ENTONCES** el remote hara
  `GET /api/reservas/mias` y mostrara **todas** las reservas, en todos los estados, cada una con
  `fecha`, `horaInicio`, `horaFin`, `estado` y su cancha (HU-04).
- **CUANDO** se muestre el listado, **ENTONCES** **no** habra filtro por `estado` ni por
  `deporte` ni separacion entre proximas y pasadas (P-03, P-05): es un historial completo.
- **CUANDO** el listado llegue, **ENTONCES** se mostrara en el orden recibido: `fecha`
  descendente y, dentro de la misma fecha, `horaInicio` descendente (D-09 de la spec 04). El
  remote **no** reordena.
- **CUANDO** una reserva llegue con `estado = FINALIZADA`, **ENTONCES** se mostrara asi tal cual:
  es un estado derivado que calcula `ms-reservas` al leer (D-02 de la spec 04) y el remote **no**
  lo recalcula comparando fechas.
- **CUANDO** se muestren los estados, **ENTONCES** seran exactamente `CONFIRMADA`, `CANCELADA` y
  `FINALIZADA`, sin traducir ni abreviar (contrato).
- **CUANDO** el usuario no tenga reservas, **ENTONCES** se mostrara un aviso de listado vacio; la
  respuesta es `200` con arreglo vacio, nunca `404`.
- **CUANDO** el remote consuma esta ruta, **ENTONCES** **no** enviara ningun parametro de
  filtrado ni de paginacion: el contrato no declara ninguno.
- **CUANDO** quien mire el listado tenga `rol = ADMIN`, **ENTONCES** vera **sus propias**
  reservas, no las del sistema: el listado global vive en `mf-administracion` (spec 08).
- **CUANDO** el listado este cargando, **ENTONCES** se mostrara un aviso de carga.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-06.

### HU-04 — Nombre de la cancha en las pantallas del remote

Como usuario, necesito ver el nombre de la cancha de cada reserva, porque un numero no me dice
nada.

- **CUANDO** el remote muestre una reserva, **ENTONCES** resolvera el `nombre` y el `deporte` de
  su `canchaId` con el catalogo obtenido de `GET /api/canchas`: la respuesta de
  `/api/reservas/mias` trae solo `canchaId`, y el contrato **no** declara `nombre` en esa
  respuesta (HU-04 de la spec 04 lo dice de forma explicita).
- **CUANDO** se pida el catalogo, **ENTONCES** se hara una sola vez por pantalla y se reutilizara
  para todas las filas, sin una llamada por reserva.
- **CUANDO** se resuelva el nombre, **ENTONCES** se usaran los campos exactos `canchaId`,
  `nombre` y `deporte` del contrato.
- **SI** un `canchaId` del listado no aparece en el catalogo recibido, **ENTONCES** se mostrara
  el `canchaId` tal cual, sin inventar un nombre. Es el caso de un `USUARIO` con una reserva de
  una cancha que fue inactivada: `GET /api/canchas` ya no la devuelve.
- **SI** la llamada al catalogo falla, **ENTONCES** el listado de reservas se muestra igual, con
  el `canchaId` en lugar del nombre y el aviso del error: un fallo del catalogo no oculta las
  reservas.

### HU-05 — Cancelar una reserva propia (PDF §3.3.3, RN-03, RN-04, RN-05)

Como usuario final, necesito cancelar una reserva mia que aun no ha ocurrido.

- **CUANDO** una reserva del listado tenga `estado = CONFIRMADA`, **ENTONCES** se ofrecera la
  accion de cancelar.
- **CUANDO** una reserva tenga `estado = CANCELADA` o `estado = FINALIZADA`, **ENTONCES** **no**
  se ofrecera la accion de cancelar: la primera ya esta cancelada y la segunda ya ocurrio
  (RN-04, precedencia C-02 de la spec 04).
- **CUANDO** el usuario pulse cancelar, **ENTONCES** el remote pedira una **confirmacion
  explicita** antes de llamar a la API, indicando la cancha, la `fecha` y el bloque de la reserva
  (P-06): cancelar es irreversible y el segundo intento responde `RESERVA_NO_CANCELABLE`.
- **CUANDO** el usuario rechace la confirmacion, **ENTONCES** no se hara ninguna llamada y el
  listado quedara igual.
- **CUANDO** el usuario confirme la cancelacion, **ENTONCES** el remote hara
  `PATCH /api/reservas/{id}/cancelacion` con el `id` de la reserva y **sin cuerpo**: el contrato
  no declara ningun campo de entrada.
- **CUANDO** la respuesta sea `200`, **ENTONCES** se mostrara el aviso de cancelacion y se
  refrescara el listado, de modo que la reserva pase a `CANCELADA` (RN-05: el bloque queda libre
  y vuelve a aparecer con `disponible = true` en HU-01).
- **CUANDO** la peticion este en curso, **ENTONCES** el boton de cancelar de esa fila quedara
  deshabilitado.
- **CUANDO** se cancele una reserva, **ENTONCES** el remote **no** enviara ninguna notificacion
  ni llamara a ninguna otra ruta: no hay notificaciones (`CLAUDE.md` §2).
- **CUANDO** se evalue si una reserva es cancelable, **ENTONCES** el remote se guiara **solo** por
  el `estado` recibido; la validacion real de RN-03 y RN-04 la aplica `ms-reservas`, y ocultar un
  boton no es control de acceso.
- **SI** la respuesta es `409` con `codigo = RESERVA_PASADA`, **ENTONCES** se mostrara el
  `mensaje` recibido y se refrescara el listado (RN-04).
- **SI** la respuesta es `409` con `codigo = RESERVA_NO_CANCELABLE`, **ENTONCES** se mostrara el
  `mensaje` recibido y se refrescara el listado: la reserva ya estaba `CANCELADA`.
- **SI** la respuesta es `403` con `codigo = SIN_PERMISO`, **ENTONCES** se mostrara el `mensaje`
  recibido. No deberia ocurrir en esta pantalla, porque el listado es de reservas propias (RN-03).
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrara el `mensaje`
  recibido y se refrescara el listado.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-06.

### HU-06 — Props recibidas del shell y sesion ajena

Como equipo, necesito que el remote use exactamente las cuatro props del contrato y no invente
su propia sesion.

- **CUANDO** el shell monte `./ReservasApp`, **ENTONCES** el remote leera exactamente `usuario`
  (`usuarioId`, `nombre`, `rol`), `token`, `apiBaseUrl` y `onLogout`, y ninguna prop mas.
- **CUANDO** el remote llame a la API, **ENTONCES** compondra la URL con el `apiBaseUrl` recibido
  (valor literal `"/api"`), nunca con una URL absoluta ni con un nombre de contenedor.
- **CUANDO** el remote llame a la API, **ENTONCES** enviara `Authorization: Bearer <token>` con
  el `token` de la prop, que llega sin el prefijo `Bearer ` (HU-07 de la spec 06).
- **CUANDO** el remote necesite la sesion, **ENTONCES** **no** leera `sessionStorage` ni
  `localStorage`: el dueño de la sesion es el shell (D-12 de la spec 06).
- **CUANDO** cualquier llamada responda `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** el
  remote invocara `onLogout()` y no pintara su propia pantalla de inicio de sesion (P-08 de la
  spec 06).
- **CUANDO** el `token` de la prop cambie, **ENTONCES** las llamadas siguientes usaran el valor
  nuevo: el remote no guarda una copia aparte.
- **CUANDO** el remote se pinte, **ENTONCES** **no** dibujara cabecera, menu de modulos ni boton
  de cierre de sesion: el layout es del shell (spec 06, E-07 y E-08).
- **CUANDO** el remote use el `nombre` del usuario, **ENTONCES** sera solo para texto en pantalla;
  el `usuarioId` de una reserva creada lo pone `ms-reservas` desde el token, no el remote (HU-02
  de la spec 04).

### HU-07 — Integracion como remote de Module Federation (PDF §4.1, rubrica §6)

Como equipo, necesito que este microfrontend se pueda desarrollar y desplegar por separado y que
el shell lo cargue en tiempo de ejecucion.

- **CUANDO** se configure `ModuleFederationPlugin`, **ENTONCES** declarara `name: "mfReservas"` y
  `exposes` con la clave exacta `"./ReservasApp"`: los dos nombres del contrato congelado.
- **CUANDO** se declare `shared`, **ENTONCES** `react` y `react-dom` iran con `singleton: true` y
  con las **mismas versiones** que el shell (`18.3.1`), para que shell y remote compartan una
  sola instancia de React.
- **CUANDO** el remote se sirva, **ENTONCES** publicara `http://localhost:3001/remoteEntry.js`,
  que es la URL que el shell ya declara en su `webpack.config.js`.
- **CUANDO** alguien abra `http://localhost:3001` en el navegador, **ENTONCES** **no** vera una
  aplicacion usable, y eso **es lo correcto, no un defecto** (P-04): el remote solo publica su
  `remoteEntry.js`, y sin el shell no hay `token`, `usuario` ni `onLogout` que entregarle. Queda
  escrito aqui para que nadie lo reporte como error.
- **CUANDO** el shell entre al modulo Reservas con el remote levantado, **ENTONCES** se descargara
  `remoteEntry.js` y se montara la pantalla del remote **en lugar** del mensaje de modulo no
  disponible que la spec 06 verifico con su borde de error.
- **CUANDO** el remote se reconstruya y se sirva de nuevo, **ENTONCES** bastara con volver a
  entrar al modulo Reservas para verlo actualizado, sin reconstruir el shell.
- **CUANDO** el remote se monte, **ENTONCES** la consola del navegador no mostrara errores de
  React duplicado ni de `hooks` invalidos: es el sintoma tipico de un `singleton` mal declarado.
- **CUANDO** se defina el arranque, **ENTONCES** `src/index.js` solo hara `import("./bootstrap")`
  (`CLAUDE.md` §3), y el modulo expuesto sera un componente de React que recibe las props, no un
  `ReactDOM.render`.

### HU-08 — El remote corre en el entorno local (PDF §4.4)

Como equipo, necesito levantar el remote junto al resto del sistema con Docker Compose.

- **CUANDO** se instalen las dependencias, **ENTONCES** sera con
  `docker run --rm -v "${PWD}:/app" -w /app node:20-alpine npm install`, nunca con `npm` en el
  host (`CLAUDE.md` §1).
- **CUANDO** se levante el entorno, **ENTONCES** el remote correra como servicio `mf-reservas` de
  `docker-compose.yml`, con el **mismo patron del servicio `shell`** ya existente: imagen
  `node:20-alpine`, `command: sh -c "npm install && npx webpack serve --mode development"`,
  volumen del codigo, volumen anonimo `mf_reservas_node_modules` para `node_modules` y
  `ports: "3001:3001"` (P-07).
- **CUANDO** se declare `depends_on`, **ENTONCES** seran **`ms-canchas` y `ms-reservas`**, los dos
  microservicios que el remote consume, y no los cuatro: no llama a `/api/usuarios` ni a
  `/api/reportes` (P-07).
- **CUANDO** se levante el sistema, **ENTONCES** el `shell` **no** declarara `depends_on` de este
  remote y el orden de arranque no importara: el `remoteEntry.js` lo descarga el navegador al
  entrar al modulo, y si el remote esta caido el borde de error del shell ya lo maneja (P-08,
  HU-06 de la spec 06).
- **CUANDO** se configure el `devServer`, **ENTONCES** llevara `host: "0.0.0.0"` y
  `allowedHosts: "all"`, o el navegador del host no alcanzara el servidor dentro del contenedor.
- **CUANDO** se configure el watcher, **ENTONCES** llevara
  `watchOptions: { poll: 1000, ignored: /node_modules/ }`: en Windows el bind mount no entrega
  eventos inotify y sin sondeo el remote nunca recompila al guardar (bitacora, T3 de la spec 06).
- **CUANDO** se agregue el servicio al `docker-compose.yml`, **ENTONCES** ese sera el **unico**
  archivo modificado fuera de `frontend/mf-reservas` (P-08).
- **CUANDO** se verifique una tarea, **ENTONCES** la **primera** comprobacion sera
  `curl.exe http://localhost:3001/remoteEntry.js`: si no responde `200`, el problema esta en el
  remote y no en la integracion (P-09).
- **CUANDO** la tarea tenga interaccion, **ENTONCES** hara falta ademas el recorrido por
  navegador: iniciar sesion en `http://localhost:3000`, entrar a Reservas, consultar
  disponibilidad, crear una reserva, verla en Mis reservas y cancelarla (P-09). Un
  `compiled successfully` no prueba que la aplicacion funcione (bitacora, T5 de la spec 06).

### HU-09 — Errores uniformes y sin datos inventados

Como equipo, necesito que el remote muestre siempre el error que devolvio el microservicio.

- **CUANDO** una llamada falle con un cuerpo de error del contrato, **ENTONCES** el remote
  mostrara el `mensaje` recibido, sin reescribirlo ni traducirlo.
- **CUANDO** el remote decida que hacer ante un error, **ENTONCES** lo hara por el `codigo`, no
  por el texto del `mensaje`.
- **CUANDO** el remote muestre cualquier dato de una reserva o de una cancha, **ENTONCES** usara
  los campos exactos del contrato y **no** calculara ni completara ninguno que la API no haya
  devuelto.
- **CUANDO** un error se muestre, **ENTONCES** la pantalla no quedara en blanco y la navegacion
  interna seguira funcionando.
- **SI** la respuesta no trae `codigo` ni `mensaje` (por ejemplo, la peticion no llego al
  microservicio), **ENTONCES** el remote mostrara un aviso propio de fallo de comunicacion y
  permitira reintentar, sin pintar un stacktrace ni el objeto de error crudo.

## 5. Reglas de negocio cubiertas

| ID | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora | **Presentada** — la grilla muestra bloques de una hora (HU-01) y el formulario envia `canchaId`, `fecha` y `horaInicio`, sin `horaFin` (HU-02). La validacion es de `ms-reservas` |
| RN-02 | No se puede reservar un bloque ya ocupado | **Presentada** — los bloques con `disponible = false` no son seleccionables, asi que al formulario solo se llega con un bloque libre, y el `409 BLOQUE_OCUPADO` se muestra y refresca la grilla (HU-01, HU-02). La valida `ms-reservas` |
| RN-03 | El usuario cancela solo sus reservas; el admin cualquiera | **Cubierta en su mitad de usuario** — este remote solo lista y cancela reservas propias (`/api/reservas/mias`); la cancelacion de cualquier reserva vive en `mf-administracion` (spec 08) |
| RN-04 | Solo se cancela una reserva que aun no ha ocurrido | **Presentada** — no se ofrece cancelar una reserva `FINALIZADA` ni `CANCELADA`, y el `409 RESERVA_PASADA` se muestra (HU-05). La valida `ms-reservas` |
| RN-05 | Cancelar libera el bloque | **Presentada** — tras cancelar se refresca el listado, y la disponibilidad vuelve a mostrar el bloque libre (HU-05, HU-01) |
| RN-06 | Limite configurable de reservas activas | **Presentada** — el remote muestra el `409 LIMITE_RESERVAS`; no cuenta reservas activas por su cuenta, porque el contrato no declara ninguna ruta para consultar el contador (HU-02) |
| RN-07 | Solo el admin gestiona canchas y su horario | **No aplica** — este remote solo **lee** el catalogo con `GET /api/canchas`; la gestion es de `mf-administracion` |
| RN-08 | Estados `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | **Presentada** — los tres se muestran con su nombre exacto; `FINALIZADA` llega ya calculada por `ms-reservas` (D-02 de la spec 04) y el remote no la recalcula (HU-03) |

Ninguna regla de negocio se **implementa** en el frontend: todas las validaciones viven en
`ms-reservas` (spec 04). Lo que hace este remote es presentar su resultado y no ofrecer acciones
que la regla ya prohibe. El caso mas explicito es P-02: el selector de fecha no bloquea el
pasado, precisamente para no duplicar en el cliente una regla que ya vive en el microservicio.

## 6. Contrato REST consumido

Nombres tomados literalmente de `docs/contratos/README.md`.

### 6.1 Rutas

| Verbo | Ruta | Rol | Respuestas | Historia |
|---|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | 200, 401 | HU-01, HU-04 |
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | ADMIN, USUARIO | 200, 400, 401, 404 | HU-01 |
| POST | `/api/reservas` | USUARIO | 201, 400, 401, 404, 409 | HU-02 |
| GET | `/api/reservas/mias` | USUARIO | 200, 401 | HU-03 |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | 200, 401, 403, 404, 409 | HU-05 |

El remote **no** consume `GET /api/reservas` (listado global, de `ADMIN` y de
`mf-administracion`), ni ninguna ruta de `/api/usuarios`, ni las de `/api/reportes`, ni las de
escritura de `/api/canchas`.

### 6.2 Campos

| Concepto | Campo | Tipo / valores | Uso en el remote |
|---|---|---|---|
| Identificador de reserva | `id` | number | clave del listado y ruta de `PATCH /api/reservas/{id}/cancelacion` |
| Estado de la reserva | `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | columna del listado y condicion para ofrecer cancelar |
| Fecha de la reserva | `fecha` | string `AAAA-MM-DD` | parametro de la consulta, cuerpo de la reserva y columna del listado |
| Hora de inicio | `horaInicio` | string `HH:mm` | bloque elegido, cuerpo de la reserva y columna del listado |
| Hora de fin | `horaFin` | string `HH:mm` | se **muestra**; nunca se envia |
| Identificador de cancha | `canchaId` | number | seleccion de cancha, cuerpo de la reserva y enlace con el catalogo |
| Nombre de cancha | `nombre` | string | selector de cancha, formulario precargado y columna del listado |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | filtro y selector de cancha, formulario precargado y columna del listado |
| Hora de apertura de la cancha | `horaApertura` | string `HH:mm` | encabezado de la grilla |
| Hora de cierre de la cancha | `horaCierre` | string `HH:mm` | encabezado de la grilla |
| Cancha activa | `activa` | boolean | se recibe en el catalogo; el remote no decide nada con el |
| Lista de bloques del dia | `bloques` | arreglo de objetos | filas de la grilla |
| Bloque libre | `disponible` | boolean | estado visual del bloque y si es seleccionable |
| Identificador de usuario | `usuarioId` | number | llega en la prop `usuario` y en cada reserva; nunca se envia en el cuerpo |
| Nombre de usuario | `nombre` | string | llega en la prop `usuario`; solo texto en pantalla |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | llega en la prop `usuario`; el remote no cambia de comportamiento por el |
| Token de sesion | `token` | string | llega por prop; va en `Authorization: Bearer <token>` |
| Codigo de error | `codigo` | ver §6.4 | selecciona la reaccion del remote |
| Mensaje de error | `mensaje` | string | se muestra tal cual |

### 6.3 Payloads consumidos

`DisponibilidadResponse`, congelado:

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

Cuerpo de `POST /api/reservas` (decision D-11 de la spec 04):

```json
{ "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00" }
```

Reserva devuelta por `POST /api/reservas`, `GET /api/reservas/mias` y
`PATCH /api/reservas/{id}/cancelacion` (campos de la spec 04, HU-02 a HU-05):

```json
{ "id": 7, "usuarioId": 2, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00", "horaFin": "10:00", "estado": "CONFIRMADA" }
```

Cancha devuelta por `GET /api/canchas` (campos del contrato, spec 03):

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00", "activa": true }
```

### 6.4 Codigos de error que el remote interpreta

| Situacion | HTTP | `codigo` | Que hace el remote |
|---|---|---|---|
| Validacion de entrada, fecha pasada, bloque fuera del horario | 400 | `DATOS_INVALIDOS` | muestra `mensaje` junto al formulario |
| Token vencido o invalido | 401 | `NO_AUTENTICADO` | invoca `onLogout()` (HU-06) |
| Sin permiso | 403 | `SIN_PERMISO` | muestra `mensaje` |
| Cancha o reserva inexistente, cancha inactiva | 404 | `NO_ENCONTRADO` | muestra `mensaje` y refresca la vista |
| Bloque ya reservado o bajo mantenimiento (RN-02, D-07) | 409 | `BLOQUE_OCUPADO` | muestra `mensaje` y refresca la grilla |
| Limite de reservas activas (RN-06) | 409 | `LIMITE_RESERVAS` | muestra `mensaje` |
| Reserva ya ocurrida (RN-04) | 409 | `RESERVA_PASADA` | muestra `mensaje` y refresca el listado |
| Reserva que ya no esta `CONFIRMADA` | 409 | `RESERVA_NO_CANCELABLE` | muestra `mensaje` y refresca el listado |
| Error no previsto en el servidor | 500 | `ERROR_INTERNO` | muestra `mensaje` y deja reintentar |

### 6.5 Contrato Module Federation

| Microfrontend | Nombre | Modulo expuesto | Puerto |
|---|---|---|---|
| shell | `shell` (host) | — | 3000 |
| **mf-reservas** | **`mfReservas`** | **`./ReservasApp`** | **3001** |
| mf-administracion | `mfAdministracion` | `./AdminApp` | 3002 |
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

## 7. Dependencias de esta spec

| Depende de | Estado | Para que |
|---|---|---|
| `ms-canchas` (spec 03) | cerrada y levantada | `GET /api/canchas` para el filtro, el selector y los nombres |
| `ms-reservas` (spec 04) | cerrada y levantada | disponibilidad, creacion, historial propio y cancelacion |
| `frontend/shell` (spec 06) | cerrada y levantada | declara `mfReservas@http://localhost:3001/remoteEntry.js` y entrega las cuatro props |
| `ms-usuarios` (spec 02) | cerrada y levantada | emite el `token` que el shell entrega por prop; el remote no llama ninguna de sus rutas |
| `mf-administracion`, `mf-reportes` | **no existen** | no se tocan aqui: son las specs 08 y 09 |

## 8. Decisiones tomadas (P-01 a P-09, respondidas el 24/08/2026)

**P-01 — Dos pantallas, con paso precargado. Salida (b).** La grilla de disponibilidad y el
formulario de nueva reserva son dos pantallas; elegir un bloque libre en la grilla lleva al
formulario ya cargado con cancha, fecha y bloque, con un boton de confirmar y otro de volver a
la grilla. Motivo: respeta las dos pantallas que el PDF §3.2 declara separadas y a la vez evita
que el usuario reescriba datos que ya eligio; la salida (c) habria colapsado dos pantallas del
alcance en una.

**P-02 — Fecha por defecto: hoy; el pasado no se bloquea en el cliente.** El campo de fecha
llega con la fecha de hoy y el selector **permite** elegir fechas pasadas: la consulta de
disponibilidad las admite (D-03 de la spec 04) y el `400 DATOS_INVALIDOS` aparece solo al
intentar reservar. Motivo: bloquear el selector duplicaria en el cliente una regla que ya vive en
`ms-reservas`, y consultar el pasado es informativo.

**P-03 — Filtro por deporte solo en la grilla. Salida (a).** Selector de `deporte` con la opcion
**"Todos"** que filtra el catalogo antes de elegir cancha; **sin** filtro en Mis reservas.
Motivo: el PDF §3.2 pide la grilla "por cancha y deporte" y con cuatro canchas el filtro ya
aporta; en Mis reservas el volumen no lo justifica. El filtro es en el navegador, porque
`GET /api/canchas` no acepta parametro de filtrado.

**P-04 — El remote no se ejecuta suelto. Salida (a).** Se sirve solo para publicar
`remoteEntry.js`; abrir `http://localhost:3001` no muestra una aplicacion usable, y **eso es
correcto, no un defecto**: sin shell no hay `token`. Queda escrito en HU-07 para que no se lea
como error. Motivo: la salida (b) obligaria a inventar de donde sale el token en desarrollo, que
es exactamente lo que el contrato de props existe para evitar.

**P-05 — Mis reservas muestra todo, sin filtros. Salida (a).** Todas las reservas, en todos los
estados, en el orden en que llegan. Motivo: es un historial (S-05 de la spec 04) y el orden ya lo
decide `ms-reservas` con D-09.

**P-06 — Confirmacion antes de cancelar. Salida (a).** Un paso de confirmacion explicito antes de
llamar a `PATCH /api/reservas/{id}/cancelacion`. Motivo: cancelar es irreversible y el segundo
intento responde `RESERVA_NO_CANCELABLE`.

**P-07 — Servicio de Compose con el patron del shell. Salida (a).** `node:20-alpine`,
`command: sh -c "npm install && npx webpack serve --mode development"`, volumen del codigo,
volumen anonimo `mf_reservas_node_modules`, `ports: "3001:3001"` y `depends_on` de **`ms-canchas`
y `ms-reservas`**, los dos que su proxy usa. No de los cuatro: el remote no llama a usuarios ni a
reportes.

**P-08 — El shell no declara `depends_on` del remote. Salida (a).** El `remoteEntry.js` lo
descarga el navegador cuando el usuario entra al modulo, asi que el orden de arranque no importa,
y si el remote esta caido el borde de error del shell ya lo maneja (HU-06 de la spec 06). Se
mantiene que `docker-compose.yml` es el **unico** archivo modificado fuera de
`frontend/mf-reservas`.

**P-09 — Verificacion doble. Salida (b).** Primero `curl.exe` contra
`http://localhost:3001/remoteEntry.js`: si no responde `200`, el problema esta en el remote y no
en la integracion. Despues el recorrido completo por navegador: iniciar sesion en
`http://localhost:3000`, entrar a Reservas, consultar disponibilidad, crear una reserva, verla en
Mis reservas y cancelarla.

## 9. Fuera de alcance de esta spec

- **`mf-administracion` y `mf-reportes`**: sus pantallas, sus `webpack.config.js` y sus servicios
  de Compose. Son las specs 08 y 09.
- **El listado global de reservas** (`GET /api/reservas`) y la cancelacion de reservas de otros
  usuarios: es la pantalla "Gestion de reservas" del modulo Administracion (PDF §3.2), no de este
  remote.
- **Gestion de canchas, horarios de atencion y bloqueos de mantenimiento**: solo `ADMIN`, vive en
  `mf-administracion` (RN-07). Aqui el catalogo se **lee**.
- **Gestion de usuarios** y toda pantalla de `/api/usuarios`: registro e inicio de sesion son del
  shell (P-07 de la spec 06) y la activacion de usuarios es de Administracion.
- **Reportes**: los tres endpoints de `/api/reportes` son de `ADMIN` y de `mf-reportes`.
- **Modificar el shell**: ya declara este remote y ya entrega las props, y P-08 confirma que no
  lleva `depends_on` del remote. Si algo obligara a tocarlo, se detiene la tarea y se avisa
  (`CLAUDE.md` §0.4).
- **Modificar `backend/`, `infra/postgres/` o `docs/contratos/README.md`**: esta spec no necesita
  ningun campo, ruta ni codigo de error nuevo.
- **Ejecutar el remote como aplicacion independiente en el navegador**: no hay `bootstrap` con
  props de desarrollo ni token de prueba (P-04).
- **Bloquear fechas pasadas en el selector** o contar reservas activas en el cliente: son reglas
  de `ms-reservas` y no se duplican (P-02, RN-06).
- **El gateway Nginx** y la eliminacion de los mapeos `8082`–`8085`: quedan para la seccion 5 de
  integracion, con la decision ya escrita en §8 de la spec 06.
- **Enrutador, gestor de estado global, libreria de UI, TypeScript, tema oscuro, i18n** y
  cualquier dependencia npm que no exija React 18 + Webpack 5 + Module Federation.
- **Pruebas automatizadas de frontend**: ninguna spec anterior las incluyo y el PDF §5 no las
  pide como entregable.
- **Reservas recurrentes, pagos, notificaciones, torneos, app movil nativa y reportes BI**:
  prohibidos por el PDF §3.5 y `CLAUDE.md` §2.
- **Calculo local de reglas de negocio**: el remote no recalcula `disponible`, ni `FINALIZADA`,
  ni el limite de RN-06. Todo llega resuelto de `ms-reservas`.
- **Diseño responsive avanzado, animaciones y accesibilidad** mas alla de HTML semantico: la
  rubrica del PDF §6 no las puntua.

---

## Supuestos

**Sin supuestos.** Los nueve datos que faltaban se preguntaron como P-01 a P-09 y estan
respondidos por el responsable el 24/08/2026 en §8; ninguno se relleno con un valor inventado.

Todo lo demas salio de una fuente escrita: el nombre del remote, su modulo expuesto y su puerto
del "Contrato Module Federation"; las cuatro props del contrato congelado el 23/08/2026 (C-1 y
P-01 de la spec 06); las cinco rutas, sus parametros, sus payloads y sus codigos de error del
contrato y de la spec 04; `FINALIZADA` calculada al leer (D-02); el `ADMIN` que reserva y tiene
historial propio (D-08 y C-2); la ausencia de una ruta para consultar el limite de RN-06; la
necesidad de resolver el nombre de la cancha con `GET /api/canchas` (HU-04 de la spec 04); el
proxy hacia nombres de contenedor frente a las URLs de navegador de los remotes (P-02 de la
spec 06); y el `poll` del watcher mas la obligacion de verificar en navegador
(`docs/bitacora.md`).
