# Spec 06 — shell (host de Module Federation) · tasks.md

Base: `requirements.md` (C1 aprobado 23/08/2026) y `design.md` (C2 aprobado 23/08/2026).

Reglas de ejecucion: **una tarea a la vez**. Al terminar cada tarea se ejecuta su comando de
verificacion, se reporta el resultado literal y se espera aprobacion. Ninguna tarea encadena la
siguiente. Cada tarea deja el shell compilando y sirviendo en `http://localhost:3000`.

Todos los comandos se ejecutan en PowerShell desde la raiz del repositorio
(`proyecto-canchas`). En esta maquina no hay Node ni npm: todo pasa por Docker (`CLAUDE.md`
§1). Se usa `curl.exe`, no `curl`, y `Copy-Item`, no `cp`.

## Diferencia con las cinco specs anteriores

No hay `mvn clean package`: un microfrontend no se compila a un `.jar`. La compilacion la hace
`webpack serve` **dentro del contenedor**, de forma continua, y la prueba de que compilo es su
propio registro:

```powershell
docker compose logs --tail=30 shell
```

Un cambio correcto imprime `compiled successfully`; un error de sintaxis o un `import` roto
imprime `ERROR in ...` y el mismo registro dice el archivo y la linea. Por eso **ninguna tarea
necesita reconstruir la imagen**: el codigo esta montado por volumen (D-03) y el servidor
recompila al guardar. Solo T1 crea el servicio.

Credenciales del seed usadas en las verificaciones (`infra/postgres/05-seed.sql`):
`admin@canchas.ec` / `Admin123` (ADMIN) y `usuario@canchas.ec` / `Usuario123` (USUARIO).

Antes de T1, el resto del entorno debe estar arriba:

```powershell
docker compose up -d
docker compose ps
```

## T1 — Andamiaje del shell, servicio en Docker Compose y proxy de `/api`

**Que hace.** Crea el proyecto `frontend/shell` con las doce dependencias exactas de D-01:
`package.json`, `.babelrc`, `public/index.html`, `webpack.config.js` con el
`ModuleFederationPlugin` del host (`name: "shell"`, sin `exposes`, los tres remotes con URLs de
navegador, `react` y `react-dom` en `shared` con `singleton: true`, `publicPath: "auto"`,
`uniqueName: "shell"`) y el `devServer` completo: `port: 3000`, `host: "0.0.0.0"`,
`allowedHosts: "all"`, `client.webSocketURL`, y el `proxy` como **arreglo** de cuatro entradas
hacia `http://ms-usuarios:8080`, `ms-canchas`, `ms-reservas` y `ms-reportes` (D-02). Crea
`src/index.js` con solo `import("./bootstrap")`, `src/bootstrap.jsx` y un `src/App.jsx` que de
momento pinta un texto fijo. Agrega el servicio `shell` a `docker-compose.yml` segun §9 del
diseño: `node:20-alpine`, comando `npm install` + `webpack serve`, volumen del codigo mas volumen
anonimo en `/app/node_modules`, `3000:3000` y `depends_on` de los cuatro microservicios.

**Cubre.** E-01, E-02, E-09, E-13, E-14; HU-06 (solo la configuracion de los remotes), HU-08;
decisiones D-01, D-02, D-03, D-14. Ninguna RN: el shell no implementa reglas de negocio (C1 §5).

**Verificacion.**

```powershell
docker compose up -d shell
docker compose logs --tail=40 shell
curl.exe -i http://localhost:3000
curl.exe -i -X POST http://localhost:3000/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
```

El registro debe decir `compiled successfully`; el primer `curl.exe` devuelve `200` con el HTML
del shell; el segundo devuelve `200` con `token` y `usuario`, lo que prueba que el proxy alcanza
`ms-usuarios` **por nombre de contenedor** sin abrir el navegador.

## T2 — Capa `api/`, modulo de sesion y bloque de error

**Que hace.** Crea `src/api/clienteApi.js` como unica pieza que llama `fetch`: rutas relativas
bajo `/api`, encabezado `Authorization: Bearer <token>` cuando se le pasa un token, y
normalizacion de toda respuesta de error a `{ codigo, mensaje }`, sintetizando
`ERROR_INTERNO` cuando el cuerpo no viene en el formato del contrato (D-04). Crea
`src/api/usuariosApi.js` con `iniciarSesion(email, password)` y
`registrarUsuario(nombre, email, password)`. Crea `src/sesion/almacenSesion.js` con `leer()`,
`guardar(sesion)` y `borrar()` sobre las dos claves `canchas.token` y `canchas.usuario` de
`sessionStorage`, validando lo que encuentra y borrando ambas si esta corrupto (D-06). Crea
`src/components/MensajeError.jsx`. `clienteApi` **no** interpreta el `401`: lo devuelve y decide
el llamador (D-07).

