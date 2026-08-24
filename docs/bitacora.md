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

## Spec 03 — ms-canchas

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md de ms-canchas | Sí, tras responder 6 preguntas | La IA se detuvo en 6 datos faltantes, dos de ellos contradicciones reales entre el contrato congelado y el DDL: nombre de cancha duplicado y bloqueo duplicado tenían restricción `UNIQUE` en la base pero ningún `409` en el contrato. Se crearon `NOMBRE_DUPLICADO` y `BLOQUEO_DUPLICADO`, se agregó el parámetro opcional `fecha` a la ruta de bloqueos y se decidió el filtrado del catálogo por rol |
| 2 | 23/08/2026 | C1 | Resolver el solapamiento parcial de bloqueos y la decisión C-01 | Sí | La IA había dejado el solapamiento parcial como supuesto sin validar (S-10) y había derivado C-01 a la spec 04. El responsable decidió ambas aquí: el solape parcial **sí** se valida, en la capa de servicio y sin tocar el DDL; y `ms-reservas` consultará con credenciales de servicio, quedando el mecanismo como asunto abierto A-01 |
| 3 | 23/08/2026 | C2 | Generar design.md con modelo, DTOs, endpoints y excepciones | Sí, tras 1 corrección | El flujo de alta de bloqueo no decía qué pasa si la cancha está **inactiva**. Se decidió permitirlo —una cancha inactiva puede estar justamente en mantenimiento— y quedó como decisión D-16 |
| 4 | 23/08/2026 | C3 | Generar tasks.md con 5 a 8 tareas verificables | Sí, tras 1 corrección | El tasks.md decía "crea" el Dockerfile, el manejador de excepciones y el filtro de token. Se cambió a **copiar** los cinco archivos desde `ms-usuarios`: si dos servicios validan el mismo JWT con código distinto, la diferencia solo aparece en la integración |
| 5 | 23/08/2026 | C3 | Ejecutar T1 a T4 (esqueleto, entidades, DTOs y seguridad) | Sí, tras 2 correcciones | Spring Initializr rechazó `3.5.3` con `compatibility range is >=4.0.0`: se descargó con `4.0.8` y se corrigió el `<parent>` a mano, como ya preveía `CLAUDE.md` §3. Los `@Pattern` de los DTOs fallaron con `illegal escape character` porque el heredoc del shell colapsó las barras dobles; se reescribieron con clases `[0-9]` |
| 6 | 23/08/2026 | C3 | Ejecutar T5 a T8 (catálogo, escritura, bloqueos y OpenAPI) | Sí | 4 ejecuciones verificadas con salida real: filtrado por rol con `404` indistinguible en cancha inactiva, `409 NOMBRE_DUPLICADO` en alta y edición, y las cuatro reglas del bloqueo —solape parcial, fuera de horario, fecha pasada y cancha inactiva permitida—. Sin correcciones de contenido |

Total de iteraciones: 6
Tiempo invertido: ____

**Observación:** el aporte de las compuertas en esta spec fue distinto al de las anteriores.
No aparecieron datos inventados, sino **huecos entre documentos ya congelados**: el DDL
prohibía cosas que el contrato no sabía responder, y el diseño describía un flujo sin decir
qué pasa en un estado que el propio sistema permite (cancha inactiva). Ninguno de los tres
lo habría detectado por separado; salieron de compararlos entre sí.

**Estado de la spec 03: cerrada.** Ocho tareas ejecutadas y verificadas con salida real; los
ocho endpoints congelados de `/api/canchas` responden con los nombres y códigos del
contrato, y el documento OpenAPI los declara con sus códigos de error.

**Dato que se conserva a propósito:** la cancha `Padel 2` (`canchaId = 4`, `08:00`–`21:00`)
creada durante T6 **no es basura de pruebas** y no debe borrarse: su horario distinto al de
las tres canchas del seed sirve en la spec 04 para comprobar que la disponibilidad respeta
el horario de atención de cada cancha y no uno fijo. La tabla `bloqueo_mantenimiento` sí se
dejó vacía, como en el seed.

---

