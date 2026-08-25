# Spec 10 — Gateway Nginx (sección 5 de integración) · requirements.md

Estado: **C1 — APROBADO** el 25/08/2026 ("Apruebo requisitos de la spec 10").

Esta spec cierra la decisión **P-02 de la spec 06**, que quedó escrita el 23/08/2026 con esta
fecha de revisión: *"cuando los tres remotes estén entregados y arranque la sección 5 de
integración"*. Los tres remotes están entregados (specs 07, 08 y 09), así que la revisión
corresponde ahora.

Las **nueve decisiones** de esta spec fueron tomadas por el responsable: cinco de alcance el
24/08/2026 (D-1 a D-5) y cuatro más el 25/08/2026, al responder las preguntas abiertas P-01 a
P-04 (D-6 a D-9). Todas están en §10 con su motivo, para la defensa del proyecto. Ninguna se
rellenó con un valor inventado (`CLAUDE.md` §0.1) y **no quedan preguntas abiertas**.

La compuerta C2 (`design.md`) fue **aprobada el 25/08/2026**. La ejecución de `tasks.md` sigue
la regla de una tarea a la vez, con parada y aprobación al terminar cada una (`CLAUDE.md` §0.3).

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`, `docker-compose.yml`,
`infra/nginx/shell.conf`, `infra/nginx/remote.conf`, los cuatro `frontend/*/webpack.config.js`,
`.claude/specs/06-shell-module-federation/requirements.md` §8 y §9 (P-02) y `docs/bitacora.md`.
Las citas de `docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` §4.2 y §4.4 las aportó el
responsable y **confirmó que son fieles** el 25/08/2026; coinciden con las referencias ya
registradas en los `requirements.md` de las specs 03, 04 y 05.

---

## 1. Objetivo

Crear el **gateway Nginx** que el PDF §4.2 describe como *"API Gateway o BFF simple como punto
de entrada único"*, en su alcance mínimo: **un solo proceso que recibe todo el tráfico `/api` de
la aplicación y lo reparte a los cuatro microservicios**.

Después de esta spec, ningún microfrontend conoce ya la dirección de un microservicio. Los
cuatro `devServer.proxy` apuntan a un único destino, `http://gateway:80`, y es el gateway quien
sabe que `/api/usuarios` es `ms-usuarios`, `/api/canchas` es `ms-canchas`, y así con los cuatro.

**Lo que esta spec NO cambia:** ni una línea de `src/` de ningún microfrontend, ni una línea de
`backend/`. Toda la aplicación llama a la API con rutas relativas bajo `/api` (`CLAUDE.md` §3), y
esas rutas funcionan igual antes y después. Ese fue exactamente el motivo escrito en §8 de la
spec 06 para poder aplazar esta decisión sin deuda.

**Opción A, decidida el 24/08/2026:** el gateway **solo enruta `/api`**. No sirve los estáticos
del shell ni de los remotes. El navegador sigue entrando por `http://localhost:3000` y los tres
remotes se siguen descargando de `http://localhost:3001|3002|3003`. La opción B —el gateway
como origen único también del navegador— fue evaluada y descartada (§10, D-1).

## 2. Entregables de la spec

| Entregable | Ruta | Fuente |
|---|---|---|
| E-01 | `infra/nginx/gateway.conf`: solo los cuatro `location /api/...`, con sus `proxy_set_header`; sin `location /` | D-5, D-9 |
| E-02 | `infra/nginx/shell.conf` **borrado** (su mitad `/api` se traslada a `gateway.conf`) | D-5, D-9 |
| E-03 | `infra/nginx/remote.conf` **borrado** | D-5 |
| E-04 | Servicio `gateway` en `docker-compose.yml`: imagen `nginx:alpine`, `gateway.conf` montado de solo lectura, `ports: 8090:80`, `depends_on` de los cuatro microservicios | PDF §4.2, §4.4, D-7 |
| E-05 | `devServer.proxy` de los cuatro `frontend/*/webpack.config.js`: **solo** cambia el `target` a `http://gateway:80`; cada uno conserva los prefijos que hoy declara | D-1, D-8 |
| E-06 | `depends_on` de los cuatro servicios de frontend en `docker-compose.yml`: pasan a depender de `gateway` | E-04 |
| E-07 | Comentarios de los mapeos `8082`–`8085` en `docker-compose.yml` **corregidos**: dejan de decir "se elimina cuando exista el gateway" | D-2 |

Archivos que esta spec toca, y ninguno más:

