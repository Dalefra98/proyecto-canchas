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

## Spec 06 — shell (host de Module Federation)

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 23/08/2026 | C1 | Generar requirements.md del shell, primer microfrontend del proyecto | Sí, tras responder nueve preguntas y corregir dos deducciones | La IA se detuvo en nueve datos faltantes, cuatro de ellos bloqueantes: la contradicción entre el contrato de props (`id`) y el campo congelado (`usuarioId`), la ausencia de cualquier pieza que traduzca `/api` a los puertos `8082`–`8085`, dónde vive el token entre recargas y si la spec entrega el shell sin remotes con los que verificar la carga real |
| 2 | 23/08/2026 | C1 | Responder las nueve preguntas y corregir las dos deducciones erróneas | Sí | **Las dos correcciones fueron del responsable, no de la IA.** (a) El `token` **sí** va en las props: sin él ningún remote puede llamar a la API, y resolverlo aquí evita que las tres specs de remotes lo inventen cada una a su manera. (b) El `ADMIN` **sí** ve el módulo Reservas: la IA lo había excluido leyendo el PDF §3.1, contradiciendo la decisión **D-08 de la spec 04**, ya cerrada, que estableció que el ADMIN crea reservas y tiene historial propio sin `403`. Ambas obligaron a modificar `docs/contratos/README.md` (props con `token` y `usuarioId`, más su fila en el registro de cambios) y `CLAUDE.md` §5 |
| 3 | 23/08/2026 | C1 | Corregir el destino del `devServer.proxy` | Sí | La IA había dejado el proxy apuntando a `localhost:8082`–`8085`, los puertos del host. El responsable corrigió a nombres de contenedor (`http://ms-usuarios:8080`), porque el proxy lo ejecuta `webpack serve` **dentro** de la red de Docker. Quedó escrita la distinción que parece contradictoria y no lo es: las URLs de los remotes son del **navegador** (`http://localhost:3001/remoteEntry.js`) y el destino del proxy es de la **red Docker**. El servicio del shell sumó `depends_on` de los cuatro microservicios |
| 4 | 23/08/2026 | C2 | Generar design.md con modelo de estado, payloads, rutas consumidas, códigos HTTP y decisiones | Sí, sin correcciones | La IA aportó por su cuenta dos trampas que no estaban en las instrucciones: **D-11**, crear los `React.lazy` fuera del render, porque crearlos dentro devuelve un componente nuevo en cada pintada y React desmonta y vuelve a descargar el remote; y **D-14**, `client.webSocketURL` apuntando al host, porque por omisión el cliente deduce la URL del host interno del contenedor y la recarga en caliente falla en silencio. Además declaró que tres de las cinco tablas pedidas no tienen equivalente en un host de Module Federation (no hay base de datos, ni endpoints expuestos, ni excepciones de servidor) y las sustituyó por su análogo, en vez de rellenar tablas vacías |
| 5 | 23-24/08/2026 | C3 | Ejecutar las siete tareas de tasks.md, una por una | Sí, con cuatro hallazgos | Cuatro hallazgos de entorno o de código, detallados en la tabla siguiente. Dos obligaron a modificar la configuración del `devServer` ya escrita en T1 (`watchOptions.poll` y `client.overlay.runtimeErrors`), y ambos quedaron incorporados al `design.md` §3.2 como configuración obligatoria —con sus decisiones **D-15** y **D-16**— porque los tres remotes los van a necesitar igual |

Tiempo invertido: ____

**Hallazgos de la spec 06.** Cuatro, ninguno previsto en el diseño:

| # | Hallazgo | Alcance | Dónde quedó resuelto |
|---|---|---|---|
| 1 | El bind mount de Windows no entrega eventos inotify dentro del contenedor: el watcher de webpack nunca despertaba, el navegador seguía sirviendo un bundle viejo y el registro repetía el `compiled successfully` anterior. Sin esto **ninguna tarea posterior era verificable** | Entorno — el shell y los tres remotes | `watchOptions: { poll: 1000, ignored: /node_modules/ }`, decisión **D-15** del `design.md` §3.2 y hallazgo de entorno al final de esta bitácora |
| 2 | El escapado de comillas de PowerShell mutila el JSON en línea de `curl.exe`: el primer POST por el proxy respondió `400 DATOS_INVALIDOS` **por el escapado, no por el código** | Entorno — todas las verificaciones con cuerpo JSON | Se pasa el cuerpo con `--data "@archivo.json"`; anotado como hallazgo de entorno |
| 3 | Bug del `SyntheticEvent` al cerrar sesión (T5): el evento del `onClick` de la cabecera llegaba como argumento a `cerrarSesion(aviso)`, se guardaba en el estado y React intentaba pintarlo como texto (`Objects are not valid as a React child`). **Compilaba sin errores** y solo apareció al ejecutarlo en el navegador | Código — `App.jsx` | `cerrarSesion()` sin parámetros para el `onClick` y la prop `onLogout`; `cerrarSesionConAviso(aviso)` para el camino de sesión expirada (P-08) |
| 4 | El overlay del dev server tapaba la pantalla con el `ScriptExternalLoadError` de un remote que `BordeError` ya había capturado y mostrado como "Modulo no disponible": ruido sobre un error ya manejado, y en la demo en vivo haría parecer un fallo el comportamiento esperado de HU-06 | Entorno — el shell y los tres remotes | `devServer.client.overlay.runtimeErrors: false`, decisión **D-16**. Los errores siguen apareciendo en la consola del navegador, así que no se pierde diagnóstico |

