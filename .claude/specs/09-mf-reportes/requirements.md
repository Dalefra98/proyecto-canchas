# Spec 09 — mf-reportes (remote de Module Federation) · requirements.md

Estado: **C1 — APROBADO** el 24/08/2026 ("Apruebo requisitos de la spec 09").

Las diez preguntas abiertas (P-01 a P-10) fueron **respondidas por el responsable el
24/08/2026** y están incorporadas al cuerpo de este documento; las decisiones y sus motivos
quedan en §10, para la defensa del proyecto. Ninguna se rellenó con un valor inventado
(`CLAUDE.md` §0.1).

La compuerta C2 (`design.md`) sigue **pendiente**: no se escribe código de producción hasta que
el diseño esté aprobado por escrito (`CLAUDE.md` §6).

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.5, 3.4, 3.5, 4.1, 4.4,
5, 6, 7), `.claude/specs/05-ms-reportes/`, `.claude/specs/06-shell-module-federation/`,
`.claude/specs/07-mf-reservas/`, `.claude/specs/08-mf-administracion/`, `docker-compose.yml`,
`frontend/shell/`, `frontend/mf-administracion/` y `backend/ms-reportes/` ya entregados.

## 1. Objetivo

Implementar `frontend/mf-reportes`: el **tercer y último remote** de Module Federation del
proyecto. Es el módulo **Reportes** del PDF, disponible únicamente para el rol `ADMIN`.

El PDF lo define en tres lugares que hay que leer juntos:

| Fuente | Funcionalidad de Reportes |
|---|---|
| PDF §3.1 (roles y permisos) | "Visualizar reportes básicos de ocupación" — solo Administrador, módulo Reportes |
| PDF §3.2 (módulos y pantallas) | "Reportes básicos — Ocupación por cancha/deporte, reservas por período, cancelaciones" |
| PDF §3.3.5 | Módulo de **solo lectura**, solo administrador, con cuatro indicadores: reservas por cancha y por deporte en un rango; porcentaje de ocupación por cancha; cancelaciones por período; **listado de las canchas con mayor y menor demanda** |

La cuarta viñeta de §3.3.5 no tiene endpoint propio: la decisión **P-08 de la spec 05** la
resolvió delegándola al frontend, "ordenando `items` en el cliente, sin endpoint nuevo". Esta
spec es ese frontend, así que el indicador **entra aquí** (HU-05), sin llamar a ninguna ruta
nueva.

Este remote es **de solo lectura**: no crea, no edita, no cancela y no llama a ninguna ruta que
no sea `GET /api/reportes/...`.

## 2. Entregables de la spec

| Entregable | Ruta | Fuente |
|---|---|---|
| E-01 | `frontend/mf-reportes/package.json` | `CLAUDE.md` §3 |
| E-02 | `frontend/mf-reportes/webpack.config.js` con `ModuleFederationPlugin` | contrato |
| E-03 | `frontend/mf-reportes/.babelrc` (el mismo patrón ya usado en `mf-administracion`) | patrón de las specs 07 y 08 |
| E-04 | `frontend/mf-reportes/public/index.html` | patrón de las specs 07 y 08 |
| E-05 | `frontend/mf-reportes/src/index.js` — solo `import("./bootstrap")` | `CLAUDE.md` §3 |
| E-06 | `frontend/mf-reportes/src/bootstrap.jsx` | `CLAUDE.md` §3 |
| E-07 | `frontend/mf-reportes/src/ReportesApp.jsx` — el componente expuesto como `./ReportesApp` | contrato |
| E-08 | `frontend/mf-reportes/src/api/` — única capa que hace `fetch` | `CLAUDE.md` §4 |
| E-09 | `frontend/mf-reportes/src/components/` — las pantallas de los tres reportes | `CLAUDE.md` §4 |
| E-10 | `frontend/mf-reportes/src/estilos.css` — CSS plano | `CLAUDE.md` §3 |
| E-11 | Servicio `mf-reportes` en `docker-compose.yml` y su volumen anónimo | PDF §4.4 |

`docker-compose.yml` es el **único** archivo que se modifica fuera de `frontend/mf-reportes`
(mismo criterio que P-08 de la spec 07 y P-08 de la spec 08).

## 3. Restricciones técnicas heredadas