```
infra/nginx/gateway.conf                     (creado)
infra/nginx/shell.conf                       (borrado)
infra/nginx/remote.conf                      (borrado)
docker-compose.yml                           (servicio nuevo, depends_on, comentarios)
frontend/shell/webpack.config.js             (solo el target del devServer.proxy)
frontend/mf-reservas/webpack.config.js       (solo el target del devServer.proxy)
frontend/mf-administracion/webpack.config.js (solo el target del devServer.proxy)
frontend/mf-reportes/webpack.config.js       (solo el target del devServer.proxy)
```

## 3. Restricciones técnicas heredadas

| Aspecto | Valor | Fuente |
|---|---|---|
| Imagen del gateway | `nginx:alpine` | `CLAUDE.md` §1 (solo Docker) |
| Puerto interno | `80` | patrón de Nginx |
| Puerto publicado | `8090:80`, **solo para verificación y demostración** | D-7 |
| Nombre de servicio y de host en la red | `gateway` | esta spec |
| Archivo de configuración | `infra/nginx/gateway.conf` | D-9 |
| Destino de cada prefijo | nombre de contenedor `ms-*:8080` | P-02 de la spec 06 |
| Destino del proxy de los frontends | `http://gateway:80` (nombre de contenedor: lo ejecuta `webpack serve` dentro de la red de Docker) | D-1 |
| Prefijos del proxy de los frontends | **los que cada uno declara hoy**, sin cambios | D-8 |
| URLs de los remotes en el shell | **sin cambios**: `http://localhost:3001|3002|3003/remoteEntry.js`, URLs de navegador | `CLAUDE.md` §3 |
| Puertos `3000`–`3003` | **sin cambios**, siguen publicados | D-1 |
| Puertos `8082`–`8085` | **se conservan** | D-2 |
| Puertos `8081` (adminer) y `5432` (postgres) | **se conservan**, fuera del alcance del gateway | D-4 |
| Modo de ejecución de los frontends | `webpack serve` en `node:20-alpine` con el código montado por volumen | P-06 de la spec 06 |
| Configuración montada | `infra/nginx/gateway.conf` como volumen de **solo lectura**, sin `Dockerfile` propio | patrón de `infra/postgres/*.sql` en el compose |
| Idioma | comentarios en español sin tildes en archivos de configuración, como el resto del repositorio | `CLAUDE.md` §7 |

**Restricción de arranque que condiciona el diseño.** Nginx resuelve por DNS el nombre de cada
`proxy_pass` **al arrancar**, no en cada petición: si `ms-usuarios` todavía no existe como
contenedor, Nginx aborta el arranque con `host not found in upstream`. Por eso el servicio
`gateway` declara `depends_on` de los cuatro microservicios con `condition: service_started`
—los contenedores existen y el DNS de Docker resuelve—, no `service_healthy`: ninguno de los
cuatro declara `healthcheck`, exactamente el mismo criterio ya escrito en el `depends_on` del
servicio `shell`.

## 4. Historias de usuario y criterios de aceptación

### HU-01 — Punto de entrada único a la API (PDF §4.2)

**Como** equipo del proyecto, **quiero** que todo el tráfico `/api` de la aplicación pase por un
único proceso, **para** cumplir el "API Gateway o BFF simple como punto de entrada único" del
PDF §4.2 sin que ningún microfrontend conozca la dirección de un microservicio.

- CUANDO llega al gateway una petición cuya ruta empieza por `/api/usuarios`, ENTONCES la
  reenvía a `http://ms-usuarios:8080` conservando la ruta completa y la cadena de consulta.
- CUANDO la ruta empieza por `/api/canchas`, ENTONCES la reenvía a `http://ms-canchas:8080`.
- CUANDO la ruta empieza por `/api/reservas`, ENTONCES la reenvía a `http://ms-reservas:8080`.
- CUANDO la ruta empieza por `/api/reportes`, ENTONCES la reenvía a `http://ms-reportes:8080`.
- SI la ruta llega con segmentos adicionales (`/api/usuarios/sesiones`,
  `/api/reservas/7/cancelacion`, `/api/canchas/3/bloqueos/12`), ENTONCES el gateway la reenvía
  igual: el enrutado es **por prefijo**, no por coincidencia exacta, y el microservicio recibe
  la ruta **íntegra**, incluido el propio prefijo `/api/<dominio>`, porque es la que declaran
  sus controladores.
- SI la ruta trae cadena de consulta (`?canchaId=1&fecha=2026-08-24`, `?desde=&hasta=`),
  ENTONCES llega al microservicio sin alterar.

