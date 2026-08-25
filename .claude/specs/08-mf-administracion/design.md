# Spec 08 — mf-administracion (remote de Module Federation) · design.md

Estado: **C2 — APROBADO** el 24/08/2026 ("Apruebo diseño de la spec 08").
Falta `tasks.md`: el código de producción se escribe tarea por tarea, una a la vez, con su
comando de verificación (`CLAUDE.md` §6).

Base: `requirements.md` de esta spec, **C1 aprobado el 24/08/2026**, con las decisiones P-01 a
P-10 ya incorporadas.

Fuentes verificadas para este diseño: `CLAUDE.md` (§1, §3, §4, §5, §7),
`docs/contratos/README.md` (campos congelados, "Rutas REST congeladas", "Formato de error",
"Contrato Module Federation"), `.claude/specs/02-ms-usuarios/` (`UsuarioResponse`,
`CambioEstadoRequest`), `.claude/specs/03-ms-canchas/` (D-03 a D-16, `CanchaRequest`,
`BloqueoRequest`, `CambioEstadoCanchaRequest`), `.claude/specs/04-ms-reservas/` (D-02, D-09,
C-02), `.claude/specs/06-shell-module-federation/design.md`,
`.claude/specs/07-mf-reservas/design.md` (D-01 a D-18, cuyo patrón se reutiliza),
`docker-compose.yml`, `frontend/shell/`, `frontend/mf-reservas/` y `docs/bitacora.md`.

## 0. Nota sobre las secciones pedidas

El comando de diseño pide cinco tablas pensadas para un microservicio. Este entregable es un
**microfrontend remote**: no tiene base de datos, no expone endpoints HTTP y no traduce
excepciones a códigos HTTP. Las secciones sin equivalente literal se sustituyen por su análogo
exacto, declarado aquí para que no parezca que se omitieron. Es la misma sustitución que los
`design.md` de las specs 06 y 07 ya aplicaron y que el responsable aprobó.

| Pedido | Qué se entrega en su lugar | Sección |
|---|---|---|
| Modelo de datos (columnas y restricciones) | **Modelo de estado del remote**: campo, tipo, valor inicial, restricciones y origen. No hay ni una tabla de base de datos | §4 |
| DTOs con validaciones | **Payloads de request y response** con las validaciones de cliente campo por campo, más las props recibidas | §5 |
| Tabla de endpoints con rol requerido | **Tabla de rutas consumidas** con su rol requerido, más las vistas internas. El remote no **expone** ninguna ruta HTTP; lo único que expone es el módulo `./AdminApp` | §6 |
| Tabla de excepciones a códigos HTTP | **Tabla de código HTTP recibido a comportamiento del remote**: la dirección es la inversa a la de un microservicio | §7 |
| Tabla de decisiones con alternativa descartada | Igual que en las siete specs anteriores | §12 |

"Ninguna consulta puede acceder a tablas de otro microservicio" se cumple de forma absoluta: el
remote **no accede a ninguna base de datos**. Su único acceso a datos son once rutas HTTP de
`ms-usuarios`, `ms-canchas` y `ms-reservas` (§6.1). No hay SQL en esta spec (§11).

## 1. Verificación campo por campo contra `docs/contratos/README.md`

Todos los campos que este diseño usa existen en el contrato con **el mismo nombre**. No se
renombra, no se abrevia, no se traduce y no se agrega ninguno.

| Campo usado | Existe en el contrato | Tipo / valores del contrato | Dónde lo usa el remote |
|---|---|---|---|
| `canchaId` | sí | number | clave del listado, rutas de edición, estado y bloqueos, enlace de la reserva con su cancha |
| `nombre` | sí | string (cancha, dueño `ms-canchas`) | columna del listado, campo del formulario, nombre de la cancha en el listado global |
| `deporte` | sí | `PADEL` \| `TENIS` \| `BASQUET` | columna del listado y selector del formulario |
| `horaApertura` | sí | string `HH:mm` | columna del listado y campo del formulario |
| `horaCierre` | sí | string `HH:mm` | columna del listado y campo del formulario |
| `activa` | sí | boolean | columna, condición de la acción y cuerpo del `PATCH` de estado de cancha |
| `bloqueoId` | sí | number | clave del listado de bloqueos y ruta del `DELETE` |
| `motivo` | sí | string | columna del listado de bloqueos y campo del formulario |
| `fecha` | sí | string `AAAA-MM-DD` | campo y columna del bloqueo, columna de la reserva |
| `horaInicio` | sí | string `HH:mm` | campo del bloqueo y columna de la reserva |
| `horaFin` | sí | string `HH:mm` | campo del bloqueo y columna de la reserva |
| `id` | sí | number | clave del listado global y ruta de `PATCH /api/reservas/{id}/cancelacion` |
| `estado` | sí | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | columna, filtro en el navegador y condición de cancelable |
| `usuarioId` | sí | number | clave del listado de usuarios, ruta del `PATCH` de estado, enlace de la reserva con su usuario y comparación con la prop `usuario` |
| `nombre` | sí | string (usuario, dueño `ms-usuarios`) | columna del listado de usuarios y nombre del usuario en el listado global |
| `email` | sí | string | columna del listado de usuarios |
| `rol` | sí | `ADMIN` \| `USUARIO` | columna del listado de usuarios y prop `usuario` (guardia de §4.5) |
| `activo` | sí | boolean | columna, condición de la acción y cuerpo del `PATCH` de estado de usuario |
| `token` | sí | string | prop; encabezado `Authorization: Bearer <token>` |
| `usuario` | sí | objeto `UsuarioResponse` | prop del contrato de props |
| `codigo` | sí | ver "Formato de error" | selecciona la reacción del remote (§7) |
| `mensaje` | sí | string | texto que se muestra tal cual |
| `apiBaseUrl` | sí | `"/api"` | prefijo de toda ruta llamada |
| `onLogout` | sí | función | se invoca ante un `401` |

Contrato de props verificado tal como quedó el 23/08/2026 en `docs/contratos/README.md` y
`CLAUDE.md` §5:

```jsx
<AdminApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

Nombres de Module Federation verificados: `mfAdministracion`, módulo `./AdminApp`, puerto 3002,
exactamente como el shell ya los declara en su `webpack.config.js`.

Rutas verificadas contra "Rutas REST congeladas": `GET /api/canchas`, `POST /api/canchas`,
`PUT /api/canchas/{canchaId}`, `PATCH /api/canchas/{canchaId}/estado`,
`GET /api/canchas/{canchaId}/bloqueos`, `POST /api/canchas/{canchaId}/bloqueos`,
`DELETE /api/canchas/{canchaId}/bloqueos/{id}`, `GET /api/reservas`,
`PATCH /api/reservas/{id}/cancelacion`, `GET /api/usuarios`,
`PATCH /api/usuarios/{usuarioId}/estado`.

**Ningún nombre discrepa.** No hay nada que detener por este motivo.

Cuatro aclaraciones de nombres que **no** son discrepancias:

- `nombre` aparece dos veces en el contrato, como nombre de cancha (`ms-canchas`) y como nombre
  de usuario (`ms-usuarios`). Este remote usa los dos en la misma pantalla de Reservas (§5.9) y
  no los mezcla: cada uno vive en su propio payload y se resuelve por su propio identificador.
- El campo de la reserva se llama `id`, no `reservaId`. Se usa tal cual en la ruta de cancelación.
- El identificador del bloqueo se llama `bloqueoId` en el payload, pero la ruta congelada del
  borrado es `DELETE /api/canchas/{canchaId}/bloqueos/{id}`: el `{id}` del path se rellena con el
  `bloqueoId` del objeto. No es un campo nuevo, es el mismo valor en la ruta.
- El estado de la cancha es `activa` y el del usuario es `activo`. Son dos campos distintos de dos
  servicios distintos y **no** se unifican: cada `PATCH` envía el suyo (§5.4 y §5.8).

`password` existe en el contrato y este remote **no lo usa en ninguna pantalla**: no aparece en
`UsuarioResponse` y el módulo no registra ni edita credenciales.

## 2. Estructura de archivos

Sigue `CLAUDE.md` §4 para microfrontends. Todo lo que se crea vive en
`frontend/mf-administracion`, con la única excepción del servicio en `docker-compose.yml` (E-11).

```
frontend/mf-administracion/
  package.json
  webpack.config.js
  .babelrc
  public/index.html
  src/index.js                                # solo import("./bootstrap")
  src/bootstrap.jsx                           # aviso estatico para localhost:3002 (D-02)
  src/AdminApp.jsx                            # modulo expuesto; vista activa y envoltorio del 401
  src/estilos.css                             # CSS plano, unico archivo de estilos
  src/api/clienteApi.js                       # unica pieza que llama fetch
  src/api/canchasApi.js                       # listar, crear, editar, cambiar estado y los tres de bloqueos
  src/api/reservasApi.js                      # listarReservas, cancelarReserva
  src/api/usuariosApi.js                      # listarUsuarios, cambiarEstadoUsuario
  src/components/NavegacionInterna.jsx        # Canchas / Reservas / Usuarios (HU-14)
  src/components/PantallaCanchas.jsx          # listado, alta, edicion y estado (HU-01 a HU-04)
  src/components/FormularioCancha.jsx         # alta y edicion, mismo componente (HU-02, HU-03)
  src/components/PanelBloqueos.jsx            # bloqueos de la cancha seleccionada (HU-05 a HU-07)
  src/components/FormularioBloqueo.jsx        # alta de bloqueo (HU-06)
  src/components/PantallaReservas.jsx         # listado global, filtro y cancelacion (HU-08)
  src/components/PantallaUsuarios.jsx         # listado y cambio de estado (HU-09)
  src/components/DialogoConfirmacion.jsx      # paso de confirmacion reutilizable (D-12)
  src/components/MensajeError.jsx             # pinta { codigo, mensaje }
```

`src/api/` es la **única** capa que llama `fetch` (`CLAUDE.md` §4). Ningún componente lo hace por
su cuenta.

No hay carpeta `mapper/` ni módulo de utilidades: el remote no transforma datos: los muestra tal
como llegan. `CLAUDE.md` §3 prohíbe las clases `Util` genéricas y aquí no se crea ninguna.

No hay carpeta `sesion/`: el remote no tiene sesión propia (HU-10 del C1).

Prefijo de clases CSS: **`mfa-`** (`mf-reservas` usa `mfr-`). Los estilos de los dos remotes y los
del shell conviven en el mismo documento cuando el módulo se monta, y sin prefijo una regla de un
remote repintaría al otro (D-16).

## 3. Configuración de Webpack y Module Federation

### 3.1 `ModuleFederationPlugin` del remote

| Clave | Valor | Motivo |
|---|---|---|
| `name` | `"mfAdministracion"` | contrato congelado; es el nombre que el shell ya declara |
| `filename` | `"remoteEntry.js"` | es la URL que el shell pide: `http://localhost:3002/remoteEntry.js` |
| `exposes` | `{ "./AdminApp": "./src/AdminApp" }` | clave exacta del contrato |
| `shared.react` | `{ singleton: true, requiredVersion: paquete.dependencies.react }` | `CLAUDE.md` §3; sin `singleton` habría dos React y los `hooks` fallarían |
| `shared["react-dom"]` | `{ singleton: true, requiredVersion: paquete.dependencies["react-dom"] }` | idem |
| `output.publicPath` | `"auto"` | sin ella los chunk del remote se piden al origen del shell (`localhost:3000`) y la carga falla |
| `output.uniqueName` | `"mfAdministracion"` | evita colisión de los registros de Webpack entre shell y los dos remotes |

La versión de React se lee de `package.json` en vez de escribirse a mano, igual que en el shell y
en `mf-reservas`: así `requiredVersion` no puede quedar desalineada de la dependencia real.

### 3.2 `devServer` y `watchOptions`

| Clave | Valor | Motivo |
|---|---|---|
| `port` | `3002` | contrato |
| `host` | `"0.0.0.0"` | dentro del contenedor, escuchar solo en `localhost` deja al navegador del host sin acceso |
| `allowedHosts` | `"all"` | idem |
| `headers` | `{ "Access-Control-Allow-Origin": "*" }` | el `remoteEntry.js` lo pide una página servida en `localhost:3000`: es otro origen |
| `hot` | `true` | patrón del shell y de `mf-reservas` |
| `client.webSocketURL` | `"ws://localhost:3002/ws"` | el socket lo abre el navegador: su URL es la del host |
| `client.overlay.runtimeErrors` | `false` | el `BordeError` del shell ya muestra el fallo del remote; el overlay lo repetiría tapando la pantalla |
| `watchOptions` | `{ poll: 1000, ignored: /node_modules/ }` | el bind mount de Windows no entrega inotify (bitácora, T3 de la spec 06) |

### 3.3 Proxy de `/api` y quién lo usa de verdad