| Aspecto | Valor | Fuente |
|---|---|---|
| Ruta en el repo | `frontend/mf-reportes` | `CLAUDE.md` §4 |
| Nombre Module Federation | `mfReportes` | contrato, "Contrato Module Federation" |
| Módulo expuesto | `./ReportesApp` | contrato |
| Puerto | 3003 | contrato |
| React | 18 (`18.3.1`, la versión del shell, de `mf-reservas` y de `mf-administracion`) | `CLAUDE.md` §3 |
| Empaquetador | Webpack 5 con `ModuleFederationPlugin` | `CLAUDE.md` §3 |
| `shared` | `react` y `react-dom` con `singleton: true` | `CLAUDE.md` §3 |
| Arranque | `src/index.js` -> `import("./bootstrap")` | `CLAUDE.md` §3 |
| Llamadas HTTP | rutas relativas bajo `/api`, única capa `src/api/` | `CLAUDE.md` §3 y §4 |
| Autenticación | `token` recibido por prop, nunca leído del almacenamiento del navegador | contrato de props, D-12 de la spec 06 |
| Enrutador | **ninguno**: la pantalla activa es estado de React | P-05 de la spec 06 |
| Pantallas | tres —Ocupación, Reservas, Cancelaciones—, con menú interno del remote | P-01 |
| Rango de fechas | **uno solo, compartido**, fuera del menú interno | P-03 |
| Disparo de la consulta | manual: el administrador pulsa consultar, y se llama **solo** la ruta del reporte visible | P-02 |
| Estilos | CSS plano, sin librerías de UI externas ni de gráficos | `CLAUDE.md` §3 |
| Lenguaje | JavaScript, **sin** TypeScript | `CLAUDE.md` §3 |
| Idioma | identificadores en español sin tildes; textos en español | `CLAUDE.md` §7 |
| Instalación de dependencias | solo por Docker (`node:20-alpine`) | `CLAUDE.md` §1 |
| Watcher | `watchOptions: { poll: 1000, ignored: /node_modules/ }` | `docs/bitacora.md` |
| Ejecución suelta en `localhost:3003` | **no** hay aplicación usable: el remote solo publica `remoteEntry.js` | P-04 de la spec 07 |

Props que el remote **recibe** del shell, tal como quedaron congeladas el 23/08/2026:

```jsx
<ReportesApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

El shell ya declara `mfReportes@http://localhost:3003/remoteEntry.js` en su
`webpack.config.js`, ya crea el `lazy` de `mfReportes/ReportesApp` en `ContenedorRemoto.jsx` y
ya restringe la opción "Reportes" del menú al rol `ADMIN` (`MenuModulos.jsx`). Esta spec **no**
toca el shell.

## 4. Historias de usuario y criterios de aceptación

### HU-01 — Elegir el rango de fechas del reporte (PDF §3.3.5, contrato)

Como administrador, necesito indicar el período sobre el que quiero ver los indicadores.

- **CUANDO** se monte el módulo, **ENTONCES** se mostrarán dos campos de fecha, `desde` y
  `hasta`, **vacíos**, en formato `AAAA-MM-DD` y con los nombres exactos del contrato, situados
  **fuera del menú interno** y compartidos por los tres reportes (P-03).
- **CUANDO** se monte el módulo, **ENTONCES** **no** se llamará a ninguna ruta y **no** se
  propondrá ningún rango por defecto: el contrato no declara ninguno y consultar solo mostraría
  números que el administrador no pidió (P-02).
