# Arquitectura — Sistema de Reserva de Canchas Deportivas

Lo que este documento **no** repite:

- Cómo levantar, verificar y detener el sistema, y qué puertos usa: [`../README.md`](../README.md).
- Los diagramas C1, C2, C3 y de despliegue: [`diagramas-c4.md`](diagramas-c4.md) y [`workspace.dsl`](workspace.dsl).
- Los nombres de campo, rutas y códigos de error congelados: [`contratos/README.md`](contratos/README.md).
- Las reglas de negocio RN-01 a RN-08 y las prohibiciones de stack: [`../CLAUDE.md`](../CLAUDE.md).

---

## 1. Estilos arquitectónicos

| Estilo | Dónde se aplica | Qué problema resuelve en este proyecto |
|---|---|---|
| **Microservicios** | `backend/ms-usuarios/`, `backend/ms-canchas/`, `backend/ms-reservas/`, `backend/ms-reportes/`, cada uno con su servicio e imagen en [`../docker-compose.yml`](../docker-compose.yml) | El dominio se parte en cuatro responsabilidades independientes: identidad, catálogo, reservas y agregación. Cada una se compila, despliega y falla por separado; con `ms-reportes` caído se sigue reservando |
| **Microfrontends con composición en tiempo de ejecución** | `frontend/shell/` como host y `frontend/mf-reservas/`, `frontend/mf-administracion/`, `frontend/mf-reportes/` como remotes, con `ModuleFederationPlugin` en cada `webpack.config.js` | Cada pantalla es un despliegue propio en su propio puerto. El shell descarga el `remoteEntry.js` del remote **en el navegador, al pulsar el módulo**; no lo importa en tiempo de compilación ni hay que recompilarlo cuando un remote cambia |
| **Arquitectura en capas dentro de cada microservicio** | `controller/ → service/ → repository/ → entity/` en los tres servicios con base propia; `controller/ → service/ → client/` en `backend/ms-reportes/` | Cada capa tiene un solo motivo para cambiar y una sola puerta de salida: solo `repository/` toca la base, solo `client/` sale por HTTP. Hace comprobable la independencia de datos en vez de dejarla en la disciplina de quien escribe |

Los tres se combinan: microservicios y microfrontends definen los límites de despliegue; las
capas definen la estructura interna dentro de cada límite.

---

## 2. Patrones aplicados