```
proxy: [
  { context: ["/api/usuarios"], target: "http://ms-usuarios:8080" },
  { context: ["/api/canchas"],  target: "http://ms-canchas:8080" },
  { context: ["/api/reservas"], target: "http://ms-reservas:8080" },
  { context: ["/api/reportes"], target: "http://ms-reportes:8080" }
]
```

Destinos: **nombres de contenedor**, porque el proxy lo ejecuta `webpack serve` dentro de la red
de Docker. La URL del `remoteEntry.js`, en cambio, es de **navegador**. Es la distinción que la
spec 06 ya resolvió en su P-02 y que aquí solo se repite.

Cuando el remote está montado dentro del shell, su código corre en el origen del shell y sus rutas
`/api/...` las proxya el `devServer` del **shell**: este `proxy` solo atiende si alguien abre
`http://localhost:3002` suelto. Se declara igualmente por simetría con `mf-reservas` y para que el
remote sea desplegable por separado (PDF §4.1). La entrada de `/api/reportes` se incluye por
simetría con los otros dos frontends aunque este remote no llame a esa ruta: el `depends_on` del
servicio sí la excluye (§9, P-08).

### 3.4 Dependencias de `package.json`

Las mismas versiones exactas de `frontend/mf-reservas`, sin agregar ni una librería:

| Paquete | Versión | Tipo |
|---|---|---|
| `react` | `18.3.1` | dependencia |
| `react-dom` | `18.3.1` | dependencia |
| `@babel/core` | `7.26.0` | desarrollo |
| `@babel/preset-env` | `7.26.0` | desarrollo |
| `@babel/preset-react` | `7.26.3` | desarrollo |
| `babel-loader` | `9.2.1` | desarrollo |
| `css-loader` | `7.1.2` | desarrollo |
| `html-webpack-plugin` | `5.6.3` | desarrollo |
| `style-loader` | `4.0.0` | desarrollo |
| `webpack` | `5.97.1` | desarrollo |
| `webpack-cli` | `5.1.4` | desarrollo |
| `webpack-dev-server` | `5.2.0` | desarrollo |

`react` y `react-dom` deben coincidir **exactamente** con las del shell: un `singleton` con
versiones distintas hace que Webpack elija una y avise, o falle (HU-11).

## 4. Modelo de estado del remote

No hay tablas ni columnas: el "modelo de datos" de un microfrontend es su estado de React. Se
declara aquí campo por campo, con su tipo, su valor inicial y su restricción.

### 4.1 Estado de `AdminApp.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `vista` | string | `"canchas"` | uno de `"canchas"`, `"reservas"`, `"usuarios"` | HU-14: pantalla inicial Canchas |

`AdminApp` guarda **solo la vista activa**. No mantiene catálogos compartidos entre pantallas,
al contrario de `ReservasApp` (D-07 de la spec 07): HU-14 exige que volver a una pantalla vuelva
a pedir sus datos, para que un cambio hecho en otra pantalla no se muestre desactualizado (D-06).

`AdminApp` aporta además dos piezas que no son estado:

- `ejecutar(operacion)`: envoltorio único del `401`. Devuelve `{ datos, error }` y nunca lanza.
  Ante un `401` invoca `onLogout()` y devuelve `{ datos: null, error: null }` a propósito: el
  shell ya está borrando la sesión y va a desmontar el remote; pintar un error sería un parpadeo
  sobre una pantalla que deja de existir. Mismo patrón que D-13 de la spec 07.
- La **guardia de rol** de §4.5.

### 4.2 Estado de `PantallaCanchas.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `canchas` | arreglo de `CanchaResponse` | `[]` | orden el de la respuesta; no se reordena | HU-01 |
| `cargando` | boolean | `true` | mientras es `true` se muestra el aviso de carga | HU-01 |
| `error` | `{ codigo, mensaje }` o `null` | `null` | error del listado | HU-01 |
| `formulario` | objeto o `null` | `null` | `null` = ningún formulario abierto | HU-02, HU-03 |
| `formulario.modo` | string | — | `"alta"` o `"edicion"` | D-08 |
| `formulario.canchaId` | number o `null` | — | `null` en alta; el `canchaId` en edición | D-08 |
| `formulario.nombre` | string | `""` en alta, el actual en edición | ver §5.1 | HU-02, HU-03 |
| `formulario.deporte` | string | `"PADEL"` en alta, el actual en edición | `PADEL` \| `TENIS` \| `BASQUET` | §5.1 |
| `formulario.horaApertura` | string | `""` en alta, la actual en edición | `HH:mm` | §5.1 |
| `formulario.horaCierre` | string | `""` en alta, la actual en edición | `HH:mm` | §5.1 |
| `errorFormulario` | `{ codigo, mensaje }` o `null` | `null` | error del `POST` o del `PUT`; se muestra junto al formulario, que no se cierra | HU-02, HU-03 |
| `enviando` | boolean | `false` | deshabilita el botón de confirmar | HU-02, HU-03 |
| `canchaIdEnCambio` | number o `null` | `null` | fila cuyo `PATCH` de estado está en curso | HU-04 |
| `errorAccion` | `{ codigo, mensaje }` o `null` | `null` | error del cambio de estado | HU-04 |
| `aviso` | string o `null` | `null` | aviso de éxito de alta, edición o estado | HU-02 a HU-04 |
| `canchaSeleccionada` | number o `null` | `null` | `canchaId` cuyos bloqueos se muestran; `null` = panel cerrado | P-01, HU-05 |

Invariante: `formulario.modo === "edicion"` implica `formulario.canchaId !== null`.

### 4.3 Estado de `PanelBloqueos.jsx`

Se monta solo cuando `canchaSeleccionada !== null` y recibe ese `canchaId` por prop.

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `bloqueos` | arreglo de `BloqueoResponse` | `[]` | todos los de la cancha; sin filtro (P-03) | HU-05 |
| `cargando` | boolean | `true` | aviso de carga | HU-05 |
| `error` | `{ codigo, mensaje }` o `null` | `null` | error del listado | HU-05 |
| `formulario` | objeto o `null` | `null` | `null` = formulario cerrado | HU-06 |
| `formulario.fecha` | string | `""` | `AAAA-MM-DD` | §5.3 |
| `formulario.horaInicio` | string | `""` | `HH:mm` | §5.3 |
| `formulario.horaFin` | string | `""` | `HH:mm` | §5.3 |
| `formulario.motivo` | string | `""` | no vacío, máximo 200 | §5.3 |
| `errorFormulario` | `{ codigo, mensaje }` o `null` | `null` | error del `POST`; el formulario no se cierra | HU-06 |
| `enviando` | boolean | `false` | deshabilita confirmar | HU-06 |
| `confirmacion` | `BloqueoResponse` o `null` | `null` | bloqueo pendiente de borrar; `null` = sin diálogo | HU-07, D-12 |
| `bloqueoIdEnBorrado` | number o `null` | `null` | fila cuyo `DELETE` está en curso | HU-07 |
| `errorAccion` | `{ codigo, mensaje }` o `null` | `null` | error del borrado | HU-07 |