- **CUANDO** el administrador pulse el control de consultar, **ENTONCES** el remote llamará
  **solo a la ruta del reporte visible** con `?desde=<AAAA-MM-DD>&hasta=<AAAA-MM-DD>`: los dos
  parámetros son **obligatorios** y ambos extremos son **inclusivos** (contrato, "Notas de las
  rutas de reportes"). Nunca se llaman las tres rutas a la vez (P-02).
- **SI** alguno de los dos campos está vacío, **ENTONCES** el remote **no** llamará a la API y
  mostrará un aviso propio pidiendo el rango completo: sin los dos parámetros la respuesta sería
  `400 DATOS_INVALIDOS`.
- **SI** `desde` es posterior a `hasta`, **ENTONCES** el microservicio responde
  `400 DATOS_INVALIDOS` y el remote mostrará el `mensaje` recibido (HU-09). El remote **no**
  reordena las fechas ni corrige la entrada por su cuenta.
- **CUANDO** el administrador elija el rango, **ENTONCES** el remote **no** impondrá un rango
  máximo ni bloqueará fechas futuras: el contrato declara que no existe ninguna de las dos
  restricciones.
- **CUANDO** el remote envíe las fechas, **ENTONCES** las enviará tal como el contrato las
  declara, sin reformatear ni convertir a otra zona horaria; la respuesta devuelve `desde` y
  `hasta` con el mismo texto recibido y el remote los muestra tal cual.
- **CUANDO** un reporte se esté cargando, **ENTONCES** se mostrará un aviso de carga y el control
  que dispara la consulta quedará deshabilitado, para no encadenar llamadas repetidas.
- **CUANDO** el administrador cambie de reporte en el menú interno, **ENTONCES** el rango escrito
  se conserva pero **no** se dispara ninguna consulta: hay que volver a pulsar consultar (P-03).

### HU-02 — Reporte de ocupación por cancha (PDF §3.3.5, criterio de aceptación §7.4)

Como administrador, necesito ver qué tan ocupada estuvo cada cancha en el período.

- **CUANDO** el administrador pida el reporte de ocupación, **ENTONCES** el remote hará
  `GET /api/reportes/ocupacion?desde&hasta` y mostrará, por cada elemento de `items`, `canchaId`,
  `nombre`, `deporte`, `horasReservadas`, `horasDisponibles` y `porcentajeOcupacion`.
- **CUANDO** se muestre `porcentajeOcupacion`, **ENTONCES** se pintará el número tal como llega,
  con su decimal, y **sin recalcularlo**: el redondeo a un decimal con `HALF_UP` ya lo hizo
  `ms-reportes` (contrato).
- **CUANDO** se muestre `porcentajeOcupacion`, **ENTONCES** irá acompañado de una **barra
  proporcional dibujada con CSS plano**, sin librerías, cuyo ancho es el propio
  `porcentajeOcupacion` sobre 100 (P-09). La barra **acompaña** al número, no lo reemplaza.
- **CUANDO** esa barra se pinte, **ENTONCES** será el **único** reporte que la lleva: los de
  reservas y cancelaciones muestran conteos, no porcentajes (P-09).
- **CUANDO** se muestren `horasReservadas` y `horasDisponibles`, **ENTONCES** se pintarán tal como
  llegan: el remote **no** las divide, no las resta ni deriva ningún indicador nuevo.
- **CUANDO** el reporte se pinte, **ENTONCES** incluirá **todas** las canchas devueltas en
  `items`, incluidas las que tienen `activa = false` y las que no tuvieron actividad, estas
  últimas con sus contadores en `0` (contrato).
- **CUANDO** el reporte se pinte, **ENTONCES** quedará escrito en la pantalla que
  `horasDisponibles` es `(horaCierre − horaApertura)` por el número de días del rango y que **no**
  descuenta los bloqueos de mantenimiento (contrato, P-03 de la spec 05): sin esa nota el número
  se lee como "horas realmente ofertadas".
- **CUANDO** se muestre el `deporte`, **ENTONCES** se usará exactamente `PADEL`, `TENIS` o
  `BASQUET`, sin traducir ni abreviar (contrato).
- **CUANDO** `items` llegue vacío, **ENTONCES** se mostrará un aviso de reporte sin datos: la
  respuesta es `200` con arreglo vacío, nunca `404`.
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se aplica HU-09: el
  remote **no** pinta un reporte parcial.

### HU-03 — Reporte de reservas por período (PDF §3.3.5, criterio de aceptación §7.4)

Como administrador, necesito ver cuántas reservas hubo por cancha y por deporte en el período.

- **CUANDO** el administrador pida el reporte de reservas, **ENTONCES** el remote hará
  `GET /api/reportes/reservas?desde&hasta` y mostrará, por cada elemento de `items`, `canchaId`,
  `nombre`, `deporte` y `totalReservas`.
- **CUANDO** el reporte se pinte, **ENTONCES** quedará escrito en la pantalla que `totalReservas`
  cuenta las reservas `CONFIRMADA` y `FINALIZADA` y excluye las `CANCELADA` (contrato), para que
  el número no se lea como "todas las reservas registradas".
- **CUANDO** el reporte se pinte, **ENTONCES** incluirá todas las canchas de `items`, con `0` las
  que no tuvieron actividad.
- **CUANDO** `items` llegue vacío, **ENTONCES** se mostrará un aviso de reporte sin datos.
- **CUANDO** se lea el "por cancha y por deporte" del PDF §3.2 y §3.3.5, **ENTONCES** se
  satisface con la **columna `deporte` de cada fila**, que es exactamente lo que `ms-reportes`
  devuelve (P-04).
- **CUANDO** el reporte se pinte, **ENTONCES** **no** habrá ningún total agrupado por `deporte`:
  sumar por deporte en el cliente mostraría un número que la API no devolvió (P-04). La misma
  lectura vale para el reporte de ocupación (HU-02).

### HU-04 — Reporte de cancelaciones por período (PDF §3.3.5)

Como administrador, necesito ver cuántas cancelaciones hubo en el período.

- **CUANDO** el administrador pida el reporte de cancelaciones, **ENTONCES** el remote hará
  `GET /api/reportes/cancelaciones?desde&hasta` y mostrará, por cada elemento de `items`,
  `canchaId`, `nombre` y `totalCancelaciones`.
- **CUANDO** se pinte una fila de este reporte, **ENTONCES** **no** se mostrará `deporte`: las
  filas de `ReporteCancelacionesResponse` no lo traen y el remote no lo completa desde otra
  llamada.
- **CUANDO** el reporte se pinte, **ENTONCES** quedará escrito en la pantalla que el rango filtra
  por la **`fecha` de la reserva cancelada**, no por la fecha en que se canceló, porque
  `reservas_db` no almacena esa segunda fecha (contrato).
- **CUANDO** `items` llegue vacío, **ENTONCES** se mostrará un aviso de reporte sin datos.

### HU-05 — Canchas de mayor y menor demanda (PDF §3.3.5 cuarta viñeta; P-08 de la spec 05)

Como administrador, necesito identificar rápidamente la cancha más y la menos usada del período.

- **CUANDO** el remote muestre el indicador, **ENTONCES** lo calculará **ordenando en el cliente
  los `items` ya recibidos**, sin llamar a ninguna ruta nueva: P-08 de la spec 05 lo dejó escrito
  así y el contrato no declara ningún endpoint de ranking.
- **CUANDO** el administrador vea el reporte de **ocupación**, **ENTONCES** el indicador destacará
  la cancha de mayor y la de menor `porcentajeOcupacion` de ese reporte (P-05).
- **CUANDO** el administrador vea el reporte de **reservas**, **ENTONCES** el indicador destacará
  la cancha de mayor y la de menor `totalReservas` de ese reporte (P-05).
- **CUANDO** el administrador vea el reporte de **cancelaciones**, **ENTONCES** **no** se pintará
  ningún indicador de demanda: la cancha con más cancelaciones no es la de mayor demanda (P-05).
- **CUANDO** el indicador se calcule, **ENTONCES** usará solo esos dos campos tal como llegan, y
  **no** una fórmula propia del remote ni una mezcla de ambos.
- **CUANDO** haya empate en el valor máximo o en el mínimo, **ENTONCES** se mostrarán **todas** las
  canchas empatadas: mostrar solo una sería dar un dato incompleto (P-06).
- **CUANDO** `items` traiga una sola cancha, **ENTONCES** esa misma cancha es a la vez la de mayor
  y la de menor valor, y así se muestra: el remote no oculta el indicador por eso.
- **CUANDO** `items` llegue vacío, **ENTONCES** el indicador no se pintará y se mostrará el mismo
  aviso de reporte sin datos, sin inventar una cancha.
- **CUANDO** el indicador se pinte, **ENTONCES** **no** agregará ningún campo que la API no haya
  devuelto: solo reordena y destaca lo que ya está en `items`.
- **CUANDO** el orden original de `items` importe, **ENTONCES** se recuerda que `ms-reportes`
  conserva el orden del catálogo de `ms-canchas` y no ordena por la métrica (D-10 de la spec 05):
  ordenar es responsabilidad de este remote.

### HU-06 — Props recibidas del shell y sesión ajena

Como equipo, necesito que el remote use exactamente las cuatro props del contrato y no invente su
propia sesión.

- **CUANDO** el shell monte `./ReportesApp`, **ENTONCES** el remote leerá exactamente `usuario`
  (`usuarioId`, `nombre`, `rol`), `token`, `apiBaseUrl` y `onLogout`, y ninguna prop más.
- **CUANDO** el remote llame a la API, **ENTONCES** compondrá la URL con el `apiBaseUrl` recibido
  (valor literal `"/api"`), nunca con una URL absoluta ni con un nombre de contenedor.
- **CUANDO** el remote llame a la API, **ENTONCES** enviará `Authorization: Bearer <token>` con el
  `token` de la prop, que llega sin el prefijo `Bearer ` (HU-07 de la spec 06).
- **CUANDO** el remote necesite la sesión, **ENTONCES** **no** leerá `sessionStorage` ni
  `localStorage`: el dueño de la sesión es el shell (D-12 de la spec 06).
- **CUANDO** cualquier llamada responda `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** el
  remote invocará `onLogout()` y no pintará su propia pantalla de inicio de sesión (P-08 de la
  spec 06).
- **CUANDO** el `token` de la prop cambie, **ENTONCES** las llamadas siguientes usarán el valor
  nuevo: el remote no guarda una copia aparte.
- **CUANDO** el remote se pinte, **ENTONCES** **no** dibujará cabecera, menú de módulos ni botón
  de cierre de sesión: el layout es del shell (spec 06, E-07 y E-08).
- **CUANDO** el remote reciba `usuario.rol` distinto de `ADMIN`, **ENTONCES** pintará un aviso
  propio de módulo no disponible y **no llamará a ninguna ruta**: sin esa comprobación, las tres
  rutas responderían `403` y el usuario vería una pantalla llena de errores en lugar de un mensaje
  claro (P-07, mismo criterio que P-07 de la spec 08).
- **CUANDO** esa comprobación se implemente, **ENTONCES** quedará escrita como **comportamiento
  defensivo, no como control de acceso**: el control real es el token que `ms-reportes` valida, y
  el shell ya restringe el módulo al `ADMIN` (HU-05 de la spec 06).

### HU-07 — Integración como remote de Module Federation (PDF §4.1, rúbrica §6)

Como equipo, necesito que este microfrontend se pueda desarrollar y desplegar por separado y que
el shell lo cargue en tiempo de ejecución.

- **CUANDO** se configure `ModuleFederationPlugin`, **ENTONCES** declarará `name: "mfReportes"` y
  `exposes` con la clave exacta `"./ReportesApp"`: los dos nombres del contrato congelado.
- **CUANDO** se declare `shared`, **ENTONCES** `react` y `react-dom` irán con `singleton: true` y
  con las **mismas versiones** que el shell (`18.3.1`).
- **CUANDO** el remote se sirva, **ENTONCES** publicará `http://localhost:3003/remoteEntry.js`,
  que es la URL que el shell ya declara en su `webpack.config.js`.
- **CUANDO** alguien abra `http://localhost:3003` en el navegador, **ENTONCES** **no** verá una
  aplicación usable, y eso **es lo correcto, no un defecto** (P-04 de la spec 07).
- **CUANDO** el `ADMIN` entre al módulo Reportes con el remote levantado, **ENTONCES** se
  descargará `remoteEntry.js` y se montará la pantalla del remote **en lugar** del mensaje de
  módulo no disponible del borde de error del shell.
- **CUANDO** el remote se monte, **ENTONCES** la consola del navegador no mostrará errores de
  React duplicado ni de `hooks` inválidos.
- **CUANDO** el remote esté montado, **ENTONCES** `mf-reservas` y `mf-administracion` seguirán
  funcionando igual: los **tres** remotes conviven en el mismo shell y comparten la misma
  instancia de React.
- **CUANDO** se defina el arranque, **ENTONCES** `src/index.js` solo hará `import("./bootstrap")`
  (`CLAUDE.md` §3), y el módulo expuesto será un componente de React que recibe las props, no un
  `ReactDOM.render`.

### HU-08 — El remote corre en el entorno local (PDF §4.4)

Como equipo, necesito levantar el remote junto al resto del sistema con Docker Compose.

- **CUANDO** se instalen las dependencias, **ENTONCES** será con
  `docker run --rm -v "${PWD}:/app" -w /app node:20-alpine npm install`, nunca con `npm` en el
  host (`CLAUDE.md` §1).
- **CUANDO** se levante el entorno, **ENTONCES** el remote correrá como servicio `mf-reportes` de
  `docker-compose.yml`, con el **mismo patrón de los servicios `shell`, `mf-reservas` y
  `mf-administracion`**: imagen `node:20-alpine`,
  `command: sh -c "npm install && npx webpack serve --mode development"`, volumen del código,
  volumen anónimo `mf_reportes_node_modules` para `node_modules` y `ports: "3003:3003"`.
- **CUANDO** se configure el `devServer`, **ENTONCES** llevará `host: "0.0.0.0"`,
  `allowedHosts: "all"` y `headers: { "Access-Control-Allow-Origin": "*" }`, o el navegador no
  podrá descargar el `remoteEntry.js` desde el origen del shell.
- **CUANDO** se configure el watcher, **ENTONCES** llevará
  `watchOptions: { poll: 1000, ignored: /node_modules/ }` (bitácora, T3 de la spec 06).
- **CUANDO** se declare el `proxy` del `devServer`, **ENTONCES** apuntará a **nombres de
  contenedor** (`http://ms-reportes:8080`), al contrario de la URL de navegador del
  `remoteEntry.js` (P-02 de la spec 06).
- **CUANDO** el shell monte este remote, **ENTONCES** sus rutas relativas `/api/...` las proxya el
  `devServer` del **shell**, porque el código corre en el origen `http://localhost:3000`.
- **CUANDO** se agregue el servicio al `docker-compose.yml`, **ENTONCES** ese será el **único**
  archivo modificado fuera de `frontend/mf-reportes`.
- **CUANDO** se declare el `depends_on` del servicio, **ENTONCES** será **solo `ms-reportes`** con
  `condition: service_started`: es el único microservicio que este remote consume. **No** se
  listan `ms-canchas` ni `ms-reservas` —que `ms-reportes` dependa de ellos por HTTP es asunto
  suyo— y `service_started` porque ninguno declara `healthcheck` y el `devServer` arranca igual
  (P-08).
- **CUANDO** el shell se levante, **ENTONCES** **no** declarará `depends_on` de este remote: el
  `remoteEntry.js` lo descarga el navegador al entrar al módulo (P-08 de la spec 07).
- **CUANDO** se verifique una tarea, **ENTONCES** la **primera** comprobación será
  `curl.exe http://localhost:3003/remoteEntry.js`: si no responde `200`, el problema está en el
  remote y no en la integración.
- **CUANDO** se verifique una tarea, **ENTONCES** también se comprobará que
  `curl.exe http://localhost:3001/remoteEntry.js` y
  `curl.exe http://localhost:3002/remoteEntry.js` siguen respondiendo `200` y que los módulos
  Reservas y Administración siguen montando: el tercer remote no puede romper a los dos anteriores.
- **CUANDO** la tarea tenga interacción, **ENTONCES** hará falta además el recorrido por navegador
  con un usuario `ADMIN`: iniciar sesión en `http://localhost:3000`, entrar a Reportes, elegir un
  rango y ejercitar la pantalla de la tarea. Un `compiled successfully` no prueba que la
  aplicación funcione (bitácora, T5 de la spec 06).

### HU-09 — Errores uniformes y sin datos inventados

Como equipo, necesito que el remote muestre siempre el error que devolvió el microservicio.

- **CUANDO** una llamada falle con un cuerpo de error del contrato, **ENTONCES** el remote
  mostrará el `mensaje` recibido, sin reescribirlo ni traducirlo.
- **CUANDO** el remote decida qué hacer ante un error, **ENTONCES** lo hará por el `codigo`, no
  por el texto del `mensaje`.
- **CUANDO** la respuesta sea `500` con `codigo = ERROR_INTERNO`, **ENTONCES** el remote mostrará
  el error y ofrecerá reintentar, y **no** pintará un reporte parcial: `ms-reportes` depende de
  `ms-canchas` y `ms-reservas` por HTTP y el contrato declara explícitamente que nunca devuelve un
  reporte a medias.
- **CUANDO** el remote muestre cualquier dato de un reporte, **ENTONCES** usará los campos exactos
  del contrato y **no** calculará ni completará ninguno que la API no haya devuelto; la única
  operación local permitida es el reordenamiento de HU-05, que no crea campos.
- **CUANDO** un error se muestre, **ENTONCES** la pantalla no quedará en blanco y la navegación
  interna seguirá funcionando.
- **SI** la respuesta no trae `codigo` ni `mensaje` (por ejemplo, la petición no llegó al
  microservicio), **ENTONCES** el remote mostrará un aviso propio de fallo de comunicación y
  permitirá reintentar, sin pintar un stacktrace ni el objeto de error crudo.

### HU-10 — Navegación interna del módulo

Como administrador, necesito moverme entre los tres reportes sin salir del módulo.

- **CUANDO** el remote se monte, **ENTONCES** pintará un **menú interno propio** con exactamente
  tres opciones —**Ocupación**, **Reservas** y **Cancelaciones**—, una por reporte, con el mismo
  patrón del menú interno de `mf-administracion` (P-01).
- **CUANDO** el remote se monte, **ENTONCES** el reporte inicial será **Ocupación**: es el que el
  PDF §3.1 nombra como funcionalidad del módulo ("visualizar reportes básicos de ocupación") y el
  primero que el criterio de aceptación §7.4 exige.
- **CUANDO** el remote pinte su navegación interna, **ENTONCES** **no** duplicará el menú de
  módulos del shell ni ofrecerá Inicio, Reservas, Administración ni cierre de sesión: eso es del
  shell (spec 06, E-07 y E-08).
- **CUANDO** el administrador cambie de reporte, **ENTONCES** el reporte activo será estado de
  React, **sin** enrutador y sin cambiar la URL del navegador (P-05 de la spec 06).
- **CUANDO** el administrador cambie de reporte, **ENTONCES** el rango de HU-01 se conserva —es
  uno solo para los tres (P-03)— y el reporte que se abandona pierde sus datos: al volver hay que
  pulsar consultar otra vez. Así ningún reporte muestra números de un rango que ya no es el que se
  ve en pantalla.
- **CUANDO** el administrador cambie el rango sin pulsar consultar, **ENTONCES** el reporte que
  está en pantalla sigue mostrando los datos del rango con el que se consultó, y la pantalla lo
  advierte: los `desde` y `hasta` que se muestran junto a la tabla son los que **devolvió la
  respuesta**, no los que están escritos en los campos.
- **CUANDO** el módulo se desmonte (el `ADMIN` sale a otro módulo del shell), **ENTONCES** no
  queda ningún estado suyo vivo: al volver, el módulo arranca limpio.

## 5. Reglas de negocio cubiertas

| ID | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora | **No aplica directamente** — el remote no crea reservas. Es la regla que hace que una reserva equivalga a una hora en `horasReservadas` (HU-02), pero el cálculo lo hace `ms-reportes` |
| RN-02 | No se puede reservar un bloque ya ocupado en la misma cancha | **No aplica** — no hay creación de reservas aquí |
| RN-03 | El usuario cancela solo sus reservas; el admin cancela cualquiera | **No aplica** — este remote no cancela nada; las cancelaciones solo se **cuentan** (HU-04). La cancelación del admin vive en `mf-administracion` (spec 08) |
| RN-04 | Solo se cancela una reserva cuya fecha y hora de inicio no hayan ocurrido | **No aplica** — no hay cancelación aquí |
| RN-05 | Cancelar libera el bloque para otro usuario | **No aplica** — no hay cancelación aquí |
| RN-06 | Límite configurable de reservas activas simultáneas por usuario | **No aplica** — el remote no crea reservas y el contrato no expone el límite |
| RN-07 | Solo el admin crea, edita o inactiva canchas y define su horario de atención | **Consumida, no aplicada** — el `horaApertura` / `horaCierre` que el admin definió es lo que `ms-reportes` usa para `horasDisponibles` (HU-02). El remote no edita canchas: eso es `mf-administracion` (spec 08) |
| RN-08 | Estados de reserva: `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | **Cubierta en su parte de trazabilidad** — es la regla que separa los tres reportes: `CONFIRMADA` y `FINALIZADA` alimentan ocupación y reservas; `CANCELADA` alimenta cancelaciones (HU-02, HU-03, HU-04). El PDF §3.4 justifica RN-08 precisamente "para efectos de trazabilidad y reportes" |

Ninguna regla de negocio se **valida** en este remote: es un módulo de solo lectura y todo llega
resuelto de `ms-reportes` (PDF §3.3.5).

## 6. Contrato REST consumido

### 6.1 Rutas

Las tres rutas son las de `docs/contratos/README.md`, sin agregar ninguna:

| Verbo | Ruta | Rol | Respuestas | Historia |
|---|---|---|---|---|
| GET | `/api/reportes/ocupacion?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | HU-02, HU-05 |
| GET | `/api/reportes/reservas?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | HU-03, HU-05 |
| GET | `/api/reportes/cancelaciones?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | HU-04 |

Este remote **no** consume `/api/usuarios`, `/api/canchas` ni `/api/reservas`.

### 6.2 Campos

Nombres exactos de `docs/contratos/README.md`. No se renombra, no se abrevia, no se traduce:

| Campo | Tipo / valores | Dónde aparece |
|---|---|---|
| `desde` | string `AAAA-MM-DD` | parámetro de consulta y campo de la respuesta |
| `hasta` | string `AAAA-MM-DD` | parámetro de consulta y campo de la respuesta |
| `items` | arreglo de objetos | los tres reportes |
| `canchaId` | number | los tres reportes |
| `nombre` | string | los tres reportes |
| `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | ocupación y reservas |
| `horasReservadas` | number | ocupación |
| `horasDisponibles` | number | ocupación |
| `porcentajeOcupacion` | number 0-100, un decimal | ocupación |
| `totalReservas` | number | reservas |
| `totalCancelaciones` | number | cancelaciones |
| `codigo` | string | cuerpo de error |
| `mensaje` | string | cuerpo de error |

### 6.3 Payloads consumidos

`ReporteOcupacionResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horasReservadas": 12, "horasDisponibles": 45, "porcentajeOcupacion": 26.7 }
  ]
}
```

`ReporteReservasResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "totalReservas": 12 }
  ]
}
```

`ReporteCancelacionesResponse`:

```json
{
  "desde": "2026-08-01",
  "hasta": "2026-08-31",
  "items": [
    { "canchaId": 1, "nombre": "Padel 1", "totalCancelaciones": 3 }
  ]
}
```

Cuerpo de error, igual para los tres:

```json
{ "codigo": "DATOS_INVALIDOS", "mensaje": "..." }
```

### 6.4 Códigos de error que el remote interpreta

| HTTP | `codigo` | Qué hace el remote |
|---|---|---|
| 400 | `DATOS_INVALIDOS` | Muestra el `mensaje` recibido junto a los campos del rango (HU-01) |
| 401 | `NO_AUTENTICADO` | Invoca `onLogout()` (HU-06) |
| 403 | `SIN_PERMISO` | Muestra el `mensaje` recibido; no reintenta |
| 500 | `ERROR_INTERNO` | Muestra el `mensaje` y ofrece reintentar; nunca pinta un reporte parcial (HU-09) |

### 6.5 Contrato Module Federation

| Microfrontend | Nombre | Módulo expuesto | Puerto |
|---|---|---|---|
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

## 7. Dependencias de esta spec

| Depende de | Estado | Para qué |
|---|---|---|
| `ms-reportes` (spec 05) | cerrada y levantada | las tres rutas de `/api/reportes` |
| `ms-usuarios` (spec 02) | cerrada y levantada | emite el `token` con el que el shell autentica; el remote **no** lo llama |
| `frontend/shell` (spec 06) | cerrada y levantada | declara `mfReportes@http://localhost:3003/remoteEntry.js`, crea el `lazy` de `mfReportes/ReportesApp`, restringe el módulo al `ADMIN` y entrega las cuatro props |
| `frontend/mf-reservas` (spec 07) | cerrada y levantada | patrón de `package.json`, `webpack.config.js`, capa `src/api/` y servicio de Compose |
| `frontend/mf-administracion` (spec 08) | cerrada y levantada | patrón más reciente de remote, incluidos los `headers` de CORS y el `proxy` del `devServer` |

