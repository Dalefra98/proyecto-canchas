# Spec 10 — Gateway Nginx (sección 5 de integración) · tasks.md

Basado en `requirements.md` (C1 aprobado el 25/08/2026) y `design.md` (C2 aprobado el
25/08/2026).

**Siete tareas.** Cada una cabe en un commit y **deja el sistema funcionando de extremo a
extremo**: el gateway entra en paralelo a lo que ya existe (T1, T2), los consumidores se
trasladan uno a uno (T3, T4), y solo al final se retira lo que quedó sin uso (T5, T6, T7).

Reglas de ejecución (`CLAUDE.md` §0.3): **una tarea a la vez**. Al terminar una, detenerse y
esperar aprobación. No encadenar tareas.

Todos los comandos se ejecutan desde la raíz del repositorio, en PowerShell. Nada de `nginx`,
`npm`, `node` ni `npx` en el host: solo Docker (`CLAUDE.md` §1). `curl.exe`, nunca `curl`.

## Condición previa a toda tarea

El sistema debe estar levantado antes de empezar:

```powershell
docker compose up -d
docker compose ps
```

Los cuatro microservicios tienen que estar en `running`: el gateway resuelve sus nombres por DNS
**al arrancar** (`design.md` §8.2), y hasta la verificación de T1 depende de ello.

## Cómo se lee el registro en esta spec

**El de `gateway` se lee tal cual**: Nginx escribe poco y no recompila, así que su registro no
engaña. **El de los cuatro microfrontends, siempre con `--timestamps`**: `webpack serve` no
limpia su registro entre reinicios y un `compiled successfully` anterior se lee como nuevo. La
bitácora ya registró ese engaño dos veces —T3 de la spec 06 y el hallazgo 2 de la spec 08—.
Antes de dar por buena una recompilación hay que comprobar que la marca de tiempo sea
**posterior** al reinicio del contenedor.

---

## T1 — Crear `infra/nginx/gateway.conf`

**Qué hace.** Crea el archivo de configuración del gateway con la estructura de `design.md` §4.3:
un único `server { listen 80; }`, los cuatro `location` de prefijo (`/api/usuarios`,
`/api/canchas`, `/api/reservas`, `/api/reportes`) con su `proxy_pass` **sin barra final** (R-1,
DD-02) y los cuatro `proxy_set_header` de §4.2 (`Host`, `X-Real-IP`, `X-Forwarded-For`,
`X-Forwarded-Proto`), más el `location / { return 404; }` de DD-04. Comentarios en español sin
tildes que registren R-1, el motivo del `return 404` y el del montaje de DD-01.

**Requisitos que cubre.** HU-01 completa; HU-03 en su parte de encabezados; HU-04.

**No hace.** No toca `docker-compose.yml`, no borra `shell.conf` ni `remote.conf`, no toca ningún
`webpack.config.js`. Nadie monta todavía este archivo: el sistema sigue funcionando exactamente
como antes de la tarea.

**Verificación.** Se valida la sintaxis y la resolución de los cuatro destinos en un contenedor
efímero, montando el archivo en la ruta que usará de verdad (DD-01) y **dentro de la red del
proyecto**, que es lo que permite que los nombres `ms-*` resuelvan:

```powershell
docker run --rm --network proyecto-canchas_default -v "${PWD}/infra/nginx/gateway.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine nginx -t
```

Debe imprimir `syntax is ok` y `test is successful`. Si aparece `host not found in upstream`, o
los microservicios no están levantados, o un nombre de destino está mal escrito.

---

## T2 — Servicio `gateway` en `docker-compose.yml`