**Observación:** esta spec cambió el criterio de verificación del proyecto. En los cuatro
microservicios, la prueba de una tarea era la **salida de un comando**: un `curl.exe` con su
código HTTP y su JSON, o un `SELECT` contra la base. En el frontend, el equivalente natural
—el `compiled successfully` del contenedor— **no prueba comportamiento**: prueba que el código
compila. Tres de los cuatro hallazgos (1, 3 y 4) solo aparecieron al abrir el navegador, y el
hallazgo 3 es el caso límite: compiló limpio en T2, T3, T4 y T5, y era un error que rompía la
pantalla en el primer clic. La consecuencia práctica para las tres specs de remotes es que
cada tarea con interacción necesita, además del log, su paso de navegador ejecutado por el
responsable.

**Estado de la spec 06.** Las siete tareas fueron ejecutadas y verificadas con salida real: el
shell corre en `http://localhost:3000` como servicio de `docker-compose.yml`, el proxy alcanza
`ms-usuarios` por nombre de contenedor, el inicio de sesión y el registro funcionan contra las
dos rutas públicas del contrato, el menú filtra por rol, la vista inicial es la bienvenida y
los tres módulos muestran "Modulo no disponible" —que es el criterio de aceptación de HU-06
mientras los remotes no existan (P-04)—. El recorrido completo de navegador de T7 queda a
cargo del responsable.

**Lo que esta spec dejó pendiente a propósito:** los tres remotes (`mf-reservas`,
`mf-administracion`, `mf-reportes`) y el **gateway Nginx**, que se crea en la sección 5 de
integración cuando existan los cuatro microfrontends; hasta entonces cada microfrontend
resuelve `/api` con su propio `devServer.proxy` (P-02, con fecha de revisión escrita en el
`requirements.md` §8). El código del shell tiene además una función sin llamador a propósito:
`manejarSesionExpirada` (P-08), porque las dos rutas del shell son públicas y ningún `401` de
token vencido puede llegarle; la estrenan los remotes al recibir un `401` y llamar `onLogout`.

## Spec 07 — mf-reservas (remote de Module Federation)

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 24/08/2026 | C1 | Generar requirements.md de `mf-reservas`, primer remote del proyecto | Sí, tras responder nueve preguntas | La IA se detuvo en nueve datos faltantes y **ninguno era de contrato**: las nueve eran de presentación o de puesta en marcha (cómo se llega a la pantalla de nueva reserva, si el selector bloquea fechas pasadas, si el filtro por deporte se resuelve en el cliente, qué muestra el remote abierto suelto en `localhost:3001`, si el listado se filtra, cómo se confirma una cancelación, dónde vive el proxy, si el shell se modifica y en qué orden se verifica). **Es la primera spec sin una sola pregunta sobre campos ni rutas**: el contrato congelado ya cubría todo lo que el remote necesitaba, y las respuestas quedaron como decisiones P-01 a P-09 |
| 2 | 24/08/2026 | C2 | Generar design.md con modelo de estado, payloads, rutas consumidas, códigos HTTP y decisiones | Sí, sin correcciones | La IA aportó por su cuenta **tres hallazgos que no estaban en las instrucciones**: **D-03**, `Access-Control-Allow-Origin: *` en el `devServer` del remote, porque el `remoteEntry.js` lo pide una página servida en `localhost:3000` y sin ese encabezado el navegador bloquea la descarga y el shell mostraría "Modulo no disponible" para siempre; **D-09**, que `new Date().toISOString().slice(0, 10)` devuelve **el día siguiente** en Ecuador (UTC-5) entre las 19:00 y las 23:59, así que la fecha de hoy se arma con los campos locales de `Date` y la grilla no abre en una fecha que el usuario no eligió; y **D-15**, importar el CSS desde `ReservasApp.jsx` y **no** desde `bootstrap.jsx`, porque el shell monta el módulo expuesto directamente y **nunca ejecuta el `bootstrap` del remote**, con lo que el remote se quedaría sin estilos en su único caso real de uso. Además sustituyó las tablas sin equivalente en un remote (base de datos, endpoints expuestos, excepciones de servidor) por su análogo, igual que la spec 06 |
| 3 | 24-25/08/2026 | C3 | Ejecutar las siete tareas de tasks.md, una por una | Sí, con tres hallazgos | Tres hallazgos, detallados en la tabla siguiente. Dos de ellos —el encabezado `X_TEST` y el criterio de verificación de T2— quedaron además anotados como hallazgos de entorno al final de esta bitácora, porque aplican a los tres remotes |

Tiempo invertido: ____

**Hallazgos de la spec 07.** Tres, ninguno previsto en el diseño:

| # | Hallazgo | Alcance | Dónde quedó resuelto |
|---|---|---|---|
| 1 | `webpack-dev-server` 5.2.0 agrega el encabezado **`X_TEST: TEST`** a toda respuesta cuando la configuración declara `devServer.headers`. No sale de nuestra configuración: es una línea de depuración de esa versión, en `node_modules/webpack-dev-server/lib/Server.js:3060`, dentro del mismo bloque que aplica los encabezados declarados. Se ve en los `curl.exe` del remote y no en los del shell, que no declara `headers` | Entorno — los tres remotes | No se resuelve: quitarlo costaría el `Access-Control-Allow-Origin: *` que D-03 necesita. Anotado como hallazgo de entorno para que no se persiga como defecto en las specs 08 y 09 |
| 2 | El `compiled successfully` de T2 **era el de T1**. La capa `api/` y `MensajeError.jsx` se crearon sin que ningún archivo los importara todavía, y **webpack solo compila lo alcanzable desde el `entry`**: los cuatro quedaban fuera del grafo de módulos y el registro habría dicho `compiled successfully` aunque tuvieran errores de sintaxis. Se detectó al leer el registro con `--timestamps` y ver que la marca era **anterior** al último guardado. **Tercer caso del proyecto de criterio de verificación insuficiente** | Método de verificación — todo el frontend | Criterio reemplazado en el `tasks.md` de T2: compilación explícita de los cuatro archivos dentro del contenedor con el mismo `.babelrc` que usa `babel-loader`, vía `@babel/core` (no `@babel/cli`, que no está entre las doce dependencias). La regla de leer siempre con `--timestamps` quedó al inicio del `tasks.md` |
| 3 | Serie de bugs de CSS que **solo aparecían en el navegador**: los botones secundarios perdían el texto al pasar el cursor (texto oscuro sobre el fondo verde oscuro que el `button:hover:enabled` del shell impone sin tocar `color`), las dos líneas de cada bloque de la grilla salían pegadas (`08:00-09:00Libre`), y el campo de fecha desalineaba la fila de filtros por el `input { margin-bottom: 14px }` del shell, que el `select` no recibe. **Ninguno detectable por log**: el CSS compila igual esté bien o mal | Código — `estilos.css` del remote, con causa en el CSS global del shell | T7. El hover se unificó en **una sola regla** `.mfr-modulo button:hover:enabled` que lee variables declaradas por cada clase de botón, en vez de una regla por botón: así ninguna clase puede quedarse con el hover del shell, ni las actuales ni las de las specs 08 y 09. El reparto vertical del bloque y la altura común de los controles se corrigieron en el mismo paso |

**Observación:** esta spec **confirma el patrón del frontend** que abrió la spec 06. En los
cuatro microservicios, la salida de un comando prueba comportamiento: un `curl.exe` con su
código HTTP y su JSON, o un `SELECT` contra la base. En el frontend no. Los tres hallazgos de
C3 exigieron abrir el navegador: el `X_TEST` apareció en una cabecera que había que mirar, el
`compiled successfully` de T2 mentía sobre *qué* se había compilado, y los bugs de CSS son
invisibles para webpack por definición. Con las tres specs de la sección de frontend ya
recorridas, el hueco no es un accidente de una tarea: es la forma de verificar de esta capa, y
las specs 08 y 09 tienen que presupuestar el paso de navegador en cada tarea con interacción.

Una diferencia con la spec 06 vale la pena anotarla: allí los hallazgos fueron **de entorno**
(el watcher, el escapado de PowerShell, el overlay) y uno de **código**; aquí los tres son de
**criterio de verificación y de presentación**. Ninguno tocó la lógica de negocio, y era lo
esperable: el remote no implementa ninguna regla de negocio (§11 del diseño), así que no tenía
dónde equivocarse en RN-01 a RN-08.

**Estado de la spec 07.** Las siete tareas fueron ejecutadas y verificadas con salida real:
`mf-reservas` corre en `http://localhost:3001` como servicio de `docker-compose.yml`, publica
su `remoteEntry.js` con `200` y `Access-Control-Allow-Origin: *`, y el shell lo monta **en
lugar** del "Modulo no disponible" que la spec 06 dejaba como criterio de HU-06. La revisión
campo por campo del §1 del diseño se rehízo en T7 y no encontró ninguna discrepancia: los
trece campos usados son los del contrato congelado, el cuerpo de `POST /api/reservas` sigue
siendo de tres campos y las cuatro props se leen tal como llegan. El recorrido completo de
navegador de T7 queda a cargo del responsable.

**Lo que esta spec dejó pendiente a propósito:** `mf-administracion` y `mf-reportes` (specs 08
y 09) y el **gateway Nginx** de la sección 5 de integración; hasta entonces este remote
resuelve `/api` con su propio `devServer.proxy`, igual que el shell. El servicio `shell` no se
tocó en ninguna tarea (P-08): ya declaraba este remote y ya entregaba las cuatro props desde la
spec 06. La función `manejarSesionExpirada` del shell, sin llamador desde entonces, **queda
estrenada** por este remote: `ReservasApp` detecta el `401 NO_AUTENTICADO` en un único
envoltorio y llama `onLogout()` (D-13).

