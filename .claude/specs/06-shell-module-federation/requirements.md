# Spec 06 — shell (host de Module Federation) · requirements.md

Estado: **C1 — APROBADO** el 23/08/2026 ("Apruebo requisitos de la spec 06").
La compuerta C2 (`design.md`) sigue pendiente: no se escribe codigo de produccion hasta que
el diseño este aprobado por escrito (`CLAUDE.md` §6).

Las nueve preguntas abiertas (P-01 a P-09) fueron **respondidas por el responsable el
23/08/2026** y ya estan incorporadas a este documento. Las decisiones quedan registradas en
§9 con su motivo, para la defensa del proyecto.

El responsable ademas **corrigio dos de las tres deducciones** que traia la primera version
de este documento (§11):

- **C-1 — el `token` si va en las props.** Obliga a modificar `docs/contratos/README.md` y
  `CLAUDE.md` §5, y el cambio **ya esta aplicado** (§10).
- **C-2 — el `ADMIN` si ve el modulo Reservas.** La decision D-08 de la spec 04 ya establecio
  que el `ADMIN` crea reservas y tiene historial propio, sin `403`. HU-05 quedo corregida.

Fuentes leidas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3, 4.1, 4.4, 5, 6, 7),
`docker-compose.yml`, `.claude/specs/01-modelo-y-contratos/` a `.claude/specs/05-ms-reportes/`
(las cinco cerradas, incluida la decision D-08 de la spec 04).

## 1. Objetivo

Implementar `frontend/shell`: la aplicacion **host** de Module Federation. Es el primer
microfrontend del proyecto y hoy `frontend/` esta vacio (solo `.gitkeep`).

El shell tiene exactamente cuatro responsabilidades, tomadas del PDF §4.1 —"layout,
navegacion, autenticacion y orquestacion de los remotes via Module Federation"—:

1. **Autenticacion**: pantalla de inicio de sesion y registro (modulo Seguridad del PDF
   §3.2, el unico modulo que **no** tiene remote asignado en el contrato congelado; P-07).
2. **Layout y navegacion**: cabecera con el usuario en sesion, menu filtrado por `rol` y
   cierre de sesion.
3. **Orquestacion**: carga en tiempo de ejecucion de los tres remotes (`mfReservas`,
   `mfAdministracion`, `mfReportes`) y entrega de las props del contrato.
4. **Sesion**: guardar el `token` y el `usuario` que devuelve `POST /api/usuarios/sesiones`,
   adjuntar el token en cada llamada del shell a `/api` y **entregarlo a los remotes** como
   prop (C-1).

El shell **no** implementa ninguna pantalla de reservas, de administracion ni de reportes:
esas viven en los remotes de las specs siguientes.

## 2. Entregables de la spec

| ID | Entregable |
|---|---|
| E-01 | Proyecto `frontend/shell`: React 18 + Webpack 5 con `ModuleFederationPlugin`, `name: "shell"`, sin `exposes`, puerto 3000 |
| E-02 | `src/index.js` que solo hace `import("./bootstrap")`, mas `src/bootstrap.jsx` y `src/App.jsx` (`CLAUDE.md` §4) |
| E-03 | Pantalla de inicio de sesion contra `POST /api/usuarios/sesiones` |
| E-04 | Pantalla de registro contra `POST /api/usuarios` |
| E-05 | Capa `src/api/` como **unica** capa que hace `fetch`, siempre con rutas relativas bajo `/api` |
| E-06 | Manejo de la sesion: `token` y `usuario` en estado de React, persistidos en `sessionStorage` (P-03), y cierre de sesion automatico ante un `401` (P-08) |
| E-07 | Layout con cabecera, nombre del usuario, su `rol`, boton de cierre de sesion y pantalla de bienvenida como vista inicial (P-09) |
| E-08 | Menu de navegacion filtrado por `rol`: tres modulos para `ADMIN`, solo Reservas para `USUARIO` (HU-05, C-2) |
| E-09 | Declaracion de los tres remotes con URLs de **navegador** (`http://localhost:3001/remoteEntry.js`, `3002`, `3003`) y `react`/`react-dom` en `shared` con `singleton: true` |
| E-10 | Carga diferida (`React.lazy` + `Suspense`) de cada remote y borde de error que muestra un mensaje si el remote no esta levantado |
| E-11 | Entrega de las props del contrato **actualizado** a todo remote: `usuario={{ usuarioId, nombre, rol }}`, `token`, `apiBaseUrl="/api"`, `onLogout` (C-1, P-01) |
| E-12 | CSS plano propio, sin librerias de UI, sin TypeScript, sin enrutador (P-05) |
| E-13 | `devServer.proxy` del shell hacia los cuatro microservicios por **nombre de contenedor** (`http://ms-usuarios:8080` y los tres restantes), con `host: "0.0.0.0"` y `allowedHosts: "all"` (P-02) |
| E-14 | Servicio `shell` en `docker-compose.yml`: `node:20-alpine` corriendo `webpack serve`, puerto `3000:3000` y `depends_on` de los cuatro microservicios (P-06) |

