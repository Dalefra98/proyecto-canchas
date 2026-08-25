# Spec 08 — mf-administracion (remote de Module Federation) · tasks.md

Base: `requirements.md` (C1 aprobado 24/08/2026) y `design.md` (C2 aprobado 24/08/2026).

Reglas de ejecución: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificación, se reporta el resultado literal y se espera aprobación. Ninguna tarea encadena la
siguiente. Cada tarea deja el remote compilando y sirviendo en `http://localhost:3002`, el shell
funcionando en `http://localhost:3000` y `mf-reservas` intacto en `http://localhost:3001`.

Todos los comandos se ejecutan en PowerShell desde la raíz del repositorio
(`proyecto-canchas`). En esta máquina no hay Node ni npm: todo pasa por Docker (`CLAUDE.md` §1).
Se usa `curl.exe`, no `curl`.

**Ocho tareas.** P-09 fijó "una tarea por pantalla, más andamiaje y Compose: unas siete". Son
ocho porque el panel de bloqueos, aunque vive **dentro** de la pantalla de Canchas (P-01), son
tres operaciones con su propio formulario y su propia confirmación: meterlo en la misma tarea que
el ABM de canchas daría un commit demasiado grande para revisar.

## Cómo se verifica un microfrontend en esta spec

**Siempre con `--timestamps`.** `webpack serve` compila de forma continua y su registro no se
limpia entre tareas: sin la marca de tiempo, el `compiled successfully` de la tarea anterior se
lee como una compilación nueva. La bitácora ya registró ese engaño (T3 de la spec 06). Antes de
leer el resultado hay que comprobar que la marca sea **posterior** al último guardado.

No hay `mvn clean package`: la compilación la hace `webpack serve` **dentro del contenedor**, de
forma continua, y la prueba de que compiló es su propio registro:

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
```

`compiled successfully` significa que compiló; `ERROR in ...` dice el archivo y la línea. El
código está montado por volumen (§9 del diseño), así que **ninguna tarea reconstruye la imagen**:
solo T1 crea el servicio.

Orden de verificación de cada tarea que lo permita:

1. `curl.exe http://localhost:3002/remoteEntry.js` → `200`. Si falla, el problema es del remote y
   no de la integración.
2. `curl.exe http://localhost:3001/remoteEntry.js` → `200`: el segundo remote no puede romper al
   primero (HU-12).
3. El recorrido en el navegador desde `http://localhost:3000`, con un usuario **ADMIN**.

Un `compiled successfully` **no** basta: en un microfrontend el registro verifica que el código
compila, no que funcione (bitácora, T5 de la spec 06). Toda tarea con interacción lleva su paso
de navegador.

Credenciales del seed (`infra/postgres/05-seed.sql`): `admin@canchas.ec` / `Admin123` (ADMIN) y
`usuario@canchas.ec` / `Usuario123` (USUARIO). El módulo Administración solo aparece en el menú
del shell para el ADMIN.

Antes de T1, el resto del entorno debe estar arriba:

```powershell
docker compose up -d
docker compose ps
```

## T1 — Andamiaje del remote, servicio en Docker Compose y `remoteEntry.js`

**Qué hace.** Crea el proyecto `frontend/mf-administracion` con las doce dependencias exactas de
§3.4 (las mismas versiones del shell y de `mf-reservas`, sin agregar ninguna): `package.json`,
`.babelrc`, `public/index.html` y `webpack.config.js` con el `ModuleFederationPlugin` del remote
(`name: "mfAdministracion"`, `filename: "remoteEntry.js"`,
`exposes: { "./AdminApp": "./src/AdminApp" }`, sin `remotes`, `react` y `react-dom` en `shared`
con `singleton: true`, `publicPath: "auto"`, `uniqueName: "mfAdministracion"`), el `devServer`
completo de §3.2 (`port: 3002`, `host: "0.0.0.0"`, `allowedHosts: "all"`, `headers` con
`Access-Control-Allow-Origin: *`, `client.webSocketURL` hacia `ws://localhost:3002/ws`,
`client.overlay.runtimeErrors: false` y el `proxy` como **arreglo** de las cuatro entradas de
§3.3) y `watchOptions` con `poll: 1000` en la raíz de la configuración. Crea `src/index.js` con
solo `import("./bootstrap")`, `src/bootstrap.jsx` con el **aviso estático** de D-02 y un
`src/AdminApp.jsx` que de momento pinta un texto fijo con el `nombre` recibido por prop. Agrega
el servicio `mf-administracion` a `docker-compose.yml` según §9: `node:20-alpine`,
`container_name: canchas-mf-administracion`, comando `npm install` + `webpack serve`, volumen del
código más volumen anónimo `mf_administracion_node_modules`, `3002:3002` y `depends_on` de
`ms-usuarios`, `ms-canchas` y `ms-reservas` con `condition: service_started` (P-08). **No** se
tocan los servicios `shell` ni `mf-reservas`.