## Spec 08 — mf-administracion (remote de Module Federation)

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 24/08/2026 | C1 | Generar requirements.md de `mf-administracion`, segundo remote del proyecto | Sí, tras responder diez preguntas | La IA se detuvo en diez datos faltantes y, **igual que en la spec 07, ninguno era de contrato**: las diez eran de presentación o de puesta en marcha (cómo se reparten las pantallas y dónde viven los bloqueos, si el cambio de estado de una cancha se confirma, si el listado de bloqueos filtra por fecha, si el listado global lleva filtros, si el `usuarioId` de cada reserva se resuelve a nombre, qué pasa si el ADMIN se inactiva a sí mismo, qué pinta el remote ante un rol que no es ADMIN, qué microservicios lleva el `depends_on`, cómo se reparten las tareas y dónde vive el formulario). Una observación que el C1 aportó sin que se le pidiera: el PDF define este módulo en **dos** sitios que hay que leer juntos —§3.2 nombra dos pantallas y §3.1 asigna al módulo dos funcionalidades más, bloqueos y usuarios—, así que el alcance real son cuatro funcionalidades y no dos. Las respuestas quedaron como decisiones P-01 a P-10 |
| 2 | 24/08/2026 | C2 | Generar design.md con modelo de estado, payloads, rutas consumidas, códigos HTTP y decisiones | Sí, sin correcciones | La IA aportó por su cuenta **tres decisiones que no estaban en las instrucciones y que separan este remote del anterior**: **D-04**, replicar `clienteApi.js` desde `mf-reservas` en vez de federarlo, porque un remote que dependa de otro deja de ser desplegable por separado (PDF §4.1) y la rúbrica §6 puntúa justo eso —son 90 líneas: la duplicación cuesta menos que el acoplamiento—; **D-06**, **no** cachear catálogos en la raíz, al revés que `ReservasApp` con su D-07, porque aquí el ADMIN **escribe**: un catálogo cacheado mostraría en la pantalla de Reservas el nombre viejo de una cancha recién editada; y **D-14**, que el `PATCH` de estado envíe el valor contrario al que **devolvió la API** para esa fila y no al de un interruptor con estado visual propio, porque con dos pestañas abiertas el interruptor mandaría un valor obsoleto. También verificó campo por campo contra el contrato y dejó escritas cuatro aclaraciones que parecen conflicto y no lo son: `nombre` de cancha frente a `nombre` de usuario, el `id` de la reserva, el `bloqueoId` que rellena el `{id}` del path del `DELETE`, y `activa` (cancha) frente a `activo` (usuario), que **no** se unifican |
| 3 | 24-25/08/2026 | C3 | Ejecutar las ocho tareas de tasks.md, una por una | Sí, con dos hallazgos | Ocho tareas y no las siete que P-09 estimaba: el panel de bloqueos se separó del ABM de canchas porque son tres operaciones con formulario y confirmación propios. Dos hallazgos, detallados en la tabla siguiente. El del `grep` quedó además anotado como hallazgo de entorno al final de esta bitácora, porque aplica a cualquier comando que corra dentro de un contenedor Alpine |

Tiempo invertido: ____

**Hallazgos de la spec 08.** Dos, ninguno de contrato:

| # | Hallazgo | Alcance | Dónde quedó resuelto |
|---|---|---|---|
| 1 | El comando de verificación de T8 escrito en `tasks.md`, `grep -rn 'fetch(' src --include=*.jsx`, **no existe en el contenedor**: `node:20-alpine` trae BusyBox y respondió `grep: unrecognized option: include=*.jsx` seguido de su modo de empleo. `--include`, `--exclude` y `-P` son de GNU grep. Agravante: BusyBox imprime la ayuda y **sigue**, así que en un `sh -c` con comandos encadenados por `;` los siguientes se ejecutan igual y es fácil leer la salida como si todo hubiera corrido | Entorno — cualquier comando que corra dentro de un contenedor Alpine | Comando corregido en el `tasks.md` de T8 a una tubería a un segundo `grep`, y anotado como hallazgo de entorno al final de esta bitácora. Regla para la spec 09: un comando de verificación que corre **dentro** de un contenedor Alpine se prueba antes de escribirlo, o se limita a opciones POSIX |
| 2 | El diseño decía que la fila propia del ADMIN pasa por confirmación (P-06), pero **no distinguía el sentido del cambio**. Al implementar T7 apareció la pregunta: reactivarse a uno mismo **devuelve** el acceso, no lo quita, así que no hay consecuencia que advertir y un diálogo ahí es ruido que enseña a confirmar sin leer | Diseño — precisión, no corrección | T7. Se implementó la confirmación **solo al inactivar** la fila propia, se consultó al responsable y quedó incorporada al `design.md` como **D-19**, con su alternativa descartada, más la precisión fechada en §4.5 y en el flujo F-08 |

**Observación de cierre:** es la **primera spec del proyecto sin ningún hallazgo que exigiera
cambiar el contrato ni tocar un servicio cerrado**. Las siete anteriores dejaron rastro en
`docs/contratos/README.md` —códigos de error nuevos, el rol `SERVICIO`, el parámetro `fecha` de
los bloqueos, las notas de reportes, el `token` en las props del shell— y varias obligaron a
volver sobre un microservicio ya entregado. Esta no: el `docker-compose.yml` fue el único archivo
tocado fuera de `frontend/mf-administracion`, y solo en T1. El molde que dejaron las specs 06 y 07
absorbió todo lo demás: el patrón de `webpack.config.js` y del servicio de Compose, la capa
`src/api/` con su forma única de error, el envoltorio del `401` que llama `onLogout`, el prefijo
de clases CSS y la regla única de hover, y la obligación de verificar en navegador toda tarea con
interacción. Este remote consume **once** rutas contra las cinco del anterior y es el único que
escribe en tres microservicios, y aun así no necesitó ni un campo nuevo: el contrato congelado en
la spec 01 y afinado hasta la 06 ya lo cubría entero.

Vale la pena anotar por qué, para la defensa: las diez preguntas del C1 fueron **todas** de
presentación, exactamente como las nueve de la spec 07. Cuando el contrato está congelado de
verdad, lo que queda por decidir en un microfrontend no es qué datos hay, sino cómo se muestran.