### HU-02 — Los cuatro microfrontends llaman a la API por un solo destino

**Como** desarrollador del frontend, **quiero** que el `devServer.proxy` de cada microfrontend
tenga un único destino, **para** que la lista de microservicios deje de estar repetida en cuatro
archivos.

- CUANDO se lee el `devServer.proxy` de cualquiera de los cuatro `webpack.config.js`, ENTONCES
  su único `target` es `http://gateway:80`.
- CUANDO se leen sus `context`, ENTONCES son **los mismos que hoy**: el shell los cuatro
  prefijos, `mf-reservas` los cuatro que ya declara, `mf-administracion` los tres suyos y
  `mf-reportes` únicamente `/api/reportes` (D-15 de la spec 09, intacta).
- SI un microservicio cambia de puerto o de nombre de contenedor, ENTONCES **solo** cambia
  `infra/nginx/gateway.conf`; ningún `webpack.config.js` se toca.
- CUANDO el shell hace `fetch("/api/reportes/ocupacion?desde=...&hasta=...")` desde el navegador,
  ENTONCES la petición viaja: navegador → `devServer` del shell (`:3000`) → `gateway:80` →
  `ms-reportes:8080`, y la respuesta vuelve por el mismo camino sin cambios.
- SI un remote se abre suelto en su propio puerto (`:3001`, `:3002`, `:3003`), ENTONCES su
  `devServer.proxy` lo atiende igual, contra el mismo `gateway:80`.
- SI un archivo de `src/` de cualquier microfrontend cambia por causa de esta spec, ENTONCES la
  tarea está mal hecha: las rutas relativas `/api` no se tocan.

### HU-03 — El gateway no altera la semántica HTTP de los microservicios

**Como** responsable del contrato congelado, **quiero** que atravesar el gateway sea
indistinguible de llamar al microservicio directamente, **para** que el contrato de
`docs/contratos/README.md` siga siendo válido sin una sola nota nueva.

- CUANDO la petición trae el encabezado `Authorization: Bearer <token>`, ENTONCES llega intacto
  al microservicio y el filtro JWT lo valida igual que hoy.
- CUANDO el microservicio responde `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409` o
  `500`, ENTONCES el gateway devuelve **ese mismo** código, sin sustituirlo ni añadir páginas
  de error propias.
- CUANDO el microservicio responde el cuerpo de error `{ "codigo": ..., "mensaje": ... }`,
  ENTONCES el gateway lo devuelve byte a byte, con su `Content-Type: application/json`.
- CUANDO la petición lleva cuerpo JSON (`POST /api/reservas`, `PUT /api/canchas/{canchaId}`),
  ENTONCES llega completo, sin truncar.
- SI el verbo es `PATCH` o `DELETE`, ENTONCES se reenvía igual que `GET` y `POST`: el gateway no
  filtra por método.
- CUANDO el gateway reenvía, ENTONCES añade `Host`, `X-Real-IP`, `X-Forwarded-For` y
  `X-Forwarded-Proto`, los encabezados que un proxy inverso debe poner y que la versión original
  de `shell.conf` no tenía.

### HU-04 — Ruta desconocida en el gateway

**Como** equipo, **quiero** que el gateway no atienda nada que no sea `/api`, **para** que su
alcance quede demostrado y no se convierta por accidente en un servidor de estáticos.

- CUANDO llega al gateway una petición a `/`, `/index.html`, `/remoteEntry.js` o cualquier ruta
  fuera de los cuatro prefijos, ENTONCES responde `404`.
- SI la ruta empieza por `/api` pero no coincide con ninguno de los cuatro dominios
  (`/api/pagos`, `/api`), ENTONCES también responde `404`: no hay destino por omisión.
- SI la configuración conserva un `location /` con `root /usr/share/nginx/html`, ENTONCES la
  tarea está mal hecha: ese directorio no existe en este proyecto (D-5).

### HU-05 — El gateway arranca en el entorno local con Docker (PDF §4.4)

**Como** evaluador, **quiero** levantar todo el sistema con un solo comando, **para** comprobar
el entorno local reproducible que pide el PDF §4.4.

- CUANDO se ejecuta `docker compose up -d`, ENTONCES el servicio `gateway` arranca después de
  los cuatro microservicios y queda en estado `running`.
- CUANDO se ejecuta `docker compose logs --tail=50 gateway`, ENTONCES no aparece
  `host not found in upstream` ni ningún error de sintaxis de Nginx.