### 4.4 Estado de `PantallaReservas.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `reservas` | arreglo de `ReservaResponse` | `[]` | orden el de la respuesta (D-09 de la spec 04); no se reordena | HU-08 |
| `canchas` | arreglo de `CanchaResponse` | `[]` | solo para resolver `canchaId` a `nombre` y `deporte` | HU-08 |
| `usuarios` | arreglo de `UsuarioResponse` | `[]` | solo para resolver `usuarioId` a `nombre` (P-05) | HU-08 |
| `cargando` | boolean | `true` | aviso de carga del listado principal | HU-08 |
| `error` | `{ codigo, mensaje }` o `null` | `null` | error de `GET /api/reservas` | HU-08 |
| `errorApoyo` | `{ codigo, mensaje }` o `null` | `null` | error del catálogo o del listado de usuarios; **no** oculta las reservas | HU-08 |
| `filtroEstado` | string | `"TODOS"` | `"TODOS"`, `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | P-04 |
| `confirmacion` | `ReservaResponse` o `null` | `null` | reserva pendiente de cancelar | HU-08, D-12 |
| `idEnCancelacion` | number o `null` | `null` | fila cuyo `PATCH` está en curso | HU-08 |
| `errorAccion` | `{ codigo, mensaje }` o `null` | `null` | error de la cancelación | HU-08 |
| `aviso` | string o `null` | `null` | aviso de cancelación hecha | HU-08 |

El filtro **no** se aplica a `reservas`: filtrarlo destruiría los datos recibidos. Se aplica al
pintar, sobre una lista derivada del estado (D-09).

### 4.5 Estado de `PantallaUsuarios.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `usuarios` | arreglo de `UsuarioResponse` | `[]` | orden el de la respuesta | HU-09 |
| `cargando` | boolean | `true` | aviso de carga | HU-09 |
| `error` | `{ codigo, mensaje }` o `null` | `null` | error del listado | HU-09 |
| `confirmacion` | `UsuarioResponse` o `null` | `null` | solo se usa cuando la fila es la del propio administrador (P-06) | HU-09 |
| `usuarioIdEnCambio` | number o `null` | `null` | fila cuyo `PATCH` está en curso | HU-09 |
| `errorAccion` | `{ codigo, mensaje }` o `null` | `null` | error del cambio de estado | HU-09 |
| `aviso` | string o `null` | `null` | aviso de cambio hecho | HU-09 |

### 4.6 Props recibidas y cómo se usan

| Prop | Tipo | Uso | Restricción |
|---|---|---|---|
| `usuario.usuarioId` | number | comparación con el `usuarioId` de cada fila de usuarios (P-06) | nunca se envía en un cuerpo |
| `usuario.nombre` | string | texto de pantalla | solo presentación |
| `usuario.rol` | `ADMIN` \| `USUARIO` | guardia de §4.7 | no sustituye al control del token |
| `token` | string | `Authorization: Bearer <token>`, sin el prefijo, que lo agrega `clienteApi` | se pasa por parámetro en cada llamada, nunca se copia a una variable de módulo |
| `apiBaseUrl` | string `"/api"` | prefijo de toda ruta | jamás una URL absoluta ni un nombre de contenedor |
| `onLogout` | función | se invoca ante un `401` | único efecto del remote sobre la sesión |

El remote **no** lee `sessionStorage` ni `localStorage` (HU-10).

### 4.7 Guardia de rol (P-07)

`AdminApp` comprueba `usuario.rol === "ADMIN"` **antes** de montar la navegación y las pantallas.
Si no lo es, pinta un aviso de módulo no disponible y **no dispara ninguna llamada**: las
pantallas no llegan a montarse, así que sus `useEffect` de carga nunca corren.

Queda escrito, como pide el C1, que es **comportamiento defensivo y no control de acceso**: el
control real lo aplica cada microservicio con el token, y el shell ya restringe la opción del menú
al `ADMIN`.

### 4.8 Campos que se reciben y no se usan para decidir

| Campo | Se recibe en | Por qué no decide nada |
|---|---|---|
| `usuarioId` de la reserva | `GET /api/reservas` | solo se muestra y se resuelve a `nombre`; el permiso de cancelar lo aplica `ms-reservas` con el token |
| `rol` de un usuario listado | `GET /api/usuarios` | se muestra como columna; el remote no cambia su comportamiento según el rol de **otro** usuario |
| `email` | `GET /api/usuarios` | solo columna del listado |
| `canchaId` del bloqueo | `BloqueoResponse` | ya se conoce: es la cancha seleccionada. Se recibe y se ignora |

## 5. Payloads y validaciones

Los nombres y tipos son los del contrato, verificados en §1. La validación de cliente es
**mínima y estructural**: obliga a que el cuerpo esté completo y con el formato del contrato, y
**no duplica ninguna regla de negocio** (D-10).

### 5.1 `CanchaRequest` — cuerpo de `POST /api/canchas` y `PUT /api/canchas/{canchaId}`

```json
{ "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00" }
```

| Campo | Tipo | Validación de cliente | Validación real (servidor) |
|---|---|---|---|
| `nombre` | string | `required`, se recorta el espacio de los extremos, máximo 80 caracteres | `@NotBlank @Size(max = 80)`; unicidad → `409 NOMBRE_DUPLICADO` |
| `deporte` | string | `select` con las tres opciones del contrato; no hay texto libre | `@Pattern(PADEL\|TENIS\|BASQUET)` |
| `horaApertura` | string `HH:mm` | `input type="time"` con `required`: el navegador ya entrega `HH:mm` | `@Pattern` de `HH:mm` |
| `horaCierre` | string `HH:mm` | `input type="time"` con `required` | `@Pattern`; que sea posterior a la apertura lo valida `ms-canchas` |

No se envían `canchaId` ni `activa`: el identificador lo genera la base y el estado va por su
`PATCH` dedicado (S-02 y S-03 de la spec 03). El `PUT` manda los **cuatro** campos aunque solo
haya cambiado uno (D-11 de la spec 03).