## 8. Criterios de aceptación del PDF que esta spec cierra

| Criterio del PDF §7 | Aporte de esta spec |
|---|---|
| 4. "El módulo de reportes básicos muestra al menos ocupación por cancha y número de reservas por período" | Se cierra por completo: HU-02 y HU-03 |
| 5. "Al menos un shell y dos microfrontends remotos integrados mediante Module Federation" | Ya cerrado por la spec 08; esta spec lo refuerza con el **tercer** remote (HU-07) |

Con esta spec queda cubierto también el criterio de la rúbrica del PDF §6 "Módulo de reportes
básicos" en su parte de frontend: `ms-reportes` ya cubría la de backend.

## 9. Fuera de alcance de esta spec

- **Cualquier reporte, filtro, agregación o campo que no esté en los tres payloads congelados**:
  reservas por usuario, ranking de usuarios, ingresos, series temporales, comparativas entre
  períodos o totales generales fuera de `items`.
- **Recalcular indicadores en el navegador**: `porcentajeOcupacion` no se vuelve a dividir,
  `horasDisponibles` no se recalcula y los bloqueos de mantenimiento no se restan. Todo llega
  resuelto de `ms-reportes` (P-03 y P-06 de la spec 05). La única operación local permitida es el
  reordenamiento de HU-05, que no crea datos.
