# Spec 07 — mf-reservas (remote de Module Federation) · tasks.md

Base: `requirements.md` (C1 aprobado 24/08/2026) y `design.md` (C2 aprobado 24/08/2026).

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado literal y se espera aprobacion. Ninguna tarea encadena la
siguiente. Cada tarea deja el remote compilando y sirviendo en `http://localhost:3001`, y el
shell funcionando en `http://localhost:3000`.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay Node ni npm: todo pasa por Docker (`CLAUDE.md` §1).
Se usa `curl.exe`, no `curl`.

## Como se verifica un microfrontend en esta spec

No hay `mvn clean package`: la compilacion la hace `webpack serve` **dentro del contenedor**, de
forma continua, y la prueba de que compilo es su propio registro:

```powershell
docker compose logs --tail=30 mf-reservas
```

`compiled successfully` significa que compilo; `ERROR in ...` dice el archivo y la linea. El
codigo esta montado por volumen (§9 del diseño), asi que **ninguna tarea reconstruye la imagen**:
solo T1 crea el servicio.

Orden de verificacion fijado por P-09, en toda tarea que lo permita:

1. `curl.exe http://localhost:3001/remoteEntry.js` -> `200`. Si falla, el problema es del remote
   y no de la integracion.
2. El recorrido en el navegador desde `http://localhost:3000`.

Un `compiled successfully` **no** basta: la bitacora ya registro que en un microfrontend el
registro verifica que el codigo compila, no que funcione (T5 de la spec 06). Toda tarea con
interaccion lleva su paso de navegador.

Credenciales del seed (`infra/postgres/05-seed.sql`): `admin@canchas.ec` / `Admin123` (ADMIN) y
`usuario@canchas.ec` / `Usuario123` (USUARIO).

Antes de T1, el resto del entorno debe estar arriba:

```powershell
docker compose up -d
docker compose ps
```

## T1 — Andamiaje del remote, servicio en Docker Compose y `remoteEntry.js`

**Que hace.** Crea el proyecto `frontend/mf-reservas` con las doce dependencias exactas de §3.4
(las mismas versiones del shell, sin agregar ninguna): `package.json`, `.babelrc`,
`public/index.html` y `webpack.config.js` con el `ModuleFederationPlugin` del remote
(`name: "mfReservas"`, `filename: "remoteEntry.js"`,
`exposes: { "./ReservasApp": "./src/ReservasApp" }`, sin `remotes`, `react` y `react-dom` en
`shared` con `singleton: true`, `publicPath: "auto"`, `uniqueName: "mfReservas"`), el `devServer`
completo (`port: 3001`, `host: "0.0.0.0"`, `allowedHosts: "all"`, `headers` con
`Access-Control-Allow-Origin: *`, `client.webSocketURL` hacia `ws://localhost:3001/ws`,
`client.overlay.runtimeErrors: false` y el `proxy` como **arreglo** de las cuatro entradas de
§3.3) y `watchOptions` con `poll: 1000` en la raiz de la configuracion. Crea `src/index.js` con
solo `import("./bootstrap")`, `src/bootstrap.jsx` con el **aviso estatico** de P-04 y un
`src/ReservasApp.jsx` que de momento pinta un texto fijo con el `nombre` recibido por prop.
Agrega el servicio `mf-reservas` a `docker-compose.yml` segun §9: `node:20-alpine`, comando
`npm install` + `webpack serve`, volumen del codigo mas volumen anonimo
`mf_reservas_node_modules`, `3001:3001`, `container_name: canchas-mf-reservas` y `depends_on` de
`ms-canchas` y `ms-reservas`. **No** se toca el servicio `shell` (P-08).

**Cubre.** E-01, E-02, E-13, E-14; HU-07 (configuracion y publicacion del `remoteEntry.js`),
HU-08; decisiones D-01, D-02, D-03. Ninguna RN: el remote no implementa reglas de negocio
(C1 §5).

**Verificacion.**

```powershell
docker compose up -d mf-reservas
docker compose logs --tail=40 mf-reservas
curl.exe -i http://localhost:3001/remoteEntry.js
curl.exe -i http://localhost:3001
```