**Estado de la spec 08.** Las ocho tareas fueron ejecutadas y verificadas con salida real:
`mf-administracion` corre en `http://localhost:3002` como servicio de `docker-compose.yml`,
publica su `remoteEntry.js` con `200`, y los dos remotes conviven —`localhost:3001` siguió
respondiendo `200` en cada verificación—. La revisión de T8 dentro del contenedor confirmó que el
único `fetch` del remote vive en `src/api/clienteApi.js`, que no hay `sessionStorage` ni
`localStorage` en ninguna parte, que todas las clases CSS llevan el prefijo `mfa-` y que las once
rutas construidas son las de §6.1, con `{ activa: ... }` para canchas y `{ activo: ... }` para
usuarios. Con este remote quedan cerrados dos criterios de aceptación del PDF §7: el **2** —el
administrador gestiona el catálogo y cancela cualquier reserva— y el **5** —un shell y **dos**
remotes integrados por Module Federation—. Los recorridos de navegador de cada tarea quedan a
cargo del responsable.

**Lo que esta spec dejó pendiente a propósito:** `mf-reportes` (spec 09) y el **gateway Nginx**
de la sección 5 de integración; hasta entonces este remote resuelve `/api` con su propio
`devServer.proxy`, igual que el shell y que `mf-reservas`. El shell no se tocó en ninguna tarea:
ya declaraba este remote, ya restringía la opción "Administracion" al rol `ADMIN` y ya entregaba
las cuatro props desde la spec 06. Tampoco se tocó `mf-reservas`, pese a que `clienteApi.js` es
casi idéntico en los dos: es la duplicación deliberada de D-04.

## Spec 09 — mf-reportes (remote de Module Federation)

Responsable: DAVID ARISTEGA

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | 24/08/2026 | C1 | Generar requirements.md de `mf-reportes`, tercer y último remote del proyecto | Sí, tras responder diez preguntas | La IA se detuvo en diez datos faltantes y, **por tercera spec seguida, ninguno era de contrato**: las diez eran de presentación (cómo se organizan los tres reportes, cuándo se disparan las llamadas y con qué rango arranca el módulo, si el rango es uno o tres, qué hacer con el "por cancha y por deporte" que el PDF pide y el contrato no agrega, qué campo mide la demanda, qué pasa con los empates, qué pinta el remote ante un rol que no es ADMIN, qué lleva el `depends_on`, si se admite una barra de porcentaje y cómo se reparten las tareas). Dos observaciones que el C1 aportó sin que se le pidieran: que la cuarta viñeta del PDF §3.3.5 —"canchas con mayor y menor demanda"— **sí entra en esta spec**, porque P-08 de la spec 05 la delegó explícitamente al frontend "ordenando `items` en el cliente, sin endpoint nuevo"; y que P-02 no podía responderse sola, porque un rango por defecto al montar sería un valor inventado que el contrato no declara. Las respuestas quedaron como decisiones P-01 a P-10. Una corrección del responsable durante el C1 resultó ser un error de lectura sobre una copia del documento —§5 y §9 no estaban duplicadas—: se señaló en vez de borrar nada (`CLAUDE.md` §0.8) |
| 2 | 24/08/2026 | C2 | Generar design.md con modelo de estado, payloads, rutas consumidas, códigos HTTP y decisiones | Sí, sin correcciones | La IA aportó por su cuenta **dos decisiones que ninguna instrucción pedía y que son el corazón del ciclo de consulta**: **D-06**, el contador `intento` dentro de `consulta`, sin el cual reintentar tras un `500` con el mismo rango no dispararía nada —`desde` y `hasta` no cambiaron, el efecto no vería ninguna diferencia y el botón parecería roto—; y **D-07**, que un error **no borre el reporte anterior**, con la etiqueta del período tomada de lo que **devolvió la respuesta** y no de lo escrito en los campos, para que conservar la tabla no pueda confundir dos rangos. También separó `rango` de `consulta` como dos estados distintos (D-05), que es lo que permite cumplir P-02 y P-03 a la vez: escribir fechas sin consultar no altera lo que está en pantalla. Verificación campo por campo contra el contrato: ningún nombre discrepa, con tres aclaraciones que parecen conflicto y no lo son —el `nombre` de cancha frente al de usuario, las filas de cancelaciones que **no** llevan `deporte`, y `desde`/`hasta` que son a la vez parámetro y campo de respuesta— |
| 3 | 25/08/2026 | C3 | Ejecutar las seis tareas de tasks.md, una por una | Sí, con dos hallazgos | Seis tareas, exactamente el reparto que P-10 fijó: andamiaje, Compose, capa `api` con el esqueleto, y una por reporte. Antes de T1 el responsable corrigió el `tasks.md` para que los seis `docker compose logs` llevaran `--timestamps`, con la nota al inicio del archivo: la bitácora ya había registrado dos veces que sin la marca de tiempo el `compiled successfully` de la tarea anterior se lee como una compilación nueva. Dos hallazgos, detallados en la tabla siguiente |

Tiempo invertido: ____

**Hallazgos de la spec 09.** Dos, ninguno de contrato:

| # | Hallazgo | Alcance | Dónde quedó resuelto |
|---|---|---|---|
| 1 | **El diseño tenía un hueco entre dos secciones que por separado eran correctas.** §8.1 pedía que el `SelectorRango` deshabilitara su botón "mientras se carga", pero §4.2 ponía `cargando` en el estado de cada pantalla, y el selector vive **por encima** de las pantallas, en `ReportesApp`: no había camino declarado entre los dos. Nadie lo nota leyendo cada sección por su lado; aparece al implementar | Diseño — hueco, detectado en T3 y resuelto antes de T4 | T3. La IA se detuvo, no improvisó el mecanismo y planteó las dos salidas (subir el estado, o bajarlo y avisar por callback). El responsable eligió **subir `cargando` a `ReportesApp`**, dueño ya de `vista`, `rango` y `consulta`, que son el ciclo completo de una consulta, y añadió la consecuencia que hacía falta escribir: cambiar de vista lo pone en `false` **en el mismo paso** que `consulta` en `null`, o el botón quedaría bloqueado por la carga de una pantalla ya desmontada. Quedó como **D-16** en el `design.md`, con §4.1, §4.2 y §8.1 corregidas |
| 2 | **Una regla global del shell filtrándose al remote.** El selector de rango salía en columna y pegado a la derecha pese a que su clase declaraba `display: flex` y su `gap`. La causa estaba fuera del remote: el `estilos.css` del shell declara `form { display: flex; flex-direction: column }` y ese contenedor es un `<form>`. La clase ganaba en `display`, pero **no declaraba `flex-direction`**, así que esa propiedad la seguía poniendo el shell. `mf-reservas` no lo sufre por una diferencia de una etiqueta: su fila de filtros es un `div`. **El prefijo de clases protege contra colisiones de nombre, no contra selectores de elemento** | Entorno — CSS global en una arquitectura de microfrontends | **D-18** en el `design.md`, con `flex-direction: row` explícito, y anotado como hallazgo de entorno al final de esta bitácora, generalizado a `form`, `table`, `input`, `label` y `button`. Es el **cuarto caso** del proyecto, tras los tres del hallazgo 3 de la spec 08, y como aquellos **solo se ve en el navegador**: el CSS compila igual esté bien o mal |

**Observación de cierre:** es la **segunda spec seguida sin ningún hallazgo que obligara a tocar
el contrato ni un servicio cerrado**. `docs/contratos/README.md` no registra un solo cambio por
esta spec, y `docker-compose.yml` volvió a ser el único archivo tocado fuera de la carpeta del
remote, esta vez en T2. El molde de las specs 06 a 08 absorbió todo: el patrón de
`webpack.config.js` y del servicio de Compose, la capa `src/api/` con su forma única de error, el
envoltorio del `401` que llama `onLogout`, el prefijo de clases CSS con su regla única de hover, y
la obligación de verificar en navegador toda tarea con interacción. Los dos hallazgos lo confirman
desde el otro lado: ninguno es de contrato ni de negocio; uno es una costura entre dos secciones
del propio diseño y el otro es CSS heredado del shell.

Vale la pena anotar el patrón completo, para la defensa: en los tres remotes —nueve preguntas en
la spec 07, diez en la 08 y diez en la 09— **ninguna de las veintinueve fue de contrato**. Cuando
el contrato está congelado de verdad, lo que queda por decidir en un microfrontend no es qué datos
hay, sino cómo se muestran. Y la única decisión de esta spec que roza el contrato —**D-17**, poner
"Cancha" y "Cancelaciones" como encabezados en vez de `canchaId` y `totalCancelaciones`— lo hace
para dejar escrito dónde termina: el contrato congela **nombres de campo del JSON**, que siguen
intactos en el código, no el texto de un encabezado de tabla.

**Estado de la spec 09.** Las seis tareas fueron ejecutadas y verificadas con salida real:
`mf-reportes` corre en `http://localhost:3003` como servicio de `docker-compose.yml`, publica su
`remoteEntry.js` con `200`, y los **tres** remotes conviven —`localhost:3001` y `localhost:3002`
siguieron respondiendo `200` en cada verificación—. La capa `api/` se verificó con el criterio que
la spec 08 dejó escrito, compilando los cinco archivos con `@babel/core` dentro del contenedor,
porque en T3 ninguno estaba todavía en el grafo de módulos de webpack. Las tres rutas se probaron
además con respuesta real por el proxy del propio remote: `ocupacion` y `reservas` devolvieron las
cinco canchas con sus contadores —incluidas las que no tuvieron actividad, en `0`—, `cancelaciones`
devolvió seis para "Padel 1", y un rango con `desde` posterior a `hasta` devolvió
`{"codigo":"DATOS_INVALIDOS", ...}`, que la pantalla muestra tal cual. Con este remote queda
cerrado el criterio de aceptación **4** del PDF §7 —el módulo de reportes muestra ocupación por
cancha y reservas por período— y reforzado el **5**, ahora con tres remotes integrados. Los
recorridos de navegador de cada tarea quedan a cargo del responsable.