**Cubre.** E-05, E-06 (la mitad de almacenamiento); HU-03 (encabezado, rutas relativas y
claves de `sessionStorage`); §5.4 y §7 del diseño.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
```

Debe decir `compiled successfully`, sin `ERROR in`. Todavia no hay pantalla que llame a estos
modulos: el criterio de esta tarea es que el proyecto siga compilando con la capa nueva dentro.

## T3 — HU-01: inicio de sesion y rehidratacion de la sesion

**Que hace.** Crea `src/components/PantallaSesion.jsx` con el formulario de `email` y
`password`, la validacion de campos vacios de §5.1, el boton deshabilitado mientras la peticion
esta en curso y el mensaje de error del contrato. Convierte `App.jsx` en el dueño del estado
`sesion`, `vista` y `avisoSesion` (§4.1): arranca leyendo `almacenSesion.leer()`, mapea el
`LoginResponse` a `{ token, usuario: { usuarioId, nombre, rol } }` descartando `email` y
`activo` (D-09), guarda en `sessionStorage` y pasa a una vista provisional que solo muestra el
nombre del usuario. Sin sesion, pinta `PantallaSesion` y no descarga ningun remote.

**Cubre.** E-03, E-06; HU-01 completa; HU-03 (persistencia en `sessionStorage`, rehidratacion en
F5 y ausencia del `password` en el almacenamiento); decisiones D-08 y D-09.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
```

Luego, en el navegador, en `http://localhost:3000`:

1. Iniciar sesion con `admin@canchas.ec` / `Admin123`: entra y muestra el nombre
   `Administrador`.
2. Pulsar F5: sigue dentro, sin volver a pedir credenciales.
3. En la consola del navegador, `sessionStorage` tiene `canchas.token` y `canchas.usuario`, y
   `canchas.usuario` **no** trae `email`, `activo` ni `password`.
4. Iniciar sesion con una contraseña incorrecta: muestra el `mensaje` del `401` y conserva el
   `email` escrito.
5. Enviar con `email` vacio: no sale ninguna peticion en la pestaña de red.

## T4 — HU-02: registro de un usuario nuevo

**Que hace.** Crea `src/components/PantallaRegistro.jsx` con los campos `nombre`, `email` y
`password`, la validacion de §5.2 y el manejo de `409 EMAIL_DUPLICADO` junto al campo `email`.
Agrega en `PantallaSesion` el enlace que lleva a `vista = "registro"` y el camino de vuelta. Tras
un `201`, vuelve a `vista = "sesion"` con el aviso de registro correcto y **sin** abrir sesion.

**Cubre.** E-04; HU-02 completa; §5.2 y §7 del diseño.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
```

En el navegador:

1. Registrar un correo nuevo: aparece el aviso de registro correcto y la pantalla de inicio de
   sesion, sin sesion abierta.
2. Iniciar sesion con ese correo: entra con `rol = USUARIO`.
3. Registrar el mismo correo otra vez: muestra el `mensaje` del `409` junto al campo `email`.

Comprobacion del lado del servidor, para verificar que el usuario se creo de verdad:

```powershell
docker compose exec postgres psql -U usuarios_user -d usuarios_db -c "SELECT usuario_id, email, rol, activo FROM usuario ORDER BY usuario_id"
```

## T5 — HU-04, HU-05 y P-08: layout, menu por rol, bienvenida y cierre de sesion

**Que hace.** Crea `src/components/Cabecera.jsx` (nombre, `rol` y boton de cerrar sesion),
`src/components/MenuModulos.jsx` (los tres modulos para `ADMIN`, solo Reservas para `USUARIO`,
segun §6.2) y `src/components/PantallaBienvenida.jsx` como vista inicial de los dos roles
(P-09). Agrega a `App.jsx` la funcion `cerrarSesion` —borra estado y las dos claves de
`sessionStorage`, vuelve a `vista = "sesion"`, sin ninguna llamada HTTP—, la validacion de rol
antes de montar un modulo y el aviso de rol no reconocido. Conecta el `401` de una llamada ya
autenticada a `cerrarSesion` con el aviso "Su sesion expiro" (P-08).

**Cubre.** E-07, E-08; HU-04 y HU-05 completas; HU-03 (cierre ante `401`); RN-07 en su unica
parte de navegacion: el menu de Administracion solo se ofrece a `rol = ADMIN`; decision D-07.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
```

En el navegador:

1. Entrar como `admin@canchas.ec`: la vista inicial es la bienvenida y el menu ofrece los
   **tres** modulos (C-2).
2. Entrar como `usuario@canchas.ec` / `Usuario123`: el menu ofrece **solo** Reservas.
3. Cerrar sesion: vuelve al inicio de sesion, y en la consola del navegador `sessionStorage`
   quedo sin `canchas.token` ni `canchas.usuario`.
4. En la pestaña de red, el cierre de sesion no genera ninguna peticion.
5. Con sesion abierta, borrar a mano `canchas.usuario` en `sessionStorage` y pulsar F5: vuelve
   al inicio de sesion en lugar de quedar en un estado roto (D-06).

## T6 — HU-06 y HU-07: contenedor de remotes, borde de error y props del contrato

**Que hace.** Crea `src/components/ContenedorRemoto.jsx`, que resuelve el `React.lazy` de cada
remote desde un mapa creado **una sola vez** a nivel de modulo (D-11), lo envuelve en `Suspense`
con el texto de carga y le entrega las cuatro props del contrato:
`usuario={{ usuarioId, nombre, rol }}`, `token`, `apiBaseUrl="/api"` y `onLogout`. Crea
`src/components/BordeError.jsx`, unico componente de clase, con `componentDidCatch`, que pinta
"Modulo no disponible" dentro del layout sin tumbar la sesion (D-10). Conecta el menu de T5 a
las tres vistas de modulo.

**Cubre.** E-10, E-11; HU-06 y HU-07 completas; decisiones D-10, D-11, D-12.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
```

En el navegador, como `ADMIN`:

1. Entrar a cada uno de los tres modulos: cada uno muestra "Modulo no disponible", porque los
   tres remotes no existen todavia (P-04). **Este es el criterio de aceptacion de HU-06 en esta
   spec**, no un fallo.
2. La cabecera, el menu y la sesion siguen vivos despues del error, y se puede cambiar de modulo
   y volver a la bienvenida.
3. En la pestaña de red, la peticion fallida a `http://localhost:3001/remoteEntry.js` sale hacia
   **`localhost:3001`** y no hacia un nombre de contenedor: confirma la tabla de §3.3.
4. La consola no muestra ningun error de "two instances of React".

## T7 — Estilos, revision del contrato y verificacion integral

**Que hace.** Crea `src/estilos.css` —unico archivo de estilos, importado desde
`bootstrap.jsx` (D-13)— y lo aplica al layout, al menu y a los formularios: CSS plano, sin
librerias de UI. **Corrige un defecto detectado durante T4** (senalado por el responsable el
23/08/2026): al reenviar un formulario, el error del servidor debe limpiarse **antes** de
validar los campos, porque hoy el `401` anterior convive con los avisos de campo obligatorio.
Afecta a `PantallaSesion` y a `PantallaRegistro`, que hacen `setError(null)` despues de la
validacion en lugar de antes. Cierra la spec con la revision del §1 del diseño: que las props entregadas sean
exactamente `usuario={{ usuarioId, nombre, rol }}`, `token`, `apiBaseUrl` y `onLogout`, y que
ningun nombre de campo se haya desviado del contrato. Sin cambios de comportamiento.

**Cubre.** E-12; §1 y §10 del diseño; cierre de HU-01 a HU-08.

**Verificacion.**

```powershell
docker compose logs --tail=30 shell
docker compose ps
curl.exe -i http://localhost:3000
curl.exe -i -X POST http://localhost:3000/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"usuario@canchas.ec\",\"password\":\"Usuario123\"}"
```

Y el recorrido completo en el navegador, de una sola pasada: registro, inicio de sesion como
`USUARIO` con un solo modulo en el menu, cierre de sesion, inicio de sesion como `ADMIN` con los
tres modulos, "Modulo no disponible" en cada uno, F5 con la sesion viva y cierre de sesion final.
Ningun error en la consola del navegador y ningun `ERROR in` en el registro del contenedor.

## Lo que ninguna tarea hace

- Crear los remotes `mf-reservas`, `mf-administracion` y `mf-reportes` (P-04): son de las specs
  siguientes.
- Crear el gateway Nginx ni quitar los mapeos `8082`–`8085` de `docker-compose.yml`: seccion 5
  de integracion (§8 del C1).
- Tocar `backend/`, `infra/` o `docs/contratos/README.md`: el cambio del contrato de props se
  aplico durante la compuerta C1 y no se repite.
- Agregar un `Dockerfile` al shell (D-03), pruebas automatizadas, enrutador o cualquier
  dependencia fuera de las doce de D-01.