El registro debe decir `compiled successfully`; el primer `curl.exe` devuelve `200` con el
`remoteEntry.js`; el segundo devuelve `200` con el HTML del aviso estatico. En el navegador,
`http://localhost:3001` muestra ese aviso y **no** una aplicacion usable: es el comportamiento
correcto de P-04, no un defecto.

## T2 — Capa `api/` y bloque de error

**Que hace.** Crea `src/api/clienteApi.js` como **unica** pieza que llama `fetch`: compone la URL
con el `apiBaseUrl` recibido, agrega `Authorization: Bearer <token>` con el token que se le pasa
por parametro (D-05), y normaliza toda respuesta de error a `{ codigo, mensaje }`, sintetizando
`ERROR_INTERNO` cuando el cuerpo no viene en el formato del contrato (§5.7). Crea
`src/api/canchasApi.js` con `listarCanchas` y `src/api/reservasApi.js` con
`consultarDisponibilidad`, `crearReserva`, `listarMisReservas` y `cancelarReserva` — esta ultima
enviando el `PATCH` **sin cuerpo** (§5.6). Crea `src/components/MensajeError.jsx`, que pinta
`{ codigo, mensaje }` sin interpretarlo. `clienteApi` no decide nada sobre el `401`: lo devuelve
normalizado y quien decide es `ReservasApp` en T3 (D-13).

**Cubre.** E-07, E-08, E-09; §5.1 a §5.7 y §7 del diseño; §6.1 (las cinco rutas quedan
declaradas en un solo lugar).

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
```

Debe decir `compiled successfully`, sin `ERROR in`. Todavia ninguna pantalla llama a estos
modulos: el criterio de esta tarea es que el proyecto siga compilando con la capa nueva dentro,
igual que la T2 de la spec 06.

## T3 — HU-06 y HU-07: modulo expuesto, catalogo y primera carga real dentro del shell

**Que hace.** Convierte `src/ReservasApp.jsx` en el modulo expuesto definitivo: recibe
`{ usuario, token, apiBaseUrl, onLogout }`, es dueño de `vista`, `canchas`, `errorCatalogo`,
`cargandoCatalogo`, `reservaPendiente` y `avisoExito` (§4.1), pide `GET /api/canchas` **una sola
vez** al montarse (D-07) y expone el envoltorio unico que detecta el `401 NO_AUTENTICADO` y llama
`onLogout()` (D-13). Crea `src/components/NavegacionInterna.jsx` con las **dos** opciones,
Disponibilidad y Mis reservas (D-17), y deja las dos vistas como marcadores de posicion que
muestran el catalogo ya cargado. Importa `src/estilos.css` desde `ReservasApp.jsx`, no desde
`bootstrap.jsx` (D-15), con el archivo aun minimo.

**Cubre.** E-11; HU-06 completa; HU-07 (carga real del remote, que la spec 06 dejo pendiente en
su P-04); HU-04 en su mitad de catalogo; decisiones D-06, D-07, D-13, D-15.

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
curl.exe -i http://localhost:3001/remoteEntry.js
```

En el navegador, iniciando sesion en `http://localhost:3000` como `USUARIO`:

1. Entrar al modulo Reservas: se monta la pantalla del remote **en lugar** del mensaje "Modulo no
   disponible" que la spec 06 verificaba. Es el criterio principal de esta tarea.
2. La navegacion interna cambia entre Disponibilidad y Mis reservas sin recargar la pagina y sin
   que la URL cambie.
3. En la pestaña de red: `remoteEntry.js` se descarga desde **`localhost:3001`** y
   `GET /api/canchas` sale hacia **`localhost:3000/api/canchas`**, o sea por el proxy del shell
   (§3.3), una sola vez.
4. La consola no muestra ningun error de "two instances of React" ni de `hooks` invalidos.
5. `docker compose stop mf-reservas` y volver a entrar al modulo: reaparece "Modulo no
   disponible" del shell, la sesion sigue viva. Despues `docker compose start mf-reservas`.

## T4 — HU-01: pantalla de disponibilidad y grilla de bloques

