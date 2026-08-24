# Spec 06 — shell (host de Module Federation) · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 06").
Falta `tasks.md`: el codigo de produccion se escribe tarea por tarea, una a la vez, con su
comando de verificacion (`CLAUDE.md` §6).

Base: `requirements.md` de esta spec, **C1 aprobado el 23/08/2026**, con las decisiones P-01 a
P-09 y las correcciones C-1 y C-2 ya incorporadas.

Fuentes verificadas para este diseño: `CLAUDE.md` (§1, §3, §4, §5, §7),
`docs/contratos/README.md` (campos congelados, `LoginResponse`, "Formato de error", "Contrato
Module Federation"), `docker-compose.yml` y `requirements.md` de esta spec.

## 0. Nota sobre las secciones pedidas

El comando de diseño pide cinco tablas pensadas para un microservicio. Este entregable es un
**microfrontend host**: no tiene base de datos, no expone endpoints y no traduce excepciones a
codigos HTTP. Las tres secciones sin equivalente literal se sustituyen por su analogo exacto,
declarado aqui para que no parezca que se omitieron:

| Pedido | Que se entrega en su lugar | Seccion |
|---|---|---|
| Modelo de datos (columnas y restricciones) | **Modelo de estado del shell**: campos, tipo, origen, persistencia y restricciones. El shell no tiene tabla alguna | §4 |
| DTOs con validaciones | **Payloads de request y response** con las validaciones de cliente de cada campo | §5 |
| Tabla de endpoints con rol requerido | **Tabla de rutas consumidas** con su rol, mas los modulos que el shell monta por rol. El shell no **expone** ninguna ruta | §6 |
| Tabla de excepciones a codigos HTTP | **Tabla de codigo HTTP recibido a comportamiento del shell**: la direccion es la inversa a la de un microservicio | §7 |
| Tabla de decisiones con alternativa descartada | Igual que en las cinco specs anteriores | §12 |

"Ninguna consulta puede acceder a tablas de otro microservicio" se cumple de forma trivial y
absoluta: el shell **no accede a ninguna base de datos**, ni propia ni ajena. Su unico acceso a
datos son dos rutas HTTP publicas de `ms-usuarios` (§6.1). No hay SQL en esta spec.

## 1. Verificacion campo por campo contra `docs/contratos/README.md`

Todos los campos que este diseño usa existen en el contrato con **el mismo nombre**. No se
renombra, no se abrevia, no se agrega ninguno.

| Campo usado | Existe en el contrato | Tipo / valores del contrato | Donde lo usa el shell |
|---|---|---|---|
| `email` | si | string | `PeticionSesion`, `PeticionRegistro` |
| `password` | si | string — solo en request, NUNCA en respuesta | `PeticionSesion`, `PeticionRegistro` |
| `nombre` | si | string | `PeticionRegistro`, cabecera, prop `usuario` |
| `token` | si | string | sesion, encabezado `Authorization`, prop `token` |
| `usuario` | si | objeto `UsuarioResponse` | sesion y prop `usuario` |
| `usuarioId` | si | number | prop `usuario` |
| `rol` | si | `ADMIN` \| `USUARIO` (y `SERVICIO`, que nunca llega aqui) | menu y prop `usuario` |
| `activo` | si | boolean | se recibe y se ignora (§4.3) |
| `codigo` | si | ver tabla "Formato de error" | seleccion de mensaje (§7) |
| `mensaje` | si | string | texto que se muestra |
| `apiBaseUrl` | si | `"/api"` | prop hacia los remotes |
| `onLogout` | si | funcion | prop hacia los remotes |

Contrato de props verificado tal como quedo el 23/08/2026 en `docs/contratos/README.md` y
`CLAUDE.md` §5:

```jsx
<RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

Nombres de Module Federation verificados: `shell` (host, 3000), `mfReservas` / `./ReservasApp`
(3001), `mfAdministracion` / `./AdminApp` (3002), `mfReportes` / `./ReportesApp` (3003).

**Ningun nombre discrepa.** No hay nada que detener por este motivo.

## 2. Estructura de archivos

Sigue `CLAUDE.md` §4 para microfrontends. Todo lo que se crea vive en `frontend/shell`, con la
unica excepcion del servicio en `docker-compose.yml` (E-14).

```
frontend/shell/
  package.json
  webpack.config.js
  .babelrc
  public/index.html
  src/index.js                      # solo import("./bootstrap")
  src/bootstrap.jsx                 # createRoot y render de <App />
  src/App.jsx                       # estado de sesion y modulo activo
  src/estilos.css                   # CSS plano, unico archivo de estilos
  src/api/clienteApi.js             # unica pieza que llama fetch
  src/api/usuariosApi.js            # iniciarSesion y registrarUsuario
  src/sesion/almacenSesion.js       # leer/guardar/borrar en sessionStorage
  src/components/PantallaSesion.jsx     # inicio de sesion (HU-01)
  src/components/PantallaRegistro.jsx   # registro (HU-02)
  src/components/Cabecera.jsx           # nombre, rol y cerrar sesion (HU-04)
  src/components/MenuModulos.jsx        # menu filtrado por rol (HU-05)
  src/components/PantallaBienvenida.jsx # vista inicial (P-09)
  src/components/ContenedorRemoto.jsx   # React.lazy + Suspense por modulo (HU-06)
  src/components/BordeError.jsx         # borde de error del remote (HU-06)
  src/components/MensajeError.jsx       # bloque de error reutilizable
```

`src/api/` es la **unica** capa que llama `fetch` (`CLAUDE.md` §4). Ningun componente lo hace
por su cuenta. `src/sesion/` no es una capa nueva del estandar: es un solo modulo con las tres
funciones de `sessionStorage`, y se separa de `api/` porque no hace HTTP.

No hay carpeta `mapper/`: el `LoginResponse` se traduce a la prop `usuario` con una funcion de
tres campos dentro de `App.jsx` (D-09).

## 3. Configuracion de Webpack y Module Federation

### 3.1 `ModuleFederationPlugin` del host

| Opcion | Valor | Motivo |
|---|---|---|
| `name` | `"shell"` | contrato |
| `exposes` | **ausente** | el host no expone nada (contrato) |
| `remotes.mfReservas` | `"mfReservas@http://localhost:3001/remoteEntry.js"` | URL de **navegador** |
| `remotes.mfAdministracion` | `"mfAdministracion@http://localhost:3002/remoteEntry.js"` | URL de **navegador** |
| `remotes.mfReportes` | `"mfReportes@http://localhost:3003/remoteEntry.js"` | URL de **navegador** |
| `shared` | `react` y `react-dom` con `singleton: true` y `requiredVersion` de `package.json` | `CLAUDE.md` §3 |
| `output.publicPath` | `"auto"` | sin ella, el host pide los `chunk` del remote a su propio origen y la carga falla |
| `output.uniqueName` | `"shell"` | evita colisiones de runtime entre host y remotes |

### 3.2 `devServer` y `watchOptions`

`watchOptions` no vive dentro de `devServer`, sino al lado, en la raiz de la configuracion.
Es **obligatorio**, no un parche del shell: los tres remotes lo necesitan igual y sin el la
recarga en caliente no funciona en Windows (D-15).

| Opcion | Valor | Motivo |
|---|---|---|
| `watchOptions.poll` | `1000` | el bind mount de Windows no entrega eventos inotify al contenedor: sin sondeo, `webpack serve` compila al arrancar y despues nunca ve un archivo guardado |
| `watchOptions.ignored` | `/node_modules/` | sondear el `node_modules` instalado dentro del contenedor gasta CPU sin ninguna ganancia |
| `port` | `3000` | contrato |
| `host` | `"0.0.0.0"` | dentro del contenedor, escuchar solo en `localhost` deja al navegador del host sin acceso (advertencia del responsable, C1 §HU-08) |
| `allowedHosts` | `"all"` | el navegador entra por `localhost:3000`, que no es el host interno del contenedor |
| `historyApiFallback` | **no se usa** | no hay enrutador (P-05): la URL nunca cambia |
| `hot` | `true` | recarga en caliente durante el desarrollo |
| `client.webSocketURL` | `"ws://localhost:3000/ws"` | el socket de recarga lo abre el **navegador**, asi que su URL es la del host, no la del contenedor |
| `client.overlay.runtimeErrors` | `false` | el overlay tapa la pantalla con un error que `BordeError` ya capturo y manejo (D-16) |
| `proxy` | array de cuatro entradas (§3.3) | P-02 |

### 3.3 Proxy de `/api` y la doble naturaleza del `devServer`

`webpack-dev-server` 5 recibe `proxy` como **arreglo** de objetos `{ context, target }`; la
forma de objeto de la version 4 ya no se acepta (D-02).

| `context` | `target` | Microservicio |
|---|---|---|
| `/api/usuarios` | `http://ms-usuarios:8080` | `ms-usuarios` |
| `/api/canchas` | `http://ms-canchas:8080` | `ms-canchas` |
| `/api/reservas` | `http://ms-reservas:8080` | `ms-reservas` |
| `/api/reportes` | `http://ms-reportes:8080` | `ms-reportes` |

Sin reescritura de ruta: los microservicios ya sirven bajo `/api/...`, asi que la peticion se
reenvia tal cual llega.

El `devServer` mira hacia **dos lados a la vez**, y esta es la tabla que el C1 pidio repetir
aqui:

| Que | Quien la resuelve | Forma de la URL |
|---|---|---|
| `remoteEntry.js` de un remote | el **navegador**, fuera de Docker | `http://localhost:3001/remoteEntry.js` |
| Socket de recarga en caliente | el **navegador**, fuera de Docker | `ws://localhost:3000/ws` |
| Destino del proxy de `/api` | `webpack serve`, **dentro** del contenedor | `http://ms-usuarios:8080` |

No es contradictorio: el navegador descarga `remoteEntry.js` por su cuenta y solo alcanza los
puertos publicados en el host; el proxy lo ejecuta el servidor de webpack dentro de la red de
Docker, donde resuelve nombres de contenedor igual que `ms-reservas` resuelve `ms-canchas`. Los
mapeos `8082`–`8085` del host siguen siendo solo para probar con `curl.exe` y esta spec no los
usa.

### 3.4 Dependencias de `package.json`

Versiones fijadas aqui, una sola vez, para el shell y para los tres remotes (D-01):

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

`ModuleFederationPlugin` viene dentro de `webpack`, no es un paquete aparte. No hay enrutador,
ni gestor de estado, ni libreria de UI, ni TypeScript (P-05, `CLAUDE.md` §3). `.babelrc` con
`@babel/preset-env` y `@babel/preset-react` en modo `automatic`, para no importar `React` en
cada archivo.

Script unico de arranque: `webpack serve --mode development`.

## 4. Modelo de estado del shell

El shell **no tiene base de datos ni tablas**. Su "modelo" es el estado de React de `App.jsx`
mas dos claves de `sessionStorage`. Se documenta con el mismo rigor que un DDL para que el
diseño sea verificable.

### 4.1 Estado de `App.jsx`

| Campo | Tipo | Valor inicial | Restricciones | Origen |
|---|---|---|---|---|
| `sesion` | objeto o `null` | lo que devuelva `almacenSesion.leer()` | `null` significa "sin sesion": obliga a mostrar `PantallaSesion` | `LoginResponse` o `sessionStorage` |
| `sesion.token` | string no vacia | — | obligatorio si `sesion` no es `null`; nunca se muestra en pantalla | campo `token` del contrato |
| `sesion.usuario.usuarioId` | number | — | obligatorio, entero positivo | campo `usuarioId` |
| `sesion.usuario.nombre` | string no vacia | — | obligatorio | campo `nombre` |
| `sesion.usuario.rol` | string | — | solo `ADMIN` o `USUARIO`; cualquier otro valor cae en el aviso de rol no reconocido (HU-05) | campo `rol` |
| `vista` | string | `"sesion"` | uno de `sesion`, `registro`, `bienvenida`, `mfReservas`, `mfAdministracion`, `mfReportes` | estado local (P-05) |
| `avisoSesion` | string o `null` | `null` | mensaje que se muestra sobre `PantallaSesion`, p. ej. "Su sesion expiro" (P-08) o el aviso de registro correcto | HU-02, HU-03 |

Invariantes:

- `sesion === null` implica `vista` en `sesion` o `registro`, y ningun remote montado.
- `sesion !== null` implica `vista` distinta de `sesion` y `registro`.
- Un `rol = USUARIO` nunca puede tener `vista` en `mfAdministracion` ni `mfReportes`: el menu
  no ofrece esas opciones y `App.jsx` valida el rol antes de montar (§6.2).
- `password` **no aparece en este modelo**: vive en el estado local del formulario que lo pide y
  se descarta al enviar (HU-01, HU-02).

### 4.2 Claves de `sessionStorage` (P-03)

| Clave | Contenido | Restricciones |
|---|---|---|
| `canchas.token` | el `token` tal cual, sin el prefijo `Bearer ` | se borra al cerrar sesion y ante un `401` |
| `canchas.usuario` | JSON con `usuarioId`, `nombre`, `rol` | los **tres** campos de la prop `usuario`, ni uno mas |

Al arrancar, `almacenSesion.leer()` exige las dos claves presentes y bien formadas; si una
falta, esta corrupta o el `rol` no es `ADMIN` ni `USUARIO`, borra ambas y devuelve `null`. Asi
un `sessionStorage` manipulado a mano degrada a "sin sesion" y no a una pantalla rota (D-06).

`localStorage` no se usa en ningun punto de esta spec.

### 4.3 Campos que se reciben y no se guardan

`LoginResponse.usuario` trae `email` y `activo`. Ninguno entra al estado ni a
`sessionStorage`: el contrato de props declara solo `usuarioId`, `nombre` y `rol`, y el shell no
decide nada con `activo` (C1 §6.2). Se descartan al mapear (D-09).

## 5. Payloads y validaciones

Equivalente de los DTOs. El shell no define esquemas de servidor: valida en el cliente **solo
lo que evita una llamada inutil**, y deja la validacion de verdad a `ms-usuarios`, que responde
`400 DATOS_INVALIDOS` (D-05).

### 5.1 `PeticionSesion` — cuerpo de `POST /api/usuarios/sesiones`

| Campo | Tipo | Validacion en el cliente | Si falla |
|---|---|---|---|
| `email` | string | obligatorio, no vacio tras recortar espacios | no se llama a la API; aviso junto al campo |
| `password` | string | obligatorio, no vacio | no se llama a la API; aviso junto al campo |

No se valida el formato del correo ni la longitud de la contraseña: esas reglas las tiene
`ms-usuarios` y duplicarlas aqui las haria divergir (D-05).

### 5.2 `PeticionRegistro` — cuerpo de `POST /api/usuarios`

| Campo | Tipo | Validacion en el cliente | Si falla |
|---|---|---|---|
| `nombre` | string | obligatorio, no vacio tras recortar espacios | no se llama a la API; aviso junto al campo |
| `email` | string | obligatorio, no vacio | no se llama a la API; aviso junto al campo |
| `password` | string | obligatorio, no vacio | no se llama a la API; aviso junto al campo |

No se envia `rol` ni `activo`: la ruta es publica y esos valores los fija `ms-usuarios`
(C1 HU-02).

### 5.3 `LoginResponse` — respuesta que se consume

| Campo | Tipo | Uso |
|---|---|---|
| `token` | string | estado y `sessionStorage` |
| `usuario.usuarioId` | number | prop `usuario` |
| `usuario.nombre` | string | prop `usuario` y cabecera |
| `usuario.email` | string | se descarta (§4.3) |
| `usuario.rol` | `ADMIN` \| `USUARIO` | menu y prop `usuario` |
| `usuario.activo` | boolean | se descarta (§4.3) |

Si la respuesta `200` no trae `token` o no trae `usuario.rol`, el shell la trata como error de
integracion: muestra el mensaje generico de servicio no disponible y **no** abre sesion. Es el
mismo criterio que `ms-reportes` aplica ante una dependencia que responde algo inesperado.

### 5.4 `ErrorResponse` — forma unica de error

| Campo | Tipo | Uso |
|---|---|---|
| `codigo` | string de la tabla "Formato de error" | selecciona el comportamiento (§7) |
| `mensaje` | string | se muestra tal cual al usuario |

Si una respuesta de error no trae ese cuerpo (por ejemplo un `502` del proxy cuando el
microservicio esta caido), `clienteApi` sintetiza
`{ codigo: "ERROR_INTERNO", mensaje: "No se pudo contactar al servicio" }` para que el resto de
la aplicacion vea siempre la misma forma (D-04).

### 5.5 Props hacia los remotes

| Prop | Tipo | Valor |
|---|---|---|
| `usuario` | objeto | `{ usuarioId, nombre, rol }`, exactamente esos tres campos |
| `token` | string | el `token` de la sesion, sin `Bearer ` |
| `apiBaseUrl` | string | literal `"/api"` |
| `onLogout` | funcion | la misma de `Cabecera` (HU-04) |

## 6. Rutas y modulos

### 6.1 Rutas HTTP que el shell consume

El shell **no expone** endpoints. Consume dos, las dos publicas de `ms-usuarios`.

| Verbo | Ruta | Rol requerido | Encabezado `Authorization` | Respuestas | Historia |
|---|---|---|---|---|---|
| POST | `/api/usuarios/sesiones` | publico | no | 200, 400, 401 | HU-01 |
| POST | `/api/usuarios` | publico | no | 201, 400, 409 | HU-02 |

Las dos son publicas, asi que el shell **nunca** envia `Authorization` en esta spec. El
encabezado `Authorization: Bearer <token>` esta implementado en `clienteApi` (HU-03) y hoy no
tiene ningun llamador: lo estrenan los remotes con su propia capa `api/`. Queda escrito para que
no se lea como codigo muerto ni se borre en una revision.

### 6.2 Modulos que el shell monta, por rol

| Modulo | Remote | Rol que lo ve | Ruta del contrato que consume el remote |
|---|---|---|---|
| Reservas | `mfReservas` / `./ReservasApp` | `ADMIN` y `USUARIO` (C-2, D-08 de la spec 04) | `/api/reservas`, `/api/canchas` |
| Administracion | `mfAdministracion` / `./AdminApp` | solo `ADMIN` | `/api/canchas`, `/api/reservas`, `/api/usuarios` |
| Reportes | `mfReportes` / `./ReportesApp` | solo `ADMIN` | `/api/reportes` |

`App.jsx` comprueba el rol **antes** de montar, no solo al pintar el menu: si `vista` vale
`mfReportes` con `rol = USUARIO`, no monta nada y vuelve a `bienvenida`. Ocultar el menu no es
control de acceso (HU-05); el control real lo hace cada microservicio con el token.

### 6.3 Vistas del shell

| Vista | Componente | Condicion |
|---|---|---|
| `sesion` | `PantallaSesion` | `sesion === null` |
| `registro` | `PantallaRegistro` | `sesion === null` y el visitante pulso "Crear cuenta" |
| `bienvenida` | `PantallaBienvenida` | sesion activa, vista inicial de los dos roles (P-09) |
| `mfReservas` / `mfAdministracion` / `mfReportes` | `ContenedorRemoto` | sesion activa y rol autorizado (§6.2) |

## 7. Codigos HTTP recibidos y comportamiento

Equivalente de la tabla de excepciones. En un microservicio la traduccion va de excepcion a
codigo; aqui va de **codigo recibido a comportamiento**, porque el shell es el cliente.

| Situacion | HTTP | `codigo` | Que hace el shell |
|---|---|---|---|
| Sesion iniciada | 200 | — | guarda `token` y `usuario`, escribe `sessionStorage`, pasa a `bienvenida` |
| Usuario creado | 201 | — | vuelve a `sesion` con el aviso de registro correcto |
| Entrada invalida | 400 | `DATOS_INVALIDOS` | muestra `mensaje` en el formulario, mantiene lo escrito salvo `password` |
| Credenciales invalidas en el inicio de sesion | 401 | `NO_AUTENTICADO` | muestra `mensaje`, conserva el `email`, limpia el `password` |
| Token vencido o invalido en una llamada ya autenticada | 401 | `NO_AUTENTICADO` | cierra la sesion (HU-04) y muestra "Su sesion expiro" sobre `PantallaSesion` (P-08) |
| Sin permiso | 403 | `SIN_PERMISO` | muestra `mensaje`; no deberia ocurrir en las dos rutas publicas |
| Recurso inexistente | 404 | `NO_ENCONTRADO` | muestra `mensaje` |
| Correo ya registrado | 409 | `EMAIL_DUPLICADO` | muestra `mensaje` junto al campo `email` |
| Error del servidor | 500 | `ERROR_INTERNO` | muestra `mensaje` y deja reintentar; no cierra la sesion |
| Microservicio caido, proxy sin destino o red cortada | sin respuesta o 502/504 | `ERROR_INTERNO` sintetizado (D-04) | muestra "No se pudo contactar al servicio" y deja reintentar |
| `remoteEntry.js` de un remote no descargable | — | — | `BordeError` muestra "Modulo no disponible", conserva sesion y menu (HU-06) |

El `401` es el unico codigo cuyo efecto depende del contexto: en el inicio de sesion es un
error de credenciales y en cualquier otra llamada es una sesion expirada. `clienteApi` no decide
eso: devuelve el error y cada llamador elige, porque solo el llamador sabe si habia sesion
(D-07).

## 8. Componentes y flujos

### 8.1 Responsabilidad de cada componente

| Componente | Responsabilidad | No hace |
|---|---|---|
| `index.js` | `import("./bootstrap")` y nada mas (`CLAUDE.md` §3) | ningun render |
| `bootstrap.jsx` | `createRoot` y render de `<App />` | ninguna logica |
| `App.jsx` | dueño de `sesion`, `vista` y `avisoSesion`; mapea `LoginResponse` a la prop `usuario`; decide que vista se pinta | ningun `fetch` |
| `PantallaSesion` | formulario de inicio de sesion, validacion de §5.1 | no guarda `sessionStorage` |
| `PantallaRegistro` | formulario de registro, validacion de §5.2 | no abre sesion |
| `Cabecera` | nombre, rol y boton de cerrar sesion | no conoce los remotes |
| `MenuModulos` | opciones segun rol (§6.2) | no monta remotes |
| `PantallaBienvenida` | texto de bienvenida, vista inicial | ninguna llamada |
| `ContenedorRemoto` | `React.lazy` del modulo pedido, `Suspense` con "Cargando modulo...", entrega de las cuatro props | no valida el rol: eso lo hizo `App` |
| `BordeError` | unico componente de clase; `componentDidCatch` para el fallo de carga o render de un remote | no reintenta solo |
| `MensajeError` | pinta `{ codigo, mensaje }` | no interpreta el codigo |
| `clienteApi` | `fetch` con rutas relativas, `Authorization` si hay token, normalizacion de errores | no conoce React |
| `usuariosApi` | `iniciarSesion(email, password)` y `registrarUsuario(nombre, email, password)` | no toca `sessionStorage` |
| `almacenSesion` | `leer()`, `guardar(sesion)`, `borrar()` sobre `sessionStorage` | no hace HTTP |

`ContenedorRemoto` crea el componente diferido **una sola vez por modulo**, en un mapa fuera del
render: crear el `React.lazy` dentro del render lo volveria a crear en cada pintada y
redescargaria el remote (D-11).

### 8.2 Flujos

**Inicio de sesion (HU-01).** `PantallaSesion` valida §5.1 y llama
`usuariosApi.iniciarSesion` -> `clienteApi` hace `POST /api/usuarios/sesiones` -> el
`devServer` proxya a `http://ms-usuarios:8080` -> `200` con `LoginResponse` -> `App` mapea a
`{ token, usuario: { usuarioId, nombre, rol } }`, llama `almacenSesion.guardar` y pone
`vista = "bienvenida"`.

**Registro (HU-02).** `PantallaRegistro` valida §5.2 -> `POST /api/usuarios` -> `201` ->
`vista = "sesion"` con `avisoSesion` de registro correcto. No se abre sesion: la respuesta no
trae `token`.

**Recarga F5 (HU-03).** `App` arranca con `almacenSesion.leer()`. Con sesion valida, entra en
`bienvenida`; con `sessionStorage` vacio o corrupto, en `sesion`. Ningun remote se descarga en
el arranque.

**Cambio de modulo (HU-05, HU-06).** El menu cambia `vista` -> `App` valida el rol -> monta
`ContenedorRemoto`, que descarga el `remoteEntry.js` de ese remote la primera vez. El remote
anterior se desmonta porque solo hay un `ContenedorRemoto` a la vez.

**Remote caido (HU-06, criterio de verificacion de esta spec).** El `import()` del remote
rechaza -> `BordeError` pinta "Modulo no disponible" dentro del layout -> la sesion, la cabecera
y el menu siguen vivos. Es el estado esperado hoy, porque los tres remotes no existen (P-04).

**Sesion expirada (P-08).** Cualquier llamada con sesion responde `401` -> el llamador invoca el
mismo `cerrarSesion` de HU-04 -> `almacenSesion.borrar()`, `sesion = null`,
`vista = "sesion"`, `avisoSesion = "Su sesion expiro"`.

**Cierre de sesion (HU-04).** `Cabecera` o la prop `onLogout` de un remote llaman a
`cerrarSesion`: borra estado y `sessionStorage`, desmonta el remote y vuelve a `sesion`. Ninguna
llamada HTTP: el contrato no declara ruta de cierre de sesion.

## 9. Servicio `shell` en `docker-compose.yml`

| Aspecto | Valor | Motivo |
|---|---|---|
| Imagen | `node:20-alpine` | `CLAUDE.md` §1; sin Node en el host |
| Comando | `sh -c "npm install && npx webpack serve --mode development"` | D-03 |
| Volumen | `./frontend/shell:/app` mas volumen anonimo en `/app/node_modules` | D-03 |
| `working_dir` | `/app` | — |
| Puertos | `3000:3000` | P-06 |
| `depends_on` | `ms-usuarios`, `ms-canchas`, `ms-reservas`, `ms-reportes`, con `condition: service_started` | el proxy sale hacia los cuatro (C1 §3) |
| Sin `build` | no hay `Dockerfile` en esta spec | D-03 |

`depends_on` con `service_started` y no `service_healthy`: los cuatro microservicios no declaran
`healthcheck` propio, y el `devServer` arranca igual aunque un destino del proxy todavia no
responda: el `502` se traduce a `ERROR_INTERNO` (§7).

## 10. Verificacion prevista (para `tasks.md`, no se ejecuta aqui)

Comandos en PowerShell desde la raiz, con `curl.exe` y solo Docker (`CLAUDE.md` §1):

- `docker compose up -d --build shell` y `docker compose logs --tail=50 shell`.
- `curl.exe http://localhost:3000` responde el HTML del shell.
- `curl.exe -X POST http://localhost:3000/api/usuarios/sesiones -H "Content-Type: application/json" -d "{...}"`
  devuelve el `LoginResponse` del ADMIN del seed: prueba que el proxy llega a `ms-usuarios` por
  nombre de contenedor, sin abrir el navegador.
- En el navegador: inicio de sesion, bienvenida, menu por rol, "Modulo no disponible" al entrar
  a un modulo, F5 con la sesion viva y cierre de sesion.

## 11. Aislamiento de datos

- El shell **no tiene base de datos** y no ejecuta ni una consulta SQL. No puede leer tablas de
  otro microservicio ni de la suya, porque no tiene ninguna.
- Su unico acceso a datos son `POST /api/usuarios/sesiones` y `POST /api/usuarios` (§6.1).
- El `devServer.proxy` declara los cuatro prefijos, pero el codigo del shell no llama a
  `/api/canchas`, `/api/reservas` ni `/api/reportes`: el proxy es infraestructura para los
  remotes que vendran, no una puerta que el shell use.
- El shell no reimplementa ninguna regla de negocio: filtra el menu por rol y nada mas (C1 §5).

## 12. Decisiones de diseño

| ID | Decision | Alternativa descartada | Motivo |
|---|---|---|---|
| D-01 | Versiones exactas de las doce dependencias (§3.4), fijadas aqui para el shell y los tres remotes | Dejar rangos `^` en cada microfrontend | Con `react` en `shared` y `singleton: true`, dos remotes que resuelvan versiones distintas rompen la instancia unica en tiempo de ejecucion. Es el mismo criterio con que `CLAUDE.md` §3 congelo Spring Boot 3.5.3 |
| D-02 | `devServer.proxy` como **arreglo** de `{ context, target }` | La forma de objeto `{ "/api": {...} }` de `webpack-dev-server` 4 | La version 5 la elimino: arrancaria con error de validacion de esquema |
| D-03 | El contenedor corre `npm install` y `webpack serve` sobre el codigo montado por volumen, sin `Dockerfile` | Un `Dockerfile` con build de produccion, como los cuatro microservicios | P-06 eligio `webpack serve`, que necesita el codigo vivo para recargar. Un volumen anonimo en `/app/node_modules` evita que el `node_modules` del host, inexistente, tape el del contenedor |
| D-04 | `clienteApi` sintetiza `{ codigo: "ERROR_INTERNO", mensaje: ... }` cuando la respuesta no trae el cuerpo de error del contrato | Propagar el error crudo de `fetch` a los componentes | Un `502` del proxy o una red cortada no traen `{ codigo, mensaje }`. Normalizar en un solo punto deja a todos los componentes con una sola forma de error que pintar |
| D-05 | El cliente valida solo campos obligatorios vacios; formato y longitud los valida `ms-usuarios` | Replicar en el shell las reglas de `jakarta.validation` de `ms-usuarios` | Duplicar reglas es garantizar que divergan. El `400 DATOS_INVALIDOS` ya trae un `mensaje` listo para mostrar |
| D-06 | `almacenSesion.leer()` valida lo que encuentra y, si esta corrupto o el rol no es valido, borra las dos claves y devuelve `null` | Confiar en el JSON de `sessionStorage` | `sessionStorage` es editable a mano. Un rol inventado no da permisos —los da el token del lado del servidor— pero si dejaria la interfaz en un estado imposible |
| D-07 | `clienteApi` no interpreta el `401`: devuelve el error y cada llamador decide | Que `clienteApi` cierre la sesion en todo `401` | El `401` del inicio de sesion son credenciales malas y no debe borrar nada; el de una llamada con sesion es un token vencido y si debe cerrarla (P-08). Solo el llamador conoce la diferencia |
| D-08 | El estado de sesion vive en `App.jsx` y baja por props | `Context` de React | Con un solo consumidor real (`ContenedorRemoto`) y un arbol de dos niveles, el `Context` agrega indireccion sin quitar nada |
| D-09 | El mapeo de `LoginResponse` a la prop `usuario` es una funcion de tres campos en `App.jsx`, sin carpeta `mapper/` | Crear `src/mapper/` por simetria con el backend | `CLAUDE.md` §4 no declara `mapper/` en la estructura de un microfrontend. El mapeo son tres campos y descartar `email` y `activo` |
| D-10 | `BordeError` es el unico componente de clase, con `componentDidCatch` | Capturar el fallo con `try/catch` alrededor del `import()` | Un `React.lazy` que rechaza propaga el error al render, y en React 18 solo un borde de error de clase lo intercepta. `try/catch` no ve un fallo de render del remote ya cargado |
| D-11 | Los `React.lazy` de los tres remotes se crean una vez, en un mapa a nivel de modulo | Crear el `React.lazy` dentro del render de `ContenedorRemoto` | Crearlo en el render devuelve un componente nuevo en cada pintada: React lo trata como otro tipo, desmonta y vuelve a descargar el remote |
| D-12 | `ContenedorRemoto` recibe el `token` por prop en cada render | Que cada remote lea el token de `sessionStorage` por su cuenta | El contrato de props declara `token` (C-1). Si cada remote leyera el almacenamiento, el shell dejaria de ser el dueño de la sesion y un cierre de sesion no se propagaria |
| D-13 | Un solo `src/estilos.css` importado desde `bootstrap.jsx` | Un archivo CSS por componente | Sin librerias de UI ni preprocesador, un archivo plano y corto es mas facil de revisar que doce imports; el shell tiene ocho componentes de presentacion |
| D-14 | `client.webSocketURL` apunta a `ws://localhost:3000/ws` | Dejar el valor por omision | Por omision el cliente deduce la URL del host interno del contenedor y la recarga en caliente falla en silencio desde el navegador del host |
| D-15 | `watchOptions` con `poll: 1000` e `ignored: /node_modules/`, obligatorio en el shell y en los tres remotes | Confiar en los eventos del sistema de archivos, que es el valor por omision | El bind mount de Windows no entrega eventos inotify dentro del contenedor: webpack compila al arrancar y luego ignora todo guardado. El sintoma engaña, porque el registro sigue mostrando el `compiled successfully` anterior mientras el archivo ya cambio dentro del contenedor. Detectado en la T3 y anotado en `docs/bitacora.md` |
| D-16 | `devServer.client.overlay.runtimeErrors: false` | Dejar el overlay activo, que es el valor por omision | El fallo de carga de un remote (`ScriptExternalLoadError`) ya lo captura `BordeError` y se muestra como "Modulo no disponible" (HU-06). El overlay lo repite tapando la pantalla completa: en desarrollo es ruido sobre un error ya manejado, y en la demo en vivo haria parecer un fallo lo que es el comportamiento esperado de HU-06 mientras los tres remotes no existan. Los errores siguen apareciendo en la consola del navegador, asi que no se pierde diagnostico. Decidido el 24/08/2026 tras verificar el paso 1 de T6 |

## 13. Fuera de alcance de este diseño

Lo mismo que declaro el C1 aprobado (§11 de `requirements.md`), sin agregados. En particular
este diseño **no** define:

- El `webpack.config.js` de ningun remote, ni sus `exposes`, ni sus pantallas.
- El gateway Nginx: queda para la seccion 5 de integracion (§8 del C1).
- Ningun cambio en `backend/`, en `infra/` ni en `docs/contratos/README.md`: el cambio del
  contrato de props ya se aplico durante la compuerta C1.
- Ningun archivo fuera de `frontend/shell`, salvo el servicio `shell` de `docker-compose.yml`
  (§9).
