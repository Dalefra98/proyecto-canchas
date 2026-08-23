# Bitácora del proyecto

Se llena EL MISMO DIA de trabajo.

## Sección 1 — Infraestructura base (vibe coding)

Herramienta: Claude Code (VS Code)   Responsable: DAVID ARISTEGA
Fecha: 23/08/2026

| N.º | Intención del prompt | Qué produjo la IA | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|
| 1 | Crear la estructura de carpetas y archivos vacíos del proyecto | Estructura completa con archivos de 0 bytes | Sí | Ninguna |
| 2 | Auditar la estructura contra la lista blanca | Reporte de sobrantes y faltantes | Sí | Se añadieron .gitkeep en backend y frontend |
| 3 | Verificar tamaño de los archivos creados | Detectó que 15 archivos seguían en 0 bytes | Sí | Se pegó el contenido manualmente |
| 4 | Levantar el entorno Docker | Falló: el daemon de Docker no estaba corriendo | No aplica | Se abrió Docker Desktop antes de reintentar |
| 5 | Verificar las bases creadas | Comando psql sin parámetro -d, error "database admin does not exist" | No | Se corrigió a psql -U admin -d postgres |

Total de iteraciones: 5
Tiempo invertido: ____

**Observación:** el script de automatización no se ejecutó y los archivos quedaron
vacíos; se detectó al verificar tamaños en bytes, no a simple vista en el editor.

---

## Spec 01 — Modelo de datos y contratos

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md del modelo de datos y los contratos | Sí, tras responder los supuestos | La IA listo 7 datos faltantes en vez de inventarlos; se congelaron los campos JSON de usuario, cancha y bloqueo, las rutas REST, los 5 archivos DDL numerados, `RESERVAS_MAX_ACTIVAS` y BCrypt |
| 2 | 23/08/2026 | C2 | Generar design.md con modelo, DTOs, endpoints y excepciones | Sí, tras 6 correcciones | Se congelaron los DTOs de disponibilidad y reportes que la IA dejaba pendientes, se agrego `GET /api/canchas/{canchaId}`, se amplio el rol de los bloqueos a USUARIO, se creo el codigo `EMAIL_DUPLICADO`, se congelo `LoginResponse` y se corrigio la longitud de BCrypt (60, no 72) |
| 3 | 23/08/2026 | C2 | Eliminar las tablas duplicadas del contrato en requirements.md y design.md | Sí | Regla nueva del proyecto: `docs/contratos/README.md` es la unica fuente de verdad; ninguna spec vuelve a copiar campos, rutas ni codigos de error |
| 4 | 23/08/2026 | C3 | Generar tasks.md con 5 a 8 tareas verificables | Sí, tras 3 correcciones | `psql` sin `-d` (mismo error de la seccion 1), una imagen de node solo para hacer `cat`, y faltaba una verificacion final con los 5 scripts montados a la vez: se agrego como T6 parte A |
| 5 | 23/08/2026 | C3 | Ejecutar T1 a T4 (DDL de usuario, cancha, bloqueo y reserva) | Sí | 4 ejecuciones, todas verificadas con salida real de PostgreSQL. Sin correcciones de contenido |
| 6 | 23/08/2026 | C3 | Ejecutar T5 a T7 (seed, verificacion de independencia y bitacora) | Sí | 3 ejecuciones verificadas. Ninguna correccion de contenido |

Total de iteraciones: 11
Tiempo invertido: ____

**Observación:** las tres compuertas sirvieron para lo mismo que en la sección 1: la IA
propone algo razonable pero incompleto, y el trabajo real esta en detectar lo que falta antes
de dejarla escribir. Los 7 datos faltantes de C1 y los DTOs pendientes de C2 habrian llegado
al codigo como nombres inventados si nadie los revisaba.

---

## Spec 02 — ms-usuarios

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md de ms-usuarios | Sí, tras responder 4 preguntas | La IA se detuvo en 4 datos faltantes (mecanismo del token, puerto y variables de Docker, coordenadas Maven, política de contraseña) en vez de inventarlos; se decidió JWT HS256 validado localmente, 8 h de vigencia y contraseña de 8 a 100 caracteres |
| 2 | 23/08/2026 | C2 | Generar design.md con modelo, DTOs, endpoints y excepciones | Sí, tras 4 correcciones | La IA dejó `ERROR_INTERNO` fuera del contrato (se agregó a `docs/contratos/README.md`), no cubría 405 ni 415 (se traducen a 400 `DATOS_INVALIDOS`), no listaba las rutas de Swagger que deben quedar públicas, y nombraba la librería JWT sin fijarla (jjwt 0.12.x) |
| 3 | 23/08/2026 | C3 | Generar tasks.md con 5 a 8 tareas verificables | Sí, tras 2 correcciones | T6 usaba una contraseña de seed que nunca se decidió (está documentada en la cabecera de `05-seed.sql`: `Admin123` / `Usuario123`), y faltaba la precondición de `postgres` en estado `healthy` antes de los `curl` |
| 4 | 23/08/2026 | C3 | Ejecutar T1 (esqueleto Maven, imagen y servicio en compose) | Sí, tras 3 correcciones | Spring Initializr ya solo entrega la rama 4.x: se fijó **Spring Boot 3.5.3** en `CLAUDE.md` §3 porque springdoc 2.8.6 exige Spring Framework 6 y la documentación OpenAPI es entregable obligatorio. El `mvn` del host falló dos veces por TLS y se resolvió con el volumen `m2repo`; el build de imagen falló por DNS en `dependency:go-offline` y se resolvió con cache mount de BuildKit, ahora patrón oficial en `CLAUDE.md` §1 |
| 5 | 23/08/2026 | C3 | Ejecutar T2 a T4 (entidad y repositorio, DTOs y manejo de excepciones, seguridad y token) | Sí | 3 ejecuciones verificadas. `ddl-auto=validate` aceptó la entidad contra el DDL de la spec 01 sin tocar el DDL. Sin correcciones de contenido |
| 6 | 23/08/2026 | C3 | Ejecutar T5 a T7 (registro, inicio de sesión, listado y cambio de estado) | Sí | 3 ejecuciones verificadas con salida real: 201/409/400 en registro, 200/401 en sesión con mensaje idéntico, y 200/403/404/400 en el listado y el cambio de estado, incluida la auto-inactivación del ADMIN |
| 7 | 23/08/2026 | C3 | Ejecutar T8 (OpenAPI y cierre de la spec) | Sí | El documento OpenAPI expone los 4 endpoints con sus códigos exactos y `password` solo en `RegistroRequest` y `LoginRequest`. Hallazgo: el 405 se traduce a 400 solo cuando la petición pasa la cadena de seguridad; sin token, la ruta protegida responde 401 antes de llegar a Spring MVC |