## 3. Contexto tecnico fijado (no se vuelve a decidir)

| Aspecto | Valor | Fuente |
|---|---|---|
| Ruta en el repo | `frontend/shell` | `CLAUDE.md` §4 |
| Nombre Module Federation | `shell` (host) | contrato, seccion "Contrato Module Federation" |
| Modulos expuestos | **ninguno** | contrato |
| Puerto | 3000, publicado como `3000:3000` | contrato, P-06 |
| React | 18 | `CLAUDE.md` §3 |
| Empaquetador | Webpack 5 con `ModuleFederationPlugin` | `CLAUDE.md` §3 |
| `shared` | `react` y `react-dom` con `singleton: true` | `CLAUDE.md` §3 |
| Arranque | `src/index.js` -> `import("./bootstrap")` | `CLAUDE.md` §3 |
| URLs de los remotes | las del **navegador**, nunca nombres de contenedor | `CLAUDE.md` §3 |
| Llamadas HTTP | rutas relativas bajo `/api`, resueltas por `devServer.proxy` hacia nombres de contenedor | `CLAUDE.md` §3, P-02 |
| Persistencia de sesion | `sessionStorage` | P-03 |
| Enrutador | **ninguno**: el modulo activo es estado de React | P-05 |
| Estilos | CSS plano, sin librerias de UI externas | `CLAUDE.md` §3 |
| Lenguaje | JavaScript, **sin** TypeScript | `CLAUDE.md` §3 |
| Idioma | identificadores en español sin tildes; textos y mensajes en español | `CLAUDE.md` §7 |
| Instalacion de dependencias | solo por Docker (`node:20-alpine`), nunca `npm` en el host | `CLAUDE.md` §1 |

Remotes que declara el shell (contrato congelado, no se renombran):

| Microfrontend | Nombre | Modulo expuesto | Puerto |
|---|---|---|---|
| mf-reservas | `mfReservas` | `./ReservasApp` | 3001 |
| mf-administracion | `mfAdministracion` | `./AdminApp` | 3002 |
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

Destinos del `devServer.proxy` (P-02), decididos el 23/08/2026. Son **nombres de contenedor**,
no puertos del host:

| Prefijo | Destino | Microservicio |
|---|---|---|
| `/api/usuarios` | `http://ms-usuarios:8080` | `ms-usuarios` |
| `/api/canchas` | `http://ms-canchas:8080` | `ms-canchas` |
| `/api/reservas` | `http://ms-reservas:8080` | `ms-reservas` |
| `/api/reportes` | `http://ms-reportes:8080` | `ms-reportes` |

El shell solo consume `/api/usuarios` (§6.1), pero declara los cuatro prefijos: el proxy es
del `devServer`, no del codigo de aplicacion, y cada remote declarara los suyos igual.

**Distincion que parece contradictoria y no lo es** (queda escrita aqui y se repite en
`design.md`):

| Que | Quien la resuelve | Forma de la URL |
|---|---|---|
| URL de un **remote** (`remoteEntry.js`) | el **navegador**, fuera de Docker | `http://localhost:3001/remoteEntry.js` |
| Destino del **proxy** de `/api` | el **servidor de webpack**, dentro del contenedor | `http://ms-usuarios:8080` |

El navegador descarga `remoteEntry.js` por su cuenta y solo conoce los puertos publicados en
el host; el proxy lo ejecuta `webpack serve` dentro de la red de Docker, y ahi resuelve nombres
de contenedor igual que `ms-reservas` resuelve `ms-canchas`. Por eso el shell **no** usa los
mapeos `8082`–`8085` del host: esos siguen siendo solo para probar con `curl.exe`.

Como el proxy sale hacia los cuatro microservicios, el servicio `shell` de
`docker-compose.yml` lleva `depends_on` de los cuatro (E-14).

## 4. Historias de usuario y criterios de aceptacion