- **Consumir `/api/canchas`, `/api/reservas` o `/api/usuarios`** para enriquecer un reporte: si un
  dato no está en `items`, no se muestra.
- **Exportar a PDF, Excel, CSV o imagen, e imprimir**: el PDF §3.5 prohíbe expresamente la
  "exportación a formatos analíticos" y los "reportes avanzados con inteligencia de negocio (BI)".
- **Librerías de gráficos** (Chart.js, Recharts, D3 o cualquier otra): `CLAUDE.md` §3 prohíbe las
  librerías de UI externas. La única representación visual aprobada es la barra de
  `porcentajeOcupacion` en CSS plano (P-09); no hay tortas, líneas ni ejes.
- **Totales agrupados por `deporte`**: el contrato no declara ninguno y P-04 los descartó. El
  "por cancha y por deporte" del PDF se satisface con la columna `deporte` de cada fila.
- **Indicador de mayor y menor demanda en el reporte de cancelaciones**: P-05 lo descartó, porque
  la cancha con más cancelaciones no es la de mayor demanda.
- **Las pantallas de los otros dos remotes**: disponibilidad, nueva reserva y mis reservas
  (`mf-reservas`); catálogo de canchas, bloqueos, listado global de reservas y usuarios
  (`mf-administracion`). No se duplica ninguna aquí.