- SI `docker compose up -d` se ejecuta desde cero, con volúmenes borrados, ENTONCES el orden
  `postgres` → microservicios → `gateway` → microfrontends se cumple por los `depends_on`
  declarados y ninguno queda esperando a un destino inexistente.
- CUANDO se edita `infra/nginx/gateway.conf`, ENTONCES basta `docker compose restart gateway`
  para aplicarlo: la configuración va montada por volumen, no copiada en una imagen.

### HU-06 — El gateway se puede verificar y demostrar (D-7)

**Como** responsable de la defensa, **quiero** poder llamar al gateway desde el host, **para**
demostrar que el punto de entrada único existe y verificar las tareas de esta spec.

- CUANDO se ejecuta
  `curl.exe http://localhost:8090/api/canchas -H "Authorization: Bearer <token>"`, ENTONCES
  responde exactamente lo mismo que `curl.exe http://localhost:8083/api/canchas` con el mismo
  token: mismo código y mismo cuerpo.
- CUANDO se ejecuta `curl.exe http://localhost:8090/api/canchas` **sin** token, ENTONCES
  responde `401` con `{ "codigo": "NO_AUTENTICADO", ... }` producido por `ms-canchas`, no por el
  gateway.
- CUANDO se ejecuta `curl.exe -i http://localhost:8090/`, ENTONCES responde `404` (HU-04).
- SI alguien lee el puerto `8090` como la vía por la que el frontend llama a la API, ENTONCES
  está leyendo mal, y el comentario del servicio en `docker-compose.yml` debe impedirlo: es
  **puerto de verificación y demostración**. Los cuatro microfrontends llaman al gateway por la
  red interna de Docker (`http://gateway:80`), nunca por `localhost:8090`.

### HU-07 — Un microservicio caído (D-6)

**Como** equipo, **quiero** saber exactamente qué ocurre cuando un microservicio no responde,
**para** que el comportamiento esté escrito y nadie lo confunda con un error de negocio.

- SI un microservicio se cae después de que el gateway arrancó, ENTONCES el gateway **sigue en
  pie** y los otros tres dominios siguen atendiendo con normalidad.
- CUANDO llega una petición a un dominio cuyo microservicio no responde, ENTONCES el gateway
  devuelve **`502 Bad Gateway` con la página HTML por omisión de Nginx**.
- SI ese `502` se intenta traducir a `{ "codigo": "ERROR_INTERNO", ... }`, ENTONCES la tarea está
  mal hecha: D-6 lo prohíbe expresamente y su motivo está escrito.
- CUANDO la capa `src/api/` de un microfrontend recibe ese `502`, ENTONCES lo trata con su
  camino de error ya existente —el mismo que hoy cubre un fallo de red—, **sin ningún cambio de
  código**: ninguna de las cuatro capas `api/` interpreta hoy códigos que no estén en el
  contrato, y `502` no lo está.

### HU-08 — Swagger UI y las herramientas de desarrollo siguen accesibles

**Como** responsable de la entrega, **quiero** que la documentación OpenAPI de los cuatro
microservicios siga abriéndose, **para** no perder un entregable obligatorio al introducir el
gateway.

- CUANDO se abre `http://localhost:8082/swagger-ui/index.html` y los tres puertos equivalentes
  (`8083`, `8084`, `8085`), ENTONCES la documentación de cada microservicio se muestra como
  hasta ahora.
- SI los mapeos `8082`–`8085` desaparecieran de `docker-compose.yml`, ENTONCES la tarea está mal
  hecha: la decisión D-2 los conserva **deliberadamente**.
- CUANDO se lee el comentario de cada mapeo en `docker-compose.yml`, ENTONCES ya no dice "se
  elimina cuando exista el gateway Nginx", sino que se conserva para Swagger UI y para la
  verificación por `curl.exe`, y que **la aplicación no los usa**.
- CUANDO se ejecuta cualquier comando `curl.exe` de verificación de las specs 03, 04 y 05 o de
  `docs/bitacora.md`, ENTONCES sigue funcionando sin cambios.
- CUANDO se abre `http://localhost:8081` (adminer), ENTONCES sigue funcionando: es herramienta
  de desarrollo, fuera del alcance del gateway (D-4).
- SI el gateway declara alguna ruta hacia `/swagger-ui` o `/v3/api-docs`, ENTONCES la tarea está
  mal hecha: Swagger **no** se enruta por el gateway (D-3), y `GET http://localhost:8090/swagger-ui/index.html`
  debe responder `404`.