### HU-01 — Inicio de sesion (PDF §3.2, modulo Seguridad)

Como usuario final o administrador, necesito iniciar sesion con mi correo y contraseña para
entrar al sistema.

- **CUANDO** el shell cargue sin sesion activa, **ENTONCES** mostrara la pantalla de inicio
  de sesion y **ningun** remote: no se descarga el `remoteEntry.js` de nadie antes de
  autenticarse.
- **CUANDO** el usuario envie el formulario, **ENTONCES** el shell hara
  `POST /api/usuarios/sesiones` con el cuerpo `{ "email": ..., "password": ... }`, con los
  nombres exactos del contrato.
- **CUANDO** la respuesta sea `200`, **ENTONCES** el shell leera `token` y `usuario`
  (`usuarioId`, `nombre`, `email`, `rol`, `activo`) del `LoginResponse` congelado, los
  guardara segun HU-03 y dara la sesion por iniciada.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** la pantalla
  mostrara el `mensaje` recibido y permanecera en el formulario, sin borrar el `email`
  escrito.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrara el
  `mensaje` recibido.
- **SI** `email` o `password` estan vacios, **ENTONCES** el shell no hara la llamada y
  mostrara el aviso de campo obligatorio.
- **CUANDO** la peticion este en curso, **ENTONCES** el boton de envio quedara deshabilitado,
  para que un doble clic no cree dos sesiones.
- **CUANDO** se muestre cualquier error, **ENTONCES** nunca se mostrara el `password` ni se
  escribira en ningun almacenamiento del navegador.

### HU-02 — Registro de un usuario nuevo (PDF §3.1 y §3.2)

Como visitante, necesito registrarme para poder reservar una cancha.

- **CUANDO** el visitante abra la pantalla de registro, **ENTONCES** vera los campos
  `nombre`, `email` y `password`, y ninguno mas: `POST /api/usuarios` es publico, y `rol` y
  `activo` los fija `ms-usuarios`.
- **CUANDO** envie el formulario, **ENTONCES** el shell hara `POST /api/usuarios` con
  `{ "nombre": ..., "email": ..., "password": ... }`.
- **CUANDO** la respuesta sea `201`, **ENTONCES** el shell mostrara el aviso de registro
  correcto y llevara a la pantalla de inicio de sesion. **No** inicia sesion de forma
  automatica: `POST /api/usuarios` no devuelve `token`.
- **SI** la respuesta es `409` con `codigo = EMAIL_DUPLICADO`, **ENTONCES** se mostrara el
  `mensaje` recibido junto al campo `email`.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrara el
  `mensaje` recibido.
- **CUANDO** el registro termine, **ENTONCES** el `password` no quedara en el estado de React
  ni en el almacenamiento del navegador.

### HU-03 — Sesion, persistencia y token en cada llamada

Como equipo, necesito que el shell sea el dueño de la sesion y la unica pieza que la obtiene,
aunque el `token` viaje despues a los remotes por prop.

- **CUANDO** exista sesion activa, **ENTONCES** toda llamada del shell a `/api` llevara el
  encabezado `Authorization: Bearer <token>`.
- **CUANDO** el shell llame a la API, **ENTONCES** usara siempre rutas relativas
  (`/api/...`), nunca `http://localhost:8082` ni un nombre de contenedor (`CLAUDE.md` §3).
- **CUANDO** se inicie sesion, **ENTONCES** el `token` y el objeto `usuario` se guardaran en
  **`sessionStorage`** (P-03), nunca en `localStorage`.
- **CUANDO** se recargue la pagina (F5) con una sesion viva en `sessionStorage`, **ENTONCES**
  el shell rehidratara la sesion y volvera a la pantalla de bienvenida, sin pedir credenciales
  otra vez.
- **CUANDO** se cierre la pestaña o el navegador, **ENTONCES** la sesion se pierde, porque
  `sessionStorage` no sobrevive al cierre (P-03).
- **CUANDO** cualquier llamada del shell responda `401` con `codigo = NO_AUTENTICADO` estando
  ya autenticado, **ENTONCES** el shell cerrara la sesion con el mismo procedimiento de HU-04
  y mostrara en la pantalla de inicio de sesion el aviso de **sesion expirada** (P-08).
- **CUANDO** el `password` se envie a la API, **ENTONCES** nunca se guardara en
  `sessionStorage`: lo unico que se persiste es `token` y `usuario`.