| Patrón | Dónde se ve | Qué hace aquí | Por qué se eligió |
|---|---|---|---|
| **Database per Service** | [`../infra/postgres/init.sql`](../infra/postgres/init.sql) | Crea tres usuarios y tres bases (`usuarios_db`, `canchas_db`, `reservas_db`), cada base propiedad de su usuario, y ejecuta `REVOKE ALL ... FROM PUBLIC` sobre las tres | Es lo que hace **imposible**, no solo prohibido, que un servicio lea la base de otro: `reservas_user` no tiene permiso sobre `canchas_db`. Spec 01, §8 |
| **Sin claves foráneas entre servicios** | [`../infra/postgres/04-ddl-reservas.sql`](../infra/postgres/04-ddl-reservas.sql) y `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/entity/Reserva.java` | `usuario_id` y `cancha_id` son `BIGINT` / `Long` simples, sin FK ni `@ManyToOne` | Esas filas viven en otras bases. Una asociación JPA obligaría a un segundo datasource o a duplicar datos. Spec 04, §9 D-02 |
| **API Gateway** | [`../infra/nginx/gateway.conf`](../infra/nginx/gateway.conf) | Un `server` en el puerto 80 con cuatro `location` por prefijo (`/api/usuarios`, `/api/canchas`, `/api/reservas`, `/api/reportes`) y un `location /` que devuelve 404 | Punto de entrada único: los cuatro frontends llaman a `/api` y solo este archivo sabe qué microservicio atiende cada prefijo. El `proxy_pass` va sin barra final para que el microservicio reciba la ruta íntegra. Spec 10, §12 DD-02 y DD-04 |
| **DTO** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/dto/ReservaRequest.java` y `.../dto/ReservaResponse.java` | Clases separadas de la entidad, validadas con `jakarta.validation`; fecha y hora viajan como `String` para respetar `AAAA-MM-DD` y `HH:mm` | La entidad sigue al DDL, el DTO sigue al contrato congelado. Con `LocalTime` y el serializador por omisión, Jackson emitiría `07:00:00` y rompería el contrato. Spec 04, §9 D-11 |
| **DTO recortado del servicio externo** | `backend/ms-reportes/src/main/java/ec/ups/dae/reportes/dto/CanchaExterna.java` y `.../dto/ReservaExterna.java` | Declaran solo los campos que `ms-reportes` usa de la respuesta ajena | Deja por escrito de qué depende realmente el servicio; campos de más sugieren acoplamientos que no existen. Spec 05, §7 D-12 |
| **Repository** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/repository/ReservaRepository.java` | Interfaz `JpaRepository` con consultas derivadas y un `@Query` JPQL (`contarActivas`, RN-06). Único punto que toca la tabla `reserva` | Concentra el acceso a datos sin implementación manual y hace verificable de un vistazo que ninguna consulta cruza a otra base. Spec 04, §9 D-05 |
| **Mapper manual** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/mapper/ReservaMapper.java` | Convierte entidad ↔ DTO con métodos explícitos, formatea `HH:mm`, parsea con `ResolverStyle.STRICT` y deriva el estado `FINALIZADA` al leer | MapStruct y Lombok están prohibidos (`CLAUDE.md` §3), y el cálculo de `FINALIZADA` necesita conversión explícita en un solo sitio para las tres salidas. Spec 04, §9 D-15 y D-16 |
| **Inyección por constructor** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/service/ReservaService.java` (campos `private final` y constructor público) y `backend/ms-reportes/src/main/java/ec/ups/dae/reportes/client/CanchasClient.java` (con `@Qualifier`) | Todas las dependencias son `final` y llegan por constructor; no hay `@Autowired` en campos | Las dependencias quedan explícitas y la clase es instanciable sin contenedor. `@Autowired` en campos está prohibido en `CLAUDE.md` §3 |
| **Autenticación por token sin estado** | `backend/ms-usuarios/src/main/java/ec/ups/dae/usuarios/config/FiltroToken.java` y `.../config/SeguridadConfig.java` (`SessionCreationPolicy.STATELESS`); el mismo par existe en los otros tres servicios | El filtro lee `Authorization: Bearer`, valida el JWT **localmente** con `JWT_SECRET` y deja `usuarioId` como principal y `rol` como authority | Ningún servicio llama a `ms-usuarios` para autenticar: no hay sesión compartida ni estado replicado, que es lo que esta arquitectura evita. Spec 02, §9 D-02 y D-03 |
| **Token de servicio para la llamada interna** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/service/EmisorTokenServicio.java` y `backend/ms-reportes/src/main/java/ec/ups/dae/reportes/service/EmisorTokenServicio.java` | Emiten un JWT con `rol = SERVICIO`, sin `sub` y con `exp` de 5 minutos, uno nuevo en cada llamada saliente | Ningún servicio propaga el token del usuario final. Reutiliza un mecanismo ya probado sin agregar infraestructura, y el `exp` corto limita el daño si el token se filtra. Spec 04, §9 D-13 y D-14 |
| **Autorización por rol en la ruta** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/config/SeguridadConfig.java` | `requestMatchers(...).hasAnyRole(...)` por verbo y ruta: `GET /api/reservas` acepta `ADMIN` y `SERVICIO`, las rutas de escritura no aceptan `SERVICIO` | Lo que depende solo de la ruta se resuelve en la configuración; lo que depende del dato —ser dueño de la reserva, RN-03— se resuelve en el servicio, porque no se conoce hasta cargar la fila. Spec 04, §9 D-07 |
| **Manejo centralizado de errores** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/exception/ManejadorExcepciones.java` (uno por microservicio) | `@RestControllerAdvice` que traduce toda excepción al `{ codigo, mensaje }` del contrato con su código HTTP | Un solo lugar decide la forma del error, y el cliente nunca recibe stacktrace, nombre de clase Java ni SQL (`CLAUDE.md` §4) |
| **Excepciones de negocio tipadas** | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/exception/BloqueOcupadoException.java`, `LimiteReservasException.java`, `ReservaPasadaException.java` | Una clase por regla violada: el servicio lanza, el manejador traduce | El servicio expresa RN-02, RN-06 y RN-04 sin conocer códigos HTTP, y la traducción vive en un único archivo |
| **Cliente HTTP entre servicios** | `backend/ms-reportes/src/main/java/ec/ups/dae/reportes/client/CanchasClient.java` y `.../client/ReservasClient.java`; en `ms-reservas`, `.../service/CanchasClient.java` | Única capa que hace HTTP saliente. Envuelve todo fallo —5xx, 401, 403, timeout, error de conexión— en una excepción propia que sale como `500 ERROR_INTERNO` | Es a la integración lo que el repositorio es a la base: una sola puerta. Un `401` recibido de otro servicio es defecto de configuración nuestro, no error del cliente final. Spec 04, §9 D-08 |
| **Timeouts explícitos, sin reintentos** | `backend/ms-reportes/src/main/java/ec/ups/dae/reportes/config/ClienteHttpConfig.java` | Dos beans `RestClient`, uno por servicio destino, cada uno con su `baseUrl` y 2 s de conexión / 5 s de lectura | La URL y el tiempo de espera de cada dependencia se configuran en un solo lugar. Reintentar multiplicaría la espera sin cambiar el resultado en el caso frecuente, que es el servicio caído. Spec 05, §7 D-13, con los timeouts de la spec 04 |
| **Composición en tiempo de ejecución (Module Federation)** | [`../frontend/shell/webpack.config.js`](../frontend/shell/webpack.config.js), bloques `remotes` y `shared` con `singleton: true`; `frontend/mf-reservas/webpack.config.js`, bloque `exposes` | El host declara tres remotes por URL del navegador; cada remote expone un módulo (`./ReservasApp`, `./AdminApp`, `./ReportesApp`) | El shell no contiene el código de los remotes: lo descarga del puerto de cada uno. `react` y `react-dom` como singleton compartido evitan dos instancias de React en la misma página |
| **Carga diferida del módulo remoto** | `frontend/shell/src/components/ContenedorRemoto.jsx` | `React.lazy(() => import("mfReservas/ReservasApp"))` creado **una sola vez a nivel de módulo**, envuelto en `Suspense` | Crearlo dentro del render devolvería un componente nuevo en cada pintada: React lo trataría como otro tipo y volvería a descargar el remote. Spec 06, §12 D-11 |
| **Error Boundary** | `frontend/shell/src/components/BordeError.jsx` | Único componente de clase del shell: `getDerivedStateFromError` y `componentDidCatch`. Si el remote no carga muestra "Modulo no disponible", y reintenta al cambiar de módulo | Un `React.lazy` que rechaza propaga el error al render, y en React 18 solo un borde de clase lo intercepta; un `try/catch` alrededor del `import()` no ve ese fallo. Spec 06, §12 D-10 |
| **Contrato de props del host al remote** | `frontend/shell/src/components/ContenedorRemoto.jsx` | Entrega exactamente `usuario`, `token`, `apiBaseUrl="/api"` y `onLogout`, y nada más | Si cada remote leyera el token de `sessionStorage`, el shell dejaría de ser dueño de la sesión y un cierre de sesión no se propagaría. Spec 06, §12 D-12 |
| **Capa de API única por microfrontend** | `frontend/mf-reservas/src/api/clienteApi.js`, con su equivalente en `frontend/shell/src/api/`, `frontend/mf-administracion/src/api/` y `frontend/mf-reportes/src/api/` | Única pieza que llama `fetch`; compone la ruta con el `apiBaseUrl` recibido por prop y normaliza todo error a `{ codigo, mensaje }`, incluso cuando la respuesta no trae cuerpo | Un `502` del proxy o una red cortada no traen el cuerpo del contrato. Normalizar en un punto deja a todos los componentes con una sola forma de error que pintar. Spec 06, §12 D-04 |