**Lo que esta spec dejó pendiente a propósito:** el **gateway Nginx** de la sección 5 de
integración y la eliminación de los mapeos `8082`–`8085`; hasta entonces este remote resuelve
`/api` con su propio `devServer.proxy`, que a diferencia de los otros dos declara **una sola
entrada**, `/api/reportes`, porque es la única ruta base que consume (D-15). El shell no se tocó
en ninguna tarea: ya declaraba este remote, ya creaba su `lazy` y ya restringía la opción
"Reportes" al rol `ADMIN` desde la spec 06. Tampoco se tocaron los otros dos remotes ni ningún
microservicio. Con `mf-reportes` cerrado, los cuatro módulos del PDF §3.2 —Seguridad, Reservas,
Administración y Reportes— están implementados.

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
- Tras reiniciar un microservicio, el que lo consume por HTTP puede devolver `500` en sus
  **primeras** peticiones aunque la dependencia ya esté arriba: `SimpleClientHttpRequestFactory`
  (el `HttpURLConnection` del JDK) mantiene conexiones cacheadas contra el contenedor
  anterior, que están muertas y fallan una vez antes de purgarse. Detectado en la T8 de la
  spec 05: dos `500` seguidos de un `200`, sin tocar nada. Con la política *sin reintentos*
  de D-06 ese fallo llega al cliente, y **se acepta**: reintentar ocultaría fallos reales de
  la dependencia. En la demo, después de `docker compose start` hay que repetir la petición
  una o dos veces antes de mostrar el `200`. Detallado en el `design.md` §7.1 de la spec 05.
- `curl.exe` en PowerShell rompe el JSON en línea: PowerShell se come las comillas escapadas y
  el cuerpo llega mutilado al microservicio, que responde `400 DATOS_INVALIDOS` aunque el
  código esté correcto. Detectado en la T1 de la spec 06 contra
  `POST /api/usuarios/sesiones`. La forma que funciona es pasar el cuerpo desde un archivo:
  `curl.exe -X POST http://localhost:3000/api/usuarios/sesiones -H "Content-Type:
  application/json" --data "@cuerpo.json"`. Un `400` en una verificación con cuerpo en línea
  hay que descartarlo como problema de escapado **antes** de tocar el código.
- En Windows, el bind mount de Docker **no entrega eventos inotify** dentro del contenedor:
  `webpack serve` compila bien al arrancar, pero después nunca ve un archivo guardado y no
  recompila. El síntoma engaña, porque `docker compose logs` sigue mostrando el
  `compiled successfully` viejo con el tamaño viejo del archivo, mientras
  `docker compose exec shell ls -l src/App.jsx` ya muestra el tamaño nuevo: parece que el
  código no se aplicó cuando en realidad no se compiló. Se resuelve con
  `watchOptions: { poll: 1000, ignored: /node_modules/ }` en `webpack.config.js`, que pasa
  el watcher a sondeo. Detectado en la T3 de la spec 06; obligatorio en el shell y en los
  tres remotes, y escrito en el `design.md` §3.2 de esa spec. Al comparar dos corridas del
  registro conviene usar `docker compose logs --timestamps`: es la forma de notar que la
  última compilación es anterior al último guardado.
- **Un `compiled successfully` no prueba que la aplicación funcione.** En la T5 de la spec 06,
  `cerrarSesion(aviso)` recibía como `aviso` el `SyntheticEvent` que React pasa al `onClick`
  del botón de la cabecera, lo guardaba en el estado y `PantallaSesion` lo intentaba pintar
  como texto: `Objects are not valid as a React child (found: object with keys {_reactName,
  _targetInst, type, nativeEvent, ...})`. El defecto es de firma —una función usada a la vez
  como manejador de evento y como receptora de un texto— y webpack no puede verlo: compiló
  limpio en T2, T3, T4 y T5. **Es la primera vez en el proyecto que la verificación por log
  resulta insuficiente**, y apareció solo al ejecutar el flujo en el navegador. Consecuencia
  para el resto del frontend: en un microfrontend, el log del contenedor verifica que el
  código *compila*, no que *funcione*; toda tarea con interacción necesita además su paso de
  navegador. Los cuatro microservicios no tenían este hueco porque su verificación era una
  petición real con `curl.exe`, no una compilación.
- `webpack-dev-server` 5.2.0 agrega el encabezado **`X_TEST: TEST`** a toda respuesta cuando la
  configuración declara `devServer.headers`. No sale de nuestra configuración: es una línea de
  depuración que quedó en esa versión, en `node_modules/webpack-dev-server/lib/Server.js:3060`,
  dentro del mismo bloque que aplica los encabezados declarados. Se ve en los `curl.exe -I` del
  remote y no en los del shell, porque el shell no declara `headers`. **No se puede quitar sin
  perder el `Access-Control-Allow-Origin: *` del `remoteEntry.js`** que la decisión D-03 de la
  spec 07 necesita para que el navegador deje al shell (`localhost:3000`) descargar el remote
  (`localhost:3001`). Detectado en la T1 de la spec 07: es ruido inofensivo, no un defecto, y no
  hay que perseguirlo en las verificaciones de los tres remotes.
- **Tercer caso: el criterio de verificación era insuficiente, otra vez.** En la T2 de la spec 07
  se creó la capa `api/` (`clienteApi.js`, `canchasApi.js`, `reservasApi.js`) y
  `MensajeError.jsx`, y el criterio escrito era el `compiled successfully` del registro. No prueba
  nada: **webpack solo compila lo que alcanza desde el `entry`**, y en T2 ningún archivo importa
  todavía esos cuatro. Quedaban fuera del grafo de módulos, así que el registro habría dicho
  `compiled successfully` aunque tuvieran errores de sintaxis. Se reemplazó por la compilación
  explícita dentro del contenedor con el mismo `.babelrc` que usa `babel-loader`:
  `docker compose exec mf-reservas node -e "...babel.transformFileSync(a)..."` sobre los cuatro
  archivos (con `@babel/core` y no `@babel/cli`, que no está entre las doce dependencias). Es el
  **tercer caso del proyecto** en que el criterio de verificación resulta insuficiente, y los tres
  tienen el mismo patrón: *en el frontend, verificar por log solo prueba lo que webpack alcanza
  desde el `entry`*. Los otros dos: el watcher sin `poll` (T3 de la spec 06), donde el log repetía
  una compilación que nunca ocurrió; y el `SyntheticEvent` de `cerrarSesion` (T5 de la spec 06),
  que compiló limpio cuatro tareas seguidas y solo apareció en el navegador. El primero es el log
  mintiendo sobre *cuándo* compiló, el segundo sobre *qué significa* haber compilado, y este
  tercero sobre *qué* se compiló. De ahí la regla de leer siempre el registro con
  `--timestamps`, anotada al inicio del `tasks.md` de la spec 07.