**Cubre.** E-01 a E-07 (versión mínima), E-11; HU-11 (configuración y publicación del
`remoteEntry.js`), HU-12; decisiones D-01, D-02, D-03. Ninguna RN: el remote no implementa reglas
de negocio (C1 §5).

**Verificación.**

```powershell
docker compose up -d mf-administracion
docker compose logs --timestamps --tail=40 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
curl.exe -i http://localhost:3002
curl.exe -i http://localhost:3001/remoteEntry.js
```

El registro debe decir `compiled successfully`; el primer `curl.exe` devuelve `200` con el
`remoteEntry.js`; el segundo devuelve `200` con el HTML del aviso estático; el tercero confirma
que `mf-reservas` sigue en pie. En el navegador, `http://localhost:3002` muestra ese aviso y
**no** una aplicación usable: es el comportamiento correcto de D-02, no un defecto.

## T2 — Capa `api/` y bloque de error

**Qué hace.** Crea `src/api/clienteApi.js` como **única** pieza que llama `fetch`: compone la URL
con el `apiBaseUrl` recibido, agrega `Authorization: Bearer <token>` con el token que se le pasa
por parámetro (D-05), no declara `Content-Type` cuando no hay cuerpo, devuelve `null` ante un
`204` y normaliza toda respuesta de error a `{ codigo, mensaje }`, sintetizando `ERROR_INTERNO`
cuando el cuerpo no viene en el formato del contrato (§5.10). Crea `src/api/canchasApi.js` con
`listarCanchas`, `crearCancha`, `editarCancha`, `cambiarEstadoCancha`, `listarBloqueos`,
`crearBloqueo` y `eliminarBloqueo`; `src/api/reservasApi.js` con `listarReservas` y
`cancelarReserva` —esta última con el `PATCH` **sin cuerpo** (§5.5)—; y `src/api/usuariosApi.js`
con `listarUsuarios` y `cambiarEstadoUsuario`. Crea `src/components/MensajeError.jsx`, que pinta
`{ codigo, mensaje }` sin interpretarlo. `clienteApi` no decide nada sobre el `401`: lo devuelve
normalizado y quien decide es `AdminApp` en T3 (§4.1).

**Cubre.** E-08; §5.1 a §5.11 y §7 del diseño; §6.1 (las once rutas quedan declaradas en un solo
lugar); D-03, D-04, D-05; HU-13 (forma única de error).

**Verificación.**

El `compiled successfully` del registro **no sirve como criterio de esta tarea**: ningún archivo
importa todavía esta capa y webpack solo compila lo que alcanza desde el `entry`. Se verifica que
esos cinco archivos compilan, pasándolos por Babel de forma explícita dentro del contenedor, con
el mismo `.babelrc` que usa `babel-loader`:

```powershell
docker compose exec mf-administracion node -e "const babel=require('@babel/core');const archivos=['src/api/clienteApi.js','src/api/canchasApi.js','src/api/reservasApi.js','src/api/usuariosApi.js','src/components/MensajeError.jsx'];for(const a of archivos){babel.transformFileSync(a);console.log('OK '+a);}"
```

Se usa `@babel/core` directamente y no `@babel/cli`, que **no** está entre las doce dependencias
de §3.4 y esta tarea no agrega ninguna. Debe imprimir las cinco líneas `OK` y salir sin error.
Esto prueba sintaxis y transformación de JSX, no comportamiento: la primera prueba real de la
capa `api/` es la carga del catálogo de T4.

## T3 — HU-10, HU-11 y HU-14: módulo expuesto, guardia de rol y navegación interna