### HU-09 — Limpieza de `infra/nginx`

**Como** equipo, **quiero** que `infra/nginx` deje de contener configuración muerta, **para** que
nadie la lea como si describiera el despliegue real.

- CUANDO se lista `infra/nginx/` después de esta spec, ENTONCES contiene **un solo archivo**:
  `gateway.conf`.
- SI `remote.conf` sigue existiendo, ENTONCES la tarea está mal hecha: describe un modelo de
  despliegue —remotes compilados a `dist/` y servidos como estáticos por Nginx con
  `Access-Control-Allow-Origin: *`— que este proyecto descartó en P-06 y P-07 de la spec 06 y
  que nunca se ejecutó. La cabecera CORS que ese archivo ponía la resuelven hoy los
  `devServer.headers` de los tres remotes.
- SI `shell.conf` sigue existiendo, ENTONCES la tarea está mal hecha: su contenido útil vive
  ahora en `gateway.conf` y su nombre ya no describe nada (D-9).
- CUANDO se lee `gateway.conf`, ENTONCES sus cuatro `location /api/...` son los mismos cuatro
  destinos que `shell.conf` ya tenía —esa mitad del archivo siempre fue correcta— y el
  `location /` con `root /usr/share/nginx/html` ha desaparecido.

## 5. Reglas de negocio cubiertas

| Regla | Cobertura en esta spec |
|---|---|
| RN-01 a RN-08 | **Ninguna.** Esta spec no implementa ni modifica ninguna regla de negocio |

Es deliberado y hay que dejarlo escrito: el gateway es **infraestructura de transporte**. Las
ocho reglas de `CLAUDE.md` §2 siguen viviendo enteras en los servicios de `ms-canchas` y
`ms-reservas`, con sus comentarios `// RN-xx`, y esta spec **no toca `backend/`**. El criterio de
aceptación transversal de HU-03 es precisamente que el comportamiento observable de las ocho
reglas sea idéntico antes y después del gateway.

## 6. Contrato REST enrutado

Esta spec **no crea, no renombra y no modifica ninguna ruta**. Enruta las 20 rutas ya congeladas
en `docs/contratos/README.md` agrupándolas por su prefijo de dominio.

### 6.1 Mapa de enrutado

| Prefijo | Destino | Rutas congeladas que cubre |
|---|---|---|
| `/api/usuarios` | `http://ms-usuarios:8080` | `POST /api/usuarios/sesiones`, `POST /api/usuarios`, `GET /api/usuarios`, `PATCH /api/usuarios/{usuarioId}/estado` |
| `/api/canchas` | `http://ms-canchas:8080` | `GET /api/canchas`, `GET /api/canchas/{canchaId}`, `POST /api/canchas`, `PUT /api/canchas/{canchaId}`, `PATCH /api/canchas/{canchaId}/estado`, `GET /api/canchas/{canchaId}/bloqueos?fecha`, `POST /api/canchas/{canchaId}/bloqueos`, `DELETE /api/canchas/{canchaId}/bloqueos/{id}` |
| `/api/reservas` | `http://ms-reservas:8080` | `GET /api/reservas/disponibilidad?canchaId&fecha`, `POST /api/reservas`, `GET /api/reservas`, `GET /api/reservas/mias`, `PATCH /api/reservas/{id}/cancelacion` |
| `/api/reportes` | `http://ms-reportes:8080` | `GET /api/reportes/ocupacion?desde&hasta`, `GET /api/reportes/reservas?desde&hasta`, `GET /api/reportes/cancelaciones?desde&hasta` |

Los cuatro prefijos son **disjuntos** y cubren las 20 rutas del contrato: no hay ruta congelada
sin destino, ni destino sin rutas.

### 6.2 Campos

**Ninguno.** El gateway no lee ni escribe cuerpos JSON. Los nombres congelados de
`docs/contratos/README.md` —`estado`, `fecha`, `horaInicio`, `horaFin`, `deporte`, `rol`,
`porcentajeOcupacion` y los demás— lo atraviesan sin ser interpretados.

### 6.3 Códigos de error

Los del contrato los produce el microservicio y el gateway los devuelve tal cual (HU-03). El
gateway mismo solo puede producir dos, y **ninguno de los dos se añade al contrato congelado**,
porque el gateway no es un microservicio (D-6):

| Situación | HTTP | Cuerpo | Origen |
|---|---|---|---|
| Ruta fuera de los cuatro prefijos | `404` | HTML por omisión de Nginx | gateway (HU-04) |
| El microservicio de destino no responde | `502` | HTML por omisión de Nginx | gateway (HU-07) |