**Qué hace.** Declara el servicio con las cinco claves de `design.md` §8.1: `image: nginx:alpine`,
`container_name: canchas-gateway`, el volumen
`./infra/nginx/gateway.conf:/etc/nginx/conf.d/default.conf:ro` (DD-01), `ports: 8090:80` con el
comentario obligatorio de que es **puerto de verificación y demostración, no vía de la
aplicación** (D-7) y de que es `8090` y no `8080` porque este último es el puerto más disputado
de una máquina de desarrollo (DD-13), y `depends_on` de los cuatro microservicios con
`condition: service_started`, también comentado con el motivo del DNS al arrancar (§8.2).

**Requisitos que cubre.** HU-05 completa; HU-06; HU-04 en su comprobación real.

**No hace.** No toca el `depends_on` de los frontends (eso es T5) ni los comentarios de
`8082`–`8085` (T6). Los cuatro microfrontends siguen llamando a los microservicios directamente:
el gateway queda levantado **en paralelo**, sin que nadie de la aplicación lo use todavía.

**Verificación.**

```powershell
docker compose config
docker compose up -d gateway
docker compose logs --tail=50 gateway
docker compose exec gateway nginx -t
curl.exe -i http://localhost:8090/
curl.exe -i http://localhost:8090/api/canchas
curl.exe -i -X POST http://localhost:8090/api/usuarios/sesiones -H "Content-Type: application/json" -d "{\"email\":\"admin@canchas.ec\",\"password\":\"Admin123\"}"
```

Esperado, en orden: el `config` no falla; el registro no muestra `host not found in upstream`;
`nginx -t` responde `test is successful`; `/` responde **404**; `/api/canchas` sin token responde
**401** con `{"codigo":"NO_AUTENTICADO",...}` producido por `ms-canchas`; el login responde **200**
con `token`. Ese `token` se reutiliza en T3 y T7, así que conviene guardarlo.

Comprobación de que el prefijo llega íntegro (R-1) comparando gateway y puerto directo:

```powershell
curl.exe -s http://localhost:8090/api/canchas -H "Authorization: Bearer <token>"
curl.exe -s http://localhost:8083/api/canchas -H "Authorization: Bearer <token>"
```

Los dos cuerpos deben ser idénticos. Si el del gateway trae `404 NO_ENCONTRADO`, el `proxy_pass`
lleva barra final.

---

## T3 — El `devServer.proxy` del shell apunta al gateway

**Qué hace.** En `frontend/shell/webpack.config.js` cambia **solo el `target`** de las cuatro
entradas del `devServer.proxy` a `http://gateway:80` (`design.md` §9). Los cuatro `context` se
quedan como están (D-8). Actualiza el comentario del bloque: sigue siendo un nombre de contenedor
resuelto dentro de la red de Docker, pero ahora es **uno solo**, y el reparto por microservicio
vive en `infra/nginx/gateway.conf`.

**Requisitos que cubre.** HU-02 para el host.

**No hace.** No toca los tres remotes (T4), ni `port`, ni `host`, ni `allowedHosts`, ni `headers`,
ni `client.webSocketURL`, ni `client.overlay`, ni las URLs de los remotes del
`ModuleFederationPlugin`, ni un solo archivo de `src/`.

**Verificación.** `webpack serve` lee su configuración **solo al arrancar** (§9): hay que
reiniciar el contenedor, no basta con guardar.

```powershell
docker compose restart shell
docker compose logs --timestamps --tail=50 shell
```

La marca de tiempo del `compiled successfully` debe ser **posterior** al reinicio. Después, en el
navegador, `http://localhost:3000`: iniciar sesión como `admin@canchas.ec` / `Admin123` y abrir
los tres módulos. Todo el tráfico `/api` del shell y de los tres remotes montados en él ya pasa
por el gateway. Se confirma en su registro:

```powershell
docker compose logs --tail=30 gateway
```

Deben verse las peticiones `/api/usuarios/sesiones`, `/api/canchas`, `/api/reservas` y
`/api/reportes` con código `200`.

---

## T4 — El `devServer.proxy` de los tres remotes apunta al gateway