**Qué hace.** Convierte `src/AdminApp.jsx` en el módulo expuesto definitivo: recibe
`{ usuario, token, apiBaseUrl, onLogout }` y ninguna prop más, guarda `vista` con valor inicial
`"canchas"` (§4.1), aplica la **guardia de rol** de §4.7 —si `usuario.rol !== "ADMIN"` pinta el
aviso de módulo no disponible y no monta nada más, sin disparar ni una llamada (P-07, D-18)— y
expone el envoltorio `ejecutar(operacion)` que devuelve `{ datos, error }` e invoca `onLogout()`
ante un `401` detectado por **estado HTTP** (§4.1, F-09). Crea
`src/components/NavegacionInterna.jsx` con las tres opciones Canchas, Reservas y Usuarios, y tres
componentes de pantalla **vacíos** (`PantallaCanchas`, `PantallaReservas`, `PantallaUsuarios`)
que de momento solo pintan su título: se llenan en T4, T6 y T7. Al cambiar de vista, la pantalla
anterior se desmonta (D-06).

**Cubre.** E-07, E-09 (esqueleto); HU-10, HU-11, HU-14; P-07; D-01, D-06, D-07, D-18.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
```

Después, en el navegador: iniciar sesión en `http://localhost:3000` con `admin@canchas.ec` /
`Admin123`, entrar al módulo **Administracion** y comprobar que se monta la pantalla del remote
**en lugar** del mensaje de módulo no disponible del borde de error del shell; que el menú
interno cambia entre las tres pantallas sin cambiar la URL; que la consola del navegador no
muestra errores de React duplicado ni de `hooks` inválidos (HU-11); y que entrar al módulo
Reservas con el mismo ADMIN sigue funcionando. Con `usuario@canchas.ec` / `Usuario123` el menú
del shell **no** debe ofrecer Administracion.

## T4 — HU-01 a HU-04: pantalla de Canchas, formulario y cambio de estado

**Qué hace.** Implementa `src/components/PantallaCanchas.jsx` con el estado de §4.2: carga
`GET /api/canchas` al montar, pinta la tabla con `canchaId`, `nombre`, `deporte`, `horaApertura`,
`horaCierre` y `activa` —incluidas las inactivas, que el ADMIN sí recibe— con aviso de carga y
aviso de listado vacío. Crea `src/components/FormularioCancha.jsx`, uno solo para alta y edición
distinguidos por `formulario.modo` (D-08), abierto **en la misma pantalla del listado** (P-10),
con los cuatro campos de §5.1: `nombre` con `maxLength` 80, `deporte` como `select` de los tres
valores del contrato, y `horaApertura` y `horaCierre` como `input type="time"`. El alta envía
`POST /api/canchas` y la edición `PUT /api/canchas/{canchaId}` con los cuatro campos y **sin**
`activa` (D-11 de la spec 03). El botón de estado envía `PATCH /api/canchas/{canchaId}/estado`
con el valor contrario al de la fila, **sin confirmación** (P-02, D-14). Tras cada escritura con
éxito se recarga el listado (D-15). Los errores se muestran según §7, con el formulario abierto
en `400` y `409`.

**Cubre.** HU-01, HU-02, HU-03, HU-04; RN-07 (presentada; la valida `ms-canchas`); flujos F-01,
F-02, F-03; D-08, D-10, D-14, D-15.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
```

Recorrido en el navegador como ADMIN, en la pantalla Canchas: ver el catálogo con las cuatro
canchas del seed; crear una cancha nueva y verla aparecer con `activa = true`; repetir el alta
con el **mismo nombre** y comprobar que se muestra el `mensaje` del `409 NOMBRE_DUPLICADO` con el
formulario abierto; editar su horario y ver el cambio en la tabla; inactivarla y volver a
activarla sin que aparezca ningún diálogo. Con `curl.exe` se contrasta que el backend recibió lo
que la pantalla dice:

```powershell
curl.exe -s -X POST http://localhost:8082/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
curl.exe -s http://localhost:8083/api/canchas -H "Authorization: Bearer <token>"
```

## T5 — HU-05 a HU-07: panel de bloqueos anidado y confirmación

**Qué hace.** Implementa `src/components/PanelBloqueos.jsx` con el estado de §4.3, montado
**dentro** de la pantalla de Canchas cuando hay una cancha seleccionada y recibiendo su
`canchaId` por prop, sin selector propio (P-01, D-17). Carga
`GET /api/canchas/{canchaId}/bloqueos` **sin** el parámetro `fecha` y sin filtro en la interfaz
(P-03). Crea `src/components/FormularioBloqueo.jsx` con los cuatro campos de §5.3 —`fecha` como
`input type="date"`, `horaInicio` y `horaFin` como `input type="time"`, `motivo` con `maxLength`
200— que envía `POST /api/canchas/{canchaId}/bloqueos` sin `canchaId` en el cuerpo. Crea
`src/components/DialogoConfirmacion.jsx`, reutilizable (D-12), y lo usa antes de
`DELETE /api/canchas/{canchaId}/bloqueos/{bloqueoId}`, mostrando `fecha`, franja y `motivo`; el
`204` no se intenta leer como JSON (§5.5). El remote **no** comprueba solapamientos por su cuenta:
espera el `409 BLOQUEO_DUPLICADO` (D-10).

**Cubre.** HU-05, HU-06, HU-07; RN-07; flujos F-04 y F-05; D-10, D-12, D-17.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
```