## Spec 04 — ms-reservas

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md de ms-reservas | Sí, tras responder 11 preguntas | La IA se detuvo en 11 datos faltantes. Tres eran huecos de fondo: **nadie producía el estado `FINALIZADA`** aunque el contrato y el DDL lo declaran; **RN-06 decía "configurable" sin decir dónde** ni qué cuenta como "activa"; y el asunto **A-01** de la spec 03 seguía sin mecanismo. Además detectó una **contradicción real entre el PDF de alcance y el contrato**: el PDF marca "No" al administrador en crear reservas e historial propio, pero el contrato no declara `403` en esas dos rutas |
| 2 | 23/08/2026 | C1 | Responder las once preguntas y actualizar el contrato | Sí | Se decidió: token de servicio `rol = SERVICIO` con `exp` de 5 min y solo lectura (A-01 resuelto); `FINALIZADA` **calculado al leer**, nunca persistido; límite 3 desde `RESERVAS_MAX_ACTIVAS` contando solo reservas futuras; y **manda el contrato** en la contradicción del PDF: el ADMIN sí reserva. Dos cambios al contrato congelado: valor `SERVICIO` en `rol` y código `RESERVA_NO_CANCELABLE` |
| 3 | 23/08/2026 | C1 | Cerrar los supuestos abiertos | Sí | La IA derivó de cruzar dos decisiones una consecuencia que nadie había pedido, **C-02**: como `FINALIZADA` no se persiste, cancelar una reserva vista como finalizada cae en RN-04 y responde `RESERVA_PASADA`, no `RESERVA_NO_CANCELABLE`. El responsable la confirmó |
| 4 | 23/08/2026 | C2 | Generar design.md con modelo, DTOs, endpoints y excepciones | Sí, tras 1 corrección | En el flujo de alta, la validación del bloqueo de mantenimiento —una **llamada HTTP**— iba antes que la del bloque ya reservado, que es una consulta local. Se reordenó: los tres devuelven `409`, así que el rechazo más frecuente no debe costar una llamada de red. Quedó como decisión **D-19** |
| 5 | 23/08/2026 | C3 | Generar tasks.md con 5 a 8 tareas verificables | Sí, tras 1 corrección | El responsable agregó **T9**, una tarea sin código: apagar `ms-canchas` y demostrar que `ms-reservas` sigue sirviendo lo que no depende de él, más el ciclo RN-02/RN-05 completo por API. Es la evidencia de independencia entre microservicios para la demo en vivo |
| 6 | 23/08/2026 | C3 | Ejecutar T1 y T2 (esqueleto, entidad y repositorio) | Sí | Spring Initializr volvió a rechazar `3.5.3`: se descargó con `4.0.8` y se corrigió el `<parent>`, como en la spec 03. El primer `docker compose build` falló en `mvn` sin causa visible y el reintento compiló |
| 7 | 23/08/2026 | C3 | Verificar el `@Query` de `contarActivas` antes de seguir | Sí | El responsable pidió revisar la única consulta que **falla en silencio**. Se ejecutó su predicado contra Postgres sobre datos sintéticos: 4 bordes correctos. Apareció un borde no escrito en ninguna parte —una reserva que empieza **exactamente ahora**— y se decidió que no cuenta como activa: ya está ocurriendo, no es un turno acaparable. Decisión **D-20**, extendida luego al otro extremo del bloque |
| 8 | 23/08/2026 | C3 | Ejecutar T3 y T4 (DTOs, mapper, excepciones y seguridad) | Sí | Al verificar T4 salió un defecto **preexistente**: una ruta inexistente respondía `500` en vez de `404`, y `ms-canchas` hacía lo mismo. El responsable decidió corregirlo pero **no ahí**: quedó como asunto abierto **A-02** y tarea **T10**, la última, para no mezclar un cambio transversal con la implementación de `ms-reservas` |
| 9 | 23/08/2026 | C3 | Ejecutar T5 y T6 (rol `SERVICIO` en `ms-canchas`, cliente HTTP y disponibilidad) | Sí | `SeguridadConfig` de `ms-canchas` **no necesitó cambio funcional**: sus `GET` ya eran `.authenticated()`. El cambio real fue el filtro, porque el token de servicio no trae `sub`. Las pruebas del rol `SERVICIO` parecían imposibles sin `java` en el host: se resolvieron acuñando el token **dentro del contenedor** con `openssl`, sin que el secreto saliera de ahí |
| 10 | 23/08/2026 | C3 | Ejecutar T7 a T10 (alta, listados, cancelación, evidencia y `404`) | Sí | 4 tareas verificadas con salida real, sin correcciones de contenido. Para probar RN-04 hubo que **insertar por SQL** una reserva pasada, porque la propia API impide crearla (D-03); quedó declarado y se limpió en T9. En T10 se confirmó primero en el log que la excepción real era `NoResourceFoundException`, así que no hizo falta la propiedad `throw-exception-if-no-handler-found` que la tarea preveía |

Total de iteraciones: 10
Tiempo invertido: ____

**Observación:** en esta spec las compuertas atraparon algo que las anteriores no habían
mostrado: **decisiones que se contradicen entre sí sin que ninguna esté mal**. `FINALIZADA`
calculado al leer y `RESERVA_NO_CANCELABLE` eran ambas razonables por separado, y juntas
dejaban un caso sin respuesta clara (C-02). Lo mismo con el PDF y el contrato: los dos
documentos son válidos y decían cosas distintas sobre el mismo endpoint. Ninguna revisión de
código lo habría detectado, porque no hay línea equivocada que señalar.