- **Modificar el shell**: ya declara este remote, ya lo carga con `lazy`, ya restringe el módulo
  al `ADMIN` y ya entrega las props. Si algo obligara a tocarlo, se detiene la tarea y se avisa
  (`CLAUDE.md` §0.4).
- **Modificar `backend/`, `infra/postgres/` o `docs/contratos/README.md`**: esta spec no necesita
  ningún campo, ruta ni código de error nuevo.
- **Ejecutar el remote como aplicación independiente en el navegador**: no hay `bootstrap` con
  props de desarrollo ni token de prueba (P-04 de la spec 07).
- **El gateway Nginx** y la eliminación de los mapeos `8082`–`8085`: quedan para la sección de
  integración, con la decisión ya escrita en §8 de la spec 06.
- **Cargar reservas de ejemplo en el seed** para que los reportes muestren números: `05-seed.sql`
  no se toca; la spec 05 ya lo dejó fuera de alcance y los datos de la demo se generan usando
  `mf-reservas`.
- **Enrutador, gestor de estado global, librería de UI, TypeScript, tema oscuro, i18n** y
  cualquier dependencia npm que no exija React 18 + Webpack 5 + Module Federation.
- **Pruebas automatizadas de frontend**: ninguna spec anterior las incluyó y el PDF §5 no las pide
  como entregable.