- **CUANDO** un remote necesite llamar a la API, **ENTONCES** lo hara con su propia capa
  `src/api/`, usando el `token` y el `apiBaseUrl` que recibe por prop (C-1); el shell no
  expone su cliente HTTP.

### HU-04 — Cierre de sesion

Como usuario en sesion, necesito salir del sistema.

- **CUANDO** el usuario pulse "Cerrar sesion", **ENTONCES** el shell borrara `token` y
  `usuario` de su estado y de `sessionStorage`, y volvera a la pantalla de inicio de sesion.
- **CUANDO** un remote invoque la prop `onLogout`, **ENTONCES** ocurrira exactamente lo mismo
  que en el punto anterior: es la misma funcion.
- **CUANDO** la sesion se cierre, **ENTONCES** ningun remote seguira montado en la pagina.
- **CUANDO** la sesion se cierre, **ENTONCES** el shell **no** llamara a ningun endpoint: el
  contrato no declara ninguna ruta de cierre de sesion y no se inventa (`CLAUDE.md` §0.1).

### HU-05 — Navegacion filtrada por rol (PDF §3.1, corregida por C-2 y decision D-08 de la spec 04)

Como sistema, necesito que cada rol vea los modulos que puede usar.

- **CUANDO** el usuario en sesion tenga `rol = ADMIN`, **ENTONCES** el menu ofrecera los
  **tres** modulos: **Reservas** (`mfReservas`), **Administracion** (`mfAdministracion`) y
  **Reportes** (`mfReportes`). El `ADMIN` crea reservas y tiene historial propio sin recibir
  `403` (decision D-08 de la spec 04), asi que ocultarle Reservas seria contradecir un
  servicio ya cerrado.
- **CUANDO** el usuario en sesion tenga `rol = USUARIO`, **ENTONCES** el menu ofrecera
  unicamente **Reservas**: `mfAdministracion` consume rutas exclusivas de `ADMIN` y los tres
  endpoints de `/api/reportes` tambien lo son.
- **CUANDO** se inicie sesion con cualquiera de los dos roles, **ENTONCES** la vista inicial
  sera la **pantalla de bienvenida del shell** con el menu, y ningun remote se descargara
  hasta que el usuario elija un modulo (P-09).
- **SI** el `rol` recibido no es `ADMIN` ni `USUARIO`, **ENTONCES** el shell no montara ningun
  remote y mostrara un aviso de rol no reconocido. En particular **nunca** habra una sesion
  con `rol = SERVICIO`: ese rol no se persiste ni aparece en ninguna respuesta (contrato) y
  `POST /api/usuarios/sesiones` no lo emite.
- **CUANDO** se cambie de modulo en el menu, **ENTONCES** el remote anterior se desmontara y
  se montara el nuevo, sin recargar la pagina y sin cambiar la URL: no hay enrutador (P-05).
- **CUANDO** el shell decida que modulos ofrecer, **ENTONCES** lo hara solo por el `rol` de la
  sesion; el permiso real lo sigue aplicando cada microservicio con el token (`401`/`403`), y
  ocultar un menu no se considera control de acceso.

### HU-06 — Integracion de los remotes por Module Federation (PDF §4.1, rubrica §6)

Como equipo, necesito que el shell cargue los tres remotes en tiempo de ejecucion y que cada
uno pueda desplegarse por separado.

- **CUANDO** se configure `ModuleFederationPlugin` en el shell, **ENTONCES** declarara
  `name: "shell"`, ningun `exposes` y los tres remotes con los nombres exactos del contrato:
  `mfReservas`, `mfAdministracion`, `mfReportes`.
- **CUANDO** se declare la URL de un remote, **ENTONCES** sera la del navegador
  (`http://localhost:3001/remoteEntry.js`, `3002`, `3003`), nunca un nombre de contenedor
  (`CLAUDE.md` §3).
- **CUANDO** se declare `shared`, **ENTONCES** `react` y `react-dom` iran con
  `singleton: true`, de modo que shell y remotes compartan una sola instancia de React.
- **CUANDO** el shell monte un remote, **ENTONCES** lo hara con `React.lazy` y `Suspense`, y
  la descarga de su `remoteEntry.js` ocurrira solo al entrar a ese modulo, no al cargar el
  shell ni al iniciar sesion.
- **SI** un remote no esta levantado o su `remoteEntry.js` no se puede descargar, **ENTONCES**
  el shell mostrara un mensaje de modulo no disponible dentro del layout, mantendra la sesion
  y el menu seguira funcionando: no se queda en blanco.