Recorrido en el navegador como ADMIN: abrir los bloqueos de una cancha; registrar uno para
mañana de 10:00 a 12:00 con un motivo; repetirlo igual y comprobar el `mensaje` del
`409 BLOQUEO_DUPLICADO`; pulsar eliminar, **rechazar** la confirmación y comprobar que el bloqueo
sigue ahí; volver a eliminar, confirmar y verlo desaparecer. Cruce con el módulo Reservas del
otro remote: la disponibilidad de esa cancha y esa fecha debe mostrar ocupados los bloques
10:00–11:00 y 11:00–12:00 mientras el bloqueo existe, y libres después de eliminarlo.

## T6 — HU-08: listado global de reservas, filtro y cancelación de cualquiera

**Qué hace.** Implementa `src/components/PantallaReservas.jsx` con el estado de §4.4: lanza
**en paralelo** `GET /api/reservas`, `GET /api/canchas` y `GET /api/usuarios` (D-13), pinta el
listado en cuanto llegan las reservas, en el orden recibido y sin reordenar (D-09 de la spec 04),
y resuelve `canchaId` a `nombre` y `deporte` y `usuarioId` a `nombre` buscando en las listas ya
cargadas, mostrando el identificador cuando no aparezcan (P-05, D-11). Si una carga de apoyo
falla, se pinta `errorApoyo` y las reservas se muestran igual. Agrega el filtro por `estado` con
"Todos" como valor inicial, aplicado **al pintar** y no sobre el estado (P-04, D-09). Ofrece
cancelar solo en las reservas `CONFIRMADA`, con `DialogoConfirmacion` mostrando cancha, `fecha` y
bloque, y envía `PATCH /api/reservas/{id}/cancelacion` **sin cuerpo**; tras el `200` recarga el
listado.

**Cubre.** HU-08; RN-03 (mitad de administrador), RN-04, RN-05, RN-08 (todas presentadas: las
valida `ms-reservas`); flujos F-06 y F-07; D-09, D-11, D-12, D-13.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
```

Recorrido en el navegador: con `usuario@canchas.ec` crear una reserva desde el módulo Reservas;
cerrar sesión y entrar como ADMIN a Administración → Reservas; comprobar que la reserva aparece
con el **nombre** del usuario y el **nombre** de la cancha, no con sus números; filtrar por
`CONFIRMADA`, `CANCELADA` y volver a "Todos"; cancelar esa reserva ajena confirmando el diálogo y
verla pasar a `CANCELADA` (RN-03); volver a entrar como ese usuario y comprobar en Mis reservas
que figura cancelada y que su bloque volvió a estar disponible (RN-05).

## T7 — HU-09: pantalla de Usuarios y autoinactivación con advertencia

**Qué hace.** Implementa `src/components/PantallaUsuarios.jsx` con el estado de §4.5: carga
`GET /api/usuarios` al montar y pinta `usuarioId`, `nombre`, `email`, `rol` y `activo`, sin
`password` en ninguna parte. Ofrece activar o inactivar según el `activo` de la fila, enviando
`PATCH /api/usuarios/{usuarioId}/estado` con `{ "activo": ... }` siempre presente (§5.4, D-14).
Cuando la fila es la del **propio administrador en sesión** —su `usuarioId` coincide con el de la
prop `usuario`—, la acción se ofrece igual pero pasa por `DialogoConfirmacion` con la advertencia
de P-06; en cualquier otra fila la llamada sale directa. Tras el `200` recarga el listado (D-15).
No hay alta, edición ni borrado de usuarios.

**Cubre.** HU-09; P-06; flujo F-08; D-12, D-14, D-15. Ninguna RN: la gestión de usuarios no está
en la tabla de reglas de negocio.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
```

