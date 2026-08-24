# Spec 05 — ms-reportes · design.md

Estado: **C2 — APROBADO** el 23/08/2026 ("Apruebo diseño de la spec 05").
La compuerta C3 (`tasks.md`) sigue pendiente: no se escribe código de producción hasta que
exista la lista de tareas y se ejecute una a la vez.

Requisitos de partida: `.claude/specs/05-ms-reportes/requirements.md`, **C1 aprobado** el
23/08/2026, con las doce decisiones P-01 a P-12 ya incorporadas.

Fuentes leídas para este diseño: `CLAUDE.md`, `docs/contratos/README.md`, el
`requirements.md` de esta spec, y el código ya en producción de `ms-reservas`
(`FiltroToken`, `SeguridadConfig`, `ClienteHttpConfig`, `EmisorTokenServicio`,
`CanchasClient`, `ManejadorExcepciones`, `pom.xml`, `application.properties`), que es el
patrón que este servicio copia.

**Verificación campo por campo contra `docs/contratos/README.md`: sin discrepancias.** Los
once nombres que este servicio serializa (`desde`, `hasta`, `items`, `canchaId`, `nombre`,
`deporte`, `horasReservadas`, `horasDisponibles`, `totalReservas`, `totalCancelaciones`,
`porcentajeOcupacion`) y los dos del error (`codigo`, `mensaje`) coinciden literalmente con
el contrato. El detalle está en §4.4.

## 1. Arquitectura del servicio

```
backend/ms-reportes/src/main/java/ec/ups/dae/reportes/
  controller/   ReporteController
  service/      ReporteService, CalculadoraOcupacion, TokenService, EmisorTokenServicio
  client/       CanchasClient, ReservasClient
  dto/          ReporteOcupacionResponse, OcupacionItem, ReporteReservasResponse,
                ReservasItem, ReporteCancelacionesResponse, CancelacionesItem,
                CanchaExterna, ReservaExterna, ErrorResponse
  mapper/       ReporteMapper
  config/       SeguridadConfig, FiltroToken, ClienteHttpConfig, OpenApiConfig
  exception/    RangoInvalidoException, CatalogoNoDisponibleException,
                ReservasNoDisponiblesException, ManejadorExcepciones
```

No hay `repository/` ni `entity/`: `client/` ocupa su lugar, según `CLAUDE.md` §4 (decisión
P-12). El flujo es siempre `controller -> service -> client`, en un solo sentido.

Flujo de una petición, idéntico para los tres reportes:

```
GET /api/reportes/<tipo>?desde&hasta
  -> FiltroToken            valida el JWT entrante y publica el rol
  -> SeguridadConfig        exige ROLE_ADMIN
  -> ReporteController      parsea y valida desde/hasta
  -> ReporteService         pide catalogo y reservas, filtra por rango, agrega
       -> CanchasClient     GET  ms-canchas /api/canchas    (token SERVICIO)
       -> ReservasClient    GET  ms-reservas /api/reservas  (token SERVICIO)
  -> CalculadoraOcupacion   solo en el reporte de ocupacion
  -> ReporteMapper          arma el DTO de respuesta
```

Las dos llamadas salientes son **secuenciales**, no concurrentes (decisión D-09).

## 2. Modelo de datos

### 2.1 `ms-reportes` no tiene esquema propio

**No hay tablas, ni entidades JPA, ni `DataSource`, ni usuario de PostgreSQL, ni archivo DDL
en `infra/postgres/`.** No se agrega `spring-boot-starter-data-jpa` ni el driver
`org.postgresql:postgresql` al `pom.xml`, así que no hay autoconfiguración de `DataSource`
que excluir ni propiedad `spring.jpa.hibernate.ddl-auto` que declarar.

Ninguna consulta de este microservicio accede a tabla alguna, ni propia ni ajena:
`reservas_db` y `canchas_db` son inalcanzables desde aquí porque el servicio no tiene
credenciales ni driver para conectarse a Postgres. La prohibición de `CLAUDE.md` §3 no
depende de la disciplina del código: está garantizada por ausencia de dependencia.

`infra/postgres/*.sql` **no se toca** en esta spec, y `docker-compose.yml` no declara
`SPRING_DATASOURCE_*` para este servicio.