### 6.4 Contrato Module Federation

**Sin cambios.** Los cuatro nombres (`shell`, `mfReservas`, `mfAdministracion`, `mfReportes`),
los tres módulos expuestos, los cuatro puertos `3000`–`3003` y el contrato de props
`<RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />`
siguen exactamente como están congelados. La opción A se eligió, entre otros motivos, para no
tocarlo (§10, D-1).

## 7. Dependencias de esta spec

| Depende de | Estado |
|---|---|
| Los cuatro microservicios (specs 02 a 05) | Entregados |
| El shell (spec 06) y los tres remotes (specs 07, 08, 09) | Entregados |
| `docs/contratos/README.md` congelado | Vigente, sin cambios en esta spec |
| P-02 de la spec 06 (fecha de revisión cumplida) | Es lo que esta spec cierra |

Ninguna spec futura depende de esta: es la última de la sección 5 de integración.

## 8. La contradicción que esta spec resuelve por escrito

Desde la spec 02 y hasta la 09, el repositorio arrastra la misma nota en cuatro lugares de
`docker-compose.yml` y en cinco `requirements.md`:

> "Mapeo TEMPORAL para probar con `curl.exe`; **se elimina cuando exista el gateway Nginx**."

**Esa nota queda anulada el 24/08/2026.** Los mapeos `8082`–`8085` **se conservan**, y esta spec
es el lugar donde eso queda registrado, porque contradice una afirmación que el proyecto
arrastra desde su segunda spec.

**Motivo, en dos partes.** Primero: la documentación OpenAPI de los cuatro microservicios es un
**entregable obligatorio** —tan obligatorio que por él se fijó Spring Boot 3.5.3 en `CLAUDE.md`
§3, ya que `springdoc-openapi` solo existe hasta 2.8.6 sobre Spring Framework 6 (bitácora,
hallazgo 3 de la spec 02)—, y Swagger UI vive en `/swagger-ui/index.html`, fuera de `/api`. Un
gateway que solo enruta `/api` no la alcanza, y sin los mapeos no quedaría ninguna forma de
abrirla. Segundo: toda la verificación por `curl.exe` de las specs 03, 04 y 05 y de
`docs/bitacora.md` está escrita contra `8082`–`8085`; eliminarlos no rompería la aplicación,
pero dejaría irreproducible el historial de verificación del proyecto.

**Lo que sí cambia:** el comentario. Deja de anunciar una eliminación que no va a ocurrir y pasa
a decir lo que es cierto —que se conservan para Swagger UI y para la verificación por `curl.exe`,
y que **la aplicación no los usa**—, para que nadie lea un puerto publicado como si fuera el
camino por el que el frontend llama a la API. El "punto de entrada único" de HU-01 se refiere al
tráfico de la aplicación; una puerta de servicio abierta para la documentación y las pruebas no
lo contradice, siempre que esté escrito que lo es. Exactamente el mismo razonamiento vale para
el `8090` del propio gateway (D-7): es puerto de verificación y demostración, no la vía de la
aplicación.

Los `requirements.md` de las specs 02 a 09 **no se corrigen**: son documentos históricos, ya
aprobados y cerrados, y reescribirlos falsearía la bitácora. La anulación vive aquí, en la spec
que la decide.

## 9. Fuera de alcance de esta spec

- **La opción B**: el gateway como origen único del navegador, sirviendo también los estáticos
  del shell y de los tres remotes. Descartada con motivo escrito (§10, D-1).
- **Enrutar Swagger UI y `/v3/api-docs`** por el gateway (D-3).
- **Eliminar los mapeos `8082`–`8085`** (D-2, §8).
- **Adminer (`8081`) y PostgreSQL (`5432`)**: herramientas de desarrollo, se conservan igual y el
  gateway no las enruta (D-4).
- **Traducir el `502` de Nginx al formato de error del contrato** (D-6, HU-07).
- **Cambiar las URLs de los remotes** en el `ModuleFederationPlugin` del shell, el `publicPath`
  de ningún microfrontend y el `client.webSocketURL` de ningún `devServer`: la opción A no los
  toca, y el HMR de los cuatro microfrontends sigue funcionando como hoy.
- **Quitar los `headers: { "Access-Control-Allow-Origin": "*" }`** de los tres remotes: con la
  opción A siguen siendo orígenes distintos del shell y el encabezado sigue haciendo falta.