Recorrido en el navegador como ADMIN, en la pantalla Usuarios: ver los usuarios del seed con su
`rol` y su `activo`; inactivar a `usuario@canchas.ec` **sin** diálogo y comprobar con `curl.exe`
que el cambio se persistió; volver a activarlo; pulsar inactivar en la **propia fila** del ADMIN
y comprobar que aparece la advertencia de P-06 y que **rechazarla** no hace ninguna llamada. La
autoinactivación no se confirma en la demo: dejaría el entorno sin ADMIN utilizable.

```powershell
curl.exe -s http://localhost:8082/api/usuarios -H "Authorization: Bearer <token>"
```

## T8 — Estilos, revisión del contrato y verificación integral

**Qué hace.** Crea `src/estilos.css` con CSS plano, todas las clases con el prefijo `mfa-`
(D-16), sin librerías de UI y sin tocar los estilos del shell ni los de `mf-reservas`. Revisa el
remote completo contra `docs/contratos/README.md` y §1 del diseño: que ningún campo se haya
renombrado, abreviado ni traducido; que ningún componente llame a `fetch` fuera de `src/api/`
(`CLAUDE.md` §4); que no haya `sessionStorage` ni `localStorage` en ninguna parte (HU-10); que
todo error se muestre por su `codigo` y con el `mensaje` recibido tal cual (HU-13); y que no
quede ninguna ruta consumida fuera de las once de §6.1.

**Cubre.** E-10; HU-13; D-16; el cierre de los criterios del PDF §7.2 y §7.5 declarado en §8 del
`requirements.md`.

**Verificación.**

```powershell
docker compose logs --timestamps --tail=30 mf-administracion
curl.exe -i http://localhost:3002/remoteEntry.js
curl.exe -i http://localhost:3001/remoteEntry.js
docker compose exec mf-administracion sh -c "echo '--- fetch en .jsx:'; grep -rn 'fetch(' src | grep '.jsx' ; echo '--- fetch en todo src:'; grep -rn 'fetch(' src ; echo '--- Storage:'; grep -rn 'Storage' src ; echo FIN"
```

El `grep` de `fetch(` sobre los `.jsx` no debe devolver ninguna línea —el único `fetch` vive en
`src/api/clienteApi.js`, que sí aparece en el segundo `grep`— y el de `Storage` tampoco.

El filtrado por extensión se hace con una tubería a un segundo `grep` y **no** con `--include`:
el `grep` de `node:20-alpine` es el de BusyBox y esa opción no existe ahí
(`grep: unrecognized option: include=*.jsx`). Anotado en `docs/bitacora.md`.

Recorrido integral en el navegador, en una sola sesión de ADMIN: entrar a Administración,
recorrer las tres pantallas, crear y eliminar un bloqueo, cancelar una reserva, cambiar el estado
de una cancha, volver a la pantalla anterior y comprobar que sus datos se recargan (HU-14);
entrar después al módulo Reservas y comprobar que sigue funcionando con el mismo React
(HU-11, HU-12).

## Lo que ninguna tarea hace

- **Tocar el shell**: ya declara este remote, ya restringe el módulo al ADMIN y ya entrega las
  cuatro props. Si algo pareciera obligar a modificarlo, se detiene la tarea y se avisa
  (`CLAUDE.md` §0.4).
- **Tocar `frontend/mf-reservas`, `backend/`, `infra/postgres/` o `docs/contratos/README.md`.**
  `docker-compose.yml` es el único archivo que se modifica fuera de
  `frontend/mf-administracion`, y solo en T1.
- **Reconstruir imágenes**: el código está montado por volumen; `docker compose build` no hace
  falta en ninguna tarea.
- **Agregar dependencias npm** más allá de las doce de §3.4.
- **Implementar reglas de negocio en el cliente**: no se valida el orden de las franjas, ni el
  solapamiento de bloqueos, ni la unicidad del nombre, ni si una reserva ya ocurrió. Todo eso
  llega resuelto del microservicio (D-10).
- **Crear archivos que nadie pidió**: ni `README`, ni notas, ni scripts auxiliares
  (`CLAUDE.md` §0.5).