### 2.2 Recursos externos consumidos, en lugar del modelo de datos

Lo que en los otros tres microservicios sería el modelo de tablas, aquí es el contrato de
lectura de dos recursos REST ajenos. Ambos ya están congelados y **no** se les pide ningún
campo nuevo.

`GET /api/canchas` de `ms-canchas`, con token `SERVICIO` (vista completa, inactivas
incluidas):

| Campo | Tipo | Uso en `ms-reportes` | ¿Obligatorio? |
|---|---|---|---|
| `canchaId` | number | Clave del cruce con las reservas y campo de salida | Sí |
| `nombre` | string | Campo de salida en los tres reportes | Sí |
| `deporte` | `PADEL`/`TENIS`/`BASQUET` | Campo de salida en ocupación y reservas | Sí |
| `horaApertura` | `HH:mm` | Cálculo de `horasDisponibles` | Sí |
| `horaCierre` | `HH:mm` | Cálculo de `horasDisponibles` | Sí |
| `activa` | boolean | Se lee y **se ignora**: P-09 incluye las inactivas | No |

`GET /api/reservas` de `ms-reservas`, con token `SERVICIO` (habilitado por P-01):

| Campo | Tipo | Uso en `ms-reportes` | ¿Obligatorio? |
|---|---|---|---|
| `canchaId` | number | Clave del cruce con el catálogo | Sí |
| `fecha` | `AAAA-MM-DD` | Filtro del rango `desde`–`hasta` | Sí |
| `estado` | `CONFIRMADA`/`CANCELADA`/`FINALIZADA` | Clasificación de los tres reportes | Sí |
| `id` | number | No se usa: no se deserializa | No |
| `usuarioId` | number | No se usa: ningún reporte lleva datos de usuario | No |
| `horaInicio` / `horaFin` | `HH:mm` | No se usan: cada reserva vale una hora por RN-01 | No |

Los campos marcados "No" **no se declaran** en el DTO de entrada: Spring Boot deja
`FAIL_ON_UNKNOWN_PROPERTIES` en `false` y los ignora. Recortar el DTO deja explícito qué
depende realmente de cada servicio.

### 2.3 Estructuras de agregación en memoria

Sustituyen a las consultas SQL de los otros microservicios. Se construyen por petición y se
descartan al responder: no hay caché (§7).

| Estructura | Tipo | Contenido | Se usa en |
|---|---|---|---|
| `canchas` | `List<CanchaExterna>` | Catálogo completo, en el orden que devuelve `ms-canchas` | los tres reportes |
| `reservasDelRango` | `List<ReservaExterna>` | Reservas con `fecha` entre `desde` y `hasta`, ambos inclusive | los tres reportes |
| `conteoPorCancha` | `Map<Long, Long>` | `canchaId` -> número de reservas que cumplen el filtro de estado | los tres reportes |

`conteoPorCancha` se calcula una vez por petición con el predicado de estado que
corresponda: `CONFIRMADA` o `FINALIZADA` para ocupación y reservas, `CANCELADA` para
cancelaciones (P-04).

## 3. Reglas de cálculo

Las tres son decisiones ya aprobadas en C1; aquí queda su forma exacta.

### 3.1 Días del rango (P-07)

```
diasDelRango = ChronoUnit.DAYS.between(desde, hasta) + 1
```

Ambos extremos inclusivos. `desde` igual a `hasta` da `1`. Nunca es `0` ni negativo, porque
`desde > hasta` ya fue rechazado con `400` antes de llegar aquí.

### 3.2 `horasDisponibles` (P-03)

```
horasPorDia      = Duration.between(horaApertura, horaCierre).toHours()
horasDisponibles = horasPorDia * diasDelRango
```

Se calcula **por cancha**, con su propio horario de atención: `Padel 1` (07:00–22:00) da 15
h/día y `Padel 2` (08:00–21:00) da 13 h/día. No se restan los bloqueos de mantenimiento y no
se consulta `GET /api/canchas/{canchaId}/bloqueos`.

Si `horaCierre` fuera menor o igual a `horaApertura`, `horasPorDia` sería `0` o negativo; se
trunca a `0` y el porcentaje sale `0`. `ms-canchas` ya impide crear una cancha así, pero el
cálculo no puede depender de eso.

