# Spec 09 — mf-reportes (remote de Module Federation) · design.md

Estado: **C2 — APROBADO** el 24/08/2026 ("Apruebo diseño de la spec 09").

Basado en `.claude/specs/09-mf-reportes/requirements.md`, **aprobado el 24/08/2026** ("Apruebo
requisitos de la spec 09"), con las diez decisiones P-01 a P-10 ya incorporadas.

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`, el `requirements.md` aprobado,
`.claude/specs/05-ms-reportes/design.md`, `.claude/specs/06-shell-module-federation/design.md`,
`.claude/specs/07-mf-reservas/design.md`, `.claude/specs/08-mf-administracion/design.md`,
`docker-compose.yml`, `frontend/shell/`, `frontend/mf-administracion/` y
`backend/ms-reportes/` ya entregados.

No se escribe código de producción en este paso (`CLAUDE.md` §6). Los fragmentos de este
documento son **configuración declarada**, no implementación.

## 0. Nota sobre las secciones pedidas

El comando de diseño pide cinco tablas pensadas para un microservicio. Este entregable es un
**microfrontend remote**: no tiene base de datos, no expone endpoints HTTP y no traduce
excepciones a códigos HTTP. Las secciones sin equivalente literal se sustituyen por su análogo
exacto, declarado aquí para que no parezca que se omitieron. Es la misma sustitución que los
`design.md` de las specs 06, 07 y 08 ya aplicaron y que el responsable aprobó.

| Pedido | Qué se entrega en su lugar | Sección |
|---|---|---|
| Modelo de datos (columnas y restricciones) | **Modelo de estado del remote**: campo, tipo, valor inicial, restricciones y origen. No hay ni una tabla de base de datos | §4 |
| DTOs con validaciones | **Parámetros de consulta y payloads de respuesta** con las validaciones de cliente campo por campo, más las props recibidas | §5 |
| Tabla de endpoints con rol requerido | **Tabla de rutas consumidas** con su rol requerido, más las vistas internas. El remote no **expone** ninguna ruta HTTP; lo único que expone es el módulo `./ReportesApp` | §6 |
| Tabla de excepciones a códigos HTTP | **Tabla de código HTTP recibido a comportamiento del remote**: la dirección es la inversa a la de un microservicio | §7 |
| Tabla de decisiones con alternativa descartada | Igual que en las ocho specs anteriores | §12 |

"Ninguna consulta puede acceder a tablas de otro microservicio" se cumple de forma absoluta: el
remote **no accede a ninguna base de datos**. Su único acceso a datos son **tres** rutas HTTP de
`ms-reportes` (§6.1). No hay SQL en esta spec (§11).

## 1. Verificación campo por campo contra `docs/contratos/README.md`

Todos los campos que este diseño usa existen en el contrato con **el mismo nombre**. No se
renombra, no se abrevia, no se traduce y no se agrega ninguno.

| Campo usado | Existe en el contrato | Tipo / valores del contrato | Dónde lo usa el remote |
|---|---|---|---|
| `desde` | sí | string `AAAA-MM-DD` | campo del selector de rango, parámetro de consulta y encabezado de la tabla consultada |
| `hasta` | sí | string `AAAA-MM-DD` | idem |
| `items` | sí | arreglo de objetos | filas de las tres tablas y entrada del indicador de demanda |
| `canchaId` | sí | number | clave de fila de las tres tablas |
| `nombre` | sí | string (cancha, dueño `ms-canchas`) | columna de las tres tablas y etiqueta del indicador de demanda |
| `deporte` | sí | `PADEL` \| `TENIS` \| `BASQUET` | columna de ocupación y de reservas; **no** existe en cancelaciones |
| `horasReservadas` | sí | number | columna de ocupación |
| `horasDisponibles` | sí | number | columna de ocupación |
| `porcentajeOcupacion` | sí | number 0-100 | columna de ocupación, ancho de la barra y métrica del indicador de demanda |
| `totalReservas` | sí | number | columna de reservas y métrica del indicador de demanda |
| `totalCancelaciones` | sí | number | columna de cancelaciones |
| `codigo` | sí | ver "Formato de error" | selecciona la reacción del remote (§7) |
| `mensaje` | sí | string | texto que se muestra tal cual |
| `token` | sí | string | prop; encabezado `Authorization: Bearer <token>` |
| `usuario` | sí | objeto `UsuarioResponse` | prop del contrato de props |
| `usuarioId` | sí | number | prop `usuario`; **no** se usa para decidir nada (§4.5) |
| `rol` | sí | `ADMIN` \| `USUARIO` | prop `usuario`; guardia de rol (§4.4) |
| `apiBaseUrl` | sí | `"/api"` | prefijo de toda ruta llamada |
| `onLogout` | sí | función | se invoca ante un `401` |

Contrato de props verificado tal como quedó el 23/08/2026 en `docs/contratos/README.md` y
`CLAUDE.md` §5:

```jsx
<ReportesApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

Nombres de Module Federation verificados: `mfReportes`, módulo `./ReportesApp`, puerto 3003,
exactamente como el shell ya los declara en su `webpack.config.js` y en `ContenedorRemoto.jsx`.

Rutas verificadas contra "Rutas REST congeladas": `GET /api/reportes/ocupacion?desde&hasta`,
`GET /api/reportes/reservas?desde&hasta`, `GET /api/reportes/cancelaciones?desde&hasta`.

Payloads verificados contra "Reportes — envoltura común": `ReporteOcupacionResponse`,
`ReporteReservasResponse` y `ReporteCancelacionesResponse`, los tres con la forma
`{ desde, hasta, items }`, y contra los `record` ya implementados en
`backend/ms-reportes/src/main/java/ec/ups/dae/reportes/dto/`.

**Ningún nombre discrepa.** No hay nada que detener por este motivo.

Tres aclaraciones de nombres que **no** son discrepancias:

- `nombre` aparece dos veces en el contrato, como nombre de cancha (`ms-canchas`) y como nombre
  de usuario (`ms-usuarios`). Este remote usa **solo** el de cancha: no consume `/api/usuarios`
  y no muestra ningún usuario.
- Las filas de `ReporteCancelacionesResponse` **no llevan `deporte`**. No es un campo que falte:
  el contrato lo declara así y el remote no lo completa desde otra llamada (HU-04).
- `desde` y `hasta` son a la vez parámetros de consulta y campos de la respuesta. Son el mismo
  nombre para el mismo concepto y se usan tal cual en ambos sentidos: el remote muestra junto a
  la tabla los que **devolvió la respuesta**, no los que están escritos en los campos (HU-10).

Campos del contrato que este remote **no** usa en ninguna pantalla: `id`, `estado`, `fecha`,
`horaInicio`, `horaFin`, `horaApertura`, `horaCierre`, `activa`, `bloqueoId`, `motivo`,
`bloques`, `disponible`, `email`, `password` y `activo`. Ninguno aparece en los tres payloads de
reportes.

## 2. Estructura de archivos

Sigue `CLAUDE.md` §4 para microfrontends. Todo lo que se crea vive en `frontend/mf-reportes`,
con la única excepción del servicio en `docker-compose.yml` (E-11).

```
frontend/mf-reportes/
  package.json
  webpack.config.js
  .babelrc
  public/index.html
  src/index.js                                 # solo import("./bootstrap")
  src/bootstrap.jsx                            # aviso estatico para localhost:3003 (D-02)
  src/ReportesApp.jsx                          # modulo expuesto; vista, rango, consulta y envoltorio del 401
  src/estilos.css                              # CSS plano, unico archivo de estilos
  src/api/clienteApi.js                        # unica pieza que llama fetch; solo GET (D-04)
  src/api/reportesApi.js                       # ocupacion, reservas, cancelaciones
  src/components/NavegacionInterna.jsx         # Ocupacion / Reservas / Cancelaciones (HU-10)
  src/components/SelectorRango.jsx             # campos desde y hasta mas el boton consultar (HU-01)
  src/components/PantallaOcupacion.jsx         # tabla, barra e indicador (HU-02, HU-05)
  src/components/PantallaReservas.jsx          # tabla e indicador (HU-03, HU-05)
  src/components/PantallaCancelaciones.jsx     # tabla, sin indicador (HU-04)
  src/components/IndicadorDemanda.jsx          # mayor y menor de una metrica (HU-05)
  src/components/BarraPorcentaje.jsx           # barra CSS de porcentajeOcupacion (P-09)
  src/components/MensajeError.jsx              # pinta { codigo, mensaje }
```

`src/api/` es la **única** capa que llama `fetch` (`CLAUDE.md` §4). Ningún componente lo hace por
su cuenta.

No hay carpeta `mapper/` ni módulo de utilidades: el remote no transforma datos, los muestra tal
como llegan. `CLAUDE.md` §3 prohíbe las clases `Util` genéricas y aquí no se crea ninguna. El
único cálculo local es el del `IndicadorDemanda`, que vive en su propio componente porque es una
decisión de presentación, no una transformación de datos (D-09).

No hay carpeta `sesion/`: el remote no tiene sesión propia (HU-06).

Prefijo de clases CSS: **`mfrep-`** (`mf-reservas` usa `mfr-` y `mf-administracion` usa `mfa-`).
Los estilos del shell y de los tres remotes conviven en el mismo documento cuando el módulo se
monta, y sin prefijo una regla de un remote repintaría a otro. Se elige `mfrep-` y no `mfr-`
porque `mfr-` ya es de `mf-reservas` (D-14).

## 3. Configuración de Webpack y Module Federation

### 3.1 `ModuleFederationPlugin` del remote

| Clave | Valor | Motivo |
|---|---|---|
| `name` | `"mfReportes"` | contrato congelado; es el nombre que el shell ya declara |
| `filename` | `"remoteEntry.js"` | es la URL que el shell pide: `http://localhost:3003/remoteEntry.js` |
| `exposes` | `{ "./ReportesApp": "./src/ReportesApp" }` | clave exacta del contrato |
| `shared.react` | `{ singleton: true, requiredVersion: paquete.dependencies.react }` | `CLAUDE.md` §3; sin `singleton` habría dos React y los `hooks` fallarían |
| `shared["react-dom"]` | `{ singleton: true, requiredVersion: paquete.dependencies["react-dom"] }` | idem |
| `output.publicPath` | `"auto"` | sin ella los chunk del remote se piden al origen del shell (`localhost:3000`) y la carga falla |
| `output.uniqueName` | `"mfReportes"` | evita colisión de los registros de Webpack entre el shell y los tres remotes |

La versión de React se lee de `package.json` en vez de escribirse a mano, igual que en el shell y
en los otros dos remotes: así `requiredVersion` no puede quedar desalineada de la dependencia
real.

### 3.2 `devServer` y `watchOptions`

| Clave | Valor | Motivo |
|---|---|---|
| `port` | `3003` | contrato |
| `host` | `"0.0.0.0"` | dentro del contenedor, escuchar solo en `localhost` deja al navegador del host sin acceso |
| `allowedHosts` | `"all"` | idem |
| `headers` | `{ "Access-Control-Allow-Origin": "*" }` | el `remoteEntry.js` lo pide una página servida en `localhost:3000`: es otro origen |
| `hot` | `true` | patrón del shell y de los otros dos remotes |
| `client.webSocketURL` | `"ws://localhost:3003/ws"` | el socket lo abre el navegador: su URL es la del host |
| `client.overlay.runtimeErrors` | `false` | el `BordeError` del shell ya muestra el fallo del remote; el overlay lo repetiría tapando la pantalla |
| `watchOptions` | `{ poll: 1000, ignored: /node_modules/ }` | el bind mount de Windows no entrega inotify (bitácora, T3 de la spec 06) |

### 3.3 Proxy de `/api` y quién lo usa de verdad

```
proxy: [
  { context: ["/api/reportes"], target: "http://ms-reportes:8080" }
]
```

**Una sola entrada**, a diferencia de los otros dos remotes: este consume una única ruta base
(§6.1). Destino: **nombre de contenedor**, porque el proxy lo ejecuta `webpack serve` dentro de la
red de Docker. La URL del `remoteEntry.js`, en cambio, es de **navegador**. Es la distinción que
la spec 06 resolvió en su P-02 y que aquí solo se repite.

Cuando el remote está montado dentro del shell, su código corre en el origen del shell y sus
rutas `/api/...` las proxya el `devServer` del **shell**: este `proxy` solo atiende si alguien
abre `http://localhost:3003` suelto. Se declara igualmente para que el remote sea desplegable por
separado (PDF §4.1). No se declaran las entradas de `/api/usuarios`, `/api/canchas` ni
`/api/reservas`: este remote no las llama y declararlas sugeriría lo contrario (D-15).

### 3.4 Dependencias de `package.json`

Las mismas versiones exactas de `frontend/mf-administracion`, sin agregar ni una librería:

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
versiones distintas hace que Webpack elija una y avise, o falle (HU-07).

**Ninguna librería de gráficos.** La barra de porcentaje es CSS plano (P-09, D-10).

## 4. Modelo de estado del remote

No hay tablas ni columnas: el "modelo de datos" de un microfrontend es su estado de React. Se
declara aquí campo por campo, con su tipo, su valor inicial y su restricción.

### 4.1 Estado de `ReportesApp.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `vista` | string | `"ocupacion"` | uno de `"ocupacion"`, `"reservas"`, `"cancelaciones"` | HU-10: reporte inicial Ocupación |
| `rango` | `{ desde, hasta }` | `{ desde: "", hasta: "" }` | dos cadenas `AAAA-MM-DD` o vacías; es lo que el administrador está escribiendo | HU-01, P-03 |
| `consulta` | `{ desde, hasta, intento }` o `null` | `null` | `null` significa "no hay nada consultado todavía"; `desde` y `hasta` son los del momento de pulsar consultar | HU-01, P-02 |
| `avisoRango` | string o `null` | `null` | aviso propio cuando falta `desde` o `hasta`; no es un error de la API | HU-01 |
| `cargando` | boolean | `false` | inicial `false`, no `true`: al montar no se llama a nada (P-02). Baja por props **al `SelectorRango` y a la pantalla activa** | HU-01 |

`cargando` vive aquí y no en la pantalla porque `ReportesApp` ya es dueño de `vista`, `rango` y
`consulta`, que son el ciclo completo de una consulta: el estado de carga es parte de ese ciclo.
La alternativa —guardarlo en la pantalla y subirlo por callback— haría que el dato viajara hacia
arriba solo para volver a bajar al selector, que es el camino largo para lo mismo (D-16).

Cuatro reglas de transición, que son el corazón de P-02 y P-03:

- Escribir en un campo cambia `rango` y **no** toca `consulta`: la tabla en pantalla sigue siendo
  la del rango con el que se consultó (HU-10).
- Pulsar consultar con los dos campos llenos copia `rango` a `consulta` e incrementa `intento`.
  El `intento` existe para que pulsar dos veces con el mismo rango vuelva a llamar: sin él, un
  reintento tras un `500` no dispararía nada porque `desde` y `hasta` no cambiaron (D-06).
- Cambiar de `vista` pone `consulta` en `null` y **conserva** `rango`. Así la pantalla nueva se
  monta sin llamar a nada y el rango escrito sigue ahí: exactamente lo que P-03 pidió (D-05).
- Cambiar de `vista` pone además `cargando` en `false`, **en el mismo paso** que `consulta` en
  `null`. Sin esto, salir de una pantalla mientras su consulta viaja dejaría el botón consultar
  deshabilitado por una carga de una pantalla ya desmontada, que nunca va a avisar que terminó:
  el módulo quedaría bloqueado hasta cambiar de vista otra vez (D-16).

`ReportesApp` aporta además dos piezas que no son estado:

- `ejecutar(operacion)`: envoltorio único del `401`. Devuelve `{ datos, error }` y nunca lanza.
  Ante un `401` invoca `onLogout()` y devuelve `{ datos: null, error: null }` a propósito: el
  shell ya está borrando la sesión y va a desmontar el remote; pintar un error sería un parpadeo
  sobre una pantalla que deja de existir. Mismo patrón que D-13 de la spec 07 y §4.1 de la 08.
- La **guardia de rol** de §4.4.

`ReportesApp` **no** guarda ningún reporte: los datos viven en la pantalla que los pidió (D-05).

### 4.2 Estado de las tres pantallas

Las tres tienen la misma forma de estado. Cambian solo el nombre del payload y las columnas.

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `reporte` | payload del reporte o `null` | `null` | `null` mientras no se haya consultado; se guarda la respuesta **completa**, con su `desde` y su `hasta` | HU-02, HU-03, HU-04 |
| `error` | `{ codigo, mensaje }` o `null` | `null` | error de la última consulta | HU-09 |

`cargando` **no** es estado de la pantalla: vive en `ReportesApp` (§4.1) y llega por prop. La
pantalla lo **enciende y apaga** llamando al `onCargando` que recibe —antes de lanzar la llamada
y al terminarla, con éxito o con error—, pero no guarda una copia propia: dos copias del mismo
dato se desincronizan en cuanto una de las dos se pierde al desmontar (D-16).

| Pantalla | Payload de `reporte` | Columnas de la tabla | Indicador |
|---|---|---|---|
| `PantallaOcupacion` | `ReporteOcupacionResponse` | `canchaId`, `nombre`, `deporte`, `horasReservadas`, `horasDisponibles`, `porcentajeOcupacion` + barra | sí, por `porcentajeOcupacion` |
| `PantallaReservas` | `ReporteReservasResponse` | `canchaId`, `nombre`, `deporte`, `totalReservas` | sí, por `totalReservas` |
| `PantallaCancelaciones` | `ReporteCancelacionesResponse` | `canchaId`, `nombre`, `totalCancelaciones` | **no** (P-05) |

Las tres pantallas reciben `consulta` por prop y llaman a su ruta cuando `consulta` deja de ser
`null` o cambia su `intento`. Con `consulta === null` no sale ni una llamada.

`reporte.items` se guarda **tal como llega** y no se reordena en el estado: el orden de la tabla
es el del catálogo que devolvió `ms-reportes` (D-10 de la spec 05). El indicador ordena una copia
al pintar, no el estado (D-09).

### 4.3 Props recibidas y cómo se usan

| Prop | Tipo | Uso |
|---|---|---|
| `usuario` | `{ usuarioId, nombre, rol }` | solo `rol`, para la guardia de §4.4 |
| `token` | string | se pasa **como parámetro** a cada llamada de `src/api/` (D-03) |
| `apiBaseUrl` | string `"/api"` | prefijo de toda ruta; nunca una URL absoluta ni un nombre de contenedor |
| `onLogout` | función | se invoca ante un `401`, desde `ejecutar` |

Ninguna otra prop se lee. El remote **no** lee `sessionStorage` ni `localStorage` (HU-06).

### 4.4 Guardia de rol (P-07)

`ReportesApp` evalúa `usuario.rol !== "ADMIN"` **antes** de montar la navegación y las pantallas.
Si se cumple, pinta un aviso propio de módulo no disponible y no monta nada más: no sale ni una
llamada.

Queda escrito en el código como **comportamiento defensivo, no como control de acceso**: el
control real es el token que `ms-reportes` valida en cada una de sus tres rutas, y el shell ya
restringe la opción del menú al `ADMIN`. Evaluarlo en la raíz garantiza que ninguna de las tres
pantallas pueda llamar por su cuenta (D-13).

### 4.5 Campos que se reciben y no se usan para decidir

- `usuario.usuarioId` y `usuario.nombre`: llegan en la prop y este remote no los necesita. No se
  muestran ni condicionan nada; los reportes no son por usuario.
- `reporte.desde` y `reporte.hasta`: se muestran junto a la tabla, pero **no** se comparan con
  `rango` para decidir nada. Son la etiqueta honesta de qué período está pintado (HU-10).

## 5. Parámetros de consulta, payloads y validaciones

### 5.1 Parámetros de consulta — las tres rutas

Las tres rutas reciben los mismos dos parámetros. No hay cuerpo: son `GET`.

| Parámetro | Tipo | Obligatorio | Formato | Validación en el cliente |
|---|---|---|---|---|
| `desde` | string | **sí** | `AAAA-MM-DD` | no vacío; el formato lo garantiza un `input` de tipo `date`, que entrega ese formato |
| `hasta` | string | **sí** | `AAAA-MM-DD` | idem |

Reglas de la validación de cliente, deliberadamente cortas:

- Si `desde` o `hasta` está vacío, **no se llama** y se pinta `avisoRango`. Es la única validación
  de cliente que bloquea la llamada (HU-01).
- **No** se compara `desde` con `hasta` en el navegador: si `desde` es posterior, la regla la
  aplica `ms-reportes` y devuelve `400 DATOS_INVALIDOS`, que el remote muestra tal cual. Duplicar
  la regla crearía dos fuentes de verdad (D-08, mismo criterio que D-10 de la spec 08).
- **No** hay rango máximo ni bloqueo de fechas futuras: el contrato declara que no existen.
- Las fechas se envían tal como están, sin reformatear ni convertir de zona horaria.

Composición de la URL: `apiBaseUrl + "/reportes/<reporte>?desde=" + desde + "&hasta=" + hasta`,
con los dos valores codificados para la URL. El `apiBaseUrl` es siempre la prop.

### 5.2 `ReporteOcupacionResponse` — respuesta de `GET /api/reportes/ocupacion`

| Campo | Tipo | Notas de uso |
|---|---|---|
| `desde` | string `AAAA-MM-DD` | se muestra como etiqueta del período consultado |
| `hasta` | string `AAAA-MM-DD` | idem |
| `items` | arreglo de objetos | puede venir vacío; `200` con arreglo vacío, nunca `404` |
| `items[].canchaId` | number | clave de fila |
| `items[].nombre` | string | columna |
| `items[].deporte` | `PADEL` \| `TENIS` \| `BASQUET` | columna, sin traducir ni abreviar |
| `items[].horasReservadas` | number | columna, tal cual |
| `items[].horasDisponibles` | number | columna, tal cual |
| `items[].porcentajeOcupacion` | number 0-100, un decimal | columna **y** ancho de la barra; no se recalcula |

La pantalla declara junto a la tabla que `horasDisponibles` es `(horaCierre − horaApertura)` por
el número de días del rango y que **no** descuenta los bloqueos de mantenimiento (HU-02).

### 5.3 `ReporteReservasResponse` — respuesta de `GET /api/reportes/reservas`

| Campo | Tipo | Notas de uso |
|---|---|---|
| `desde` / `hasta` | string `AAAA-MM-DD` | etiqueta del período |
| `items` | arreglo de objetos | puede venir vacío |
| `items[].canchaId` | number | clave de fila |
| `items[].nombre` | string | columna |
| `items[].deporte` | `PADEL` \| `TENIS` \| `BASQUET` | columna; **sin** total agrupado por deporte (P-04) |
| `items[].totalReservas` | number | columna y métrica del indicador |

La pantalla declara junto a la tabla que `totalReservas` cuenta `CONFIRMADA` y `FINALIZADA` y
excluye `CANCELADA` (HU-03).

### 5.4 `ReporteCancelacionesResponse` — respuesta de `GET /api/reportes/cancelaciones`

| Campo | Tipo | Notas de uso |
|---|---|---|
| `desde` / `hasta` | string `AAAA-MM-DD` | etiqueta del período |
| `items` | arreglo de objetos | puede venir vacío |
| `items[].canchaId` | number | clave de fila |
| `items[].nombre` | string | columna |
| `items[].totalCancelaciones` | number | columna |

**No hay `deporte`** en estas filas y el remote no lo completa (HU-04). La pantalla declara que el
rango filtra por la `fecha` de la reserva cancelada, no por la fecha en que se canceló.

### 5.5 `ErrorResponse` — forma única de error

| Campo | Tipo | Uso |
|---|---|---|
| `codigo` | string | **decide** la reacción del remote (§7) |
| `mensaje` | string | se muestra tal cual, sin reescribir ni traducir |

`clienteApi` normaliza a esta forma **toda** respuesta fallida, incluso la que no trae cuerpo (un
`502` del proxy, la red cortada): en ese caso sintetiza
`{ codigo: "ERROR_INTERNO", mensaje: "No se pudo contactar al servicio" }` con `estado = 0`, para
que los componentes conozcan una sola forma (D-04).

### 5.6 Props recibidas del shell

Ya declaradas en §4.3. Son exactamente las cuatro del contrato congelado, ni una más.

## 6. Rutas, módulo expuesto y vistas

### 6.1 Rutas HTTP que el remote consume

| Verbo | Ruta | Rol requerido | Respuestas declaradas | Pantalla | Función de `reportesApi` |
|---|---|---|---|---|---|
| GET | `/api/reportes/ocupacion?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | `PantallaOcupacion` | `obtenerOcupacion` |
| GET | `/api/reportes/reservas?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | `PantallaReservas` | `obtenerReservas` |
| GET | `/api/reportes/cancelaciones?desde&hasta` | ADMIN | 200, 400, 401, 403, 500 | `PantallaCancelaciones` | `obtenerCancelaciones` |

Las tres son de rol `ADMIN` y las tres son `GET`. El remote **no** llama a ninguna otra ruta del
sistema: ni `/api/usuarios`, ni `/api/canchas`, ni `/api/reservas`.

El remote **no emite ni acepta tokens `SERVICIO`**: envía el token del `ADMIN` que recibió por
prop. `ms-reportes` rechaza los tokens `SERVICIO` que le lleguen (P-11 de la spec 05), lo que
aquí no aplica porque el remote nunca emite uno.

### 6.2 Módulo expuesto

| Clave expuesta | Archivo | Props que recibe |
|---|---|---|
| `./ReportesApp` | `src/ReportesApp.jsx` | `usuario`, `token`, `apiBaseUrl`, `onLogout` |

Es lo **único** que el remote expone. No expone `clienteApi`, ni componentes sueltos, ni estilos:
un remote que exporte piezas internas invita al acoplamiento que la rúbrica §6 penaliza.

### 6.3 Vistas internas

No hay enrutador ni rutas de navegador: la vista activa es estado de React (P-05 de la spec 06).

| Valor de `vista` | Etiqueta del menú | Componente | Historia |
|---|---|---|---|
| `"ocupacion"` | Ocupación | `PantallaOcupacion` | HU-02, HU-05 |
| `"reservas"` | Reservas | `PantallaReservas` | HU-03, HU-05 |
| `"cancelaciones"` | Cancelaciones | `PantallaCancelaciones` | HU-04 |

El `SelectorRango` vive **fuera** del menú y por encima de las tres pantallas: es uno solo para
las tres (P-03).

## 7. Códigos HTTP recibidos y comportamiento

La dirección es la inversa a la de un microservicio: aquí no se traducen excepciones a códigos, se
traducen **códigos recibidos a comportamiento**.

| HTTP | `codigo` | Cuándo puede llegar | Comportamiento del remote |
|---|---|---|---|
| 200 | — | las tres consultas | guarda la respuesta completa en `reporte`, limpia `error` y pinta tabla, barra e indicador |
| 200 con `items` vacío | — | rango sin actividad, o catálogo vacío | aviso de reporte sin datos; **no** se pinta el indicador de demanda (HU-05) |
| 400 | `DATOS_INVALIDOS` | `desde` posterior a `hasta`, formato inválido, fecha inexistente | muestra `mensaje` junto al selector de rango; `reporte` **no** se borra: sigue viéndose lo último consultado |
| 401 | `NO_AUTENTICADO` | cualquiera | `ejecutar` invoca `onLogout()`; no se pinta error (§4.1) |
| 403 | `SIN_PERMISO` | rol distinto de `ADMIN`, o token `SERVICIO` | muestra `mensaje`; no debería ocurrir con la guardia de §4.4, pero se maneja |
| 500 | `ERROR_INTERNO` | `ms-canchas` o `ms-reservas` no respondieron a `ms-reportes` | muestra `mensaje` y deja reintentar pulsando consultar otra vez; **nunca** se pinta un reporte parcial (HU-09) |
| sin respuesta | `ERROR_INTERNO` (sintético, `estado = 0`) | `ms-reportes` caído, proxy sin destino, red cortada | aviso propio de fallo de comunicación y reintento; nunca un stacktrace |
| otro no declarado | lo que traiga el cuerpo, o el sintético | un `404` del proxy si la ruta no existe | se trata como error genérico: se muestra `codigo` y `mensaje` y se deja reintentar |

Reglas transversales:

- La reacción se elige por `codigo`, **nunca** por el texto del `mensaje` (HU-09).
- El `401` se detecta por el **estado HTTP**, que es lo que fija el contrato, no por el `codigo`.
- Un error nunca deja la pantalla en blanco: el menú interno y el selector de rango siguen
  funcionando (HU-09).
- Un error **no** borra el reporte anterior. Si la consulta nueva falla, sigue viéndose la tabla
  del rango anterior, con su propia etiqueta `desde`/`hasta`, y el error al lado. Borrarla dejaría
  al administrador sin nada, y la etiqueta impide confundir el período (D-07).

Códigos del contrato que este remote **no** puede recibir: `EMAIL_DUPLICADO`, `NOMBRE_DUPLICADO`,
`BLOQUEO_DUPLICADO`, `BLOQUE_OCUPADO`, `LIMITE_RESERVAS`, `RESERVA_PASADA`,
`RESERVA_NO_CANCELABLE` y `NO_ENCONTRADO`. Ninguna de las tres rutas los declara: son de
escritura o de recursos por identificador, y este remote no escribe nada.

## 8. Componentes y flujos

### 8.1 Responsabilidad de cada componente

| Componente | Responsabilidad | Qué **no** hace |
|---|---|---|
| `ReportesApp` | módulo expuesto; guarda `vista`, `rango`, `consulta`, `avisoRango` y `cargando`; envoltorio del `401`; guardia de rol | no llama a la API, no guarda reportes |
| `NavegacionInterna` | tres botones, uno por reporte; marca el activo | no dibuja cabecera, ni Inicio, ni cierre de sesión |
| `SelectorRango` | dos campos de fecha y el botón consultar; deshabilita el botón mientras se carga, con el `cargando` que **recibe por prop desde `ReportesApp`** (§4.1) | no guarda `cargando`, no valida reglas de negocio, no compara `desde` con `hasta` |
| `PantallaOcupacion` | pide su ruta, guarda su reporte, pinta tabla, barra e indicador; **enciende y apaga el `cargando` de `ReportesApp`** con el `onCargando` recibido | no guarda su propio `cargando`, no recalcula `porcentajeOcupacion`, no agrupa por deporte |
| `PantallaReservas` | pide su ruta, guarda su reporte, pinta tabla e indicador; enciende y apaga el `cargando` de `ReportesApp` | no guarda su propio `cargando`, no suma totales por deporte (P-04) |
| `PantallaCancelaciones` | pide su ruta, guarda su reporte, pinta tabla; enciende y apaga el `cargando` de `ReportesApp` | no guarda su propio `cargando`, no muestra `deporte`, no pinta indicador (P-05) |
| `IndicadorDemanda` | recibe `items` y el nombre de la métrica; calcula máximo y mínimo y lista **todas** las canchas empatadas | no inventa canchas, no mezcla métricas, no se pinta con `items` vacío |
| `BarraPorcentaje` | recibe `porcentajeOcupacion` y dibuja una barra cuyo ancho es ese valor sobre 100 | no reemplaza al número, no redondea |
| `MensajeError` | pinta `{ codigo, mensaje }` tal como llegó | no reescribe ni traduce el `mensaje` |
| `clienteApi` | única pieza con `fetch`; solo `GET`; normaliza errores | no interpreta el `401`: eso es de `ReportesApp` |
| `reportesApi` | compone las tres URL con sus parámetros y delega en `clienteApi` | no guarda estado, no cachea |

### 8.2 Flujos

**F-01 — Montaje del módulo.** El shell monta `./ReportesApp` con las cuatro props. Si el `rol` no
es `ADMIN`, aviso y fin (§4.4). Si lo es, se pintan `NavegacionInterna` (con Ocupación activa),
`SelectorRango` con los campos vacíos y `PantallaOcupacion` con `reporte = null`. **No sale
ninguna llamada** (P-02).

**F-02 — Primera consulta.** El administrador escribe `desde` y `hasta` y pulsa consultar. Si
falta uno, se pinta `avisoRango` y no se llama. Si están los dos, `consulta` toma el rango y el
`intento` sube; `PantallaOcupacion` lo detecta, enciende el `cargando` de `ReportesApp` con el
`onCargando` recibido, llama a `obtenerOcupacion` a
través de `ejecutar` y pinta el resultado. Solo se llama **la ruta de la pantalla visible**.

**F-03 — Cambio de reporte.** El administrador pulsa Cancelaciones. `vista` cambia,
`consulta` pasa a `null` y `rango` se conserva. `PantallaCancelaciones` se monta con
`reporte = null` y no llama a nada; el administrador pulsa consultar y se llama solo la ruta de
cancelaciones. Al volver a Ocupación, esa pantalla vuelve a montarse limpia: hay que consultar
otra vez (HU-10).

**F-04 — Cambio de rango sin consultar.** El administrador edita `hasta`. `rango` cambia,
`consulta` no. La tabla sigue mostrando los datos anteriores, etiquetados con el `desde` y el
`hasta` que **devolvió la respuesta**. Al pulsar consultar, se llama con el rango nuevo.

**F-05 — Reintento tras un fallo.** La consulta responde `500`. Se pinta el `mensaje` y se
conserva la tabla anterior si la había. El administrador pulsa consultar de nuevo: el `intento`
sube aunque `desde` y `hasta` no hayan cambiado, y la llamada se repite (D-06).

**F-06 — Sesión caída.** Cualquier consulta responde `401`. `ejecutar` invoca `onLogout()`; el
shell borra la sesión y desmonta el remote. No se pinta error (§4.1).

**F-07 — Reporte sin datos.** La respuesta es `200` con `items` vacío. Se pinta el aviso de
reporte sin datos, no se pinta el indicador de demanda y no se inventa ninguna cancha (HU-05).

**F-08 — Empate en el indicador.** Dos canchas comparten el máximo. `IndicadorDemanda` lista las
dos como canchas de mayor demanda (P-06). Con una sola cancha en `items`, esa misma es la mayor y
la menor, y así se muestra.

## 9. Servicio `mf-reportes` en `docker-compose.yml`

Mismo patrón que `shell`, `mf-reservas` y `mf-administracion`, único archivo tocado fuera de la
carpeta del remote.

| Clave | Valor | Motivo |
|---|---|---|
| `image` | `node:20-alpine` | `CLAUDE.md` §1: nada instalado en el host |
| `container_name` | `canchas-mf-reportes` | convención de los servicios existentes |
| `working_dir` | `/app` | idem |
| `command` | `sh -c "npm install && npx webpack serve --mode development"` | patrón de los tres frontends ya entregados |
| `volumes` | `./frontend/mf-reportes:/app` y `mf_reportes_node_modules:/app/node_modules` | el volumen anónimo evita que el `node_modules` inexistente del host tape el del contenedor |
| `ports` | `"3003:3003"` | contrato |
| `depends_on` | **solo** `ms-reportes`, con `condition: service_started` | P-08: el único microservicio que este remote consume |

Se agrega `mf_reportes_node_modules` a la sección `volumes` del archivo. El `shell` **no** declara
`depends_on` de este remote (P-08 de la spec 07).

## 10. Verificación prevista (para `tasks.md`, no se ejecuta aquí)

| Nivel | Comprobación |
|---|---|
| 1 | `curl.exe http://localhost:3003/remoteEntry.js` responde `200` |
| 2 | `curl.exe http://localhost:3001/remoteEntry.js` y `curl.exe http://localhost:3002/remoteEntry.js` siguen respondiendo `200`: el tercer remote no rompe a los dos anteriores |
| 3 | `docker compose logs --tail=50 mf-reportes` sin errores de compilación |
| 4 | Recorrido por navegador con un `ADMIN`: iniciar sesión en `http://localhost:3000`, entrar a Reportes, escribir un rango, pulsar consultar y ejercitar la pantalla de la tarea |
| 5 | Consola del navegador sin errores de React duplicado ni de `hooks` inválidos |

Un `compiled successfully` no prueba que la aplicación funcione (bitácora, T5 de la spec 06): toda
tarea con interacción exige el nivel 4.

Los reportes solo muestran números si hay reservas en el rango. Generarlas es parte del recorrido
de prueba, con `mf-reservas`: el seed no las trae y esta spec no lo toca.

## 11. Aislamiento de datos

- El remote **no tiene base de datos** y **no ejecuta SQL**. No hay ninguna consulta que pueda
  tocar la tabla de otro microservicio.
- Todo dato se pide a `ms-reportes`, que es el dueño de los tres reportes. El remote **no**
  consulta `canchas_db` ni `reservas_db`, ni directamente ni a través de `ms-canchas` o
  `ms-reservas`: si necesitara un dato que `items` no trae, no lo muestra (HU-09).
- El cruce entre canchas y reservas ya lo hizo `ms-reportes` por HTTP, con su capa `client`
  (`CLAUDE.md` §4). Este remote solo pinta el resultado.
- El remote no emite tokens `SERVICIO` y no propaga su token a ningún destino distinto del
  microservicio al que llama.

## 12. Decisiones de diseño

| ID | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| D-01 | El módulo expuesto es un **componente** que recibe las cuatro props | Exponer un `createRoot` que se monte solo en un `div` | El shell lo monta dentro de su propio árbol de React; un `createRoot` crearía un árbol aparte, rompería el `singleton` en la práctica y dejaría las props sin camino |
| D-02 | `bootstrap.jsx` pinta un **aviso estático** para quien abra `localhost:3003` | Montar `ReportesApp` con props de desarrollo y un token de prueba | Sin shell no hay `token` ni `usuario`; inventarlos es exactamente lo que el contrato de props existe para evitar (P-04 de la spec 07) |
| D-03 | El `token` viaja como **parámetro** de cada llamada | Guardarlo en una variable de módulo de `clienteApi` al montar | Es una prop que puede cambiar; una copia guardada se quedaría con el valor viejo tras un cambio de sesión |
| D-04 | `clienteApi.js` se **replica** desde `mf-administracion`, recortado a `GET` | Exponerlo como módulo federado desde otro remote, o copiar los cinco verbos | Un remote que dependa de otro deja de ser desplegable por separado (PDF §4.1). Y copiar `publicar`, `reemplazar`, `parchear` y `eliminar` dejaría en el archivo cuatro funciones muertas que sugieren que este módulo escribe algo: es de solo lectura (PDF §3.3.5) |
| D-05 | `ReportesApp` guarda `vista`, `rango` y `consulta`; los **reportes viven en cada pantalla**, y cambiar de vista pone `consulta` en `null` | Guardar los tres reportes en la raíz para conservarlos al cambiar de pantalla | Conservarlos obligaría a recordar con qué rango se pidió cada uno y a explicarlo en pantalla; con tres reportes independientes, montar limpio es más honesto y es lo que P-03 pidió: el rango se conserva, los datos no |
| D-06 | `consulta` lleva un contador `intento` que sube en cada pulsación | Disparar la llamada comparando solo `desde` y `hasta` | Sin el contador, reintentar tras un `500` con el mismo rango no dispararía nada: el efecto no vería ningún cambio y el botón parecería roto (F-05) |
| D-07 | Un error **no borra** el reporte anterior; se muestran los dos, con la etiqueta del período consultado | Vaciar la tabla en cuanto una consulta falla | Vaciarla deja al administrador sin nada por un fallo pasajero de `ms-reportes`. La etiqueta `desde`/`hasta` de la respuesta impide confundir el período que se está viendo |
| D-08 | La validación de cliente es **estructural**: los dos campos obligatorios y el tipo `date` del `input` | Comparar `desde` con `hasta` en el navegador y avisar antes de llamar | Duplicar la regla crea dos fuentes de verdad que se desincronizan. `ms-reportes` ya devuelve `400 DATOS_INVALIDOS` con su `mensaje`, y mostrarlo es más fiel que inventar un texto propio |
| D-09 | El indicador de demanda ordena una **copia** de `items` al pintar; el estado conserva el orden recibido | Ordenar `items` dentro del estado al recibir la respuesta | La tabla debe mostrar el orden del catálogo (D-10 de la spec 05); ordenar el estado cambiaría también la tabla y perdería el orden original sin poder recuperarlo |
| D-10 | La barra de porcentaje es un `div` con ancho en porcentaje, CSS plano | Un `<progress>`, un SVG dibujado a mano o una librería de gráficos | `CLAUDE.md` §3 prohíbe las librerías de UI; `<progress>` casi no se puede estilar entre navegadores y un SVG sería más código para el mismo rectángulo |
| D-11 | El indicador se calcula **solo con los `items` del reporte que se está viendo** | Consultar las tres rutas para calcular una demanda combinada | P-05 fijó dos métricas separadas; combinarlas inventaría un indicador que ni el contrato ni el PDF declaran, y obligaría a llamar rutas que P-02 dejó fuera |
| D-12 | Estado local por pantalla; sin `Context` ni gestor de estado global | Un `Context` con el rango y los tres reportes | Tres pantallas hermanas con un único dato compartido —el rango— no justifican un `Context`: bajarlo por props son dos niveles. `CLAUDE.md` §3 prohíbe dependencias que no exija Module Federation |
| D-13 | La guardia de rol se evalúa en `ReportesApp` **antes** de montar las pantallas | Comprobar el rol dentro de cada pantalla, o dejar que las tres rutas devuelvan `403` | Comprobarlo en la raíz garantiza que no sale ni una llamada (P-07); repetirlo en cada pantalla sería la misma condición escrita tres veces |
| D-14 | Prefijo de clases CSS `mfrep-` | `mfr-`, por simetría con `mfa-` de administración | `mfr-` ya es de `mf-reservas`: reutilizarlo repintaría ese remote, que es exactamente lo que el prefijo existe para evitar. Los tres remotes conviven en el mismo documento |
| D-15 | El `proxy` del `devServer` declara **solo** `/api/reportes` | Copiar las cuatro entradas de `mf-administracion` por simetría | Declarar destinos que el remote nunca llama sugiere un acoplamiento que no existe y, si algún día se rompiera el aislamiento, el proxy ya lo estaría permitiendo en silencio |
| D-16 | `cargando` **sube a `ReportesApp`** y baja por props al `SelectorRango` y a la pantalla activa; cambiar de vista lo pone en `false` junto con `consulta` en `null` | Guardarlo en cada pantalla y subirlo al selector con un callback `onCargandoCambio` | `ReportesApp` ya es dueño de `vista`, `rango` y `consulta`, que son el ciclo completo de una consulta: el estado de carga es parte de ese ciclo. Con la alternativa el dato vive abajo y sube por callback solo para volver a bajar al selector, que es el camino largo para lo mismo. Precisión acordada el 25/08/2026, tras T3, sobre un hueco que el diseño no cubría |

## 13. Fuera de alcance de este diseño

- `tasks.md` y cualquier archivo de código: se escriben tras aprobar esta compuerta, tarea por
  tarea (`CLAUDE.md` §6). P-10 ya fijó el reparto: una tarea por reporte, más andamiaje, capa
  `api` y Compose.
- Modificar el shell, `frontend/mf-reservas`, `frontend/mf-administracion`, `backend/`,
  `infra/postgres/` o `docs/contratos/README.md`: este diseño no necesita ningún campo, ruta ni
  código de error nuevo.
- El gateway Nginx y la eliminación de los mapeos `8082`–`8085`.
- Pruebas automatizadas de frontend, enrutador, gestor de estado global, librería de UI o de
  gráficos, TypeScript, i18n y tema oscuro.
- Todo lo listado en §9 del `requirements.md` aprobado: exportación, totales por deporte,
  indicador de demanda en cancelaciones, consumo de otras rutas y recálculo de indicadores.