---

## 3. Patrones descartados deliberadamente

| Patrón | Por qué no aplica a este sistema |
|---|---|
| **Saga y transacciones distribuidas** | Ninguna operación escribe en dos bases. El alta de reserva escribe **solo** en `reservas_db`; lo que necesita de `ms-canchas` es lectura (horario de atención y bloqueos), y el contrato limita el token `SERVICIO` a operaciones de lectura. Sin escritura distribuida no hay nada que compensar: la atomicidad la dan la transacción local y el índice único parcial `ux_reserva_bloque_confirmada` (spec 01 §9 D-02; spec 04 §9 D-03, la doble barrera de RN-02) |
| **Circuit Breaker** | La política de fallo ya está decidida y es la contraria: fallar rápido y por completo. Los clientes HTTP llevan 2 s / 5 s de timeout y **sin reintentos**, y cualquier fallo de dependencia sale como `500 ERROR_INTERNO`; nunca se devuelve un reporte parcial, porque un reporte con `0 %` en todas las canchas es indistinguible de un mes sin reservas (spec 05 §7 D-08). Un cortacircuitos agregaría estado y una biblioteca para cambiar ese mismo `500` por otro `500`. El gateway aplica el mismo criterio: no traduce el `502` al contrato (spec 10 §12 DD-09) |
| **CQRS** | La separación entre lectura y escritura ya existe **entre servicios**, no dentro de uno: `ms-reportes` es el lado de lectura agregada y su `pom.xml` no incluye JPA ni el driver de PostgreSQL, precisamente para que la prohibición de leer bases ajenas esté garantizada por construcción (spec 05 §7 D-01). Dentro de `ms-reservas` el mismo agregado se lee y se escribe con un solo modelo; dos modelos obligarían a sincronizarlos, y el alcance del proyecto no tiene el volumen que lo justifique |
| **Event sourcing** | El estado se guarda como estado, no como historia de eventos. Cancelar es cambiar `estado` a `CANCELADA`, no borrar la fila ni registrar un evento, y eso ya da la trazabilidad que exige RN-08 y el conteo de cancelaciones que necesita `ms-reportes` (spec 01 §9 D-03). Un almacén de eventos con proyecciones sería infraestructura nueva para conservar un dato que una columna ya conserva |
| **Service Discovery** | Los destinos son fijos y los resuelve el DNS de Docker Compose: `MS_CANCHAS_URL: http://ms-canchas:8080` en `docker-compose.yml` y `proxy_pass http://ms-canchas:8080` en `gateway.conf`. Hay **una** instancia por microservicio, así que el gateway no declara bloques `upstream` ni `resolver` en tiempo de ejecución: se prefirió el fallo ruidoso al arrancar sobre el fallo silencioso en caliente (spec 10 §12 DD-05 y DD-10). Un registro de servicios resolvería un problema —réplicas que aparecen y desaparecen— que este despliegue no tiene |