### 3.3 `horasReservadas`, `totalReservas` y `totalCancelaciones` (P-04)

Cada reserva vale exactamente **una hora** (RN-01, respaldada por
`ck_reserva_bloque_una_hora` en `reservas_db`), así que las tres cifras son conteos, no
sumas de duración. `horaInicio` y `horaFin` no intervienen.

| Cifra | Estados que cuentan |
|---|---|
| `horasReservadas` | `CONFIRMADA`, `FINALIZADA` |
| `totalReservas` | `CONFIRMADA`, `FINALIZADA` |
| `totalCancelaciones` | `CANCELADA` |

`horasReservadas` y `totalReservas` son numéricamente el mismo valor para una misma cancha y
un mismo rango. Son dos campos porque el contrato los declara en dos payloads distintos y
significan cosas distintas; se calculan con el mismo método y no se unifican los endpoints.

`ms-reportes` **no** recalcula `FINALIZADA`: toma el `estado` tal cual llega de
`ms-reservas`, que lo calcula al leer (D-02 de la spec 04). Un `estado` desconocido no
cuenta en ningún reporte y se registra en el log como `WARN`.

### 3.4 `porcentajeOcupacion` (P-06)

```
si horasDisponibles <= 0  ->  0.0
si no                     ->  BigDecimal(horasReservadas * 100)
                                .divide(BigDecimal(horasDisponibles), 1, RoundingMode.HALF_UP)
```

Un decimal, `HALF_UP`, tipo `BigDecimal` para que la división no arrastre error binario y
para que Jackson serialice `26.7` y no `26.699999999999999`. El resultado está siempre entre
`0.0` y `100.0`: `horasReservadas` no puede superar a `horasDisponibles` mientras
`ms-reservas` respete el horario de atención al crear la reserva.

### 3.5 Cruce catálogo–reservas

El catálogo manda: `items` tiene **una fila por cancha del catálogo**, en el mismo orden en
que `ms-canchas` las devuelve (decisión D-10), con ceros si no hubo actividad (P-09).

Una reserva cuyo `canchaId` no esté en el catálogo se **descarta** y se registra como `WARN`
en el log. No puede ocurrir hoy —`ms-canchas` inactiva, no borra— y si ocurriera, inventar
una fila sin `nombre` ni `deporte` produciría un payload incompleto.

## 4. DTOs

Todos son `record` de Java 21, inmutables, sin Lombok. Los de salida se serializan tal cual:
el nombre del componente **es** el nombre del campo JSON, sin `@JsonProperty` que renombre
nada.

### 4.1 DTOs de salida

| DTO | Componentes | Notas |
|---|---|---|
| `ReporteOcupacionResponse` | `String desde`, `String hasta`, `List<OcupacionItem> items` | `desde`/`hasta` se devuelven **tal como llegaron** en la petición |
| `OcupacionItem` | `Long canchaId`, `String nombre`, `String deporte`, `long horasReservadas`, `long horasDisponibles`, `BigDecimal porcentajeOcupacion` | |
| `ReporteReservasResponse` | `String desde`, `String hasta`, `List<ReservasItem> items` | |
| `ReservasItem` | `Long canchaId`, `String nombre`, `String deporte`, `long totalReservas` | |
| `ReporteCancelacionesResponse` | `String desde`, `String hasta`, `List<CancelacionesItem> items` | |
| `CancelacionesItem` | `Long canchaId`, `String nombre`, `long totalCancelaciones` | **Sin `deporte`**: el payload congelado no lo declara |
| `ErrorResponse` | `String codigo`, `String mensaje` | Copia literal del de los otros tres servicios |

`desde` y `hasta` viajan como `String`, no como `LocalDate`, para devolver exactamente el
texto recibido sin depender de la serialización de fechas de Jackson. El `LocalDate` parseado
vive solo dentro del servicio.

Las tres envolturas comparten forma pero **no** se factorizan en una clase genérica
`ReporteResponse<T>` (decisión D-11).

### 4.2 DTOs de entrada desde otros servicios

Nunca se serializan hacia el cliente final.