- **CUANDO** se verifique esta spec, **ENTONCES** el criterio sera ese mensaje de modulo no
  disponible: los tres remotes no existen todavia y esta spec **no** los crea (P-04). La carga
  real de un remote se verifica en la spec 07.
- **CUANDO** un remote se reconstruya y se sirva de nuevo, **ENTONCES** bastara con volver a
  entrar a su modulo para verlo actualizado, sin reconstruir el shell.

### HU-07 — Contrato de props hacia los remotes (corregido por C-1 y P-01)

Como equipo, necesito que el shell entregue a todo remote exactamente las props congeladas,
para que las tres specs de remotes se escriban contra un contrato estable y ninguna invente
como autenticar.

- **CUANDO** el shell monte cualquier remote, **ENTONCES** le pasara exactamente `usuario`,
  `token`, `apiBaseUrl` y `onLogout`, y ninguna prop mas:

  ```jsx
  <RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
  ```

- **CUANDO** se arme el objeto `usuario`, **ENTONCES** tendra exactamente `usuarioId`,
  `nombre` y `rol`, con esos nombres (P-01): `usuarioId` es el nombre congelado del
  identificador de usuario y el contrato de props quedo corregido el 23/08/2026. **No** se
  pasa `email` ni `activo`, que el contrato de props no declara.
- **CUANDO** se pase `token`, **ENTONCES** sera el mismo `token` de la sesion, tal cual lo
  devolvio `POST /api/usuarios/sesiones`, sin el prefijo `Bearer `: cada remote arma su propio
  encabezado.
- **CUANDO** se pase `apiBaseUrl`, **ENTONCES** su valor sera literalmente `"/api"`.
- **CUANDO** se pase `onLogout`, **ENTONCES** sera la misma funcion de HU-04, para que un
  remote que reciba `401` pueda cerrar la sesion del sistema entero.
- **CUANDO** la sesion se renueve o se cierre, **ENTONCES** el remote montado recibira el
  `token` nuevo o se desmontara: el `token` es una prop, no una copia que el remote guarde
  aparte.
- **CUANDO** los tres remotes reciban props, **ENTONCES** recibiran las mismas cuatro: el
  contrato no distingue por remote.

### HU-08 — El shell corre en el entorno local

Como equipo, necesito abrir el shell en el navegador y llegar a los microservicios.

- **CUANDO** se instalen las dependencias, **ENTONCES** sera con
  `docker run --rm -v "${PWD}:/app" -w /app node:20-alpine npm install`, nunca con `npm` en
  el host (`CLAUDE.md` §1).
- **CUANDO** se levante el entorno, **ENTONCES** el shell correra como servicio `shell` de
  `docker-compose.yml`, con imagen `node:20-alpine`, `webpack serve`, el puerto `3000:3000` y
  `depends_on` de `ms-usuarios`, `ms-canchas`, `ms-reservas` y `ms-reportes` (P-06).
- **CUANDO** el shell este servido, **ENTONCES** respondera en `http://localhost:3000` y
  cargara sin errores en la consola del navegador.
- **CUANDO** el shell llame a `/api/usuarios/sesiones` desde el navegador, **ENTONCES** el
  `devServer.proxy` reenviara la peticion a `http://ms-usuarios:8080` por la red de Docker y
  el inicio de sesion funcionara de punta a punta contra el usuario ADMIN del seed (P-02).
- **CUANDO** se configure el `devServer`, **ENTONCES** cada prefijo de `/api` apuntara al
  **nombre de contenedor** de su microservicio, y ninguno a `localhost:8082`–`8085`: el proxy
  corre dentro de la red de Docker (§3).
- **CUANDO** se declaren los remotes, **ENTONCES** seguiran usando URLs de **navegador**
  (`http://localhost:3001/remoteEntry.js`): el navegador esta fuera de la red de Docker y no
  resuelve nombres de contenedor (§3, HU-06).
- **CUANDO** se agregue el servicio al `docker-compose.yml`, **ENTONCES** ese sera el **unico**
  archivo modificado fuera de `frontend/shell`, junto a los dos del contrato ya aplicados
  (§10).

Nota para `design.md` (advertencia del responsable, 23/08/2026): corriendo dentro de un
contenedor, el `devServer` necesita `host: "0.0.0.0"` y `allowedHosts: "all"`, o el navegador
del host no alcanza el servidor. Es el equivalente frontend del `pg_isready` del healthcheck
de PostgreSQL. El `design.md` debe escribir esas dos opciones junto a la tabla de §3, para que
quede claro de un vistazo que el `devServer` mira hacia dos lados a la vez: escucha en
`0.0.0.0` para el navegador del host y proxya hacia nombres de contenedor dentro de la red de
Docker.