---

## 4. Recorrido guiado

Cinco archivos en orden, para mostrar la arquitectura en vivo en dos minutos.

| # | Ruta | Qué se ve ahí | Qué decir |
|---|---|---|---|
| 1 | [`../infra/postgres/init.sql`](../infra/postgres/init.sql) | Tres usuarios, tres bases, `REVOKE ALL ... FROM PUBLIC` sobre las tres | "La independencia de datos no es una regla del equipo: es un permiso que no existe. `reservas_user` no puede leer `canchas_db` aunque alguien escriba la consulta" |
| 2 | `backend/ms-reservas/src/main/java/ec/ups/dae/reservas/service/ReservaService.java` | Constructor con `ReservaRepository`, `CanchasClient` y `ReservaMapper`; comentarios `// RN-02`, `// RN-06`, `// RN-03` sobre cada validación | "Aquí conviven las capas y las reglas: el dato propio entra por el repositorio, el ajeno por el cliente HTTP, y cada regla de negocio está marcada con su identificador junto a la línea que la implementa" |
| 3 | [`../infra/nginx/gateway.conf`](../infra/nginx/gateway.conf) | Cuatro `location` por prefijo, más el `location /` que devuelve 404 | "Este archivo es el mapa completo del sistema: cuatro prefijos, cuatro microservicios. El frontend solo conoce `/api`; si un servicio cambia de sitio, cambia aquí y en ningún otro lugar" |
| 4 | [`../frontend/shell/webpack.config.js`](../frontend/shell/webpack.config.js) y `frontend/shell/src/components/ContenedorRemoto.jsx` | El bloque `remotes` con URLs del navegador y `shared` singleton; el `React.lazy` con `Suspense` dentro de `BordeError` | "El shell no contiene el código de los otros tres frontends: lo descarga del puerto de cada uno cuando el usuario pulsa el módulo. Y si un remote está caído, el borde de error muestra 'Modulo no disponible' en vez de tumbar la aplicación entera" |
| 5 | [`../.claude/specs/04-ms-reservas/design.md`](../.claude/specs/04-ms-reservas/design.md), §9 | La tabla de veinte decisiones, con alternativa descartada y motivo en cada fila | "Ninguna de estas decisiones se tomó mientras se escribía el código. Cada una tiene su alternativa evaluada y su motivo, escritos antes de implementar, y existe una tabla así en cada una de las diez specs" |
CM
---