**Que hace.** Crea `src/components/PantallaDisponibilidad.jsx` con el filtro de `deporte` y la
opcion "Todos" (P-03, D-08), el selector de cancha alimentado por el catalogo de T3, el campo de
fecha con **hoy** por defecto calculado con los campos locales de `Date` (D-09) y sin bloquear
fechas pasadas (P-02), el boton de consultar que dispara la llamada (D-18) y los estados de carga
y error de §4.2. Crea `src/components/GrillaBloques.jsx`, que pinta `bloques` en el orden
recibido con `horaInicio`, `horaFin` y su estado visual segun `disponible`, muestra
`horaApertura`–`horaCierre` en el encabezado y solo permite elegir un bloque con
`disponible === true`.

**Cubre.** E-03; HU-01 completa; RN-01 y RN-02 en su parte de presentacion; RN-08 no aplica aqui;
decisiones D-08, D-09, D-18, D-19.

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
```

En el navegador, como `USUARIO` en el modulo Reservas:

1. La fecha llega con el dia de hoy y el filtro de deporte reduce el selector de canchas; con
   "Todos" vuelven a aparecer todas.
2. Consultar una cancha con horario `07:00`–`22:00` pinta **15 bloques**, de `07:00`–`08:00` a
   `21:00`–`22:00`, en orden ascendente.
3. Un bloque con reserva `CONFIRMADA` o bajo bloqueo de mantenimiento aparece ocupado y no se
   puede elegir.
4. Elegir una fecha pasada **si** consulta y responde con normalidad (P-02, D-03 de la spec 04).
5. Consultar sin elegir cancha no hace ninguna llamada.

## T5 — HU-02: nueva reserva precargada

**Que hace.** Crea `src/components/PantallaNuevaReserva.jsx`. Al elegir un bloque libre en la
grilla, `ReservasApp` guarda `reservaPendiente` con `canchaId`, `fecha`, `horaInicio` y `horaFin`
y cambia a `vista = "nuevaReserva"` (D-10); la pantalla muestra esos datos **precargados y no
editables**, con el `nombre` y el `deporte` de la cancha, y ofrece solo confirmar o volver a la
grilla (P-01). Confirmar hace `POST /api/reservas` con el cuerpo de tres campos de §5.2, con el
boton deshabilitado mientras la peticion esta en curso. Tras el `201` muestra el aviso de exito,
limpia `reservaPendiente`, vuelve a la grilla y la reconsulta (D-11). Conecta los codigos de
error de §7: `409 BLOQUE_OCUPADO`, `409 LIMITE_RESERVAS`, `400 DATOS_INVALIDOS`,
`404 NO_ENCONTRADO` y `500 ERROR_INTERNO`.

**Cubre.** E-04; HU-02 completa; RN-01, RN-02 y RN-06 en su parte de presentacion; decisiones
D-10, D-11, D-14.

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
```

En el navegador, como `USUARIO`:

1. Elegir un bloque libre abre la pantalla con cancha, fecha y bloque ya cargados, sin reescribir
   nada; "Volver a la grilla" regresa sin crear nada.
2. Confirmar devuelve `201`, muestra el aviso y la grilla reconsultada deja ese bloque **ocupado**
   (RN-02 visible sin tocar nada).
3. Reservar un bloque que otro usuario acaba de tomar muestra el `mensaje` de
   `409 BLOQUE_OCUPADO` y refresca la grilla.
4. Con tres reservas activas, una cuarta muestra el `mensaje` de `409 LIMITE_RESERVAS` (RN-06).
5. Confirmar un bloque de una fecha pasada muestra el `mensaje` de `400 DATOS_INVALIDOS`: es el
   unico punto donde ese rechazo aparece (P-02).
6. Un `ADMIN` puede reservar igual, sin `403` (D-08 de la spec 04).

## T6 — HU-03, HU-04 y HU-05: mis reservas y cancelacion con confirmacion

**Que hace.** Crea `src/components/PantallaMisReservas.jsx`, que pide `GET /api/reservas/mias` y
pinta **todas** las reservas, en todos los estados, en el orden recibido y sin filtros (P-05).
Crea `src/components/FilaReserva.jsx`, que resuelve `canchaId` a `nombre` y `deporte` con el
catalogo de T3 y muestra el `canchaId` tal cual si la cancha no esta en el catalogo (HU-04);
ofrece cancelar **solo** si `estado === "CONFIRMADA"` (§6.3). Crea
`src/components/ConfirmacionCancelacion.jsx` con el paso de confirmacion propio, sin
`window.confirm` (P-06, D-12). Confirmar hace `PATCH /api/reservas/{id}/cancelacion` sin cuerpo,
deshabilita el boton de esa fila mientras dura y recarga el listado; conecta
`409 RESERVA_PASADA`, `409 RESERVA_NO_CANCELABLE`, `403 SIN_PERMISO` y `404 NO_ENCONTRADO`.