El máximo de 80 caracteres se aplica con `maxLength` en el campo, que impide escribir de más; no
es una regla de negocio, es el mismo límite que el DTO ya declara.

### 5.2 `CambioEstadoCanchaRequest` — cuerpo de `PATCH /api/canchas/{canchaId}/estado`

```json
{ "activa": false }
```

| Campo | Tipo | Validación de cliente | Motivo |
|---|---|---|---|
| `activa` | boolean | siempre presente, con el valor **contrario** al `activa` de la fila | el DTO usa `Boolean` con `@NotNull`: un cuerpo sin el campo es `400`, no un `false` implícito |

### 5.3 `BloqueoRequest` — cuerpo de `POST /api/canchas/{canchaId}/bloqueos`

```json
{ "fecha": "2026-08-24", "horaInicio": "10:00", "horaFin": "12:00", "motivo": "Mantenimiento de piso" }
```

| Campo | Tipo | Validación de cliente | Validación real (servidor) |
|---|---|---|---|
| `fecha` | string `AAAA-MM-DD` | `input type="date"` con `required`: el navegador entrega ese formato y no permite un día inexistente | `@Pattern` más parseo estricto (D-04 de la spec 03) |
| `horaInicio` | string `HH:mm` | `input type="time"` con `required` | `@Pattern` de `HH:mm` |
| `horaFin` | string `HH:mm` | `input type="time"` con `required` | `@Pattern`; el orden de la franja lo valida `ms-canchas` (D-10 de la spec 03) |
| `motivo` | string | `required`, recortado, `maxLength` 200 | `@NotBlank @Size(max = 200)` |

No se envía `canchaId` —viaja en la ruta— ni `bloqueoId`. El solapamiento con otro bloqueo lo
detecta `ms-canchas` y llega como `409 BLOQUEO_DUPLICADO`: el remote **no** lo comprueba contra la
lista que ya tiene (D-10).

### 5.4 `CambioEstadoRequest` — cuerpo de `PATCH /api/usuarios/{usuarioId}/estado`

```json
{ "activo": false }
```

| Campo | Tipo | Validación de cliente | Motivo |
|---|---|---|---|
| `activo` | boolean | siempre presente, con el valor contrario al `activo` de la fila | `Boolean` con `@NotNull`, igual que el de cancha |

Es el campo `activo`, del usuario. No se confunde con `activa`, de la cancha (§1).

### 5.5 Cancelación y borrado — peticiones sin cuerpo

| Operación | Cuerpo | Respuesta | Nota |
|---|---|---|---|
| `PATCH /api/reservas/{id}/cancelacion` | **ninguno** | `200` con `ReservaResponse` | el contrato no declara campos de entrada; `clienteApi` no manda `Content-Type` cuando no hay cuerpo |
| `DELETE /api/canchas/{canchaId}/bloqueos/{id}` | **ninguno** | `204` **sin cuerpo** | `clienteApi` devuelve `null` ante un `204` y nadie intenta leer su JSON |