## 5. Reglas de negocio cubiertas

| ID | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora | No aplica — el shell no crea ni muestra reservas |
| RN-02 | No se puede reservar un bloque ya ocupado | No aplica — la valida `ms-reservas` (spec 04) y la presenta `mf-reservas` |
| RN-03 | El usuario cancela solo las suyas; el admin cualquiera | No aplica — el shell no muestra reservas. Tras C-2 el menu ya no distingue roles en el modulo Reservas: el reparto lo aplica `ms-reservas` con el token, y la pantalla de cancelacion global vive en `mf-administracion` |
| RN-04 | Solo se cancela una reserva que aun no ha ocurrido | No aplica — es de `ms-reservas` |
| RN-05 | Cancelar libera el bloque | No aplica — es de `ms-reservas` |
| RN-06 | Limite configurable de reservas activas por usuario | No aplica — es de `ms-reservas` |
| RN-07 | Solo el admin gestiona canchas y su horario | **Cubierta parcialmente, solo como navegacion** — el menu de Administracion se ofrece unicamente a `rol = ADMIN` (HU-05); la autorizacion real la aplica `ms-canchas` con el token |
| RN-08 | Estados `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | No aplica — el shell no muestra reservas ni sus estados |

El shell **no implementa ninguna regla de negocio**: es layout, autenticacion y orquestacion
(PDF §4.1). Lo unico que hace con RN-07 es no ofrecer al `USUARIO` un menu que su rol no puede
usar, y ocultar un menu no sustituye al `403` del microservicio.

## 6. Contrato REST consumido

Nombres tomados literalmente de `docs/contratos/README.md`. El shell consume **solo dos
rutas**, las dos publicas de `ms-usuarios`. Las demas rutas del sistema las consumen los
remotes en sus propias specs.

### 6.1 Rutas

| Verbo | Ruta | Rol | Respuestas | Historia |
|---|---|---|---|---|
| POST | `/api/usuarios/sesiones` | publico | 200, 400, 401 | HU-01 |
| POST | `/api/usuarios` | publico | 201, 400, 409 | HU-02 |

El shell **no** consume `GET /api/usuarios` ni `PATCH /api/usuarios/{usuarioId}/estado`: la
gestion de usuarios es una pantalla del modulo Administracion (PDF §3.2), no del shell.

### 6.2 Campos

| Concepto | Campo | Tipo / valores | Uso en el shell |
|---|---|---|---|
| Nombre de usuario | `nombre` | string | request de registro, cabecera del layout y prop `usuario` |
| Correo de acceso | `email` | string | request de registro e inicio de sesion |
| Contraseña | `password` | string — **solo en request, NUNCA en respuesta** | request de registro e inicio de sesion |
| Token de sesion | `token` | string | `Authorization: Bearer <token>`, `sessionStorage` y prop `token` |
| Usuario de la sesion | `usuario` | objeto `UsuarioResponse` | origen de la prop `usuario` |
| Identificador de usuario | `usuarioId` | number | campo de la prop `usuario` (P-01) |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | filtro del menu (HU-05) y campo de la prop `usuario` |
| Usuario activo | `activo` | boolean | se recibe; el shell no decide nada con el y no lo propaga |
| Codigo de error | `codigo` | ver tabla "Formato de error" | selecciona el mensaje que se muestra |
| Mensaje de error | `mensaje` | string | se muestra tal cual al usuario |

### 6.3 Payload de respuesta consumido

`LoginResponse`, congelado:

```json
{
  "token": "...",
  "usuario": { "usuarioId": 1, "nombre": "Ana", "email": "ana@demo.ec", "rol": "USUARIO", "activo": true }
}
```

### 6.4 Codigos de error que el shell interpreta

| Situacion | HTTP | `codigo` | Que hace el shell |
|---|---|---|---|
| Validacion de entrada | 400 | `DATOS_INVALIDOS` | muestra `mensaje` en el formulario |
| Credenciales invalidas | 401 | `NO_AUTENTICADO` | en el inicio de sesion, muestra `mensaje` y se queda en el formulario |
| Token vencido o invalido en una llamada ya autenticada | 401 | `NO_AUTENTICADO` | cierra la sesion y avisa "sesion expirada" (P-08) |
| Sin permiso | 403 | `SIN_PERMISO` | muestra `mensaje`; no deberia ocurrir en las dos rutas publicas |
| Email ya registrado | 409 | `EMAIL_DUPLICADO` | muestra `mensaje` junto al campo `email` |
| Error no previsto en el servidor | 500 | `ERROR_INTERNO` | muestra `mensaje` y deja reintentar |

### 6.5 Contrato Module Federation

| Microfrontend | Nombre | Modulo expuesto | Puerto |
|---|---|---|---|
| shell | `shell` (host) | — | 3000 |
| mf-reservas | `mfReservas` | `./ReservasApp` | 3001 |
| mf-administracion | `mfAdministracion` | `./AdminApp` | 3002 |
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

Props que el shell entrega a todo remote, **tal como quedaron el 23/08/2026** (C-1, P-01):

```jsx
<RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