**Cubre.** E-05, E-06; HU-03, HU-04 y HU-05 completas; RN-03, RN-04, RN-05 y RN-08 en su parte de
presentacion; decision D-12.

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
```

En el navegador, como `USUARIO`:

1. El listado muestra la reserva creada en T5 con su cancha, `fecha`, `horaInicio`, `horaFin` y
   `estado = CONFIRMADA`, con lo mas reciente arriba (D-09 de la spec 04).
2. Una reserva `CANCELADA` o `FINALIZADA` se muestra con su estado exacto y **sin** boton de
   cancelar (RN-04, RN-08).
3. Cancelar pide confirmacion; rechazarla no hace ninguna llamada (pestaña de red vacia).
4. Confirmar devuelve `200`, la reserva pasa a `CANCELADA` y su bloque vuelve a aparecer
   **libre** en la grilla de T4 (RN-05).
5. Intentar cancelar dos veces la misma reserva muestra el `mensaje` de
   `409 RESERVA_NO_CANCELABLE`.
6. Un usuario sin reservas ve el aviso de listado vacio, no un error.

## T7 — Estilos, revision del contrato y verificacion integral

**Que hace.** Completa `src/estilos.css` —unico archivo de estilos, importado desde
`ReservasApp.jsx` (D-15)— con **todas** las clases prefijadas `mfr-` (D-16) para la navegacion
interna, la grilla, el formulario, el listado y los bloques de aviso y de error: CSS plano, sin
librerias de UI. Cierra la spec con la revision del §1 del diseño: que los campos usados sean
exactamente los del contrato (`canchaId`, `nombre`, `deporte`, `horaApertura`, `horaCierre`,
`fecha`, `bloques`, `horaInicio`, `horaFin`, `disponible`, `id`, `usuarioId`, `estado`), que el
cuerpo de `POST /api/reservas` siga siendo de tres campos y que las cuatro props se lean tal como
llegan. Sin cambios de comportamiento.

**Cubre.** E-12; §1, §10 y §11 del diseño; cierre de HU-01 a HU-09.

**Verificacion.**

```powershell
docker compose logs --tail=30 mf-reservas
docker compose ps
curl.exe -i http://localhost:3001/remoteEntry.js
```

Y el recorrido completo del navegador en una sola pasada (P-09): iniciar sesion como `USUARIO`,
entrar a Reservas, filtrar por deporte, consultar disponibilidad, reservar un bloque libre, verlo
ocupado en la grilla, abrir Mis reservas, cancelar con confirmacion, comprobar el bloque libre de
nuevo, cerrar sesion, entrar como `ADMIN` y repetir reserva y cancelacion. Ningun error en la
consola del navegador y ningun `ERROR in` en el registro del contenedor.

## Lo que ninguna tarea hace

- Crear `mf-administracion` ni `mf-reportes`: son las specs 08 y 09.
- Modificar el servicio `shell` de `docker-compose.yml` ni un solo archivo de `frontend/shell`
  (P-08): el shell ya declara este remote y ya entrega las props.
- Tocar `backend/`, `infra/` o `docs/contratos/README.md`: esta spec no necesita ningun campo,
  ruta ni codigo de error nuevo.
- Consumir `GET /api/reservas`, `/api/usuarios` o `/api/reportes` (§6.1).
- Crear el gateway Nginx ni quitar los mapeos `8082`–`8085`: seccion 5 de integracion.
- Agregar un `Dockerfile` al remote (§9), pruebas automatizadas, enrutador, `prop-types` o
  cualquier dependencia fuera de las doce de §3.4.
- Bloquear fechas pasadas en el selector, contar reservas activas en el cliente o recalcular
  `FINALIZADA`: son reglas de `ms-reservas` (§11).