### 5.6 `CanchaResponse` — elemento de `GET /api/canchas` y respuesta de las cuatro escrituras

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00", "activa": true }
```

Los seis campos se muestran. Al `ADMIN` la ruta le devuelve **todas** las canchas, incluidas las
`activa = false` (D-05 de la spec 03): el remote no filtra nada.

### 5.7 `BloqueoResponse` — elemento de `GET` y respuesta de `POST` de bloqueos

```json
{ "bloqueoId": 3, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "10:00", "horaFin": "12:00", "motivo": "Mantenimiento de piso" }
```

Se muestran `fecha`, `horaInicio`, `horaFin` y `motivo`; `bloqueoId` es la clave de la fila y el
`{id}` del borrado; `canchaId` se ignora (§4.8).

### 5.8 `UsuarioResponse` — elemento de `GET /api/usuarios` y respuesta del `PATCH` de estado

```json
{ "usuarioId": 1, "nombre": "Ana", "email": "ana@demo.ec", "rol": "USUARIO", "activo": true }
```

Los cinco campos se muestran. **No** trae `password` y el remote no lo pide en ninguna pantalla.

### 5.9 `ReservaResponse` — elemento de `GET /api/reservas` y respuesta de la cancelación

```json
{ "id": 7, "usuarioId": 2, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00", "horaFin": "10:00", "estado": "CONFIRMADA" }
```

`canchaId` se resuelve a `nombre` y `deporte` con el catálogo; `usuarioId` se resuelve a `nombre`
con el listado de usuarios (P-05). Los dos se buscan por identificador en la lista ya cargada, sin
una llamada por fila; si el identificador no aparece, se muestra el número tal cual (D-11).

`estado = FINALIZADA` llega calculado por `ms-reservas` (D-02 de la spec 04) y el remote no lo
recalcula.

### 5.10 `ErrorResponse` — forma única de error

```json
{ "codigo": "BLOQUEO_DUPLICADO", "mensaje": "La franja ya esta bloqueada" }
```

`clienteApi` normaliza **toda** falla a esta forma, incluso cuando la respuesta no la trae (un
`502` del proxy, la red cortada): en ese caso emite
`{ codigo: "ERROR_INTERNO", mensaje: "No se pudo contactar al servicio" }` con `estado = 0`. Así
los componentes conocen una sola forma y nunca pintan un objeto de error crudo (HU-13).

### 5.11 Props recibidas del shell

Las cuatro del contrato y ninguna más, ya detalladas en §4.6. El remote no declara props propias
ni valores por defecto para ellas: si el shell no las entregara, sería un fallo de integración del
shell, no algo que este remote deba suplir.

## 6. Rutas, módulo expuesto y vistas

### 6.1 Rutas HTTP que el remote consume

El remote **no expone** ninguna ruta HTTP. Esta tabla es la de rutas que **consume**, con el rol
que el contrato exige.

| Verbo | Ruta | Rol requerido | Función de `src/api/` | Pantalla |
|---|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | `listarCanchas` | Canchas (HU-01) y Reservas (HU-08) |
| POST | `/api/canchas` | ADMIN | `crearCancha` | Canchas (HU-02) |
| PUT | `/api/canchas/{canchaId}` | ADMIN | `editarCancha` | Canchas (HU-03) |
| PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | `cambiarEstadoCancha` | Canchas (HU-04) |
| GET | `/api/canchas/{canchaId}/bloqueos` | ADMIN, USUARIO | `listarBloqueos` | Canchas → panel de bloqueos (HU-05) |
| POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | `crearBloqueo` | Canchas → panel de bloqueos (HU-06) |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | `eliminarBloqueo` | Canchas → panel de bloqueos (HU-07) |
| GET | `/api/reservas` | ADMIN | `listarReservas` | Reservas (HU-08) |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | `cancelarReserva` | Reservas (HU-08) |
| GET | `/api/usuarios` | ADMIN | `listarUsuarios` | Usuarios (HU-09) y Reservas (P-05) |
| PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | `cambiarEstadoUsuario` | Usuarios (HU-09) |

Las once son autenticadas: todas llevan `Authorization: Bearer <token>`. Ninguna acepta el token
`SERVICIO`, que no interviene aquí: este remote es un cliente de navegador, no un microservicio.

Rutas del contrato que este remote **no** consume: `POST /api/usuarios/sesiones`,
`POST /api/usuarios`, `GET /api/canchas/{canchaId}`, `GET /api/reservas/disponibilidad`,
`POST /api/reservas`, `GET /api/reservas/mias` y las tres de `/api/reportes`.

### 6.2 Módulo expuesto

| Clave expuesta | Archivo | Forma |
|---|---|---|
| `./AdminApp` | `src/AdminApp.jsx` | componente de React que recibe las cuatro props; **no** un `createRoot` |

`src/index.js` solo hace `import("./bootstrap")` (`CLAUDE.md` §3). `src/bootstrap.jsx` monta un
aviso estático para quien abra `http://localhost:3002` suelto, sin props inventadas y sin llamar a
ninguna ruta (D-02).

### 6.3 Vistas internas

Sin enrutador: la vista es estado de React y la URL del navegador no cambia (P-05 de la spec 06).

| Vista | Componente | Cómo se entra | Cómo se sale |
|---|---|---|---|
| `canchas` | `PantallaCanchas` | vista inicial; opción "Canchas" del menú interno | eligiendo otra opción del menú |
| `reservas` | `PantallaReservas` | opción "Reservas" del menú interno | idem |
| `usuarios` | `PantallaUsuarios` | opción "Usuarios" del menú interno | idem |

Dentro de `canchas` hay dos estados anidados que **no** son vistas: el formulario de alta o
edición (abierto o cerrado) y el panel de bloqueos de la cancha seleccionada (abierto o cerrado).
Los dos viven en la misma pantalla (P-01, P-10) y cerrarlos no cambia de vista.

Al cambiar de vista, el componente anterior se **desmonta**: su estado —listados, formularios a
medias, diálogos abiertos— se descarta, y al volver se piden los datos de nuevo (HU-14, D-06).

## 7. Códigos HTTP recibidos y comportamiento

La dirección es la inversa a la de un microservicio: aquí no se traducen excepciones a códigos, se
traducen **códigos recibidos a comportamiento**.

| HTTP | `codigo` | Operaciones que lo pueden devolver | Comportamiento del remote |
|---|---|---|---|
| 200 | — | listados, `PUT`, los dos `PATCH` de estado, cancelación | actualiza el estado, refresca el listado afectado y muestra el aviso de éxito |
| 201 | — | alta de cancha, alta de bloqueo | aviso de creación y refresco del listado; el formulario se cierra |
| 204 | — | borrado de bloqueo | refresca el listado de bloqueos; **no** se intenta leer cuerpo |
| 400 | `DATOS_INVALIDOS` | altas, edición y los dos `PATCH` de estado | muestra `mensaje` junto al formulario, que **no** se cierra y conserva lo escrito |
| 401 | `NO_AUTENTICADO` | cualquiera | `ejecutar` invoca `onLogout()`; no se pinta error (§4.1) |
| 403 | `SIN_PERMISO` | cualquier escritura y los `GET` de rol ADMIN | muestra `mensaje`; no debería ocurrir con la guardia de §4.7, pero se maneja |
| 404 | `NO_ENCONTRADO` | edición, estado, bloqueos, cancelación, estado de usuario | muestra `mensaje` y refresca el listado: el recurso ya no existe |
| 409 | `NOMBRE_DUPLICADO` | alta y edición de cancha | muestra `mensaje` y deja el formulario abierto para corregir el `nombre` |
| 409 | `BLOQUEO_DUPLICADO` | alta de bloqueo | muestra `mensaje` y deja el formulario abierto |
| 409 | `RESERVA_PASADA` | cancelación | muestra `mensaje` y refresca el listado (RN-04) |
| 409 | `RESERVA_NO_CANCELABLE` | cancelación | muestra `mensaje` y refresca el listado |
| 500 | `ERROR_INTERNO` | cualquiera | muestra `mensaje` y deja reintentar sin recargar la página |
| sin respuesta | `ERROR_INTERNO` (sintético, `estado = 0`) | cualquiera | aviso propio de fallo de comunicación y reintento; nunca un stacktrace |

Reglas transversales:

- La reacción se elige por `codigo`, **nunca** por el texto del `mensaje` (HU-13).
- El `401` se detecta por el **estado HTTP**, que es lo que fija el contrato, no por el `codigo`.
- Un error nunca deja la pantalla en blanco: la navegación interna sigue funcionando (HU-13).

Códigos del contrato que este remote **no** puede recibir: `EMAIL_DUPLICADO` (registro, del
shell), `BLOQUE_OCUPADO` y `LIMITE_RESERVAS` (creación de reservas, de `mf-reservas`).

## 8. Componentes y flujos

### 8.1 Responsabilidad de cada componente

| Componente | Responsabilidad | No hace |
|---|---|---|
| `AdminApp` | vista activa, guardia de rol, envoltorio del `401`, reparto de props | no llama a la API por su cuenta ni guarda listados |
| `NavegacionInterna` | tres botones y cuál está activo | no decide permisos ni pinta el menú del shell |
| `PantallaCanchas` | carga el catálogo, pinta la tabla, abre el formulario y el panel de bloqueos, cambia el estado | no valida reglas de negocio ni filtra canchas |
| `FormularioCancha` | campos de alta y edición, con precarga en edición | no llama a la API: entrega el cuerpo a la pantalla |
| `PanelBloqueos` | carga, pinta, crea y borra bloqueos de la cancha recibida | no ofrece selector de cancha (P-01) |
| `FormularioBloqueo` | los cuatro campos del bloqueo | no comprueba solapamientos (D-10) |
| `PantallaReservas` | tres cargas, filtro por `estado`, resolución de nombres, cancelación | no reordena ni recalcula `FINALIZADA` |
| `PantallaUsuarios` | listado y cambio de estado, con el caso propio de P-06 | no crea, edita ni elimina usuarios |
| `DialogoConfirmacion` | pregunta con detalle y dos acciones | no llama a la API: devuelve la decisión |
| `MensajeError` | pinta `{ codigo, mensaje }` | no interpreta ni traduce el mensaje |

### 8.2 Flujos

**F-01 — Alta de cancha.** Botón "Nueva cancha" → `formulario = { modo: "alta", ... }` en la misma
pantalla → confirmar → `enviando = true` → `POST /api/canchas` → `201`: aviso, formulario cerrado
y recarga del listado; `400` o `409`: `errorFormulario` y formulario abierto con lo escrito.

**F-02 — Edición de cancha.** Botón "Editar" de una fila → `formulario = { modo: "edicion",
canchaId, ...valores actuales }` → confirmar → `PUT /api/canchas/{canchaId}` con los cuatro campos
→ `200`: aviso, formulario cerrado y recarga; `404`: mensaje y recarga.

**F-03 — Cambio de estado de cancha.** Botón "Activar" o "Inactivar" → **sin confirmación**
(P-02) → `canchaIdEnCambio` fija la fila → `PATCH .../estado` con el valor contrario → `200`:
recarga del listado.

**F-04 — Bloqueos de una cancha.** Botón "Bloqueos" de una fila → `canchaSeleccionada = canchaId`
→ `PanelBloqueos` se monta y hace `GET /api/canchas/{canchaId}/bloqueos` **sin** parámetro `fecha`
(P-03) → alta con `POST` → `201`: recarga del panel.

**F-05 — Borrado de bloqueo.** Botón "Eliminar" → `confirmacion = bloqueo` →
`DialogoConfirmacion` muestra `fecha`, franja y `motivo` → confirmar →
`DELETE .../bloqueos/{bloqueoId}` → `204`: recarga del panel; rechazar → `confirmacion = null` y
**ninguna llamada**.

**F-06 — Listado global de reservas.** Al montar, tres cargas: `GET /api/reservas` (principal),
`GET /api/canchas` y `GET /api/usuarios` (apoyo). El listado se pinta en cuanto llegan las
reservas; los nombres aparecen cuando llegan las otras dos. Si una de apoyo falla, se pinta
`errorApoyo` y las filas muestran el identificador (HU-08, D-11).

**F-07 — Cancelación de cualquier reserva.** Botón "Cancelar" en una fila `CONFIRMADA` →
`confirmacion = reserva` → el diálogo muestra cancha, `fecha` y bloque → confirmar →
`PATCH /api/reservas/{id}/cancelacion` sin cuerpo → `200`: aviso y recarga del listado; `409`:
mensaje y recarga.

**F-08 — Cambio de estado de usuario.** Botón "Activar" o "Inactivar" → si el `usuarioId` de la
fila **no** es el de la prop `usuario`, la llamada sale directa; si **sí** lo es,
`confirmacion = usuario` y el diálogo advierte que se está inactivando a sí mismo (P-06) →
`PATCH /api/usuarios/{usuarioId}/estado` → `200`: recarga del listado.

**F-09 — Token vencido.** Cualquier llamada responde `401` → `ejecutar` invoca `onLogout()` → el
shell borra la sesión, vuelve al inicio de sesión y desmonta el remote. El remote no pinta nada
más.

**F-10 — Rol no ADMIN.** `AdminApp` detecta `usuario.rol !== "ADMIN"` al pintar → aviso de módulo
no disponible → **ninguna** llamada sale (P-07).

## 9. Servicio `mf-administracion` en `docker-compose.yml`

Mismo patrón que `shell` y `mf-reservas`, único archivo tocado fuera de la carpeta del remote.

| Clave | Valor | Motivo |
|---|---|---|
| `image` | `node:20-alpine` | `CLAUDE.md` §1: nada instalado en el host |
| `container_name` | `canchas-mf-administracion` | convención de los servicios existentes |
| `working_dir` | `/app` | idem |
| `command` | `sh -c "npm install && npx webpack serve --mode development"` | patrón del shell y de `mf-reservas` |
| `volumes` | `./frontend/mf-administracion:/app` y `mf_administracion_node_modules:/app/node_modules` | el volumen anónimo evita que el `node_modules` inexistente del host tape el del contenedor |
| `ports` | `"3002:3002"` | contrato |
| `depends_on` | `ms-usuarios`, `ms-canchas`, `ms-reservas`, los tres con `condition: service_started` | P-08: los tres que este remote consume; **no** `ms-reportes` |

Se agrega `mf_administracion_node_modules` a la sección `volumes` del archivo. El `shell` **no**
declara `depends_on` de este remote (P-08 de la spec 07).

## 10. Verificación prevista (para `tasks.md`, no se ejecuta aquí)

| Nivel | Comprobación |
|---|---|
| 1 | `curl.exe http://localhost:3002/remoteEntry.js` responde `200` |
| 2 | `curl.exe http://localhost:3001/remoteEntry.js` sigue respondiendo `200`: el segundo remote no rompe al primero |
| 3 | `docker compose logs --tail=50 mf-administracion` sin errores de compilación |
| 4 | Recorrido por navegador con un `ADMIN`: iniciar sesión en `http://localhost:3000`, entrar a Administración y ejercitar la pantalla de la tarea |
| 5 | Consola del navegador sin errores de React duplicado ni de `hooks` inválidos |

Un `compiled successfully` no prueba que la aplicación funcione (bitácora, T5 de la spec 06): toda
tarea con interacción exige el nivel 4.

## 11. Aislamiento de datos

- El remote **no tiene base de datos** y **no ejecuta SQL**. No hay ninguna consulta que pueda
  tocar la tabla de otro microservicio.
- Cada dato se pide al microservicio **dueño** del recurso: canchas y bloqueos a `ms-canchas`,
  reservas a `ms-reservas`, usuarios a `ms-usuarios`. El cruce de `canchaId` y `usuarioId` con sus
  nombres ocurre **en el navegador**, sobre respuestas de sus dueños, nunca con un `join` entre
  bases.
- El remote no propaga el token a ningún servicio distinto del destinatario de la llamada, y no
  emite tokens `SERVICIO`: eso es cosa de los microservicios entre sí.

## 12. Decisiones de diseño

| ID | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D-01 | El módulo expuesto es un **componente** que recibe las cuatro props | Exponer un `createRoot` que se monte solo en un `div` | El shell lo monta dentro de su propio árbol de React; un `createRoot` crearía un árbol aparte, rompería el `singleton` en la práctica y dejaría las props sin camino |
| D-02 | `bootstrap.jsx` pinta un **aviso estático** para quien abra `localhost:3002` | Montar `AdminApp` con props de desarrollo y un token de prueba | Sin shell no hay `token` ni `usuario`; inventarlos es exactamente lo que el contrato de props existe para evitar (P-04 de la spec 07) |
| D-03 | `src/api/` es la única capa con `fetch`, dividida en tres archivos por microservicio dueño | Un solo `api.js`, o `fetch` dentro de cada componente | `CLAUDE.md` §4 lo exige, y un archivo por dueño deja visible qué microservicio consume cada pantalla |
| D-04 | `clienteApi.js` se **replica** desde `mf-reservas`, no se comparte | Exponer `clienteApi` como módulo federado desde `mf-reservas` y consumirlo aquí | Un remote que dependa de otro deja de ser desplegable por separado (PDF §4.1) y crea un acoplamiento que la rúbrica §6 penaliza. Son 90 líneas: la duplicación cuesta menos que el acoplamiento |
| D-05 | El `token` viaja como **parámetro** de cada llamada | Guardarlo en una variable de módulo de `clienteApi` al montar | Es una prop que puede cambiar; una copia guardada se quedaría con el valor viejo tras un cambio de sesión |
| D-06 | Cada pantalla **carga sus propios datos** al montarse y no hay catálogo compartido en `AdminApp` | Cargar canchas y usuarios una vez en `AdminApp` y bajarlos por props, como hizo `ReservasApp` con D-07 | Aquí el `ADMIN` **escribe**: el catálogo cambia en la propia sesión. Un catálogo cacheado en la raíz mostraría en la pantalla de Reservas el nombre viejo de una cancha recién editada. HU-14 lo pide explícito |
| D-07 | Estado local por pantalla; `AdminApp` solo guarda `vista` | Un `Context` o un gestor de estado global | Tres pantallas hermanas sin datos compartidos no justifican un `Context`, y `CLAUDE.md` §3 prohíbe dependencias que no exija Module Federation |
| D-08 | Un **único** `FormularioCancha` para alta y edición, distinguidos por `formulario.modo` | Dos componentes separados | Los cuatro campos y sus validaciones son idénticos; lo único que cambia es el valor inicial y la ruta a la que se envía. Dos componentes duplicarían el formulario entero |
| D-09 | El filtro por `estado` se aplica **al pintar**, sobre una lista derivada; `reservas` conserva todo lo recibido | Guardar en el estado solo las reservas filtradas | Filtrar el estado obligaría a volver a llamar a la API al cambiar de filtro, y `GET /api/reservas` no acepta parámetros: sería una recarga completa por cada cambio de selector |
| D-10 | La validación de cliente es **estructural**: campos obligatorios, tipos de `input` y longitudes máximas del DTO. Nada de reglas de negocio | Validar en el navegador que `horaCierre > horaApertura`, que la franja del bloqueo no solape con las ya listadas o que el nombre no se repita | Duplicar una regla en el cliente crea dos fuentes de verdad que se desincronizan. El mismo criterio de P-02 de la spec 07 con las fechas pasadas: la regla vive en el microservicio y el remote muestra su `409` |
| D-11 | `canchaId` y `usuarioId` se resuelven buscando en la lista ya cargada; si no aparecen, se muestra el número | Una llamada `GET /api/canchas/{canchaId}` por fila, o inventar un texto como "Cancha eliminada" | Una llamada por fila multiplica el tráfico sin aportar nada, e inventar un texto es fabricar un dato que la API no devolvió (`CLAUDE.md` §0.1) |
| D-12 | Un `DialogoConfirmacion` propio, reutilizado por los tres casos irreversibles | `window.confirm` | `window.confirm` bloquea el hilo, no se puede estilar, no muestra el detalle de la operación y en una demo se ve como un error del navegador. Además la advertencia de P-06 necesita texto propio |
| D-13 | Las tres cargas de la pantalla de Reservas se lanzan **en paralelo** y el listado se pinta con lo que haya llegado | Encadenarlas: primero canchas, luego usuarios, luego reservas | Encadenar suma las tres latencias y, peor, un fallo del catálogo impediría ver las reservas, que es justo lo que HU-08 prohíbe |
| D-14 | El `PATCH` de estado envía el valor **contrario** al de la fila, calculado al pulsar | Un interruptor que envíe el valor de su propio estado visual | El valor de referencia debe ser el que devolvió la API, no el que el componente cree tener: si dos pestañas cambian la misma cancha, el interruptor mandaría un valor obsoleto |
| D-15 | Tras cada escritura con éxito se **recarga el listado** desde la API, en vez de actualizar el arreglo en memoria con la respuesta | Insertar o reemplazar el elemento devuelto dentro del arreglo del estado | La recarga es una llamada barata y garantiza que se ve lo que el servidor tiene, incluido lo que cambió otro administrador. Actualizar en memoria acumula divergencias silenciosas |
| D-16 | Todas las clases CSS llevan el prefijo `mfa-` | Nombres genéricos como `.tabla` o `.boton` | Los estilos del shell y de los dos remotes conviven en el mismo documento cuando el módulo se monta: sin prefijo, una regla de este remote repintaría `mf-reservas` |
| D-17 | El panel de bloqueos vive **dentro** de la pantalla de Canchas y recibe el `canchaId` por prop | Un componente de bloqueos con su propio selector de cancha | P-01 del C1; además un selector propio duplicaría el listado que ya está al lado y podría quedar desincronizado con él |
| D-18 | La guardia de rol se evalúa en `AdminApp` **antes** de montar las pantallas | Comprobar el rol dentro de cada pantalla, o dejar que cada llamada devuelva `403` | Comprobarlo en la raíz garantiza que no sale ni una llamada (P-07); repetirlo en cada pantalla sería la misma condición escrita tres veces |

## 13. Fuera de alcance de este diseño

- `tasks.md` y cualquier archivo de código: se escriben tras aprobar esta compuerta, tarea por
  tarea (`CLAUDE.md` §6). P-09 ya fijó el reparto: una tarea por pantalla, más andamiaje y Compose.
- `mf-reportes` (spec 09) y las rutas de `/api/reportes`.
- Modificar el shell, `frontend/mf-reservas`, `backend/`, `infra/postgres/` o
  `docs/contratos/README.md`: este diseño no necesita ningún campo, ruta ni código de error nuevo.
- El gateway Nginx y la eliminación de los mapeos `8082`–`8085`.
- Pruebas automatizadas de frontend, enrutador, gestor de estado global, librería de UI,
  TypeScript, i18n y tema oscuro.
- Todo lo listado en §9 del `requirements.md` aprobado.