| DTO | Componentes | Origen |
|---|---|---|
| `CanchaExterna` | `Long canchaId`, `String nombre`, `String deporte`, `String horaApertura`, `String horaCierre` | `GET /api/canchas` |
| `ReservaExterna` | `Long canchaId`, `String fecha`, `String estado` | `GET /api/reservas` |

`activa` no se declara en `CanchaExterna`: P-09 incluye las canchas inactivas, así que el
dato no cambia ninguna decisión y declararlo sugeriría un filtro que no existe.

### 4.3 Validaciones

No hay ningún cuerpo de petición: los tres endpoints son `GET` con dos parámetros de
consulta. Por eso **no se usa `jakarta.validation` con `@Valid`** — no hay objeto que anotar
— y la validación vive en el controlador, con parseo estricto (decisión D-04).

| Parámetro | Regla | Fallo |
|---|---|---|
| `desde` | Obligatorio (`@RequestParam` sin `defaultValue`) | `400 DATOS_INVALIDOS` |
| `hasta` | Obligatorio | `400 DATOS_INVALIDOS` |
| `desde` | Formato `AAAA-MM-DD`, parseo estricto con `DateTimeFormatter.ISO_LOCAL_DATE` | `400 DATOS_INVALIDOS` |
| `hasta` | Formato `AAAA-MM-DD`, mismo parseo | `400 DATOS_INVALIDOS` |
| `desde`, `hasta` | `desde` no puede ser posterior a `hasta` | `400 DATOS_INVALIDOS` |

No hay rango máximo ni restricción de fechas futuras (P-07). `2026-02-30` es una fecha
inexistente y el parseo estricto la rechaza con `400`.

`desde` y `hasta` se reciben como `String` y se parsean a mano, no como `LocalDate` con
`@DateTimeFormat`: así el mensaje de error lo controla este servicio y no depende de cómo
Spring formule el fallo de conversión (mismo criterio que D-11 de la spec 04).

### 4.4 Verificación campo por campo contra el contrato

| Campo del contrato | Componente del DTO | ¿Coincide? |
|---|---|---|
| `desde` | `desde` | Sí |
| `hasta` | `hasta` | Sí |
| `items` | `items` | Sí |
| `canchaId` | `canchaId` | Sí |
| `nombre` | `nombre` | Sí |
| `deporte` | `deporte` | Sí |
| `horasReservadas` | `horasReservadas` | Sí |
| `horasDisponibles` | `horasDisponibles` | Sí |
| `totalReservas` | `totalReservas` | Sí |
| `totalCancelaciones` | `totalCancelaciones` | Sí |
| `porcentajeOcupacion` | `porcentajeOcupacion` | Sí |
| `codigo` | `codigo` | Sí |
| `mensaje` | `mensaje` | Sí |

Ningún nombre se renombra, abrevia ni traduce, y no se agrega ningún campo que el contrato
no declare. `CancelacionesItem` no lleva `deporte` justamente por eso.

## 5. Endpoints

### 5.1 Tabla de endpoints

| Verbo | Ruta | Rol requerido | Parámetros | Respuesta `200` | Otros códigos |
|---|---|---|---|---|---|
| GET | `/api/reportes/ocupacion` | `ADMIN` | `desde`, `hasta` (obligatorios) | `ReporteOcupacionResponse` | 400, 401, 403, 500 |
| GET | `/api/reportes/reservas` | `ADMIN` | `desde`, `hasta` (obligatorios) | `ReporteReservasResponse` | 400, 401, 403, 500 |
| GET | `/api/reportes/cancelaciones` | `ADMIN` | `desde`, `hasta` (obligatorios) | `ReporteCancelacionesResponse` | 400, 401, 403, 500 |