Total de iteraciones: 7
Tiempo invertido: ____

**Hallazgos de la spec 02.** Cuatro, ninguno previsto en el diseño:

| # | Hallazgo | Alcance | Dónde quedó resuelto |
|---|---|---|---|
| 1 | `repo.maven.apache.org` corta el handshake TLS al descargar el árbol completo de dependencias. Dos corridas seguidas de `mvn` fallaron en artefactos distintos, señal de red y no de `pom.xml` | Entorno — specs 03 a 05 | `CLAUDE.md` §1: el comando oficial monta el volumen `m2repo` en `/root/.m2` |
| 2 | El builder de BuildKit no resuelve DNS (`Unknown host repo.maven.apache.org`) y `docker compose build` no monta volúmenes, así que `dependency:go-offline` redescargaba todo y fallaba | Entorno — specs 03 a 05 | `CLAUDE.md` §1: patrón oficial de `Dockerfile` sin `go-offline` y con `--mount=type=cache,target=/root/.m2` |
| 3 | Spring Initializr ya solo entrega la rama 4.x, donde el starter web se renombra y llega Spring Security 7, mientras `springdoc-openapi` solo existe hasta 2.8.6 sobre Spring Framework 6. Como la documentación OpenAPI es entregable obligatorio, se fijó Spring Boot 3.5.3 corrigiendo el `<parent>` a mano | Decisión — los cuatro microservicios | `CLAUDE.md` §3 y `design.md` §8 de esta spec |
| 4 | El 405 se traduce a 400 `DATOS_INVALIDOS` solo cuando la petición pasa la cadena de seguridad. Sin token, una ruta protegida responde 401 antes de llegar a Spring MVC: el manejador es correcto, lo que manda es el orden de las capas | Técnico — esta spec, útil para las demás | Verificado en T8 con y sin token ADMIN |

**Observación:** las correcciones de más peso no fueron de código sino de versiones y de
entorno. Los hallazgos 1 y 2 obligaron a cambiar los comandos oficiales del proyecto, y el 3
a fijar una versión que ninguna spec había decidido. Las tres compuertas volvieron a servir
para lo mismo: la IA propone algo plausible pero incompleto, y el trabajo real está en
detectar lo que falta antes de que llegue al código.

**Estado de la spec 02: cerrada.** Ocho tareas ejecutadas y verificadas con salida real; los
cuatro endpoints congelados de `/api/usuarios` responden con los nombres y códigos del
contrato.

---

**Hallazgos de entorno (aplican a todas las specs):**
- `pg_isready` responde OK mientras Postgres aun ejecuta los scripts de
  `docker-entrypoint-initdb.d`. El `healthcheck` del compose da un falso positivo,
  por lo que un microservicio con `depends_on: service_healthy` puede arrancar antes
  de que existan las tablas. Resuelto antes de la spec 02: la sonda ya no usa
  `pg_isready` sino un `SELECT` del usuario ADMIN del seed, es decir del último script
  del init; `ms-usuarios` arrancó siempre con las tablas ya creadas.
- `repo.maven.apache.org` corta el handshake TLS al descargar el árbol completo de
  dependencias, y falla en un artefacto distinto en cada intento. El comando oficial de
  compilación monta el volumen `m2repo` en `/root/.m2` para cachearlas (`CLAUDE.md` §1).
- El builder de BuildKit no resuelve DNS hacia `repo.maven.apache.org` y
  `docker compose build` no monta volúmenes, así que la caché `m2repo` no aplica dentro
  del build de imagen. El `Dockerfile` oficial no usa `dependency:go-offline` y compila
  con `--mount=type=cache,target=/root/.m2` (`CLAUDE.md` §1).
- En Git Bash, `psql -f /docker-entrypoint-initdb.d/...` requiere `MSYS_NO_PATHCONV=1`
  para que no se traduzca la ruta. Lo mismo aplica a `docker run -w /app`.
