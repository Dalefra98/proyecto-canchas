# Spec 07 — mf-reservas (remote de Module Federation) · design.md

Estado: **C2 — APROBADO** el 24/08/2026 ("Apruebo diseño de la spec 07").
Falta `tasks.md`: el codigo de produccion se escribe tarea por tarea, una a la vez, con su
comando de verificacion (`CLAUDE.md` §6).

Base: `requirements.md` de esta spec, **C1 aprobado el 24/08/2026**, con las decisiones P-01 a
P-09 ya incorporadas.

Fuentes verificadas para este diseño: `CLAUDE.md` (§1, §3, §4, §5, §7),
`docs/contratos/README.md` (campos congelados, `DisponibilidadResponse`, "Rutas REST
congeladas", "Formato de error", "Contrato Module Federation"),
`.claude/specs/04-ms-reservas/requirements.md` (D-02, D-03, D-04, D-05, D-06, D-07, D-08, D-09,
D-11 y C-02), `.claude/specs/03-ms-canchas/`, `.claude/specs/06-shell-module-federation/design.md`
(§3.4 y D-01 a D-16), `docker-compose.yml`, `frontend/shell/` y `docs/bitacora.md`.

## 0. Nota sobre las secciones pedidas

El comando de diseño pide cinco tablas pensadas para un microservicio. Este entregable es un
**microfrontend remote**: no tiene base de datos, no expone endpoints HTTP y no traduce
excepciones a codigos HTTP. Las secciones sin equivalente literal se sustituyen por su analogo
exacto, declarado aqui para que no parezca que se omitieron. Es la misma sustitucion que el
`design.md` de la spec 06 ya aplico y que el responsable aprobo.

| Pedido | Que se entrega en su lugar | Seccion |
|---|---|---|
| Modelo de datos (columnas y restricciones) | **Modelo de estado del remote**: campo, tipo, valor inicial, restricciones y origen. No hay ni una tabla de base de datos | §4 |
| DTOs con validaciones | **Payloads de request y response** con las validaciones de cliente campo por campo, mas las props recibidas | §5 |
| Tabla de endpoints con rol requerido | **Tabla de rutas consumidas** con su rol requerido, mas las vistas internas del remote. El remote no **expone** ninguna ruta HTTP; lo unico que expone es el modulo `./ReservasApp` | §6 |
| Tabla de excepciones a codigos HTTP | **Tabla de codigo HTTP recibido a comportamiento del remote**: la direccion es la inversa a la de un microservicio | §7 |
| Tabla de decisiones con alternativa descartada | Igual que en las seis specs anteriores | §12 |

"Ninguna consulta puede acceder a tablas de otro microservicio" se cumple de forma absoluta:
el remote **no accede a ninguna base de datos**. Su unico acceso a datos son cinco rutas HTTP de
`ms-canchas` y `ms-reservas` (§6.1). No hay SQL en esta spec (§11).

## 1. Verificacion campo por campo contra `docs/contratos/README.md`

Todos los campos que este diseño usa existen en el contrato con **el mismo nombre**. No se
renombra, no se abrevia, no se traduce y no se agrega ninguno.

| Campo usado | Existe en el contrato | Tipo / valores del contrato | Donde lo usa el remote |
|---|---|---|---|
| `canchaId` | si | number | selector de cancha, `PeticionReserva`, enlace con el catalogo |
| `nombre` | si | string (cancha, dueño `ms-canchas`) | selector, formulario precargado, listado |
| `deporte` | si | `PADEL` \| `TENIS` \| `BASQUET` | filtro, selector, listado |
| `horaApertura` | si | string `HH:mm` | encabezado de la grilla |
| `horaCierre` | si | string `HH:mm` | encabezado de la grilla |
| `activa` | si | boolean | se recibe en el catalogo y no se usa para decidir (§4.4) |
| `fecha` | si | string `AAAA-MM-DD` | consulta, `PeticionReserva`, listado |
| `bloques` | si | arreglo de objetos | filas de la grilla |
| `horaInicio` | si | string `HH:mm` | bloque, `PeticionReserva`, listado |
| `horaFin` | si | string `HH:mm` | grilla y listado; **nunca** se envia |
| `disponible` | si | boolean | estado visual del bloque y si es seleccionable |
| `id` | si | number | clave del listado y ruta de cancelacion |
| `usuarioId` | si | number | llega en la reserva y en la prop `usuario`; nunca se envia |
| `estado` | si | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | columna del listado y condicion de cancelable |
| `token` | si | string | prop; encabezado `Authorization: Bearer <token>` |
| `usuario` | si | objeto `UsuarioResponse` | prop del contrato de props |
| `rol` | si | `ADMIN` \| `USUARIO` | prop; no cambia el comportamiento del remote (§4.3) |
| `codigo` | si | ver "Formato de error" | selecciona la reaccion del remote (§7) |
| `mensaje` | si | string | texto que se muestra tal cual |
| `apiBaseUrl` | si | `"/api"` | prefijo de toda ruta llamada |
| `onLogout` | si | funcion | se invoca ante un `401` |

Contrato de props verificado tal como quedo el 23/08/2026 en `docs/contratos/README.md` y
`CLAUDE.md` §5:

```jsx
<ReservasApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

Nombres de Module Federation verificados: `mfReservas`, modulo `./ReservasApp`, puerto 3001,
exactamente como el shell ya los declara en su `webpack.config.js`.

Rutas verificadas contra "Rutas REST congeladas": `GET /api/canchas`,
`GET /api/reservas/disponibilidad?canchaId&fecha`, `POST /api/reservas`,
`GET /api/reservas/mias`, `PATCH /api/reservas/{id}/cancelacion`.

**Ningun nombre discrepa.** No hay nada que detener por este motivo.

Dos aclaraciones de nombres que **no** son discrepancias:

- `nombre` aparece dos veces en el contrato, como nombre de cancha (`ms-canchas`) y como nombre
  de usuario (`ms-usuarios`). El remote usa los dos, cada uno en su payload, y no los mezcla.
- El campo `id` de la reserva se llama `id`, no `reservaId`. Se usa tal cual en la ruta de
  cancelacion.

## 2. Estructura de archivos

Sigue `CLAUDE.md` §4 para microfrontends. Todo lo que se crea vive en `frontend/mf-reservas`,
con la unica excepcion del servicio en `docker-compose.yml` (E-14, P-07).

```
frontend/mf-reservas/
  package.json
  webpack.config.js
  .babelrc
  public/index.html
  src/index.js                             # solo import("./bootstrap")
  src/bootstrap.jsx                        # aviso estatico para localhost:3001 (P-04, D-02)
  src/ReservasApp.jsx                      # modulo expuesto; dueño del estado
  src/estilos.css                          # CSS plano, unico archivo de estilos
  src/api/clienteApi.js                    # unica pieza que llama fetch
  src/api/canchasApi.js                    # listarCanchas
  src/api/reservasApi.js                   # consultarDisponibilidad, crearReserva,
                                           # listarMisReservas, cancelarReserva
  src/components/NavegacionInterna.jsx     # Disponibilidad / Mis reservas (E-11, D-17)
  src/components/PantallaDisponibilidad.jsx# filtros, selectores y consulta (HU-01)
  src/components/GrillaBloques.jsx         # bloques de una hora (HU-01)
  src/components/PantallaNuevaReserva.jsx  # confirmacion precargada (HU-02)
  src/components/PantallaMisReservas.jsx   # listado (HU-03, HU-04)
  src/components/FilaReserva.jsx           # una reserva y su accion de cancelar (HU-05)
  src/components/ConfirmacionCancelacion.jsx # paso de confirmacion (P-06, D-12)
  src/components/MensajeError.jsx          # pinta { codigo, mensaje }
```

`src/api/` es la **unica** capa que llama `fetch` (`CLAUDE.md` §4). Ningun componente lo hace por
su cuenta.

No hay carpeta `mapper/` ni modulo de utilidades: el unico calculo del remote es la fecha de hoy
en formato `AAAA-MM-DD`, que vive como funcion local de `PantallaDisponibilidad` (D-09).
`CLAUDE.md` §3 prohibe las clases `Util` genericas y aqui no se crea ninguna.

No hay carpeta `sesion/`: el remote no tiene sesion propia (HU-06 del C1).

## 3. Configuracion de Webpack y Module Federation

### 3.1 `ModuleFederationPlugin` del remote

| Opcion | Valor | Motivo |
|---|---|---|
| `name` | `"mfReservas"` | contrato; es el nombre con el que el shell lo declara |
| `filename` | `"remoteEntry.js"` | el shell apunta a `mfReservas@http://localhost:3001/remoteEntry.js` |
| `exposes` | `{ "./ReservasApp": "./src/ReservasApp" }` | clave exacta del contrato (D-01) |
| `remotes` | **ausente** | un remote no consume otros remotes en esta arquitectura |
| `shared` | `react` y `react-dom` con `singleton: true` y `requiredVersion` de `package.json` | `CLAUDE.md` §3 |
| `output.publicPath` | `"auto"` | sin ella, los `chunk` del remote se piden al origen del shell (`localhost:3000`) y la carga falla |
| `output.uniqueName` | `"mfReservas"` | evita colisiones de runtime entre host y remote |

### 3.2 `devServer` y `watchOptions`

Mismos valores que el shell, con el puerto cambiado. `watchOptions` va en la raiz de la
configuracion, no dentro de `devServer`.

| Opcion | Valor | Motivo |
|---|---|---|
| `watchOptions.poll` | `1000` | el bind mount de Windows no entrega eventos inotify al contenedor: sin sondeo, `webpack serve` compila al arrancar y despues nunca ve un archivo guardado (D-15 de la spec 06) |
| `watchOptions.ignored` | `/node_modules/` | sondear el `node_modules` del contenedor gasta CPU sin ganancia |
| `port` | `3001` | contrato |
| `host` | `"0.0.0.0"` | dentro del contenedor, escuchar solo en `localhost` deja al navegador del host sin acceso |
| `allowedHosts` | `"all"` | el navegador entra por `localhost:3001`, que no es el host interno del contenedor |
| `headers` | `{ "Access-Control-Allow-Origin": "*" }` | el `remoteEntry.js` lo descarga una pagina servida en `localhost:3000`: es otro origen (D-03) |
| `hot` | `true` | recarga en caliente durante el desarrollo |
| `client.webSocketURL` | `"ws://localhost:3001/ws"` | el socket lo abre el **navegador**: su URL es la del host |
| `client.overlay.runtimeErrors` | `false` | mismo criterio que D-16 de la spec 06: el `BordeError` del shell ya muestra el fallo y el overlay lo repetiria tapando la pantalla |
| `historyApiFallback` | **no se usa** | no hay enrutador (P-05 de la spec 06) |
| `proxy` | arreglo de cuatro entradas (§3.3) | P-02 de la spec 06 |

### 3.3 Proxy de `/api` y quien lo usa de verdad

`webpack-dev-server` 5 recibe `proxy` como **arreglo** de `{ context, target }` (D-02 de la
spec 06).

| `context` | `target` | Microservicio |
|---|---|---|
| `/api/usuarios` | `http://ms-usuarios:8080` | `ms-usuarios` |
| `/api/canchas` | `http://ms-canchas:8080` | `ms-canchas` |
| `/api/reservas` | `http://ms-reservas:8080` | `ms-reservas` |
| `/api/reportes` | `http://ms-reportes:8080` | `ms-reportes` |

Se declaran los cuatro prefijos, como en el shell, aunque el remote solo llame a dos: el proxy es
infraestructura del `devServer`, no codigo de aplicacion, y la spec 06 dejo escrito que "cada
remote declarara los suyos igual".

**Cual proxy atiende cada peticion**, que es el punto que confunde:

| Situacion | Origen de la pagina | Quien proxya `/api/...` |
|---|---|---|
| Remote montado dentro del shell (caso real de uso) | `http://localhost:3000` | el `devServer` del **shell** |
| Remote abierto suelto en `http://localhost:3001` | `http://localhost:3001` | el `devServer` del **remote** |

Como el aviso estatico de `bootstrap.jsx` no llama a la API (P-04, D-02), el proxy del remote no
atiende ninguna peticion hoy. Queda declarado para que el microfrontend sea autonomo, como pide
el PDF §4.1, y para que no se lea como un olvido.

Y la distincion que ya cerro la spec 06 y aqui no se reabre:

| Que | Quien la resuelve | Forma de la URL |
|---|---|---|
| `remoteEntry.js` de este remote | el **navegador**, fuera de Docker | `http://localhost:3001/remoteEntry.js` |
| Socket de recarga en caliente | el **navegador**, fuera de Docker | `ws://localhost:3001/ws` |
| Destino del proxy de `/api` | `webpack serve`, **dentro** del contenedor | `http://ms-reservas:8080` |

### 3.4 Dependencias de `package.json`

**Las mismas doce versiones exactas que fijo el `design.md` de la spec 06 en su §3.4** (D-01 de
esa spec). Este remote no agrega ni una dependencia mas.

| Paquete | Version | Tipo |
|---|---|---|
| `react` | 18.3.1 | dependencia |
| `react-dom` | 18.3.1 | dependencia |
| `webpack` | 5.97.1 | desarrollo |
| `webpack-cli` | 5.1.4 | desarrollo |
| `webpack-dev-server` | 5.2.0 | desarrollo |
| `html-webpack-plugin` | 5.6.3 | desarrollo |
| `@babel/core` | 7.26.0 | desarrollo |
| `@babel/preset-env` | 7.26.0 | desarrollo |
| `@babel/preset-react` | 7.26.3 | desarrollo |
| `babel-loader` | 9.2.1 | desarrollo |
| `css-loader` | 7.1.2 | desarrollo |
| `style-loader` | 4.0.0 | desarrollo |

Con `react` en `shared` y `singleton: true`, una version distinta a la del shell rompe la
instancia unica en tiempo de ejecucion: por eso las versiones se copian, no se re-eligen.
Script unico de arranque: `webpack serve --mode development`. `.babelrc` con
`@babel/preset-env` y `@babel/preset-react` en modo `automatic`.

## 4. Modelo de estado del remote

El remote **no tiene base de datos ni tablas**, y tampoco usa `sessionStorage` ni `localStorage`
(HU-06 del C1). Su "modelo" es el estado de React de `ReservasApp.jsx` y de sus pantallas. Se
documenta con el mismo rigor que un DDL para que el diseño sea verificable.

### 4.1 Estado de `ReservasApp.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `vista` | string | `"disponibilidad"` | uno de `disponibilidad`, `nuevaReserva`, `misReservas` | estado local (E-11) |
| `canchas` | arreglo de objetos | `[]` | cada elemento con `canchaId`, `nombre`, `deporte`, `horaApertura`, `horaCierre`, `activa` | `GET /api/canchas` |
| `errorCatalogo` | objeto `{ codigo, mensaje }` o `null` | `null` | si no es `null`, el listado y el selector siguen funcionando con `canchaId` en vez de `nombre` (HU-04) | §7 |
| `cargandoCatalogo` | boolean | `true` | mientras sea `true`, los selectores estan deshabilitados | — |
| `reservaPendiente` | objeto o `null` | `null` | obligatorio y no `null` cuando `vista === "nuevaReserva"`; se descarta al volver o al crear (D-10) | seleccion de la grilla |
| `reservaPendiente.canchaId` | number | — | entero positivo; existe en `canchas` | grilla |
| `reservaPendiente.fecha` | string | — | formato `AAAA-MM-DD` | grilla |
| `reservaPendiente.horaInicio` | string | — | formato `HH:mm`, minutos `00` | bloque elegido |
| `reservaPendiente.horaFin` | string | — | formato `HH:mm`; solo se **muestra** | bloque elegido |
| `avisoExito` | string o `null` | `null` | mensaje propio del remote tras un `201` o un `200` de cancelacion | HU-02, HU-05 |
| `consultaGrilla` | objeto o `null` | `null` | `null` mientras no se haya consultado nada; una vez consultado, sobrevive al ida y vuelta a la pantalla de nueva reserva | boton de consultar (D-18) y D-11 |
| `consultaGrilla.canchaId` | number | — | la cancha efectivamente consultada | seleccion del usuario |
| `consultaGrilla.fecha` | string | — | formato `AAAA-MM-DD` | seleccion del usuario |
| `consultaGrilla.refresco` | number | — | contador que solo crece; cada incremento pide reconsultar **los mismos** `canchaId` y `fecha` | D-11 |

`consultaGrilla` se agrego el 24/08/2026, al implementar T5, porque el modelo original no
alcanzaba: HU-02 pide volver a la grilla "con la misma cancha y fecha consultadas" y D-11 pide
reconsultarla tras el `201`, pero §4.2 pone `canchaIdElegida`, `fecha` y `disponibilidad` dentro
de `PantallaDisponibilidad`, que se desmonta al pasar a `vista = "nuevaReserva"` (§6.3), y
`reservaPendiente` —el unico otro sitio donde viven esa cancha y esa fecha— se descarta
justo en ese momento por el invariante de abajo. Sin un campo que sobreviva al ida y vuelta, la
grilla volvia vacia y con la fecha reiniciada a hoy.

Es el cambio mas chico que cumple las dos: §4.2 queda intacta, porque los campos de la pantalla
siguen siendo suyos y `consultaGrilla` solo fija sus valores iniciales y dispara la consulta. El
`refresco` es un contador y no un booleano para que dos reconsultas seguidas de la misma cancha y
fecha —dos `409 BLOQUE_OCUPADO` sobre el mismo bloque, por ejemplo— se distingan una de otra. El
`null` inicial es lo que mantiene D-18: al montar el remote la grilla esta vacia y no sale ni una
llamada hasta que el usuario pulse Consultar.

Invariantes:

- `vista === "nuevaReserva"` implica `reservaPendiente !== null`. Al volver a la grilla o tras un
  `201`, `reservaPendiente` vuelve a `null`.
- `reservaPendiente` solo puede nacer de un bloque con `disponible === true` (HU-02).
- El `token`, el `usuario` y el `apiBaseUrl` **no son estado**: son props y se leen en cada
  render (§4.3).
- No existe ningun campo de sesion, ni copia del `token`, ni `password`: nada de eso pasa por
  este remote.

### 4.2 Estado de las pantallas

| Pantalla | Campo | Tipo | Valor inicial | Restricciones |
|---|---|---|---|---|
| `PantallaDisponibilidad` | `deporteFiltro` | string | `"TODOS"` | `TODOS`, `PADEL`, `TENIS` o `BASQUET` (P-03) |
| `PantallaDisponibilidad` | `canchaIdElegida` | number o `""` | `""` | si no es `""`, existe en el catalogo filtrado |
| `PantallaDisponibilidad` | `fecha` | string | **la fecha de hoy**, `AAAA-MM-DD` | formato `AAAA-MM-DD`; el pasado **si** se permite (P-02, D-09) |
| `PantallaDisponibilidad` | `disponibilidad` | objeto o `null` | `null` | `DisponibilidadResponse` completo tal como llega |
| `PantallaDisponibilidad` | `cargando` | boolean | `false` | mientras sea `true`, el boton de consultar esta deshabilitado |
| `PantallaDisponibilidad` | `error` | `{ codigo, mensaje }` o `null` | `null` | se pinta con `MensajeError` |
| `PantallaNuevaReserva` | `enviando` | boolean | `false` | mientras sea `true`, el boton de confirmar esta deshabilitado (HU-02) |
| `PantallaNuevaReserva` | `error` | `{ codigo, mensaje }` o `null` | `null` | se pinta con `MensajeError` |
| `PantallaMisReservas` | `reservas` | arreglo de objetos | `[]` | en el orden recibido; **no** se reordena (D-09 de la spec 04) |
| `PantallaMisReservas` | `cargando` | boolean | `true` | aviso de carga |
| `PantallaMisReservas` | `error` | `{ codigo, mensaje }` o `null` | `null` | se pinta con `MensajeError` |
| `PantallaMisReservas` | `cancelacionPendiente` | number o `null` | `null` | el `id` de la reserva a confirmar (P-06, D-12) |
| `PantallaMisReservas` | `cancelandoId` | number o `null` | `null` | mientras no sea `null`, el boton de esa fila esta deshabilitado |

### 4.3 Props recibidas y como se usan

| Prop | Tipo | Uso | Restriccion |
|---|---|---|---|
| `usuario` | objeto `{ usuarioId, nombre, rol }` | solo texto en pantalla | no se copia al estado; no decide que se muestra |
| `token` | string | `Authorization: Bearer <token>` en cada llamada | se lee en cada render; **nunca** se guarda en un modulo ni en almacenamiento (D-05) |
| `apiBaseUrl` | string `"/api"` | prefijo de las cinco rutas | no se concatena con ninguna URL absoluta |
| `onLogout` | funcion | se invoca ante un `401 NO_AUTENTICADO` | el remote no pinta pantalla de inicio de sesion |

El `rol` llega y **no** cambia el comportamiento del remote: un `ADMIN` ve exactamente las
mismas tres pantallas que un `USUARIO`, porque puede reservar y tiene historial propio (D-08 de
la spec 04, C-2 de la spec 06). El reparto real lo aplica cada microservicio con el token.

### 4.4 Campos que se reciben y no se usan para decidir

`GET /api/canchas` devuelve `activa`. El remote lo recibe y **no** decide nada con el: el
filtrado por rol ya lo hizo `ms-canchas` (un `USUARIO` solo recibe las `activa = true`), y para
un `ADMIN` una cancha inactiva se consulta igual y responde con todos los bloques en
`disponible = false` (D-05 de la spec 04). Duplicar aqui esa decision seria reimplementar una
regla del servidor.

## 5. Payloads y validaciones

Equivalente de los DTOs. El remote no define esquemas de servidor: valida en el cliente **solo
lo que evita una llamada inutil**, y deja la validacion de verdad a `ms-reservas`, que responde
`400 DATOS_INVALIDOS` con su `mensaje` listo para mostrar (mismo criterio que D-05 de la spec 06).

### 5.1 `PeticionDisponibilidad` — parametros de `GET /api/reservas/disponibilidad`

| Parametro | Tipo | Obligatorio | Validacion de cliente | Validacion del servidor |
|---|---|---|---|---|
| `canchaId` | number | si | no se consulta si esta vacio | `400 DATOS_INVALIDOS` si falta o no es numero; `404` si la cancha no existe |
| `fecha` | string `AAAA-MM-DD` | si | no se consulta si esta vacia | `400 DATOS_INVALIDOS` si el formato es invalido |

El cliente **no** valida que la fecha sea futura: consultar el pasado es legitimo (P-02, D-03 de
la spec 04).

### 5.2 `PeticionReserva` — cuerpo de `POST /api/reservas`

Cuerpo exacto de tres campos (D-11 de la spec 04):

```json
{ "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00" }
```

| Campo | Tipo | Obligatorio | Validacion de cliente | Validacion del servidor |
|---|---|---|---|---|
| `canchaId` | number | si | viene de `reservaPendiente`, nunca escrito a mano | `404 NO_ENCONTRADO` si no existe o esta inactiva |
| `fecha` | string `AAAA-MM-DD` | si | viene de `reservaPendiente` | `400 DATOS_INVALIDOS` por formato o por fecha pasada |
| `horaInicio` | string `HH:mm` | si | viene del bloque elegido, con minutos `00` por construccion | `400` si no es hora en punto o no cabe en el horario; `409 BLOQUE_OCUPADO`; `409 LIMITE_RESERVAS` |

Campos que **no** se envian nunca: `horaFin` (lo calcula el servicio), `usuarioId` (sale del
claim `sub` del token), `id` y `estado` (los fija `ms-reservas`).

### 5.3 `DisponibilidadResponse` — respuesta que se consume

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

| Campo | Uso en el remote |
|---|---|
| `canchaId` | se compara con la cancha elegida; se muestra su `nombre` desde el catalogo |
| `fecha` | encabezado de la grilla |
| `horaApertura` / `horaCierre` | encabezado del horario de atencion; nunca un rango fijo en codigo |
| `bloques[].horaInicio` / `horaFin` | etiqueta de cada bloque |
| `bloques[].disponible` | `true` -> bloque libre y seleccionable; `false` -> ocupado y no seleccionable |

El remote pinta `bloques` **en el orden recibido** y no lo recalcula ni lo completa (HU-01).

### 5.4 `ReservaResponse` — respuesta de crear, listar y cancelar

```json
{ "id": 7, "usuarioId": 2, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00", "horaFin": "10:00", "estado": "CONFIRMADA" }
```

| Campo | Uso en el remote |
|---|---|
| `id` | clave de la fila y ruta `PATCH /api/reservas/{id}/cancelacion` |
| `usuarioId` | se recibe; en `/mias` siempre es el propio y no se muestra como columna |
| `canchaId` | se resuelve a `nombre` y `deporte` con el catalogo (HU-04) |
| `fecha`, `horaInicio`, `horaFin` | columnas del listado, tal como llegan |
| `estado` | columna del listado y **unica** condicion para ofrecer cancelar (§6.3) |

`estado` llega ya resuelto, `FINALIZADA` incluida (D-02 de la spec 04): el remote no compara
fechas para deducirlo.

### 5.5 `CanchaResponse` — elemento de `GET /api/canchas`

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00", "activa": true }
```

Se usa para el filtro por `deporte`, el selector de cancha y la resolucion de `canchaId` a
`nombre`. `activa` se recibe y no decide nada (§4.4).

### 5.6 `PeticionCancelacion` — cuerpo de `PATCH /api/reservas/{id}/cancelacion`

**Sin cuerpo.** El contrato no declara ningun campo de entrada y la ruta ya expresa la operacion
(HU-05 de la spec 04). El remote envia el `PATCH` sin `body` y sin `Content-Type`.

### 5.7 `ErrorResponse` — forma unica de error

```json
{ "codigo": "BLOQUE_OCUPADO", "mensaje": "El bloque horario ya esta reservado" }
```

`clienteApi` devuelve siempre esta forma, incluso cuando la respuesta no la trae (proxy sin
destino, red cortada): en ese caso sintetiza `{ codigo: "ERROR_INTERNO", mensaje: ... }` (D-04
de la spec 06, mismo criterio aqui). Los componentes reciben una sola forma de error que pintar.

### 5.8 Props recibidas del shell

| Prop | Tipo | Valor |
|---|---|---|
| `usuario` | objeto | `{ usuarioId, nombre, rol }`, exactamente esos tres campos |
| `token` | string | el `token` de la sesion, sin `Bearer ` |
| `apiBaseUrl` | string | literal `"/api"` |
| `onLogout` | funcion | la del shell (HU-04 de la spec 06) |

El remote **no** declara ni consume ninguna prop mas, y no valida su presencia con una libreria
de tipos: sin TypeScript y sin `prop-types`, el contrato se respeta por diseño y se verifica en
el navegador.

## 6. Rutas, modulo expuesto y vistas

### 6.1 Rutas HTTP que el remote consume

El remote **no expone** endpoints HTTP. Consume cinco.

| Verbo | Ruta | Rol requerido | `Authorization` | Respuestas | Pantalla |
|---|---|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | si | 200, 401 | Disponibilidad, Mis reservas |
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | ADMIN, USUARIO | si | 200, 400, 401, 404 | Disponibilidad |
| POST | `/api/reservas` | USUARIO (y ADMIN, D-08 de la spec 04) | si | 201, 400, 401, 404, 409 | Nueva reserva |
| GET | `/api/reservas/mias` | USUARIO (y ADMIN, D-08) | si | 200, 401 | Mis reservas |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | si | 200, 401, 403, 404, 409 | Mis reservas |

Las cinco llevan `Authorization: Bearer <token>` con el `token` de la prop. Ninguna es publica.

Rutas que el remote **no** llama, y por que: `GET /api/reservas` (listado global de `ADMIN`, es
de `mf-administracion`), todo `/api/usuarios` (sesion y gestion de usuarios: shell y
Administracion), todo `/api/reportes` (`mf-reportes`) y las rutas de escritura de `/api/canchas`
(RN-07, Administracion).

### 6.2 Modulo expuesto

| Clave | Archivo | Firma |
|---|---|---|
| `./ReservasApp` | `src/ReservasApp.jsx` | componente de React que recibe `{ usuario, token, apiBaseUrl, onLogout }` |

El modulo expuesto es un **componente**, no un `createRoot`: el shell lo monta dentro de su
propio arbol (D-01).

### 6.3 Vistas internas

| Vista | Componente | Como se llega | Condicion |
|---|---|---|---|
| `disponibilidad` | `PantallaDisponibilidad` + `GrillaBloques` | vista inicial y opcion de la navegacion interna | siempre disponible |
| `nuevaReserva` | `PantallaNuevaReserva` | **solo** eligiendo un bloque con `disponible === true` en la grilla (P-01) | `reservaPendiente !== null` |
| `misReservas` | `PantallaMisReservas` + `FilaReserva` | opcion de la navegacion interna | siempre disponible |

`NavegacionInterna` ofrece **dos** opciones, Disponibilidad y Mis reservas: "Nueva reserva" no
aparece ahi porque solo se llega desde un bloque libre (D-17). La navegacion interna no cambia la
URL y no usa enrutador (P-05 de la spec 06).

Regla de cancelable, que vive en un solo punto:

| `estado` recibido | Se ofrece cancelar |
|---|---|
| `CONFIRMADA` | si |
| `CANCELADA` | no |
| `FINALIZADA` | no |

## 7. Codigos HTTP recibidos y comportamiento

Equivalente de la tabla de excepciones. En un microservicio la traduccion va de excepcion a
codigo; aqui va de **codigo recibido a comportamiento**, porque el remote es el cliente.

| Situacion | HTTP | `codigo` | Que hace el remote |
|---|---|---|---|
| Catalogo, disponibilidad o listado obtenidos | 200 | — | pinta los datos tal como llegan |
| Reserva creada | 201 | — | muestra el aviso de exito, vuelve a la grilla y reconsulta la disponibilidad de esa cancha y fecha |
| Reserva cancelada | 200 | — | muestra el aviso y recarga `GET /api/reservas/mias` |
| Entrada invalida, fecha pasada, bloque fuera del horario | 400 | `DATOS_INVALIDOS` | muestra `mensaje` junto al formulario; no borra la seleccion |
| Token vencido o invalido | 401 | `NO_AUTENTICADO` | invoca `onLogout()`; no pinta pantalla de sesion (D-13) |
| Sin permiso | 403 | `SIN_PERMISO` | muestra `mensaje`; no deberia ocurrir con reservas propias |
| Cancha inexistente o inactiva, reserva inexistente | 404 | `NO_ENCONTRADO` | muestra `mensaje` y refresca la vista afectada |
| Bloque ya reservado o bajo mantenimiento | 409 | `BLOQUE_OCUPADO` | muestra `mensaje` en la pantalla de nueva reserva y marca la grilla para reconsulta; el usuario vuelve con "Volver a la grilla", que ya la trae reconsultada y con el bloque ocupado |
| Limite de reservas activas alcanzado | 409 | `LIMITE_RESERVAS` | muestra `mensaje`; no deshabilita nada por adelantado (RN-06) |
| Reserva ya ocurrida | 409 | `RESERVA_PASADA` | muestra `mensaje` y recarga el listado |
| Reserva que ya no esta `CONFIRMADA` | 409 | `RESERVA_NO_CANCELABLE` | muestra `mensaje` y recarga el listado |
| Fallo de `ms-canchas` visto por `ms-reservas`, o error no previsto | 500 | `ERROR_INTERNO` | muestra `mensaje` y ofrece reintentar; **no** reintenta solo (D-14) |
| Proxy sin destino, microservicio caido o red cortada | sin respuesta o 502/504 | `ERROR_INTERNO` sintetizado | muestra "No se pudo contactar al servicio" y ofrece reintentar |

La fila del `409 BLOQUE_OCUPADO` se corrigio el 24/08/2026, al implementar T5. Decia "muestra
`mensaje`, vuelve a la grilla y la reconsulta", y las dos cosas no caben juntas: el mensaje se
pinta en la pantalla que hizo la llamada, asi que volver de inmediato lo haria desaparecer antes
de que el usuario alcance a leerlo, y no hay ningun campo del modelo (§4.1) por el que ese
mensaje viaje hasta la grilla. El aviso se queda donde el usuario esta mirando y la reconsulta se
adelanta con el contador `consultaGrilla.refresco`, de modo que el retorno —a un clic, con el
boton de "Volver a la grilla" que P-01 ya exige— muestra el bloque ya ocupado. Lo que HU-02 pide
se cumple igual: se ve el `mensaje` y se refresca la grilla.

El `401` es el unico codigo con un efecto global: cierra la sesion del sistema entero llamando
`onLogout()`. Todos los demas son locales a la pantalla que hizo la llamada.

Nota operativa de la bitacora, que explica un `500` que **no** es un defecto: tras reiniciar
`ms-canchas`, las primeras peticiones de `ms-reservas` pueden fallar por conexiones cacheadas
muertas, y con la politica "sin reintentos" de D-06 de la spec 04 ese `500` llega al cliente. En
la demo hay que repetir la consulta una o dos veces.

## 8. Componentes y flujos

### 8.1 Responsabilidad de cada componente

| Componente | Responsabilidad | No hace |
|---|---|---|
| `index.js` | `import("./bootstrap")` y nada mas (`CLAUDE.md` §3) | ningun render |
| `bootstrap.jsx` | `createRoot` y render del **aviso estatico** de que este microfrontend se usa desde el shell (P-04, D-02) | no monta `ReservasApp`, no llama a la API, no inventa un token |
| `ReservasApp.jsx` | modulo expuesto; dueño de `vista`, `canchas`, `reservaPendiente` y `avisoExito`; pide el catalogo una vez; envuelve las llamadas para detectar el `401` (D-07, D-13) | ningun `fetch` directo; no lee almacenamiento del navegador |
| `NavegacionInterna` | dos opciones: Disponibilidad y Mis reservas (D-17) | no monta pantallas; no muestra cabecera ni cierre de sesion |
| `PantallaDisponibilidad` | filtro por `deporte`, selector de cancha, fecha con hoy por defecto, consulta y estado de carga y error | no crea reservas; no recalcula `disponible` |
| `GrillaBloques` | pinta los `bloques` en el orden recibido y avisa del bloque elegido | no llama a la API; no permite elegir un bloque ocupado |
| `PantallaNuevaReserva` | muestra cancha, fecha y bloque precargados; confirma o vuelve (P-01) | no permite editar los datos; no consulta disponibilidad |
| `PantallaMisReservas` | pide `GET /api/reservas/mias`, pinta el listado completo sin filtros y coordina la cancelacion | no reordena; no recalcula `FINALIZADA` |
| `FilaReserva` | una reserva con su cancha resuelta y su boton de cancelar si `estado === "CONFIRMADA"` | no llama a la API |
| `ConfirmacionCancelacion` | paso de confirmacion con cancha, fecha y bloque a la vista (P-06) | no llama a la API: devuelve la respuesta del usuario |
| `MensajeError` | pinta `{ codigo, mensaje }` | no interpreta el codigo ni lo traduce |
| `clienteApi` | `fetch` con `apiBaseUrl` + ruta relativa, `Authorization: Bearer <token>`, normalizacion del error a `{ codigo, mensaje }` | no conoce React; no decide que hacer con el `401` |
| `canchasApi` | `listarCanchas(token)` | no filtra por deporte: eso es de la pantalla |
| `reservasApi` | `consultarDisponibilidad`, `crearReserva`, `listarMisReservas`, `cancelarReserva` | no interpreta codigos de error |

### 8.2 Flujos

**Montaje del remote.** El shell entra al modulo Reservas -> descarga
`http://localhost:3001/remoteEntry.js` -> monta `<ReservasApp usuario token apiBaseUrl onLogout />`
-> `ReservasApp` pide `GET /api/canchas` una sola vez y pinta `PantallaDisponibilidad` con la
fecha de hoy.

**Consulta de disponibilidad (HU-01).** El usuario elige `deporte` (o "Todos"), cancha y fecha ->
pulsa consultar -> `reservasApi.consultarDisponibilidad` -> `200` -> `GrillaBloques` pinta los
bloques. El filtro por `deporte` no hace ninguna llamada: filtra `canchas` en memoria (D-08).

**Nueva reserva (HU-02, P-01).** Clic en un bloque con `disponible === true` ->
`reservaPendiente = { canchaId, fecha, horaInicio, horaFin }` y `vista = "nuevaReserva"` ->
`PantallaNuevaReserva` muestra los datos precargados -> confirmar -> `POST /api/reservas` ->
`201` -> `avisoExito`, `reservaPendiente = null`, `vista = "disponibilidad"` y reconsulta de la
grilla, donde el bloque ya sale ocupado. El boton "Volver a la grilla" hace lo mismo sin llamar
a la API.

**Mis reservas (HU-03, HU-04).** `vista = "misReservas"` -> `GET /api/reservas/mias` -> cada fila
resuelve su `canchaId` contra el mapa de `canchas`; si no esta, muestra el `canchaId` (HU-04).

**Cancelacion (HU-05, P-06).** Clic en cancelar -> `cancelacionPendiente = id` ->
`ConfirmacionCancelacion` muestra cancha, fecha y bloque -> si el usuario confirma,
`PATCH /api/reservas/{id}/cancelacion` sin cuerpo -> `200` -> aviso y recarga del listado. Si
rechaza, `cancelacionPendiente = null` y no se llama a nada.

**Token vencido (HU-06).** Cualquier llamada responde `401 NO_AUTENTICADO` -> el envoltorio de
`ReservasApp` invoca `onLogout()` -> el shell borra la sesion y desmonta el remote. El remote no
pinta nada mas: ya no esta en el arbol (D-13).

**Remote abierto suelto (P-04).** `http://localhost:3001` sirve el `index.html` y `bootstrap.jsx`
pinta el aviso de que este microfrontend se usa desde el shell. No hay grilla, no hay llamadas y
**no es un defecto** (D-02).

## 9. Servicio `mf-reservas` en `docker-compose.yml`

Mismo patron del servicio `shell`, con el puerto y el volumen cambiados (P-07).

| Aspecto | Valor | Motivo |
|---|---|---|
| Imagen | `node:20-alpine` | `CLAUDE.md` §1; sin Node en el host |
| Comando | `sh -c "npm install && npx webpack serve --mode development"` | D-03 de la spec 06: `webpack serve` necesita el codigo vivo |
| `working_dir` | `/app` | — |
| Volumenes | `./frontend/mf-reservas:/app` mas volumen anonimo `mf_reservas_node_modules` en `/app/node_modules` | sin el volumen anonimo, el `node_modules` inexistente del host tapa el del contenedor |
| Puertos | `3001:3001` | contrato y P-07 |
| `depends_on` | `ms-canchas` y `ms-reservas`, con `condition: service_started` | los dos que el remote consume (P-07) |
| Sin `build` | no hay `Dockerfile` en esta spec | D-03 de la spec 06 |
| `container_name` | `canchas-mf-reservas` | misma convencion que `canchas-shell` |

`service_started` y no `service_healthy`: ninguno de los dos microservicios declara
`healthcheck`, y el `devServer` arranca igual aunque un destino del proxy todavia no responda.

El servicio `shell` **no se modifica**: no lleva `depends_on` de este remote (P-08). El
`remoteEntry.js` lo descarga el navegador al entrar al modulo, y si el remote esta caido el
`BordeError` del shell ya lo maneja.

## 10. Verificacion prevista (para `tasks.md`, no se ejecuta aqui)

Comandos en PowerShell desde la raiz, con `curl.exe` y solo Docker (`CLAUDE.md` §1). El orden es
el que fijo P-09:

1. `docker compose up -d mf-reservas` y `docker compose logs --tail=50 mf-reservas` hasta ver el
   `compiled successfully`.
2. **Primera comprobacion:** `curl.exe http://localhost:3001/remoteEntry.js` responde `200`. Si
   no, el problema esta en el remote y no en la integracion (P-09).
3. `curl.exe http://localhost:3001` responde el HTML con el aviso estatico de P-04.
4. Recorrido en el navegador: iniciar sesion en `http://localhost:3000`, entrar a Reservas,
   consultar disponibilidad de una cancha, crear una reserva en un bloque libre, verla en Mis
   reservas y cancelarla con su paso de confirmacion.
5. Casos de error del recorrido: reservar un bloque ocupado (`409 BLOQUE_OCUPADO`), reservar una
   fecha pasada (`400 DATOS_INVALIDOS`) y cancelar dos veces la misma reserva
   (`409 RESERVA_NO_CANCELABLE`).
6. Consola del navegador sin errores de React duplicado ni de `hooks` invalidos (HU-07).

Un `compiled successfully` **no** basta: la bitacora ya registro que en un microfrontend el log
verifica que el codigo compila, no que funcione (T5 de la spec 06).

## 11. Aislamiento de datos

- El remote **no tiene base de datos** y no ejecuta ni una consulta SQL. No puede leer tablas de
  ningun microservicio, porque no tiene acceso a ninguna.
- Su unico acceso a datos son las cinco rutas HTTP de §6.1, dos de `ms-canchas` y `ms-reservas`
  segun su dueño: `GET /api/canchas` es de `ms-canchas`; las cuatro de `/api/reservas` son de
  `ms-reservas`.
- El cruce entre una reserva y su cancha se hace **en el navegador**, uniendo `canchaId` con el
  catalogo que devolvio `ms-canchas` (HU-04). Nadie consulta `canchas_db` desde `reservas_db` ni
  al contrario: eso ya lo resolvio `ms-reservas` con su token de servicio (D-01 de la spec 04).
- El remote no reimplementa ninguna regla de negocio: no recalcula `disponible`, no deduce
  `FINALIZADA`, no cuenta reservas activas y no bloquea fechas pasadas (§4.4, P-02).
- El remote no accede a la sesion: el `token` llega por prop y no se guarda en ningun
  almacenamiento del navegador.

## 12. Decisiones de diseño

| ID | Decision | Alternativa descartada | Motivo |
|---|---|---|---|
| D-01 | El modulo expuesto `./ReservasApp` es un **componente** de React que recibe las cuatro props | Exponer `./bootstrap` con un `createRoot` propio | El shell lo monta dentro de su arbol y le pasa props; un `createRoot` crearia una raiz aparte, con su propia instancia de React, y `singleton: true` dejaria de tener sentido |
| D-02 | `bootstrap.jsx` pinta un **aviso estatico** de que el microfrontend se usa desde el shell | Montar `ReservasApp` con props de desarrollo y un token pegado a mano | P-04: sin shell no hay token, y un token de desarrollo obligaria a inventar de donde sale, que es lo que el contrato de props existe para evitar. El aviso deja claro que no es un fallo |
| D-03 | `devServer.headers` con `Access-Control-Allow-Origin: *` | Dejar los encabezados por omision | El `remoteEntry.js` y sus `chunk` los pide una pagina servida en `localhost:3000`: es otro origen, y sin el encabezado el navegador bloquea la descarga y el shell mostraria "Modulo no disponible" para siempre |
| D-04 | Capa `api/` propia del remote, con su `clienteApi` | Reutilizar el `clienteApi` del shell importandolo por Module Federation | El shell no declara `exposes` (contrato): no expone nada. Y `CLAUDE.md` §4 pide que cada microfrontend tenga su propia carpeta `api/` como unica capa de `fetch` |
| D-05 | El `token` y el `apiBaseUrl` viajan como **parametros** de cada funcion de `api/` | Guardarlos una vez en una variable del modulo `clienteApi` | El `token` es una prop que puede cambiar (HU-06); una variable de modulo se quedaria con el valor viejo tras un cambio de sesion y las llamadas seguirian con un token muerto |
| D-06 | El estado vive en `ReservasApp` y baja por props | `Context` de React o un gestor de estado | Tres pantallas y un arbol de dos niveles: el `Context` agrega indireccion sin quitar nada. Mismo criterio que D-08 de la spec 06 |
| D-07 | El catalogo de canchas se pide **una vez** al montar `ReservasApp` y se comparte entre las tres pantallas | Pedir `GET /api/canchas` en cada pantalla que lo necesita | Disponibilidad y Mis reservas lo usan para lo mismo (resolver `canchaId` a `nombre`); dos llamadas por navegacion no aportan datos nuevos y multiplican los puntos de fallo |
| D-08 | El filtro por `deporte` se aplica en el navegador sobre `canchas` | Pedir a `ms-canchas` un parametro de filtrado por deporte | `GET /api/canchas` no declara ningun parametro en el contrato congelado, y agregarlo obligaria a cambiar una spec cerrada por una comodidad de presentacion (P-03) |
| D-09 | La fecha de hoy se arma con los campos locales de `Date` (`getFullYear`, `getMonth`, `getDate`) en una funcion local de `PantallaDisponibilidad` | `new Date().toISOString().slice(0, 10)` | `toISOString` convierte a UTC: en Ecuador (UTC-5) toda hora local anterior a las 19:00 sigue dando el dia correcto, pero de 19:00 a 23:59 devuelve **el dia siguiente**, y la grilla abriria en una fecha que el usuario no eligio |
| D-10 | Elegir un bloque guarda `reservaPendiente` con `canchaId`, `fecha`, `horaInicio` y `horaFin`, y la pantalla de nueva reserva **no** vuelve a consultar | Que `PantallaNuevaReserva` reconsulte la disponibilidad para confirmar que el bloque sigue libre | Una reconsulta no da ninguna garantia: entre ella y el `POST` el bloque puede ocuparse igual. La garantia real es el `409 BLOQUE_OCUPADO` con su doble barrera en `ms-reservas` (HU-02 de la spec 04) |
| D-11 | Tras un `201`, el remote vuelve a la grilla y la reconsulta | Quedarse en la pantalla de nueva reserva con el aviso de exito | La grilla reconsultada es la prueba visible de RN-02 y RN-05 en la demo: el bloque recien reservado aparece ocupado sin que nadie toque nada |
| D-12 | La confirmacion de cancelacion es un componente propio (`ConfirmacionCancelacion`) dentro del layout | `window.confirm` del navegador | `window.confirm` bloquea el hilo, no se puede estilizar, no muestra los datos de la reserva y en la demo aparece con el aspecto del navegador, no del sistema |
| D-13 | El `401` se detecta en un unico envoltorio de `ReservasApp`, que llama `onLogout()` | Que cada pantalla decida que hacer con su `401` | Todas las rutas de este remote son autenticadas: un `401` siempre significa token vencido, nunca credenciales malas. Concentrarlo evita repetir la misma decision en cuatro lugares (a diferencia del shell, que si tenia los dos casos, D-07 de la spec 06) |
| D-14 | Ningun error se reintenta de forma automatica: se muestra y se ofrece reintentar | Reintentar el `500` una o dos veces en silencio | Es el mismo criterio de D-06 de la spec 04: reintentar oculta fallos reales de la dependencia. Y el `500` por conexiones muertas de la bitacora se resuelve con un clic del usuario, no con un reintento escondido |
| D-15 | Un solo `src/estilos.css`, importado desde **`ReservasApp.jsx`** | Importarlo desde `bootstrap.jsx`, como hace el shell | El shell nunca ejecuta el `bootstrap` del remote: monta directamente el modulo expuesto. Importar el CSS en `bootstrap.jsx` dejaria el remote sin estilos en su unico caso real de uso |
| D-16 | Las clases CSS del remote llevan el prefijo `mfr-` | Nombres genericos como `.grilla` o `.fila` | El CSS del shell y el del remote conviven en la misma pagina y son globales: sin prefijo, una clase con el mismo nombre en los dos proyectos se pisa y el estilo depende del orden de carga |
| D-17 | La navegacion interna ofrece **dos** opciones (Disponibilidad, Mis reservas); "Nueva reserva" no aparece | Ofrecer las tres pantallas del PDF §3.2 en la navegacion | A "Nueva reserva" solo se llega con un bloque elegido (P-01): una opcion de menu la abriria sin `reservaPendiente`, con un formulario vacio que el diseño no contempla y que violaria el invariante de §4.1 |
| D-18 | La consulta de disponibilidad se dispara con un boton | Consultar en cada cambio de cancha, deporte o fecha | Cambiar tres selectores generaria tres llamadas, dos de ellas con una seleccion a medias; y el boton deshabilitado durante la carga es lo que HU-01 pide para evitar consultas repetidas |
| D-19 | El remote no valida formatos: solo comprueba que la seleccion este completa antes de llamar | Replicar en el cliente las validaciones de `jakarta.validation` de `ms-reservas` | Duplicar reglas es garantizar que divergan. El `400 DATOS_INVALIDOS` ya trae un `mensaje` listo para mostrar. Mismo criterio que D-05 de la spec 06 |

## 13. Fuera de alcance de este diseño

- Los archivos de `mf-administracion` y `mf-reportes`: son las specs 08 y 09.
- Cualquier cambio en `frontend/shell`, en `backend/`, en `infra/postgres/` o en
  `docs/contratos/README.md`: este diseño no necesita ningun campo, ruta ni codigo de error
  nuevo, y P-08 ya cerro que el shell no se toca.
- El gateway Nginx y la eliminacion de los mapeos `8082`–`8085`: seccion 5 de integracion (§8 de
  la spec 06).
- Enrutador, gestor de estado global, libreria de UI, `prop-types`, TypeScript y cualquier
  dependencia fuera de las doce de §3.4.
- Pruebas automatizadas de frontend: el PDF §5 no las pide como entregable.
- El contenido de `tasks.md`: el orden de las tareas y sus comandos de verificacion se escriben
  en la compuerta siguiente.