**Qué hace.** El mismo cambio de T3 en `frontend/mf-reservas/webpack.config.js` (cuatro entradas),
`frontend/mf-administracion/webpack.config.js` (tres) y
`frontend/mf-reportes/webpack.config.js` (una sola, `/api/reportes`): **solo el `target`**, más el
comentario actualizado. La entrada única de `mf-reportes` se conserva tal cual —**D-15 de la spec
09 queda intacta** (D-8)—.

**Requisitos que cubre.** HU-02 completa.

**No hace.** No unifica los `context`, no toca `headers` ni `client.webSocketURL`, no toca `src/`.

**Verificación.**

```powershell
docker compose restart mf-reservas mf-administracion mf-reportes
docker compose logs --timestamps --tail=30 mf-reservas
docker compose logs --timestamps --tail=30 mf-administracion
docker compose logs --timestamps --tail=30 mf-reportes
```

Las tres marcas de `compiled successfully` deben ser posteriores al reinicio. Después, abrir cada
remote **suelto** en su propio puerto —`http://localhost:3001`, `:3002`, `:3003`— que es el único
caso en que su `devServer.proxy` se usa, y comprobar en `docker compose logs --tail=30 gateway`
que las peticiones llegan al gateway.

---

## T5 — `depends_on` de los cuatro frontends hacia `gateway`

**Qué hace.** En `docker-compose.yml`, los servicios `shell`, `mf-reservas`, `mf-administracion` y
`mf-reportes` sustituyen sus `depends_on` hacia los `ms-*` por uno solo hacia `gateway`, con
`condition: service_started` (DD-11). El comentario de cada uno explica que el frontend ya no
habla con ningún microservicio y **remite al `context` de su `webpack.config.js`**, que es donde
sigue documentado qué consume realmente ese microfrontend (§8.2).

**Requisitos que cubre.** HU-05 en su parte de orden de arranque.

**No hace.** No toca el `depends_on` de `gateway` (T2) ni el de `postgres`.

**Verificación.** Cambiar `depends_on` exige recrear, no reiniciar:

```powershell
docker compose config
docker compose up -d
docker compose ps
```

Los diez servicios en `running`. Prueba real del orden completo, desde cero:

```powershell
docker compose down
docker compose up -d
docker compose ps
docker compose logs --tail=30 gateway
```

Ningún servicio debe quedar caído y el gateway no debe mostrar `host not found in upstream`.

---

## T6 — Corregir los comentarios de los mapeos `8082`–`8085`

**Qué hace.** Sustituye, en los cuatro microservicios de `docker-compose.yml`, el comentario
`# Mapeo TEMPORAL para probar con curl.exe; se elimina cuando exista el gateway Nginx.` por el
texto fijado en `design.md` §8.3: puerto de servicio conservado a propósito, Swagger UI fuera de
`/api`, la verificación por `curl.exe` de las specs 03 a 05 y de la bitácora, y **la aplicación no
los usa**.

**Requisitos que cubre.** HU-08; §8 del `requirements.md` (la contradicción que la spec anula).

**No hace.** **No elimina ni un solo mapeo de puerto** (D-2). No toca `8081` de adminer ni `5432`
de postgres, que nunca anunciaron su eliminación (D-4). No modifica los `requirements.md` de las
specs 02 a 09: son documentos históricos y reescribirlos falsearía la bitácora.

**Verificación.** El YAML sigue siendo válido, la frase anulada ya no aparece, los cuatro mapeos
siguen ahí y Swagger UI sigue abriéndose:

```powershell
docker compose config
Select-String -Path docker-compose.yml -Pattern "se elimina cuando exista el gateway"
Select-String -Path docker-compose.yml -Pattern "8082:8080|8083:8080|8084:8080|8085:8080"
curl.exe -s -o NUL -w "%{http_code}`n" http://localhost:8082/swagger-ui/index.html
```

El primer `Select-String` no debe devolver **ninguna** línea; el segundo debe devolver **cuatro**;
el `curl.exe` debe imprimir `200`. Repetir el último con `8083`, `8084` y `8085`.

---

## T7 — Borrar `shell.conf` y `remote.conf`, y verificación de extremo a extremo

**Qué hace.** Borra `infra/nginx/shell.conf` —su mitad `/api` vive desde T1 en `gateway.conf`, y
su `location /` apuntaba a un `root` que no existe (D-5, D-9, DD-12)— y `infra/nginx/remote.conf`
—modelo de despliegue que el proyecto descartó (D-5)—. Deja `infra/nginx/` con **un solo
archivo**. Cierra la spec con la verificación completa de `design.md` §10.

**Requisitos que cubre.** HU-09 completa; HU-07; y la comprobación final de HU-01 a HU-08.

**No hace.** No toca ningún otro archivo. Los dos borrados no afectan a nada en ejecución: desde
T2 el único archivo montado es `gateway.conf`.

**Verificación.** Primero, que `infra/nginx/` quede con un solo archivo:

```powershell
Get-ChildItem infra\nginx
docker compose restart gateway
docker compose exec gateway nginx -t
```

Después, la batería de `design.md` §10 completa. Un microservicio caído produce `502` y **no**
tumba el gateway (V-8, D-6):

```powershell
docker compose stop ms-reportes
curl.exe -i http://localhost:8090/api/reportes/ocupacion?desde=2026-08-01&hasta=2026-08-31 -H "Authorization: Bearer <token>"
curl.exe -i http://localhost:8090/api/canchas -H "Authorization: Bearer <token>"
docker compose start ms-reportes
```

Esperado: la primera responde **502** con HTML de Nginx —no `{"codigo":"ERROR_INTERNO"}`, que
sería tarea mal hecha—; la segunda responde **200**, porque los otros tres dominios siguen
atendiendo; el gateway sigue en `running` en `docker compose ps`.

Y el recorrido de extremo a extremo (V-9), en el navegador sobre `http://localhost:3000`: iniciar
sesión como `ADMIN`, consultar disponibilidad y crear una reserva en Reservas, editar una cancha
en Administración, y consultar los tres reportes con un rango de fechas. Todo el tráfico `/api`
debe verse en `docker compose logs --tail=100 gateway`.

---

## Resumen del reparto

| Tarea | Archivos que toca | HU que cubre |
|---|---|---|
| T1 | `infra/nginx/gateway.conf` (creado) | HU-01, HU-03, HU-04 |
| T2 | `docker-compose.yml` (servicio `gateway`) | HU-05, HU-06 |
| T3 | `frontend/shell/webpack.config.js` | HU-02 |
| T4 | los tres `frontend/mf-*/webpack.config.js` | HU-02 |
| T5 | `docker-compose.yml` (`depends_on` de los frontends) | HU-05 |
| T6 | `docker-compose.yml` (comentarios de `8082`–`8085`) | HU-08 |
| T7 | `infra/nginx/shell.conf` y `remote.conf` (borrados) | HU-07, HU-09 |

Ningún archivo de `src/`, ningún archivo de `backend/`, ningún archivo de `infra/postgres/` y
ninguna línea de `docs/contratos/README.md`.

## Fuera del alcance de estas tareas

- Eliminar los mapeos `8082`–`8085` (D-2).
- Enrutar Swagger UI o `/v3/api-docs` por el gateway (D-3).
- Servir estáticos del shell o de los remotes desde el gateway (D-1, opción A).
- Traducir el `502` al formato de error del contrato (D-6, DD-09).
- Cambiar las URLs de los remotes, el `publicPath` o el `client.webSocketURL` de cualquier
  microfrontend.
- `resolver`, `upstream`, `proxy_http_version`, timeouts propios, `client_max_body_size`,
  `add_header` y `error_page`: las siete directivas que DD-05 a DD-09 descartaron con motivo.