## 7. Dependencias de esta spec

| Depende de | Estado | Para que |
|---|---|---|
| `ms-usuarios` (spec 02) | cerrada y levantada | `POST /api/usuarios/sesiones` y `POST /api/usuarios` |
| `ms-canchas`, `ms-reservas`, `ms-reportes` | cerradas y levantadas | solo como destinos del `devServer.proxy` y como `depends_on` del servicio `shell`; el shell no llama ninguna de sus rutas |
| `mf-reservas`, `mf-administracion`, `mf-reportes` | **no existen** | HU-06 se verifica con el borde de error; la carga real de un remote se verifica en la spec 07 (P-04) |

## 8. Decision sobre el acceso a `/api` y el gateway (P-02)

`docker-compose.yml` dice hoy, en los cuatro microservicios, que el mapeo de puertos
`8082`–`8085` es "TEMPORAL para probar con `curl.exe`; se elimina cuando exista el gateway
Nginx". Esa es la unica mencion del gateway en el repositorio y **ninguna spec lo ha creado**.

**Decision del 23/08/2026:** por ahora, `devServer.proxy` en el `webpack.config.js` del shell
y, mas adelante, de cada remote. El destino de cada prefijo es el **nombre de contenedor** del
microservicio (`http://ms-usuarios:8080` y los tres restantes, §3), porque `webpack serve`
corre dentro de la red de Docker. El gateway Nginx se crea como **trabajo aparte, en la
seccion 5 de integracion**, cuando existan los cuatro microfrontends.

**Motivo:** las rutas relativas `/api` funcionan igual en los dos modos, asi que pasar al
gateway no obliga a tocar codigo de aplicacion. El unico archivo que cambia entonces es el
`webpack.config.js` de cada microfrontend, y los mapeos `8082`–`8085` de `docker-compose.yml`
se eliminan en ese mismo momento, como ya anuncian sus comentarios.

**Fecha de revision:** cuando los tres remotes esten entregados y arranque la seccion 5 de
integracion. Hasta entonces, ninguna spec vuelve a discutir este punto.

## 9. Decisiones tomadas (P-01 a P-09 y correcciones C-1 y C-2, respondidas el 23/08/2026)

**C-1 — El `token` si va en las props.** El contrato de props suma `token`:
`<RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />`.
Motivo: sin `token`, ningun remote puede llamar a la API. Resolverlo aqui evita que las tres
specs de remotes lo inventen cada una a su manera. Cambio aplicado en
`docs/contratos/README.md` y `CLAUDE.md` §5 (§10).

**C-2 — El `ADMIN` si ve el modulo Reservas.** La decision D-08 de la spec 04 ya establecio
que el `ADMIN` puede crear reservas y tiene historial propio, sin `403`. El `ADMIN` ve los
tres modulos; el `USUARIO`, solo Reservas. HU-05 y §5 quedaron corregidas.

**P-01 — Contradiccion en la prop `usuario`. Salida (b):** el contrato de props se corrige a
`usuarioId`. Un mismo concepto con dos nombres es exactamente lo que el contrato congelado
existe para evitar. Cambio aplicado junto al de C-1.

**P-02 — Acceso a `/api`. Salida (a) por ahora:** `devServer.proxy` en el `webpack.config.js`
del shell y de cada remote, apuntando a los **nombres de contenedor** de los cuatro
microservicios (`http://ms-usuarios:8080`, `ms-canchas`, `ms-reservas`, `ms-reportes`), no a
los puertos `8082`–`8085` del host: el proxy lo ejecuta `webpack serve` dentro de la red de
Docker, mientras las URLs de los remotes siguen siendo del navegador (tabla de §3). El gateway
Nginx queda para la seccion 5 de integracion. Motivo y fecha de revision en §8.