- **Cualquier archivo de `src/`** de cualquier microfrontend, y **cualquier archivo de
  `backend/`**: esta spec no modifica ni una línea de código de aplicación.
- **El DDL, el seed y cualquier archivo de `infra/postgres/`.**
- **`docs/contratos/README.md`**: esta spec no crea, no renombra y no modifica ninguna ruta ni
  ningún campo, así que el contrato no se toca y el registro de cambios no suma fila.
- **Funciones de gateway más allá del enrutado**: TLS/HTTPS, autenticación o validación de JWT en
  el gateway, límites de tasa, caché, balanceo entre réplicas, reintentos, circuit breaker,
  agregación de respuestas (el BFF "de verdad"), reescritura de rutas y CORS centralizado.
  El PDF §4.2 pide un gateway **simple**; nada de eso es simple ni está pedido.
- **Ajustar tiempos de espera y tamaños de cuerpo de Nginx**: se usan los valores por omisión.
  Los tiempos de espera de las llamadas entre microservicios ya están declarados dentro de
  `ms-reservas` y `ms-reportes` (D-12 de la spec 04), y son menores que el de Nginx.
- **Un modo producción** con `npm run build`, imágenes de frontend y estáticos servidos por
  Nginx: el proyecto nunca lo adoptó, y `remote.conf` se borra justamente por eso.
- **Pruebas automatizadas**: ninguna spec anterior las incluyó.

---

## 10. Decisiones tomadas (D-1 a D-9)

### Decisiones de alcance, respondidas el 24/08/2026

**D-1 — Alcance del gateway. OPCIÓN A: solo enruta `/api`.** Los cuatro `devServer.proxy` pasan
a un destino único `http://gateway:80` y el navegador sigue entrando por `localhost:3000`.
*Motivo:* la opción B rompe el HMR de los cuatro microfrontends —los cuatro `client.webSocketURL`
están fijados a `ws://localhost:300X/ws`—, obliga a reescribir el `publicPath` de los tres
remotes y a reescribir las tres URLs de remote del shell, y mete Nginx delante de `webpack serve`
en modo desarrollo a días de la entrega. El PDF §4.2 pide un "gateway simple" y además **no lo
declara obligatorio**. La opción A cumple el punto de entrada único **a la API**, que es lo que
esa frase declara.

**D-2 — Los mapeos `8082`–`8085` se conservan.** No se eliminan; sus comentarios se corrigen.
*Motivo y consecuencias:* §8 completa.

**D-3 — Swagger UI no se enruta por el gateway.** Se accede por los puertos directos, como hasta
ahora. *Motivo:* cuatro `/swagger-ui` en un mismo origen exigiría desambiguar por prefijo y
reescribir rutas, y `springdoc` no lo resuelve limpio.

**D-4 — Adminer (`8081`) y PostgreSQL (`5432`) se conservan igual.** Son herramientas de
desarrollo, fuera del alcance del gateway, y queda escrito que lo son.

**D-5 — `remote.conf` se borra; la configuración del gateway se reescribe.** De `shell.conf` se
conserva su mitad `/api` —que siempre fue correcta y es idéntica a lo que hoy hace el
`devServer.proxy` del shell—, se elimina el `location /` con su `root` inexistente y se le
agregan los `proxy_set_header` que un proxy inverso necesita. `remote.conf` cubre un modelo de
despliegue que el proyecto descartó.

### Decisiones sobre las preguntas abiertas, respondidas el 25/08/2026

**D-6 — Microservicio caído: se deja el `502` de Nginx (salida (a) de P-01).** El gateway no
traduce el fallo al formato `{ "codigo", "mensaje" }` del contrato.
*Motivo:* **el gateway no es un microservicio y no habla el contrato.** Traducirlo disfrazaría un
fallo de infraestructura de error de negocio, y sumaría una fila al contrato congelado por un
caso que no es de negocio. La distinción es deliberada y defendible: `500 ERROR_INTERNO` significa
"un microservicio falló procesando la petición"; `502` significa "el microservicio no está". Son
dos cosas distintas y deben verse distintas. Consecuencia declarada en HU-07: ninguna capa
`src/api/` cambia, porque ya trata como fallo genérico todo lo que no está en el contrato.

**D-7 — El gateway publica `8090:80` (salida (b) de P-02).** El responsable cambió su criterio
inicial —el gateway funcionalmente no necesita puerto publicado— al constatar que sin él **no se
puede demostrar el gateway con `curl.exe` en la defensa ni verificar las tareas de esta spec**.
Queda escrito, en la spec y en el comentario del compose, que `8090` es **puerto de verificación
y demostración, no la vía de la aplicación**: los cuatro microfrontends siguen llamando al
gateway por la red interna de Docker (`http://gateway:80`).