Los tres los sirve un único `ReporteController` con tres métodos. Ninguna otra ruta se
publica. Las rutas de `springdoc` (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`)
quedan abiertas sin token, como en los otros tres microservicios.

Cada método declara sus códigos de error con `@ApiResponse` de `springdoc-openapi`,
incluido el `500`.

### 5.2 Autorización

| Situación del token entrante | Resultado |
|---|---|
| Ausente, vencido, firma inválida o mal formado | `401 NO_AUTENTICADO` |
| `rol = ADMIN` | `200` |
| `rol = USUARIO` | `403 SIN_PERMISO` |
| `rol = SERVICIO` | `401 NO_AUTENTICADO` (P-11) |

`FiltroToken` es copia del de `ms-reservas`, incluido el rechazo del rol `SERVICIO`: ese
filtro descarta el token antes de autenticar a nadie, y la cadena responde `401` por su
punto de entrada. `SeguridadConfig` exige `hasRole("ADMIN")` en `/api/reportes/**` — es la
única regla de rol del servicio, porque los tres endpoints son de ADMIN.

`ms-reportes` **emite** tokens `SERVICIO` hacia fuera y **no los acepta** hacia dentro. Esa
asimetría, que en la spec 04 estaba implícita y causó el problema de P-01, ahora está escrita
en el contrato.

### 5.3 Llamadas salientes

| Cliente | Llamada | Credencial | Timeouts |
|---|---|---|---|
| `CanchasClient` | `GET ${MS_CANCHAS_URL}/api/canchas` | `Bearer` token de servicio, recién emitido | 2 s / 5 s, sin reintentos |
| `ReservasClient` | `GET ${MS_RESERVAS_URL}/api/reservas` | `Bearer` token de servicio, recién emitido | 2 s / 5 s, sin reintentos |

`EmisorTokenServicio` es copia del de `ms-reservas`: JWT HS256 con `JWT_SECRET`,
`rol = SERVICIO`, **sin** claim `sub`, `exp` de 5 minutos, uno nuevo por llamada, sin caché.

`ClienteHttpConfig` declara **dos** beans `RestClient` distintos, `clienteCanchas` y
`clienteReservas`, inyectados por nombre de parámetro. Cada uno con su `baseUrl`; los
timeouts son los mismos.

## 6. Excepciones y códigos HTTP

`ManejadorExcepciones` es un `@RestControllerAdvice`, copia recortada del de `ms-reservas`:
se quedan solo los manejadores que este servicio puede disparar, y se agregan los dos de
dependencia caída.

| Excepción | HTTP | `codigo` | Cuándo |
|---|---|---|---|
| `MissingServletRequestParameterException` | 400 | `DATOS_INVALIDOS` | Falta `desde` o `hasta` |
| `RangoInvalidoException` | 400 | `DATOS_INVALIDOS` | Formato distinto de `AAAA-MM-DD`, fecha inexistente, o `desde` posterior a `hasta` |
| `MethodArgumentTypeMismatchException` | 400 | `DATOS_INVALIDOS` | Parámetro con tipo inesperado |
| `HttpRequestMethodNotSupportedException` | 400 | `DATOS_INVALIDOS` | `POST` sobre una ruta de reportes |
| `CatalogoNoDisponibleException` | 500 | `ERROR_INTERNO` | `ms-canchas` caído, `5xx`, `401`, `403` o fuera de plazo |
| `ReservasNoDisponiblesException` | 500 | `ERROR_INTERNO` | `ms-reservas` caído, `5xx`, `401`, `403` o fuera de plazo |
| `NoResourceFoundException` | 404 | `NO_ENCONTRADO` | Ruta inexistente (asunto A-02, ya cerrado en los otros tres) |
| `Exception` | 500 | `ERROR_INTERNO` | Red de seguridad; el detalle solo va al log |

El `401` y el `403` del cliente los produce la cadena de Spring Security, no este manejador:
`SeguridadConfig` escribe el `ErrorResponse` desde su `AuthenticationEntryPoint` y su
`AccessDeniedHandler`, igual que en `ms-reservas`.

`RangoInvalidoException` es una sola excepción para los tres fallos de rango; el mensaje
distingue el caso. No se crean tres clases para tres mensajes.

Mensajes fijos de los dos fallos de dependencia, con la causa real solo en el log:

| Excepción | `mensaje` al cliente |
|---|---|
| `CatalogoNoDisponibleException` | `No se pudo consultar el catalogo de canchas` |
| `ReservasNoDisponiblesException` | `No se pudo consultar las reservas` |

Nunca se devuelve un reporte parcial. Si falla cualquiera de las dos llamadas, la petición
entera responde `500`: un reporte con la mitad de los datos y aspecto de completo es peor que
un error visible (decisión D-08).

Ningún `404` de negocio existe en este servicio: un rango sin datos es `200` con `items`
lleno de ceros, y no hay recurso identificable que pueda no existir.

## 7. Decisiones de diseño

| ID | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D-01 | `pom.xml` sin `spring-boot-starter-data-jpa` ni driver de PostgreSQL | Incluir JPA y excluir `DataSourceAutoConfiguration` con `@SpringBootApplication(exclude = ...)`, o apuntar a una base vacía | Sin la dependencia, la prohibición de leer bases ajenas está garantizada por construcción, no por disciplina. Excluir la autoconfiguración deja la puerta abierta a que alguien agregue un `@Repository` después |
| D-02 | Un solo `ReporteController` con tres métodos | Tres controladores, uno por reporte | Comparten prefijo de ruta, rol, validación de rango y las mismas dos llamadas salientes. Tres clases de un método cada una repartirían lo mismo en más archivos |
| D-03 | Un solo `ReporteService` con tres métodos públicos, más `CalculadoraOcupacion` para la aritmética de ocupación | Un servicio por reporte | Los tres reportes son el mismo pipeline con distinto predicado de estado y distinto DTO de salida. Se separa solo el cálculo de ocupación, que es el único con lógica propia (horario por cancha, división, redondeo) |
| D-04 | `desde` y `hasta` se reciben como `String` y se parsean a mano con `ISO_LOCAL_DATE` estricto | `@RequestParam @DateTimeFormat(iso = DATE) LocalDate` | Con la conversión automática, una fecha mal formada produce un `MethodArgumentTypeMismatchException` cuyo mensaje depende de Spring. Parseando a mano, el `400 DATOS_INVALIDOS` dice exactamente qué parámetro falló y por qué. Mismo criterio que D-11 de la spec 04 |
| D-05 | `porcentajeOcupacion` como `BigDecimal` con escala 1 y `HALF_UP` | `double` redondeado con `Math.round(x * 10) / 10.0` | `double` arrastra error binario y Jackson puede serializar `26.699999999999999`. `BigDecimal` con escala fija garantiza el `26.7` del contrato y hace explícito el modo de redondeo que pide P-06 |
| D-06 | `horasReservadas`, `horasDisponibles`, `totalReservas` y `totalCancelaciones` como `long` | `int` | Un rango amplio multiplicado por las horas diarias de la cancha crece rápido; `long` cuesta lo mismo y quita el techo. Jackson los serializa como número JSON en ambos casos |
| D-07 | Las reservas se piden **una vez por petición** y se filtran en memoria por rango y por estado | Pedirlas una vez por cancha, o filtrar en el origen | `GET /api/reservas` no acepta parámetros (P-02) y una llamada por cancha multiplicaría el tráfico sin traer nada nuevo. La limitación de escala ya quedó asumida en §7.1 del `requirements.md` |
| D-08 | Si falla cualquiera de las dos llamadas, la petición entera responde `500` | Devolver un reporte parcial: solo el catálogo con ceros si `ms-reservas` no responde | Un reporte de ocupación con `0 %` en todas las canchas es indistinguible de un mes sin reservas. Un dato falso que parece verdadero es peor que un error |
| D-09 | Las dos llamadas salientes son secuenciales: primero canchas, luego reservas | Lanzarlas en paralelo con `CompletableFuture` o `RestClient` reactivo | Son dos llamadas dentro de la misma red de Docker. El paralelismo ahorraría milisegundos a cambio de un pool de hilos, manejo de excepciones envueltas y un modo de fallo más difícil de explicar en la defensa |
| D-10 | `items` conserva el orden en que `ms-canchas` devuelve el catálogo | Ordenar por `porcentajeOcupacion` o `totalReservas` descendente | El contrato no declara ningún orden, y ordenar por la métrica sería adelantar el "mayor y menor demanda" que P-08 dejó fuera. El orden del catálogo es estable y el frontend reordena si quiere |
| D-11 | Tres pares de DTOs concretos, sin genéricos | Una envoltura `ReporteResponse<T>` con `desde`, `hasta` e `items` | Ahorraría dos clases, pero `springdoc` documenta peor los tipos genéricos y el esquema de OpenAPI dejaría de mostrar los campos de cada fila. La documentación es un entregable evaluado (E3 del PDF) |
| D-12 | `CanchaExterna` y `ReservaExterna` declaran solo los campos que se usan | Espejar el payload completo de cada servicio | El DTO recortado deja por escrito de qué depende realmente `ms-reportes`. Campos que sobran sugieren acoplamientos que no existen |
| D-13 | Dos beans `RestClient` distintos, uno por servicio destino | Un `RestClient` sin `baseUrl` con la URL completa en cada llamada | Con `baseUrl` por bean, la URL de cada dependencia se configura en un solo lugar y los timeouts quedan atados al cliente, no repartidos por el código |
| D-14 | El token de servicio se emite en cada llamada, sin caché | Cachear el token durante sus 5 minutos de vigencia | Firmar un JWT corto es despreciable frente a la llamada HTTP que lo acompaña. Cachearlo obligaría a manejar expiración y concurrencia sin ahorro medible. Mismo criterio que D-13 de la spec 04 |
| D-15 | Una reserva con `canchaId` fuera del catálogo se descarta y se registra `WARN` | Crear una fila con `nombre` y `deporte` nulos | El contrato declara `nombre` y `deporte` como campos presentes. Devolver nulos rompería el payload, y hoy el caso no puede ocurrir porque las canchas se inactivan, nunca se borran |
| D-16 | El cambio en `ms-reservas` se limita a `FiltroToken` y `SeguridadConfig` | Crear un endpoint interno nuevo en `ms-reservas`, del tipo `/api/reservas/internas` | Un endpoint nuevo no está en el contrato congelado y duplicaría una ruta que ya devuelve exactamente lo necesario. Abrir la existente al rol `SERVICIO` es el cambio más pequeño que resuelve P-01 |

### 7.1 Consecuencia conocida de D-06: conexiones cacheadas tras reiniciar una dependencia

Detectado al ejecutar T8 el 23/08/2026, con salida real. No es un defecto del código: es el
precio de la política *sin reintentos* que D-06 heredó de la spec 04, y queda escrito aquí
para que nadie lo diagnostique dos veces.

Tras `docker compose stop ms-canchas` y su posterior `start`, las **primeras** peticiones a
`ms-reportes` siguen devolviendo `500 ERROR_INTERNO` aunque `ms-canchas` ya haya escrito
`Started MsCanchasApplication` en su log. La causa es que `SimpleClientHttpRequestFactory`
—que por debajo es `HttpURLConnection`— mantiene un pool de conexiones **cacheadas contra el
contenedor anterior**: esas conexiones están muertas y fallan una vez antes de purgarse. En
T8 fallaron las dos primeras peticiones y la tercera respondió `200`; al repetir las dos
primeras, respondieron `200` sin ningún cambio.

**Se acepta el comportamiento y no se agrega ningún reintento.** Reintentar
automáticamente ocultaría fallos reales de la dependencia, que es justo lo que D-08 quiere
que se vea, y en operación normal los servicios no se reinician. La alternativa —una política
de reintento o un pool con validación de conexión— quedó fuera de alcance en el
`requirements.md` §10 y no se reabre.

**Consecuencia práctica para la demo en vivo:** al ejecutar el escenario de dependencia
caída, después de `docker compose start ms-canchas` hay que **repetir la petición una o dos
veces** antes de mostrar el `200`. El primer `500` de esa secuencia es la conexión muerta, no
un servicio caído.

## 8. Cambio en `ms-reservas` (decisión P-01)

Único archivo de código fuera de `backend/ms-reportes` que esta spec toca. Alcance exacto,
prohibido ampliarlo:

| Archivo | Cambio |
|---|---|
| `config/FiltroToken.java` | Deja de descartar el token con `rol = SERVICIO`. Lo autentica con la authority `ROLE_SERVICIO` y **principal nulo**, porque no hay claim `sub` |
| `config/SeguridadConfig.java` | La regla de `GET /api/reservas` pasa de `hasRole("ADMIN")` a `hasAnyRole("ADMIN", "SERVICIO")` |

Las otras cuatro reglas de `SeguridadConfig` **no se tocan** y siguen siendo
`.authenticated()`. Como un token `SERVICIO` ahora sí autentica, hay que ser explícito sobre
por qué eso no le abre esas rutas:

| Ruta | Regla actual | Efecto para un token `SERVICIO` |
|---|---|---|
| `GET /api/reservas/disponibilidad` | `.authenticated()` | **Pasaría la cadena.** Se agrega la exclusión explícita para que responda `403 SIN_PERMISO` |
| `GET /api/reservas/mias` | `.authenticated()` | **Pasaría la cadena.** Se agrega la exclusión explícita |
| `POST /api/reservas` | `.authenticated()` | **Pasaría la cadena.** Se agrega la exclusión explícita |
| `PATCH /api/reservas/*/cancelacion` | `.authenticated()` | **Pasaría la cadena.** Se agrega la exclusión explícita |

Esta es la consecuencia no evidente del cambio y por eso queda escrita: hoy
`FiltroToken` descarta el token `SERVICIO` y por eso las cuatro rutas lo rechazan "gratis".
En cuanto el filtro lo autentique, `.authenticated()` deja de ser suficiente. La forma
concreta —`hasAnyRole("ADMIN", "USUARIO")` en esas cuatro reglas— es la mínima que conserva
el comportamiento actual: `ADMIN` y `USUARIO` siguen entrando exactamente donde entraban.

Sin esa exclusión, `POST /api/reservas` con token de servicio llegaría al controlador y
crearía una reserva con `usuarioId` nulo. Es el riesgo real del cambio y se cierra en el
mismo paso.

Ninguna entidad, DTO, mapper, consulta, regla de negocio ni endpoint de `ms-reservas` se
toca. `ms-canchas` no se toca en absoluto: ya acepta el rol `SERVICIO` desde la spec 04.

## 9. Configuración

`application.properties` de `ms-reportes`:

| Propiedad | Valor | Origen |
|---|---|---|
| `spring.application.name` | `ms-reportes` | — |
| `server.port` | `8080` | — |
| `jwt.secret` | `${JWT_SECRET}` | `.env`, la misma de los otros tres |
| `reportes.token-servicio.duracion` | `5m` | Contrato |
| `mscanchas.url` | `${MS_CANCHAS_URL:http://ms-canchas:8080}` | `docker-compose.yml` |
| `msreservas.url` | `${MS_RESERVAS_URL:http://ms-reservas:8080}` | `docker-compose.yml` |
| `mscanchas.timeout.conexion` / `msreservas.timeout.conexion` | `2s` | D-06 de la spec 04 |
| `mscanchas.timeout.lectura` / `msreservas.timeout.lectura` | `5s` | D-06 de la spec 04 |

No hay `spring.datasource.*` ni `spring.jpa.*`: no hay base.

`docker-compose.yml` suma el servicio `ms-reportes`, con `build: ./backend/ms-reportes`,
`container_name: canchas-ms-reportes`, puerto `8085:8080` (P-10), las variables `JWT_SECRET`,
`MS_CANCHAS_URL` y `MS_RESERVAS_URL`, y `depends_on` de `ms-canchas` y `ms-reservas` con
`condition: service_started`. **No** depende de `postgres`.

`Dockerfile` según el patrón oficial de `CLAUDE.md` §1, cambiando solo el nombre del `.jar` a
`ms-reportes-0.0.1-SNAPSHOT.jar`.

`pom.xml`: `spring-boot-starter-parent` 3.5.3 corregido a mano, `spring-boot-starter-web`,
`spring-boot-starter-security`, `springdoc-openapi-starter-webmvc-ui` 2.8.6 y los tres
artefactos de `io.jsonwebtoken` 0.12.6. **Sin** `spring-boot-starter-data-jpa`, **sin**
`postgresql` y **sin** `spring-boot-starter-validation`: no hay cuerpo de petición que
validar (§4.3).

## 10. Lo que este diseño no incluye

Todo lo que `requirements.md` §10 dejó fuera sigue fuera. En particular, este diseño no
introduce: caché de respuestas ni de token, reintentos, circuit breaker, paralelismo en las
llamadas salientes, base de datos ni tabla de agregados, un cuarto reporte, orden o
paginación configurables de `items`, un bloque de totales por deporte, ni consumo de
`GET /api/canchas/{canchaId}/bloqueos`.

Tampoco introduce ninguna clase `Util` genérica, ni Lombok, ni MapStruct, ni `@Autowired` en
campos (`CLAUDE.md` §3).
