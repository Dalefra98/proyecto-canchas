# Spec 10 — Gateway Nginx (sección 5 de integración) · design.md

Estado: **C2 — APROBADO** el 25/08/2026 ("Apruebo diseño de la spec 10").

Basado en `.claude/specs/10-gateway-nginx/requirements.md`, **aprobado el 25/08/2026**
("Apruebo requisitos de la spec 10"), con las nueve decisiones D-1 a D-9 ya incorporadas.

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`, el `requirements.md` aprobado,
`docker-compose.yml`, `infra/nginx/shell.conf`, `infra/nginx/remote.conf`, los cuatro
`frontend/*/webpack.config.js` y `.claude/specs/06-shell-module-federation/requirements.md` §8.

No se escribe código de producción en este paso (`CLAUDE.md` §6). Los valores de este documento
son **configuración declarada**: fijan qué directiva y qué valor llevará cada archivo, para que
la ejecución de `tasks.md` no tenga que decidir nada.

---

## 0. Nota sobre las secciones pedidas

El comando de diseño pide cinco tablas pensadas para un microservicio. Este entregable es
**infraestructura de transporte**: no tiene base de datos, no expone endpoints propios, no
declara DTOs y no traduce excepciones de negocio. Las secciones sin equivalente literal se
sustituyen por su análogo exacto, declarado aquí para que no parezca que se omitieron. Es la
misma sustitución que aplicaron y que aprobaste en los `design.md` de las specs 06 a 09.

| Pedido | Qué se entrega en su lugar | Sección |
|---|---|---|
| Modelo de datos (columnas y restricciones) | **Modelo de enrutado**: prefijo, destino, tipo de coincidencia y restricciones. No hay ni una tabla de base de datos | §4 |
| DTOs con validaciones | **Forma de la petición y de la respuesta que atraviesan el gateway**, elemento por elemento, con lo que se conserva y lo que se añade. El gateway no construye ni valida ningún cuerpo | §5 |
| Tabla de endpoints con rol requerido | **Tabla de rutas enrutadas con su rol requerido**, heredado del contrato. El gateway **no evalúa el rol**: lo sigue haciendo el filtro JWT de cada microservicio | §6 |
| Tabla de excepciones a códigos HTTP | **Tabla de condición de Nginx a código HTTP**, con la separación explícita entre lo que produce el gateway y lo que solo transporta | §7 |
| Tabla de decisiones con alternativa descartada | Igual que en las nueve specs anteriores | §12 |

**"Ninguna consulta puede acceder a tablas de otro microservicio" se cumple de forma absoluta.**
El gateway **no accede a ninguna base de datos**: no tiene cliente de PostgreSQL, no recibe
ninguna variable `SPRING_DATASOURCE_*` ni credencial equivalente, y no ejecuta una sola consulta.
No hay SQL en esta spec. Más aún, el gateway **refuerza** esa regla: al obligar a que todo el
tráfico entre capas pase por HTTP sobre `/api`, deja sin ninguna vía alternativa la integración
entre microservicios, que sigue siendo REST (`CLAUDE.md` §3).

## 1. Verificación campo por campo contra `docs/contratos/README.md`

**El gateway no lee, no escribe, no renombra y no interpreta ningún campo.** Los cuerpos JSON lo
atraviesan sin ser deserializados, así que no hay superficie donde un nombre pueda desviarse:
`estado`, `fecha`, `horaInicio`, `horaFin`, `deporte`, `rol`, `porcentajeOcupacion`, `usuarioId`,
`canchaId`, `codigo`, `mensaje` y todos los demás llegan y salen idénticos.

Lo que sí hay que verificar en esta spec son las **rutas**, porque el enrutado por prefijo es
precisamente lo que se diseña. Las **20 rutas congeladas** de `docs/contratos/README.md` se
verificaron una por una contra los cuatro prefijos de §4.1: las 20 caen dentro de un prefijo y
solo de uno. La tabla completa está en §6.1.

> **Discrepancia detectada y corregida.** El `requirements.md` aprobado decía "22 rutas" en §6 y
> §6.1. El contrato congelado tiene **20**: 4 de usuarios, 8 de canchas, 5 de reservas y 3 de
> reportes. Es un error de conteo mío en el documento anterior, no una diferencia de nombres ni
> una ruta faltante: los cuatro prefijos y sus destinos eran y siguen siendo correctos. El
> `requirements.md` queda corregido a 20 junto con la entrega de este diseño.

## 2. Topología: antes y después

**Hoy** — cada microfrontend conoce los cuatro microservicios:

```
navegador :3000/:3001/:3002/:3003
      |
      v
devServer de cada microfrontend  (4 archivos, 4 destinos cada uno)
      |-- /api/usuarios --> ms-usuarios:8080
      |-- /api/canchas  --> ms-canchas:8080
      |-- /api/reservas --> ms-reservas:8080
      '-- /api/reportes --> ms-reportes:8080
```

**Después** — cada microfrontend conoce un solo destino:

```
navegador :3000/:3001/:3002/:3003          curl.exe :8090  (verificacion, D-7)
      |                                          |
      v                                          |
devServer de cada microfrontend                  |
      '-- (sus prefijos) --> gateway:80 <--------'
                                 |
                                 |-- /api/usuarios --> ms-usuarios:8080
                                 |-- /api/canchas  --> ms-canchas:8080
                                 |-- /api/reservas --> ms-reservas:8080
                                 '-- /api/reportes --> ms-reportes:8080
```

Los estáticos del shell y de los tres remotes **no** pasan por el gateway (opción A, D-1): el
navegador los sigue pidiendo a `localhost:3000` y a `localhost:3001|3002|3003`, y el HMR de los
cuatro sigue abriendo su WebSocket contra su propio puerto.

## 3. Archivos y su destino final

| Archivo | Acción | Contenido resultante |
|---|---|---|
| `infra/nginx/gateway.conf` | **crear** | §4.2, §4.3 |
| `infra/nginx/shell.conf` | **borrar** | su mitad `/api` se traslada a `gateway.conf` (D-5, D-9) |
| `infra/nginx/remote.conf` | **borrar** | modelo de despliegue descartado (D-5) |
| `docker-compose.yml` | modificar | §8: servicio `gateway`, `depends_on` de los cuatro frontends, comentarios de `8082`–`8085` |
| `frontend/shell/webpack.config.js` | modificar | §9: solo el `target` del `devServer.proxy` |
| `frontend/mf-reservas/webpack.config.js` | modificar | §9 |
| `frontend/mf-administracion/webpack.config.js` | modificar | §9 |
| `frontend/mf-reportes/webpack.config.js` | modificar | §9 |

Ningún archivo de `src/` y ningún archivo de `backend/`.

## 4. Modelo de enrutado (equivalente del modelo de datos)

### 4.1 Tabla de enrutado

| Prefijo | Tipo de coincidencia | Destino | `proxy_pass` con barra final | Ruta que recibe el microservicio |
|---|---|---|---|---|
| `/api/usuarios` | prefijo | `http://ms-usuarios:8080` | **no** | la ruta íntegra, prefijo incluido |
| `/api/canchas` | prefijo | `http://ms-canchas:8080` | **no** | la ruta íntegra, prefijo incluido |
| `/api/reservas` | prefijo | `http://ms-reservas:8080` | **no** | la ruta íntegra, prefijo incluido |
| `/api/reportes` | prefijo | `http://ms-reportes:8080` | **no** | la ruta íntegra, prefijo incluido |
| `/` | prefijo (el más corto: solo entra si no coincidió ninguno de los cuatro) | — | — | `return 404` |

**Restricciones del modelo, y por qué son restricciones:**

| # | Restricción | Motivo |
|---|---|---|
| R-1 | `proxy_pass` **sin** barra final ni ruta | Con barra (`http://ms-canchas:8080/`) Nginx **sustituye** el prefijo coincidente por esa ruta, y `ms-canchas` recibiría `/{canchaId}` en vez de `/api/canchas/{canchaId}`. Los controladores declaran la ruta completa `/api/<dominio>/...` (`CLAUDE.md` §4), así que el prefijo debe llegar. Es el error clásico de esta configuración y por eso queda escrito como restricción, no como detalle |
| R-2 | Los cuatro prefijos son **disjuntos** | Ninguna ruta del contrato puede coincidir con dos destinos. Verificado ruta por ruta en §6.1 |
| R-3 | La cadena de consulta se conserva sin tocar | `?canchaId=1&fecha=2026-08-24`, `?desde=&hasta=`, `?fecha=` llegan literales. Nginx lo hace por omisión con R-1 |
| R-4 | La selección de `location` la hace Nginx por **prefijo más largo**, no por orden en el archivo | El orden de los cinco bloques en `gateway.conf` es indiferente para el comportamiento; se escriben en el orden de la tabla por legibilidad |
| R-5 | Sin `default_server` explícito y sin `server_name` | Un único `server` en el puerto 80 (§4.3): es el servidor por omisión de esa escucha, sin necesidad de declararlo |

### 4.2 Directivas de cada `location` de `/api`

Las cuatro son idénticas salvo el destino. Cada una declara exactamente esto:

| Directiva | Valor | Motivo |
|---|---|---|
| `proxy_pass` | `http://ms-<dominio>:8080` | el destino, sin barra final (R-1) |
| `proxy_set_header Host` | `$host` | el microservicio ve el host pedido y no el del contenedor |
| `proxy_set_header X-Real-IP` | `$remote_addr` | trazabilidad del origen real |
| `proxy_set_header X-Forwarded-For` | `$proxy_add_x_forwarded_for` | cadena de proxies, la forma estándar de acumularla |
| `proxy_set_header X-Forwarded-Proto` | `$scheme` | el microservicio sabe si el cliente habló HTTP o HTTPS |

Los cuatro `proxy_set_header` son la parte que el `shell.conf` original **no tenía** y que el
requirements exige en HU-03.

**Encabezados que NO se tocan, y es deliberado:** `Authorization` viaja intacto porque Nginx
reenvía por omisión todo encabezado de la petición que no se reescriba explícitamente. No hay
ninguna directiva que lo altere y no debe haberla: si el `Bearer` se perdiera, los cuatro
filtros JWT responderían `401` y toda la aplicación dejaría de funcionar. Lo mismo vale para
`Content-Type`, `Accept` y el cuerpo de la petición.

### 4.3 Estructura de `infra/nginx/gateway.conf`

Un único bloque `server`, con esta forma y en este orden:

| Posición | Bloque | Contenido |
|---|---|---|
| 1 | `server { listen 80; ... }` | el único servidor; sin `server_name` (R-5) |
| 2 | `location /api/usuarios` | las cinco directivas de §4.2, destino `ms-usuarios:8080` |
| 3 | `location /api/canchas` | ídem, destino `ms-canchas:8080` |
| 4 | `location /api/reservas` | ídem, destino `ms-reservas:8080` |
| 5 | `location /api/reportes` | ídem, destino `ms-reportes:8080` |
| 6 | `location / { return 404; }` | HU-04: todo lo que no es `/api/<dominio>` |

El archivo lleva comentarios en español sin tildes (`CLAUDE.md` §7) que registren, como mínimo:
R-1 (por qué `proxy_pass` va sin barra), el motivo del `return 404` y el motivo del montaje de
§8.1 (DD-01).

**Lo que el archivo NO lleva**, y cada motivo está en §12: `resolver`, `upstream`, `proxy_http_version`,
`proxy_read_timeout`, `client_max_body_size`, `error_page`, `add_header`, cualquier directiva de
CORS, cualquier `location` para `/swagger-ui` o `/v3/api-docs`, y cualquier `root` o `try_files`.

## 5. Lo que atraviesa el gateway (equivalente de los DTOs y sus validaciones)

El gateway **no declara ningún DTO y no valida nada**. Esa es la afirmación de diseño que hay que
poder defender, así que se declara elemento por elemento qué le ocurre a cada parte de la
petición y de la respuesta.

### 5.1 Petición, de entrada a salida

| Elemento | Qué hace el gateway | Validación |
|---|---|---|
| Método (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`) | lo reenvía sin filtrar | ninguna: el gateway no restringe verbos (HU-03) |
| Ruta | la usa para elegir destino; la reenvía **íntegra** (R-1) | solo la coincidencia de prefijo; ninguna validación de forma |
| Cadena de consulta | la reenvía literal (R-3) | ninguna: `desde`, `hasta`, `fecha`, `canchaId` los valida `jakarta.validation` en el microservicio, y un valor inválido sigue respondiendo `400 DATOS_INVALIDOS` desde allí |
| `Authorization: Bearer <token>` | lo reenvía intacto | **ninguna**: el gateway no lee, no verifica y no firma JWT. La identidad y el rol los resuelve el filtro de cada microservicio, exactamente como hoy |
| `Content-Type`, `Accept` | los reenvía intactos | ninguna |
| Cuerpo JSON | lo reenvía completo | ninguna: el gateway no lo deserializa. Tamaño máximo por omisión de Nginx, `1m`, muy por encima de cualquier payload del contrato (DD-07) |
| `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` | los **añade o reescribe** (§4.2) | son los únicos cuatro elementos que el gateway modifica |

### 5.2 Respuesta, de salida a entrada

| Elemento | Qué hace el gateway |
|---|---|
| Código de estado del microservicio | lo devuelve **idéntico**: `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `500` |
| Cuerpo JSON de éxito | byte a byte, sin tocar |
| Cuerpo de error `{ "codigo": ..., "mensaje": ... }` | byte a byte, con su `Content-Type: application/json` |
| Encabezados de respuesta | se reenvían; el gateway **no añade ninguno** (no hay `add_header`: DD-08) |

### 5.3 Props y contrato Module Federation

**Sin cambios.** `<RemoteApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />`
queda igual, y `apiBaseUrl` sigue valiendo `"/api"`: la ruta es relativa y el navegador la
resuelve contra el origen del shell, que sigue siendo `localhost:3000`. Ningún remote se entera
de que existe un gateway.

## 6. Rutas enrutadas y rol requerido (equivalente de la tabla de endpoints)

El gateway **no expone ninguna ruta propia** y **no evalúa ningún rol**. La columna "Rol" es la
del contrato congelado y la sigue aplicando el microservicio de destino; se reproduce aquí para
dejar constancia de que el gateway no la altera ni la duplica.

### 6.1 Las 20 rutas congeladas y su destino

| # | Verbo | Ruta | Rol (lo aplica el microservicio) | Prefijo | Destino |
|---|---|---|---|---|---|
| 1 | POST | `/api/usuarios/sesiones` | público | `/api/usuarios` | `ms-usuarios:8080` |
| 2 | POST | `/api/usuarios` | público | `/api/usuarios` | `ms-usuarios:8080` |
| 3 | GET | `/api/usuarios` | ADMIN | `/api/usuarios` | `ms-usuarios:8080` |
| 4 | PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | `/api/usuarios` | `ms-usuarios:8080` |
| 5 | GET | `/api/canchas` | ADMIN, USUARIO | `/api/canchas` | `ms-canchas:8080` |
| 6 | GET | `/api/canchas/{canchaId}` | ADMIN, USUARIO | `/api/canchas` | `ms-canchas:8080` |
| 7 | POST | `/api/canchas` | ADMIN | `/api/canchas` | `ms-canchas:8080` |
| 8 | PUT | `/api/canchas/{canchaId}` | ADMIN | `/api/canchas` | `ms-canchas:8080` |
| 9 | PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | `/api/canchas` | `ms-canchas:8080` |
| 10 | GET | `/api/canchas/{canchaId}/bloqueos?fecha` | ADMIN, USUARIO | `/api/canchas` | `ms-canchas:8080` |
| 11 | POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | `/api/canchas` | `ms-canchas:8080` |
| 12 | DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | `/api/canchas` | `ms-canchas:8080` |
| 13 | GET | `/api/reservas/disponibilidad?canchaId&fecha` | ADMIN, USUARIO | `/api/reservas` | `ms-reservas:8080` |
| 14 | POST | `/api/reservas` | USUARIO | `/api/reservas` | `ms-reservas:8080` |
| 15 | GET | `/api/reservas` | ADMIN | `/api/reservas` | `ms-reservas:8080` |
| 16 | GET | `/api/reservas/mias` | USUARIO | `/api/reservas` | `ms-reservas:8080` |
| 17 | PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | `/api/reservas` | `ms-reservas:8080` |
| 18 | GET | `/api/reportes/ocupacion?desde&hasta` | ADMIN | `/api/reportes` | `ms-reportes:8080` |
| 19 | GET | `/api/reportes/reservas?desde&hasta` | ADMIN | `/api/reportes` | `ms-reportes:8080` |
| 20 | GET | `/api/reportes/cancelaciones?desde&hasta` | ADMIN | `/api/reportes` | `ms-reportes:8080` |

Las 20 caen en un prefijo y solo en uno (R-2). Ninguna queda sin destino y ningún prefijo queda
sin rutas.

### 6.2 Rutas que el gateway NO enruta

| Ruta | Respuesta del gateway | Dónde se accede |
|---|---|---|
| `/swagger-ui/index.html` de los cuatro microservicios | `404` | `localhost:8082`–`8085` directos (D-2, D-3) |
| `/v3/api-docs` de los cuatro | `404` | ídem |
| `/`, `/index.html`, `/remoteEntry.js`, cualquier estático | `404` | `localhost:3000`–`3003` directos (D-1) |
| Adminer | `404` | `localhost:8081` (D-4) |
| PostgreSQL | no aplica: no es HTTP | `localhost:5432` (D-4) |
| `/api`, `/api/pagos` y cualquier `/api/<dominio>` inexistente | `404` | no existe: no hay destino por omisión |

## 7. Condición a código HTTP (equivalente de la tabla de excepciones)

La dirección es la inversa a la de un microservicio: aquí no hay excepciones de negocio que
traducir, sino condiciones de transporte.

### 7.1 Lo que produce el gateway

| Condición | HTTP | Cuerpo | Directiva que lo produce |
|---|---|---|---|
| La ruta no coincide con ninguno de los cuatro prefijos | `404` | HTML por omisión de Nginx | `location / { return 404; }` |
| El microservicio de destino no acepta la conexión (contenedor caído o detenido) | `502` | HTML por omisión de Nginx | comportamiento por omisión de `proxy_pass`; **no se configura nada** (D-6) |
| El microservicio acepta pero no responde en 60 s | `504` | HTML por omisión de Nginx | `proxy_read_timeout` por omisión (DD-06) |
| El nombre del destino no resuelve **al arrancar** | el contenedor `gateway` **no arranca** | — | comportamiento de Nginx; se previene con el `depends_on` de §8.2 |

Ninguno de estos códigos se añade a `docs/contratos/README.md`: el gateway no es un
microservicio y no habla el formato de error del contrato (D-6). `500 ERROR_INTERNO` sigue
significando "un microservicio falló procesando la petición"; `502` significa "el microservicio
no está". Son dos cosas distintas y el diseño quiere que se vean distintas.

### 7.2 Lo que el gateway solo transporta

| Situación | HTTP | `codigo` | Quién lo produce |
|---|---|---|---|
| Validación de entrada | 400 | `DATOS_INVALIDOS` | el microservicio |
| Sin token o token inválido | 401 | `NO_AUTENTICADO` | el microservicio |
| Sin permiso | 403 | `SIN_PERMISO` | el microservicio |
| Recurso inexistente | 404 | `NO_ENCONTRADO` | el microservicio |
| Conflictos de negocio (`EMAIL_DUPLICADO`, `NOMBRE_DUPLICADO`, `BLOQUEO_DUPLICADO`, `BLOQUE_OCUPADO`, `LIMITE_RESERVAS`, `RESERVA_PASADA`, `RESERVA_NO_CANCELABLE`) | 409 | el del contrato | el microservicio |
| Error no previsto | 500 | `ERROR_INTERNO` | el microservicio |

**Ambigüedad declarada y aceptada:** un `404` puede venir del gateway (ruta sin prefijo, cuerpo
HTML) o del microservicio (`NO_ENCONTRADO`, cuerpo JSON). Se distinguen por el cuerpo y por el
`Content-Type`. No se resuelve con un código distinto porque ninguna ruta que la aplicación llame
puede caer en el `404` del gateway: las 20 del contrato están cubiertas (§6.1), así que el `404`
del gateway solo lo verá quien pruebe a mano.

### 7.3 Efecto en el frontend

| Código nuevo | Qué hace hoy la capa `src/api/` de cada microfrontend | Cambio necesario |
|---|---|---|
| `502`, `504` | los trata por su camino de error genérico, el mismo que ya cubre un fallo de red: ninguna de las cuatro capas `api/` interpreta códigos que no estén en el contrato | **ninguno** |

Esto es lo que permite que esta spec no toque ni una línea de `src/` (HU-07 del requirements).

## 8. `docker-compose.yml`

### 8.1 Servicio `gateway`

| Clave | Valor | Motivo |
|---|---|---|
| `image` | `nginx:alpine` | `CLAUDE.md` §1: solo Docker, sin build propio |
| `container_name` | `canchas-gateway` | patrón de los nueve servicios existentes (`canchas-*`) |
| `volumes` | `./infra/nginx/gateway.conf:/etc/nginx/conf.d/default.conf:ro` | **DD-01**: el archivo del repositorio se llama `gateway.conf` (D-9) y se monta **en la ruta de `default.conf`** para reemplazar el `server` de bienvenida que trae la imagen. `:ro` porque el gateway nunca escribe su configuración |
| `ports` | `8090:80` | D-7: puerto de **verificación y demostración**, no vía de la aplicación. Comentario obligatorio diciéndolo. `8090` y no `8080`: DD-13 |
| `depends_on` | los cuatro `ms-*` con `condition: service_started` | §8.2 |

**Sin `environment`, sin `healthcheck`, sin volumen de datos.** El gateway no tiene estado.

### 8.2 Orden de arranque

```
postgres (service_healthy)
   -> ms-usuarios, ms-canchas, ms-reservas, ms-reportes (service_started)
        -> gateway (service_started)
             -> shell, mf-reservas, mf-administracion, mf-reportes
```

| Decisión | Valor | Motivo |
|---|---|---|
| `gateway` depende de los cuatro microservicios | `service_started` | Nginx resuelve por DNS el nombre de cada `proxy_pass` **al arrancar**: si un contenedor destino no existe todavía, aborta con `host not found in upstream`. `service_started` basta —el contenedor existe y el DNS de Docker resuelve— y `service_healthy` es imposible: ninguno de los cuatro declara `healthcheck`. Es el mismo criterio ya escrito en el `depends_on` del servicio `shell` |
| Los cuatro frontends dependen de `gateway` | `service_started` | sustituye sus `depends_on` actuales hacia los `ms-*`: ya no hablan con ellos. El orden hacia los microservicios se mantiene por transitividad |

**Consecuencia que hay que aceptar por escrito:** hoy el `depends_on` de cada frontend documenta
qué microservicios consume ese remote (P-07 y P-08 de las specs 07 y 08: `mf-reportes` depende
solo de `ms-reportes`). Al pasar a depender de `gateway`, esa información desaparece del compose.
No se pierde: vive en el `context` del `devServer.proxy` de cada `webpack.config.js`, que D-8
conserva intacto justamente por esto. El comentario de cada `depends_on` debe remitir allí.

### 8.3 Comentarios de los mapeos `8082`–`8085`

El comentario actual, repetido en los cuatro microservicios:

```
# Mapeo TEMPORAL para probar con curl.exe; se elimina cuando exista el gateway Nginx.
```

se sustituye por este, idéntico en los cuatro (sin tildes, `CLAUDE.md` §7):

```
# Puerto de servicio, se conserva a proposito (spec 10, D-2): Swagger UI vive fuera
# de /api y el gateway no lo enruta, y toda la verificacion por curl.exe de las specs
# 03 a 05 y de la bitacora esta escrita contra estos puertos. La aplicacion NO los usa:
# los cuatro microfrontends llaman a la API por gateway:80.
```

Los mapeos `8081` (adminer) y `5432` (postgres) **no se tocan y no reciben comentario nuevo**:
son herramientas de desarrollo y nunca anunciaron su eliminación (D-4).

## 9. `devServer.proxy` de los cuatro microfrontends

**Solo cambia el `target`.** Los `context` quedan exactamente como están (D-8), y ninguna otra
clave de `devServer` —`port`, `host`, `allowedHosts`, `headers`, `hot`, `client.webSocketURL`,
`client.overlay`— se toca.

| Archivo | `context` (sin cambios) | `target` antes | `target` después |
|---|---|---|---|
| `frontend/shell/webpack.config.js` | `/api/usuarios`, `/api/canchas`, `/api/reservas`, `/api/reportes` | los cuatro `ms-*:8080` | `http://gateway:80` en las cuatro entradas |
| `frontend/mf-reservas/webpack.config.js` | los mismos cuatro | los cuatro `ms-*:8080` | `http://gateway:80` en las cuatro |
| `frontend/mf-administracion/webpack.config.js` | `/api/usuarios`, `/api/canchas`, `/api/reservas` (ver DD-14) | los tres `ms-*:8080` | `http://gateway:80` en las tres |
| `frontend/mf-reportes/webpack.config.js` | `/api/reportes` (**D-15 de la spec 09**, intacta) | `ms-reportes:8080` | `http://gateway:80` |

El comentario que hoy explica el destino ("Destino del proxy: nombres de contenedor. Lo ejecuta
webpack serve DENTRO de la red de Docker…") se actualiza en los cuatro archivos: sigue siendo un
nombre de contenedor y sigue ejecutándose dentro de la red de Docker, pero ahora es **uno solo**,
y el reparto por microservicio vive en `infra/nginx/gateway.conf`.

**Nota de operación que la ejecución necesita:** `webpack serve` lee `webpack.config.js` **solo
al arrancar**. Cambiar el `target` no lo recoge el `watchOptions` ni el HMR: hay que reiniciar el
contenedor del microfrontend (`docker compose restart <servicio>`).

## 10. Cómo se verifica

Los comandos de cada tarea se detallarán en `tasks.md`; el diseño fija **qué** hay que poder
demostrar y con qué herramienta.

| # | Qué demuestra | Herramienta |
|---|---|---|
| V-1 | `gateway.conf` es sintácticamente válido | `docker compose exec gateway nginx -t` |
| V-2 | El compose es válido y el servicio está declarado como se diseñó | `docker compose config` |
| V-3 | El gateway arrancó sin `host not found in upstream` | `docker compose logs --tail=50 gateway` |
| V-4 | Los cuatro prefijos enrutan | `curl.exe` a `localhost:8090` contra una ruta de cada dominio, con y sin token |
| V-5 | La respuesta por el gateway es idéntica a la del puerto directo | mismo `curl.exe` contra `8080` y contra `8083`, comparando código y cuerpo |
| V-6 | Ruta fuera de `/api` responde `404` | `curl.exe -i http://localhost:8090/` |
| V-7 | Swagger UI sigue accesible por los puertos directos | navegador en `8082`–`8085` |
| V-8 | Un microservicio caído produce `502` y no tumba el gateway | `docker compose stop ms-reportes`, `curl.exe`, `docker compose start ms-reportes` |
| V-9 | Los cuatro microfrontends siguen funcionando de extremo a extremo | navegador en `localhost:3000`: login, un módulo de cada remote |
| V-10 | Los cuatro devServer recompilaron con la configuración nueva | `docker compose logs --timestamps <servicio>`, comprobando que la marca sea **posterior** al reinicio |

V-10 conserva la regla de lectura del registro que las specs 06 a 09 fijaron: **siempre con
`--timestamps`**, porque `webpack serve` no limpia su registro y un `compiled successfully`
antiguo se lee como nuevo. La bitácora ya registró ese engaño dos veces.

## 11. SQL de esta spec

**Ninguno.** El gateway no accede a ninguna base de datos (§0). `infra/postgres/` no se toca.

## 12. Decisiones de diseño

| # | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|
| DD-01 | `gateway.conf` se monta en `/etc/nginx/conf.d/default.conf` | Montarlo en `/etc/nginx/conf.d/gateway.conf`, dejando el `default.conf` de la imagen | La imagen `nginx:alpine` trae un `default.conf` con un `server` que ya escucha en el puerto 80. Con los dos presentes habría **dos `server` en la misma escucha sin `server_name`**, y el primero por orden alfabético —`default.conf`— sería el servidor por omisión: se quedaría con todas las peticiones y los cuatro `location /api` **nunca se usarían**. El fallo sería silencioso: el gateway arrancaría bien y devolvería la página de bienvenida de Nginx. El nombre del repositorio sigue siendo `gateway.conf` (D-9); lo que cambia es la ruta de montaje |
| DD-02 | `proxy_pass` **sin** barra final ni ruta | `proxy_pass http://ms-canchas:8080/` | Con barra, Nginx sustituye el prefijo coincidente y `ms-canchas` recibiría `/{canchaId}` en lugar de `/api/canchas/{canchaId}`: los controladores declaran la ruta completa y responderían `404` a todo. Es el error clásico de esta configuración (R-1) |
| DD-03 | Cuatro `location` por prefijo simple | Un `location ~ ^/api/(usuarios\|canchas\|reservas\|reportes)` con expresión regular y destino por variable | La regex obligaría a `set` y a un `resolver`, y cambiaría el manejo de fallos (DD-05). Cuatro bloques literales son legibles, no tienen orden significativo (R-4) y hacen visible el mapa completo de un vistazo, que es lo que se defiende en la presentación |
| DD-04 | `location / { return 404; }` | Omitir el bloque y dejar que Nginx sirva su `root` por omisión | Sin él, `/` devolvería la página de bienvenida de Nginx: parecería que el gateway "funciona" cuando en realidad no enrutó nada. El `404` explícito hace que el alcance del gateway sea comprobable (HU-04, V-6) |
| DD-05 | Sin `resolver` ni resolución de DNS en tiempo de ejecución | `resolver 127.0.0.11 valid=10s` con el destino en una variable | La resolución en tiempo de ejecución sobrevive a que un microservicio se recree con otra IP, pero exige variables y cambia el fallo de "no arranca" a "responde `502` siempre". Con `depends_on` (§8.2) el arranque está garantizado, y en desarrollo local los contenedores no se recrean solos. Se prefiere el fallo ruidoso al arrancar sobre el fallo silencioso en caliente |
| DD-06 | Tiempos de espera por omisión de Nginx (60 s) | `proxy_read_timeout` explícito, más corto, sobre todo para `/api/reportes` | `ms-reservas` y `ms-reportes` ya declaran sus propios tiempos de espera hacia los servicios que consumen (D-12 de la spec 04), y son **menores** que 60 s: el microservicio corta antes y responde `500 ERROR_INTERNO`, que sí es del contrato. Un valor propio en el gateway solo añadiría un segundo umbral que podría dispararse antes y convertir un `500` del contrato en un `504` que no lo es |
| DD-07 | `client_max_body_size` por omisión (`1m`) | Declararlo explícito | Ningún payload del contrato se acerca: el mayor es un `PUT /api/canchas/{canchaId}`, unos cientos de bytes. Declararlo sugeriría que hay cargas grandes que atender |
| DD-08 | Sin `add_header` de ningún tipo | `add_header Access-Control-Allow-Origin *` en el gateway, como hacía `remote.conf` | Con la opción A el navegador **nunca llama al gateway**: llama al devServer de su propio origen, que proxya del lado del servidor. No hay petición entre orígenes que autorizar. Añadir CORS aquí sería una cabecera permisiva sin ninguna función, y en la defensa habría que explicar por qué está |
| DD-09 | Sin `error_page` para el `502` | `error_page 502 = @json` devolviendo `{ "codigo": "ERROR_INTERNO" }` | D-6, ya aprobada: el gateway no es un microservicio y no habla el contrato. Traducirlo disfrazaría un fallo de infraestructura de error de negocio y sumaría una fila al contrato congelado por un caso que no lo es |
| DD-10 | Un solo `server`, sin `server_name` y sin `upstream` | Un bloque `upstream` por microservicio | `upstream` existe para agrupar varias réplicas y elegir política de balanceo. Aquí hay **una** instancia por microservicio: cuatro bloques `upstream` de una línea cada uno serían ceremonia sin efecto |
| DD-11 | Los cuatro frontends pasan a `depends_on: gateway` | Conservar además los `depends_on` hacia los `ms-*` | Un frontend ya no habla con ningún microservicio: declararlo sería declarar una dependencia falsa. El orden se mantiene por transitividad, y el acoplamiento real de cada remote sigue documentado en su `context` (D-8, §8.2) |
| DD-12 | `shell.conf` se borra y `gateway.conf` se crea, en vez de renombrar | `git mv` conservando el historial | El archivo cambia de contenido casi por completo —pierde el `location /`, gana los cuatro `proxy_set_header`— y de propósito. El historial del archivo original queda igual en el repositorio; lo que importa es que no sobreviva un `shell.conf` que ya no describe nada (D-9, HU-09) |
| DD-13 | El puerto publicado del gateway es `8090` | `8080:80`, el valor con el que se aprobó D-7 | `8080` es el puerto más disputado de una máquina de desarrollo: servidores de aplicaciones, herramientas y contenedores de otros proyectos lo toman por omisión. El proyecto no debe depender de que esté libre en la máquina de quien lo despliegue, y al responsable ya le ocurrió con un contenedor suyo de otro trabajo durante esta misma spec. `8090` no compite con nada del proyecto ni con ningún valor por omisión habitual. El cambio no toca `gateway.conf`: el puerto **interno** sigue siendo el `80` y el mapeo vive solo en `docker-compose.yml` |
| DD-14 | `mf-administracion` pierde su `context` `/api/reportes` | Conservarlo, y corregir §9 y D-8 para que dijeran cuatro prefijos | El archivo declaraba cuatro `context`, pero ese remote **no consume `ms-reportes`** (P-08 de la spec 08). La entrada era **residual: anterior al gateway y nunca usada**, no un permiso que el gateway quite. Declarar un prefijo que el remote no consume sugiere un acoplamiento inexistente, que es el criterio con el que D-15 dejó a `mf-reportes` con una sola entrada. Documentar la entrada residual habría sido documentar un error en vez de arreglarlo. Corrige D-8, con fecha y motivo en el `requirements.md` |

## 13. Fuera de alcance de este diseño

Lo declarado en §9 del `requirements.md`, sin cambios ni añadidos. En particular, este diseño
**no** introduce: TLS, autenticación en el gateway, límites de tasa, caché, balanceo, reintentos,
agregación de respuestas, reescritura de rutas, CORS, enrutado de Swagger, servido de estáticos,
ni ninguna modificación de `src/`, `backend/`, `infra/postgres/` o `docs/contratos/README.md`.

## 14. Supuestos

**Sin supuestos.** Las nueve decisiones del `requirements.md` (D-1 a D-9) cubren todo lo que este
diseño necesitaba decidir, y las doce decisiones de diseño (DD-01 a DD-12) se derivan de ellas o
del comportamiento documentado de Nginx y de Docker Compose. Ningún dato se rellenó con un valor
inventado.

La única corrección respecto del documento anterior es el conteo de rutas —20, no 22—, explicada
en §1; no afecta a ninguna decisión ni a ningún destino.