**P-03 — Persistencia del token. Salida (a): `sessionStorage`.** `localStorage` sobrevive al
cierre del navegador y un JWT de 8 horas no deberia; solo en memoria obliga a reloguear en
cada F5 y molesta en la demo.

**P-04 — Alcance frente a los remotes. Salida (a):** esta spec entrega **solo** el shell.
HU-06 se verifica con el borde de error; la carga real de un remote se verifica en la spec 07.

**P-05 — Navegacion. Salida (a):** sin enrutador. El modulo activo es estado de React y la URL
no cambia.

**P-06 — Puesta en marcha. Salida (a):** `webpack serve` en un contenedor `node:20-alpine`
declarado en `docker-compose.yml`, puerto `3000:3000`.

**P-07 — Registro en el shell. Confirmado:** registro e inicio de sesion viven en el shell.

**P-08 — `401` en una llamada ya autenticada. Salida (a):** cerrar la sesion y volver a la
pantalla de inicio con un aviso de sesion expirada.

**P-09 — Vista inicial. Salida (c):** pantalla de bienvenida del shell con el menu. No se
elige por el usuario que modulo abrir primero.

## 10. Archivos ya modificados fuera de esta spec

Las respuestas a C-1 y P-01 obligaron a tocar el contrato, y el cambio **ya esta aplicado**
(autorizado de forma explicita por el responsable el 23/08/2026):

| Archivo | Cambio | Origen |
|---|---|---|
| `docs/contratos/README.md` | Seccion "Contrato Module Federation": las props suman `token` y el identificador pasa de `id` a `usuarioId` | C-1, P-01 |
| `docs/contratos/README.md` | Nueva fila en el "Registro de cambios" con fecha 23/08/2026 | C-1, P-01 |
| `CLAUDE.md` §5 | El mismo contrato de props, para que las dos fuentes no se contradigan | C-1, P-01 |

Queda pendiente de la ejecucion de las tareas un unico archivo mas fuera de
`frontend/shell`: el servicio `shell` en `docker-compose.yml` (E-14, P-06). Nada de `backend/`
ni de `infra/` se toca.

## 11. Fuera de alcance de esta spec

- **Los tres remotes** `mf-reservas`, `mf-administracion` y `mf-reportes`: sus pantallas, sus
  capas `api/`, sus `webpack.config.js` y sus `ModuleFederationPlugin` con `exposes`. Esta spec
  solo los **declara** como remotes del host (P-04).
- Toda pantalla de negocio: consulta de disponibilidad, nueva reserva, mis reservas, gestion de
  canchas, gestion de bloqueos, gestion global de reservas, gestion de usuarios y reportes.
- Los cuatro microservicios del backend: esta spec **no modifica ni una linea** de `backend/`.
- El DDL, el seed y cualquier archivo de `infra/postgres/`.
- **El gateway Nginx**: queda para la seccion 5 de integracion, con la decision y la fecha de
  revision escritas en §8.
- Eliminar los mapeos de puerto `8082`–`8085` de `docker-compose.yml`: se hace cuando exista el
  gateway, no ahora.
- Cambiar `docs/contratos/README.md` mas alla del cambio de C-1 y P-01 ya aplicado (§10).
- Enrutador, gestor de estado global, libreria de UI, TypeScript, tema oscuro, i18n y cualquier
  dependencia npm que no exija React 18 + Webpack 5 + Module Federation (P-05).
- Pruebas automatizadas de frontend: ninguna spec anterior las incluyo y el PDF §5 no las pide
  como entregable.
- Recuperacion de contraseña, edicion de perfil y refresco de token: el contrato no declara
  ninguna de esas rutas. Un token vencido termina en cierre de sesion (P-08).
- Diseño responsive avanzado, animaciones y accesibilidad mas alla de HTML semantico: la
  rubrica del PDF §6 no las puntua.

---

## Supuestos

**Sin supuestos.** Los nueve datos que faltaban se preguntaron como P-01 a P-09 y estan
respondidos en §9; ninguno se relleno con un valor inventado.

De las tres deducciones que traia la primera version, el responsable corrigio dos —el `token`
en las props (C-1) y el acceso del `ADMIN` al modulo Reservas (C-2)— y confirmo la tercera:
el modulo Seguridad vive en el shell (P-07). Las tres quedan incorporadas al cuerpo del
documento, no como supuestos pendientes.