## 5. Dónde está el porqué de cada decisión

Las rutas de la última columna son relativas a [`../.claude/specs/`](../.claude/specs/).

| Decisión | Alternativa descartada | Justificada en |
|---|---|---|
| Una base y un usuario por microservicio, sin FK cruzadas | Base única compartida con FK entre tablas | `01-modelo-y-contratos/design.md` §8 y §9 D-08 |
| `estado` y `deporte` como `VARCHAR` + `CHECK` | Tipo `ENUM` de PostgreSQL | `01-modelo-y-contratos/design.md` §9 D-01 |
| Índice único **parcial** sobre `CONFIRMADA` | Índice único sobre las tres columnas sin filtro | `01-modelo-y-contratos/design.md` §9 D-02 |
| Cancelar = cambiar `estado`, no borrar la fila | `DELETE` de la fila | `01-modelo-y-contratos/design.md` §9 D-03 |
| `LoginResponse` = `{ token, usuario }` | `UsuarioResponse` plano y el token en una cabecera | `01-modelo-y-contratos/design.md` §9 D-13 |
| Reportes con envoltura `{ desde, hasta, items }` | Devolver el arreglo desnudo | `01-modelo-y-contratos/design.md` §9 D-14 |
| El `rol` viaja como claim del token, validado localmente | Consultar el rol a `ms-usuarios` en cada petición | `02-ms-usuarios/design.md` §9 D-02 |
| Sin revocación ni lista negra de tokens | Tabla de tokens revocados consultada en cada validación | `02-ms-usuarios/design.md` §9 D-03 |
| Filtrado por rol leyendo el `SecurityContext` en el servicio | Un parámetro `?activa=true`, o filtrar en el frontend | `03-ms-canchas/design.md` §9 D-05 |
| Solapamiento validado en el servicio con consulta derivada | `EXCLUDE` con `btree_gist` en el DDL | `03-ms-canchas/design.md` §9 D-07 |
| `usuarioId` y `canchaId` como `Long` simples, sin asociación JPA | `@ManyToOne` a entidades espejo de usuario y cancha | `04-ms-reservas/design.md` §9 D-02 |
| Doble barrera en RN-02: consulta previa más índice único | Solo la consulta previa, o solo la restricción de base | `04-ms-reservas/design.md` §9 D-03 |
| RN-03 resuelta en el servicio, no en la cadena de filtros | Anotaciones de seguridad a nivel de método | `04-ms-reservas/design.md` §9 D-07 |
| Un `401` o `403` recibido de otro servicio se convierte en `500` | Reenviar el mismo código al cliente final | `04-ms-reservas/design.md` §9 D-08 |
| Fechas y horas como `String` en los DTOs | `LocalDate` / `LocalTime` con `@JsonFormat` | `04-ms-reservas/design.md` §9 D-11 |
| Cliente HTTP con `RestClient` | `RestTemplate`, `WebClient` o un cliente generado | `04-ms-reservas/design.md` §9 D-12 |
| Token de servicio emitido en cada llamada, sin caché | Emitir uno y reutilizarlo hasta que expire | `04-ms-reservas/design.md` §9 D-13 y `05-ms-reportes/design.md` §7 D-14 |
| El rol `SERVICIO` reutiliza el mismo `JWT_SECRET` y el mismo filtro | Una clave compartida en cabecera propia, o un par de claves aparte | `04-ms-reservas/design.md` §9 D-14 |
| `FINALIZADA` calculada en el mapper | `CASE` en SQL, o persistirla con una tarea programada | `04-ms-reservas/design.md` §9 D-15 |
| Mapper manual con métodos explícitos | MapStruct o reflexión genérica | `04-ms-reservas/design.md` §9 D-16 |
| `ms-reportes` sin JPA ni driver de PostgreSQL en el `pom.xml` | Incluir JPA y excluir la autoconfiguración del datasource | `05-ms-reportes/design.md` §7 D-01 |
| Fallo de una dependencia = `500`, nunca reporte parcial | Devolver el catálogo con ceros si `ms-reservas` no responde | `05-ms-reportes/design.md` §7 D-08 |
| Las dos llamadas salientes son secuenciales | Lanzarlas en paralelo con `CompletableFuture` o un cliente reactivo | `05-ms-reportes/design.md` §7 D-09 |
| Un bean `RestClient` por servicio destino, con `baseUrl` y timeouts propios | Un cliente sin `baseUrl` y la URL completa en cada llamada | `05-ms-reportes/design.md` §7 D-13 |
| Versiones exactas de las dependencias del frontend | Rangos `^` en cada microfrontend | `06-shell-module-federation/design.md` §12 D-01 |
| Error normalizado a `{ codigo, mensaje }` en `clienteApi` | Propagar el error crudo de `fetch` a los componentes | `06-shell-module-federation/design.md` §12 D-04 |
| Estado de sesión en `App.jsx`, bajando por props | `Context` de React | `06-shell-module-federation/design.md` §12 D-08 |
| Error Boundary de clase para el fallo del remote | `try/catch` alrededor del `import()` | `06-shell-module-federation/design.md` §12 D-10 |
| `React.lazy` creados una vez a nivel de módulo | Crearlos dentro del render de `ContenedorRemoto` | `06-shell-module-federation/design.md` §12 D-11 |
| El `token` llega al remote por prop | Que cada remote lo lea de `sessionStorage` | `06-shell-module-federation/design.md` §12 D-12 |
| `gateway.conf` montado como `default.conf` | Montarlo como un archivo más de `conf.d/` | `10-gateway-nginx/design.md` §12 DD-01 |
| `proxy_pass` sin barra final ni ruta | `proxy_pass http://ms-canchas:8080/` | `10-gateway-nginx/design.md` §12 DD-02 |
| Cuatro `location` literales por prefijo | Un `location` con expresión regular y destino por variable | `10-gateway-nginx/design.md` §12 DD-03 |
| `location /` con `return 404` explícito | Dejar que Nginx sirva su `root` por omisión | `10-gateway-nginx/design.md` §12 DD-04 |
| Sin `resolver` ni resolución DNS en tiempo de ejecución | `resolver 127.0.0.11 valid=10s` con el destino en una variable | `10-gateway-nginx/design.md` §12 DD-05 |
| Sin `error_page` que traduzca el `502` al contrato | `error_page 502 = @json` devolviendo `{ codigo: ERROR_INTERNO }` | `10-gateway-nginx/design.md` §12 DD-09 |
| Un solo `server`, sin bloques `upstream` | Un `upstream` por microservicio | `10-gateway-nginx/design.md` §12 DD-10 |