**Corrección del 25/08/2026: el puerto publicado es `8090`, no `8080`.** El valor inicial de esta
decisión era `8080:80`. Se cambia a `8090:80` por un motivo que no es de diseño sino de
despliegue: **`8080` es el puerto más disputado en una máquina de desarrollo** —servidores de
aplicaciones, herramientas y contenedores de otros proyectos lo toman por omisión—, y este
proyecto no debe depender de que esté libre en la máquina de quien lo despliegue. Al responsable
ya le ocurrió con un contenedor suyo de otro trabajo durante esta misma spec. `8090` no compite
con nada del proyecto ni con ningún valor por omisión habitual. La lista completa de puertos que
el sistema necesita libres está en §11.

**D-8 — Cada microfrontend conserva sus prefijos; solo cambia el `target` (salida (b) de P-03).**
El shell mantiene los cuatro `context`, `mf-reservas` los cuatro que ya declara,
`mf-administracion` los tres suyos y `mf-reportes` únicamente `/api/reportes`.
*Motivo:* respeta **D-15 de la spec 09** —"declarar los otros destinos sugeriría un acoplamiento
que no existe"—, deja escrito en cada `webpack.config.js` qué consume realmente ese
microfrontend, y es el cambio mínimo: una línea por archivo.

**D-9 — El archivo se renombra a `infra/nginx/gateway.conf` (salida (b) de P-04).** La
instrucción del 24/08/2026 decía "se reescribe" refiriéndose al contenido.
*Motivo:* un archivo llamado `shell.conf` que ya no tiene ninguna relación con el shell —no sirve
sus estáticos y no lo conoce— es una trampa para quien lo lea después. El nombre queda alineado
con el servicio `gateway` del compose y con lo que el archivo hace.

## 11. Puertos que el sistema necesita libres

Esta tabla es la lista que el manual de despliegue debe declarar como requisito previo: si
cualquiera de estos puertos está ocupado en la máquina de destino, `docker compose up -d` falla
al publicar el mapeo.

| Puerto del host | Servicio | Para qué se usa | ¿Lo usa la aplicación? |
|---|---|---|---|
| `3000` | `shell` | host de Module Federation; **es la URL por la que se entra al sistema** | **sí** |
| `3001` | `mf-reservas` | `remoteEntry.js` del remote, pedido por el navegador | **sí** |
| `3002` | `mf-administracion` | `remoteEntry.js` del remote | **sí** |
| `3003` | `mf-reportes` | `remoteEntry.js` del remote | **sí** |
| `8090` | `gateway` | verificación con `curl.exe` y demostración del punto de entrada único (D-7) | no: los frontends lo llaman por la red interna |
| `8081` | `adminer` | herramienta de desarrollo (D-4) | no |
| `8082` | `ms-usuarios` | Swagger UI y verificación por `curl.exe` (D-2) | no |
| `8083` | `ms-canchas` | Swagger UI y verificación por `curl.exe` (D-2) | no |
| `8084` | `ms-reservas` | Swagger UI y verificación por `curl.exe` (D-2) | no |
| `8085` | `ms-reportes` | Swagger UI y verificación por `curl.exe` (D-2) | no |
| `5432` | `postgres` | acceso directo a la base con `psql` o un cliente (D-4) | no |

Los cinco de la columna "no" son puertos de servicio: si uno estuviera ocupado, la aplicación
seguiría funcionando en cuanto se cambiara su mapeo, porque nada del frontend depende de ellos.
Los cuatro de `3000`–`3003` **sí** son de la aplicación: cambiarlos obliga a tocar la URL del
remote en el `ModuleFederationPlugin` del shell y el `client.webSocketURL` del microfrontend
afectado, así que no se cambian a la ligera.

**Ninguno de los once es `8080`**, y es deliberado (D-7).

## 12. Supuestos

**Sin supuestos.** Ningún dato faltante se rellenó con un valor inventado. Los cuatro que faltaban
se plantearon como P-01 a P-04 y están respondidos por el responsable en §10 (D-6 a D-9). **No
quedan preguntas abiertas.**

Las citas del PDF §4.2 ("API Gateway o BFF simple como punto de entrada único", no obligatorio) y
§4.4 (entorno local reproducible con Docker) las aportó el responsable y las confirmó como fieles
el 25/08/2026, sin necesidad de abrir el documento.