- **Reservas recurrentes, pagos, notificaciones, torneos, app móvil nativa y reportes BI**:
  prohibidos por el PDF §3.5 y `CLAUDE.md` §2.
- **Diseño responsive avanzado, animaciones y accesibilidad** más allá de HTML semántico: la
  rúbrica del PDF §6 no las puntúa.

---


## 10. Decisiones tomadas (P-01 a P-10, respondidas el 24/08/2026)

**P-01 — Menú interno con tres pantallas, una por reporte. Salida (b).** Mismo patrón de
`mf-administracion`. Motivo: los tres reportes tienen columnas distintas y apilarlos en una sola
pantalla obliga a desplazarse mucho; las pestañas son lo mismo con otro nombre, y el proyecto ya
tiene un patrón de menú interno de remote.

**P-02 — Al montar no se llama nada, y al consultar se llama solo la ruta visible. Salida (a).**
El administrador escribe el rango y pulsa consultar. Motivo: consultar con un rango por defecto
inventado mostraría números que el usuario no pidió —y el contrato no declara ningún rango por
defecto—, y llamar las tres rutas a la vez triplicaría el trabajo de `ms-reportes`, que en cada
llamada consulta dos microservicios por HTTP.

**P-03 — Un rango compartido por los tres. Salida (a).** Se elige una vez arriba, fuera del menú
interno, y vale para el reporte que se esté viendo. Cambiar de reporte conserva el rango pero
**no** dispara la consulta: hay que pulsar consultar. Motivo: el `ADMIN` compara los tres
indicadores del mismo período, y reescribir las fechas tres veces es fricción pura.