- **Los comandos escritos para un shell de Linux completo no siempre corren en Alpine.** En la T8
  de la spec 08, la verificación escrita en `tasks.md` era
  `grep -rn 'fetch(' src --include=*.jsx` dentro del contenedor, y respondió
  `grep: unrecognized option: include=*.jsx` seguido del modo de empleo de BusyBox. La causa es
  la imagen: `node:20-alpine` trae **BusyBox**, cuyo `grep` implementa un subconjunto de
  opciones; `--include`, `--exclude` y `-P` son de GNU grep y no existen ahí. Lo mismo vale para
  otras utilidades de la imagen (`sed`, `find`, `ps`): el comando que funciona en la terminal del
  host, o en una imagen `debian`/`ubuntu`, puede no existir dentro del contenedor. La sustitución
  usada, y ya corregida en el `tasks.md` de la spec 08, es una tubería a un segundo `grep`:
  `grep -rn 'fetch(' src | grep '.jsx'`. Consecuencia para las specs que quedan: un comando de
  verificación que corre **dentro** de un contenedor Alpine se prueba antes de escribirlo en
  `tasks.md`, o se limita a opciones POSIX. Detalle que ahorra tiempo: el fallo es ruidoso pero
  inofensivo —BusyBox imprime su ayuda y sigue—, así que en un `sh -c` con varios comandos
  encadenados por `;` **los siguientes se ejecutan igual**, y es fácil leer el resultado como si
  todo hubiera corrido. En la T8 el `grep -rn 'Storage' src` posterior sí corrió y devolvió vacío;
  el que no llegó a ejecutarse fue solo el primero.
- **El prefijo de clases protege contra colisiones de nombre, no contra reglas globales por
  selector de elemento.** En la spec 09, el selector de rango de `mf-reportes` aparecía en
  columna y pegado a la derecha, con un hueco grande a la izquierda, pese a que su clase
  `.mfrep-rango` declaraba `display: flex` y su propio `gap`. La causa no estaba en el remote:
  el `estilos.css` del **shell** declara `form { display: flex; flex-direction: column }`, y ese
  contenedor es un `<form>`. La clase gana en `display` por especificidad, pero **no declaraba
  `flex-direction`**, así que esa propiedad la seguía poniendo el shell; combinada con el
  `align-items: flex-end` del remote, los tres controles salían apilados a la derecha.
  `mf-reservas` no lo sufre por una diferencia de una etiqueta: su fila de filtros es un `div`
  (`.mfr-filtros`), no un `form`. Se resolvió declarando `flex-direction: row` explícito, anotado
  como **D-18** en el `design.md` de la spec 09.

  Generalizado, que es lo que importa para el resto del proyecto: **en una arquitectura de
  microfrontends el CSS sigue siendo global aunque el código esté federado**. Module Federation
  aísla módulos de **JavaScript**, no hojas de estilo: cuando el shell monta un remote, los
  estilos de los cuatro proyectos —shell y tres remotes— conviven en el mismo documento. El
  prefijo (`mfr-`, `mfa-`, `mfrep-`) evita que dos clases con el mismo nombre se pisen, y para eso
  funciona; pero **no evita nada** cuando el shell declara reglas sobre etiquetas HTML: `form`,
  `table`, `input`, `label`, `button`. Un remote hereda todas ellas, y las hereda en silencio,
  porque el síntoma no es un error sino un elemento que se ve distinto de lo que su propio CSS
  dice. Es el cuarto caso del proyecto: los tres anteriores fueron el `button:hover:enabled` del
  shell que dejaba ilegible el texto de los botones secundarios, el `label { margin-bottom: 4px }`
  y el `input { margin-bottom: 14px }` que desalineaban los formularios de los remotes (hallazgo 3
  de la spec 08). Los cuatro se detectaron **solo en el navegador**: el CSS compila igual esté
  bien o mal, así que ningún registro de contenedor los muestra.

  Consecuencia práctica para un remote nuevo: cuando un elemento no se comporta como dice su
  clase, la primera sospecha es una regla del shell sobre esa etiqueta, no la propia clase; y toda
  propiedad de disposición de la que dependa el diseño —`display`, `flex-direction`,
  `align-items`, `margin`— conviene declararla explícita aunque parezca el valor por defecto,
  porque el valor por defecto del remote puede no ser el que hay en la página. Material para la
  sección de arquitectura del informe: **Module Federation aísla módulos de JavaScript, no hojas
  de estilo**; el aislamiento real de CSS exigiría Shadow DOM, CSS Modules o clases generadas, y
  ninguno está en el alcance de este proyecto (`CLAUDE.md` §3 pide CSS plano).