El otro aporte fue de **orden de ejecución**, no de contenido: D-19 y la separación de T10 no
cambian ninguna respuesta de la API, cambian cuánto cuesta darla y qué queda mezclado en un
commit.

**Estado de la spec 04: cerrada.** Diez tareas ejecutadas y verificadas con salida real; las
cinco rutas congeladas de `/api/reservas` responden con los nombres y códigos del contrato, y
el documento OpenAPI declara sus códigos de error. Quedan cerrados los dos asuntos abiertos:
**A-01** (mecanismo de credenciales de servicio, que venía de la spec 03) y **A-02** (`404`
en ruta inexistente, corregido en los tres microservicios).

**Estado en que quedó el entorno:** la tabla `reserva` está **vacía** —las 7 reservas de
prueba se borraron en T9—, `bloqueo_mantenimiento` sigue vacía como en el seed, y la cancha
`Padel 2` (`canchaId = 4`, `08:00`–`21:00`) se conserva activa: la spec 05 la necesita por la
misma razón que la spec 04, tener un horario distinto al de las tres canchas del seed.

---

## Spec 05 — ms-reportes

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md de ms-reportes | Sí, tras responder 12 preguntas | La IA se detuvo en 12 datos faltantes, cuatro de ellos bloqueantes. El hallazgo de fondo fue una **contradicción entre dos specs ya cerradas**: la spec 04 decidió que `ms-reservas` rechaza todo token `SERVICIO`, y `GET /api/reservas` es ADMIN, así que **`ms-reportes` no tenía ninguna credencial válida** para leer su única fuente de datos. También se detectó que la fórmula de `horasDisponibles` no existe en ningún documento y que el ejemplo del contrato (`45`) no cuadra con las 15 h diarias del seed |
| 2 | 23/08/2026 | C1 | Responder las doce preguntas y actualizar contrato, CLAUDE.md y la spec 04 | Sí | El responsable eligió abrir `GET /api/reservas` al rol `SERVICIO` en vez de reenviar el token del ADMIN, para no contradecir C-01 de la spec 03: **un solo mecanismo en todo el sistema vale más que no tocar una spec cerrada**. Se decidió además contar `CONFIRMADA` + `FINALIZADA` en ocupación y reservas (contar solo `CONFIRMADA` daría cero en todo rango pasado), no restar bloqueos de `horasDisponibles`, y asumir por escrito que filtrar el rango en memoria **no escala**. Tres archivos fuera de la spec quedaron modificados: el contrato, `CLAUDE.md` §4 (capa `client`) y la HU-08 de la spec 04, con nota de revisión |

| 3 | 23/08/2026 | C2 | Generar design.md con modelo, DTOs, endpoints, excepciones y decisiones | Sí | Al detallar el cambio de P-01 apareció una **consecuencia que C1 no había visto**: las otras cuatro rutas de `ms-reservas` son `.authenticated()` y hoy rechazan el token `SERVICIO` solo porque el filtro lo descarta antes. En cuanto el filtro lo autentique, `POST /api/reservas` con token de servicio llegaría al controlador y crearía una reserva con `usuarioId` **nulo**. El diseño lo cierra en el mismo paso pasando esas cuatro reglas a `hasAnyRole("ADMIN", "USUARIO")` |

**Observación:** esta spec confirmó que una compuerta también sirve para **releer lo ya
cerrado**. La spec 04 previó el caso ("si `ms-reportes` necesitara llamar con token de
servicio, el mecanismo ya está congelado") y aun así lo dejó roto, porque congeló el
mecanismo de emisión y lo rechazó en la entrada. No es un error de código: las dos frases
son correctas por separado y el defecto solo aparece al escribir el consumidor.

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
- Un token de servicio (o cualquier JWT firmado con `JWT_SECRET`) se puede acuñar **dentro
  de un contenedor** con `openssl dgst -sha256 -mac HMAC -macopt "key:$JWT_SECRET"`, sin
  `java` ni `mvn` en el host y sin que el secreto salga del contenedor. Es la forma de
  probar a mano un rol que ningún endpoint emite. El `wget` de BusyBox, en cambio, **no
  imprime el cuerpo** de las respuestas de error: sirve para el código HTTP, no para el JSON.
- Levantar los tres microservicios a la vez con `docker compose up -d --build` tarda 60–70 s
  cada uno, contra ~11 s cuando se levanta uno solo. Un `curl` inmediato devuelve `HTTP 000`;
  hay que esperar a ver `Started Ms...Application` en el log antes de verificar.