**P-04 — Solo la columna `deporte` de cada fila, sin agrupamiento. Salida (a).** El "por cancha y
por deporte" del PDF §3.2 y §3.3.5 se satisface con el campo `deporte` en cada fila, que es
exactamente lo que `ms-reportes` devuelve. Motivo: la salida (b) mostraría un número que la API no
devolvió, y la (c) agregaría complejidad visual sin aportar un dato nuevo.

**P-05 — Cada reporte destaca su propia cancha de mayor y menor valor. Salida (c).** Ocupación por
`porcentajeOcupacion`, reservas por `totalReservas`. Cancelaciones **no** lleva indicador: la
cancha con más cancelaciones no es la de mayor demanda. Motivo: son dos métricas distintas y
elegir una sola escondería la otra.

**P-06 — Se muestran todas las canchas empatadas. Salida (a).** Motivo: ocultar un empate sería
mostrar un dato incompleto.

**P-07 — Un `rol` distinto de `ADMIN` ve un aviso propio, sin llamadas.** Mismo comportamiento
defensivo que P-07 de la spec 08, y escrito como **defensivo, no como control de acceso**: el
control real sigue siendo el token que valida `ms-reportes`.

**P-08 — `depends_on`: solo `ms-reportes`, con `condition: service_started`.** No se listan
`ms-canchas` ni `ms-reservas`: este remote no los llama, y que `ms-reportes` dependa de ellos es
asunto suyo.

**P-09 — Tabla más barra proporcional en CSS plano para `porcentajeOcupacion`. Salida (b).** Solo
en el reporte de ocupación, sin librerías, y la barra **acompaña** al número, no lo reemplaza.
Motivo: es el único indicador porcentual del proyecto y una barra lo hace legible de un vistazo en
la demo, sin agregar dependencias.

**P-10 — Una tarea por reporte. Salida (a).** Más las de andamiaje, capa `api` y Compose: unas
seis tareas en total. Afecta solo a `tasks.md`, que se escribe después de aprobar el diseño.

## 11. Supuestos

**Sin supuestos.** Los diez datos que faltaban se preguntaron como P-01 a P-10 y están
respondidos por el responsable el 24/08/2026 en §10; ninguno se rellenó con un valor inventado.

Todo lo demás salió de una fuente verificable: el nombre del remote, su módulo expuesto y su
puerto, del "Contrato Module Federation"; las cuatro props del contrato congelado el 23/08/2026;
las tres rutas, sus parámetros, sus payloads y sus códigos de error, de `docs/contratos/README.md`
y de los DTO ya implementados en la spec 05; las "Notas de las rutas de reportes" (rango
obligatorio e inclusivo, sin rango máximo ni restricción de fechas futuras, `porcentajeOcupacion`
con un decimal y `HALF_UP`, `horasDisponibles` sin restar bloqueos, estados que cuentan en cada
reporte, imputación de la cancelación a la `fecha` de la reserva y presencia de todas las canchas
en `items`); la delegación de "mayor y menor demanda" al frontend (P-08 de la spec 05); el orden
de `items` igual al del catálogo (D-10 de la spec 05); y el patrón de remote, `devServer` y
servicio de Compose ya entregado en `frontend/mf-administracion`.
