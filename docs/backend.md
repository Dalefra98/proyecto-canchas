# Cómo funciona el backend

Qué hace cada microservicio, cómo se comunican entre ellos, qué validan y qué ocurre cuando
uno no responde. Todo lo que se afirma aquí está en el código; las rutas son verificables.

**Por dónde empezar.** La sección 0 explica qué es cada capa (DTO, entidad, mapper, servicio,
repositorio) sin dar nada por sabido: si es su primer acercamiento al proyecto, léala primero.
Las secciones 1 y 2 ubican las piezas y dicen dónde está cada endpoint. La sección 3 sigue una
petición real de principio a fin y es la mejor entrada a lo técnico. Las secciones 4 a 8 son de
consulta: comunicación entre servicios, validaciones, acceso a la base, fallos y reglas fijas.

Documentos que este **no** repite:

- Estilos, patrones y el porqué de cada decisión: [`arquitectura.md`](arquitectura.md).
- Nombres de campo, rutas y códigos de error congelados: [`contratos/README.md`](contratos/README.md).
- Cómo levantar y verificar el sistema: [`../README.md`](../README.md).
- Diagramas C4: [`diagramas-c4.md`](diagramas-c4.md).

---

## 0. Las capas: DTO, entidad, mapper, servicio y repositorio

Antes de seguir un recorrido completo hace falta saber qué es cada pieza. Todas se explican
con el mismo caso: un usuario reserva la cancha 3 el 2026-09-12 a las 10:00.

### DTO — el dato tal como viaja por la red

**D**ata **T**ransfer **O**bject: una clase que solo existe para representar el JSON que entra
o que sale. No tiene lógica y no sabe nada de la base de datos. Viven en `dto/`.

`ms-reservas · dto/ReservaRequest.java` es exactamente el cuerpo que manda el navegador:

```java
public record ReservaRequest(
        @NotNull @Positive Long canchaId,
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$") String fecha,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String horaInicio) { }
```

Dos cosas que conviene notar: `fecha` es un `String`, porque en JSON eso es lo que llega; y no
declara `usuarioId` ni `estado`, porque el cliente no tiene permitido decidirlos.

Hay dos tipos: los `...Request`, que son lo que entra, y los `...Response`, que son lo que
sale (`ReservaResponse`).

**Por qué existe, en vez de recibir la entidad directamente:** si el controlador aceptara un
objeto `Reserva`, cualquiera podría mandar `"usuarioId": 99` y reservar a nombre de otro. El
DTO es la lista cerrada de lo que el cliente puede decir.

### Entidad — el dato tal como vive en la base

`entity/Reserva.java` es el espejo de la tabla `reserva`: cada campo es una columna, con el
tipo real. Es la misma reserva que el DTO, en otra forma:

| Campo | DTO `ReservaRequest` | Entidad `Reserva` |
|---|---|---|
| `fecha` | `"2026-09-12"`, texto | `LocalDate`, fecha real |
| `usuarioId` | no existe | `7`, sacado del token |
| `horaFin` | no existe | `11:00`, calculado por el servicio |
| `estado` | no existe | `CONFIRMADA`, puesto por el servicio |

### Mapper — el traductor entre esas dos formas

`mapper/ReservaMapper.java` convierte una cosa en la otra, y nada más. Está escrito a mano,
campo por campo: `CLAUDE.md` prohíbe MapStruct y Lombok.

```java
public ReservaResponse aRespuesta(Reserva reserva) {
    return new ReservaResponse(
            reserva.getId(),
            reserva.getUsuarioId(),
            reserva.getCanchaId(),
            reserva.getFecha().format(FECHA),        // LocalDate → "2026-09-12"
            reserva.getHoraInicio().format(HORA),    // LocalTime → "10:00"
            reserva.getHoraFin().format(HORA),
            estadoVisible(reserva).name());
}
```

También hace el camino inverso —`aFecha("2026-09-12")` devuelve un `LocalDate`— y ahí es donde
se rechaza `"2026-02-31"`.

**Por qué es una clase aparte:** el formato `HH:mm` del contrato se decide en un solo lugar. Si
cambiara, se toca un archivo y no veinte.

### Servicio — el que decide

`service/ReservaService.java` es donde viven las reglas de negocio RN-01 a RN-08. Es la única
capa que puede decir que no: el controlador no decide nada, el mapper tampoco, el repositorio
tampoco.

En el alta son nueve comprobaciones: ¿la hora es en punto?, ¿el bloque ya ocurrió?, ¿la cancha
existe y está activa?, ¿el bloque está libre?, ¿el usuario llegó a su límite? Si algo falla,
lanza una excepción; si todo pasa, manda guardar.

**Por qué separado del controlador:** las reglas quedan en un sitio que no depende de HTTP. El
controlador solo traduce entre una petición HTTP y una llamada Java.

### Repositorio — el que habla con la base

`repository/ReservaRepository.java` es la única puerta a `reservas_db`. Es una interfaz: se
declara el nombre del método y Spring Data escribe la consulta.

```java
boolean existsByCanchaIdAndFechaAndHoraInicioAndEstado(...);
```

**Por qué separado del servicio:** el servicio dice *qué* necesita saber ("¿está ocupado este
bloque?") y el repositorio sabe *cómo* preguntárselo a PostgreSQL.

### Cómo encajan

```
JSON entra
  → Controller       recibe, valida la forma con @Valid
  → DTO Request      { canchaId: 3, fecha: "2026-09-12", horaInicio: "10:00" }
  → Service          decide: RN-01…RN-06. Pide traducir y pide guardar
      → Mapper       "2026-09-12" → LocalDate
      → Repository   → PostgreSQL → fila guardada
  → Entidad          Reserva{ id: 41, usuarioId: 7, ..., CONFIRMADA }
  → Mapper           entidad → DTO Response
  → JSON sale
```

La regla que sostiene todo: **cada capa habla solo con la siguiente**. Un controlador nunca
toca el repositorio, un DTO nunca llega a la base y una entidad nunca sale por HTTP. Por eso
`CLAUDE.md` la escribe como `controller → service → repository → entity`.

`ms-reportes` es la única excepción: no tiene entidad ni repositorio porque no tiene base. En
su lugar tiene `client/`, que cumple el mismo papel —traer los datos— pero preguntando por
HTTP a otro microservicio en vez de a PostgreSQL.

El recorrido completo de una petición real, capa por capa y con archivos y líneas, está en la
sección 3.

---

## 1. Mapa general

```
NAVEGADOR
    │  llamadas relativas a /api
    ▼
devServer de cada microfrontend  shell 3000 · mf-reservas 3001
    │                    mf-administracion 3002 · mf-reportes 3003
    │  devServer.proxy → http://gateway:80   (red interna de Docker)
    ▼
┌─────────────────── GATEWAY Nginx ───────────────────┐
│  infra/nginx/gateway.conf   ·   8090:80             │
│  /api/usuarios → ms-usuarios:8080                   │
│  /api/canchas  → ms-canchas:8080                    │
│  /api/reservas → ms-reservas:8080                   │
│  /api/reportes → ms-reportes:8080                   │
│  cualquier otra ruta → 404                          │
└──────┬──────────┬───────────┬───────────┬───────────┘
       ▼          ▼           ▼           ▼
  ms-usuarios ms-canchas  ms-reservas  ms-reportes
   (8082)      (8083)       (8084)       (8085)
       │          │           │           │      │
       ▼          ▼           ▼           │ HTTP │ HTTP
  usuarios_db  canchas_db  reservas_db ◄──┘      │
                    ▲───────────────────────────-┘
                    │
              ms-reservas ──HTTP──► ms-canchas
```

Los puertos `8082`–`8085` existen solo para probar con `curl.exe` y abrir Swagger UI, que vive
fuera de `/api` y el gateway no enruta. **La aplicación no los usa**: los cuatro
microfrontends llaman a la API por `gateway:80`.

---

## 2. Qué hace cada microservicio

| Servicio | Responsabilidad | Base | Puerto directo |
|---|---|---|---|
| `ms-usuarios` | Registro, inicio de sesión, listado e inactivación de usuarios. **Es el único que emite el token de persona** | `usuarios_db` — tabla `usuario` | 8082 |
| `ms-canchas` | Catálogo de canchas y bloqueos de mantenimiento (RN-07) | `canchas_db` — tablas `cancha` y `bloqueo_mantenimiento` | 8083 |
| `ms-reservas` | Disponibilidad y ciclo de vida de la reserva: RN-01 a RN-06 y RN-08 | `reservas_db` — tabla `reserva` | 8084 |
| `ms-reportes` | Agrega ocupación, reservas y cancelaciones. **No tiene base de datos ni siquiera JPA en su `pom.xml`** | — | 8085 |

La columna "Puerto directo" es la de la sección 1: sirve para probar con `curl.exe` o abrir
Swagger UI, y la aplicación no la usa. En uso normal todo entra por el gateway.

### 2.1 Dónde está cada endpoint

**Cómo leer las rutas de archivo.** Todas las clases de un microservicio cuelgan del mismo
prefijo:

```
backend/<microservicio>/src/main/java/ec/ups/dae/<dominio>/<capa>/<Clase>.java
```

donde `<dominio>` es `usuarios`, `canchas`, `reservas` o `reportes`. Por ejemplo,
`ms-reservas · controller/ReservaController.java:105` es el archivo

```
backend/ms-reservas/src/main/java/ec/ups/dae/reservas/controller/ReservaController.java
```

en su línea 105. En las tablas se usa siempre esa forma corta. Los números de línea son del
código tal como está hoy y pueden correrse si alguien edita el archivo; el nombre del método
no cambia.

Los 20 endpoints del sistema, cada uno con el archivo y el método que lo atiende:

| Método y ruta | Rol que la abre | Controlador (archivo:línea) | Método Java | Servicio que decide |
|---|---|---|---|---|
| `POST /api/usuarios/sesiones` | público | `ms-usuarios · controller/UsuarioController.java:52` | `iniciarSesion` | `service/AutenticacionService.iniciarSesion` |
| `POST /api/usuarios` | público | `ms-usuarios · controller/UsuarioController.java:68` | `registrar` | `service/UsuarioService.registrar` |
| `GET /api/usuarios` | `ADMIN` | `ms-usuarios · controller/UsuarioController.java:84` | `listar` | `service/UsuarioService.listar` |
| `PATCH /api/usuarios/{usuarioId}/estado` | `ADMIN` | `ms-usuarios · controller/UsuarioController.java:105` | `cambiarEstado` | `service/UsuarioService.cambiarEstado` |
| `GET /api/canchas` | cualquier token válido | `ms-canchas · controller/CanchaController.java:46` | `listar` | `service/CanchaService.listar` |
| `GET /api/canchas/{canchaId}` | cualquier token válido | `ms-canchas · controller/CanchaController.java:64` | `obtener` | `service/CanchaService.obtener` |
| `POST /api/canchas` | `ADMIN` | `ms-canchas · controller/CanchaController.java:85` | `crear` | `service/CanchaService.crear` |
| `PUT /api/canchas/{canchaId}` | `ADMIN` | `ms-canchas · controller/CanchaController.java:109` | `editar` | `service/CanchaService.editar` |
| `PATCH /api/canchas/{canchaId}/estado` | `ADMIN` | `ms-canchas · controller/CanchaController.java:131` | `cambiarEstado` | `service/CanchaService.cambiarEstado` |
| `GET /api/canchas/{canchaId}/bloqueos` | cualquier token válido | `ms-canchas · controller/BloqueoController.java:50` | `listar` | `service/BloqueoService.listar` |
| `POST /api/canchas/{canchaId}/bloqueos` | `ADMIN` | `ms-canchas · controller/BloqueoController.java:76` | `crear` | `service/BloqueoService.crear` |
| `DELETE /api/canchas/{canchaId}/bloqueos/{id}` | `ADMIN` | `ms-canchas · controller/BloqueoController.java:97` | `eliminar` | `service/BloqueoService.eliminar` |
| `GET /api/reservas/disponibilidad` | `ADMIN`, `USUARIO` | `ms-reservas · controller/ReservaController.java:73` | `disponibilidad` | `service/DisponibilidadService` |
| `POST /api/reservas` | `ADMIN`, `USUARIO` | `ms-reservas · controller/ReservaController.java:105` | `crear` | `service/ReservaService.crear` |
| `GET /api/reservas` | `ADMIN`, `SERVICIO` | `ms-reservas · controller/ReservaController.java:124` | `listar` | `service/ReservaService.listarTodas` |
| `GET /api/reservas/mias` | `ADMIN`, `USUARIO` | `ms-reservas · controller/ReservaController.java:141` | `listarMias` | `service/ReservaService.listarMias` |
| `PATCH /api/reservas/{id}/cancelacion` | `ADMIN`, `USUARIO` | `ms-reservas · controller/ReservaController.java:168` | `cancelar` | `service/ReservaService.cancelar` |
| `GET /api/reportes/ocupacion` · `/reservas` · `/cancelaciones` | `ADMIN` | `ms-reportes · controller/ReporteController.java:62, 86, 112` | `ocupacion`, `reservas`, `cancelaciones` | `service/ReporteService` |

El prefijo de cada controlador está en su `@RequestMapping`: `/api/usuarios`, `/api/canchas`,
`/api/canchas/{canchaId}/bloqueos`, `/api/reservas` y `/api/reportes`. La ruta completa de un
endpoint es ese prefijo más lo que diga su `@PostMapping`, `@GetMapping`, `@PutMapping`,
`@PatchMapping` o `@DeleteMapping`. Si busca un endpoint y no sabe dónde está, ese
`@RequestMapping` es lo único que hay que mirar: no hay rutas declaradas en ningún otro lugar
del backend.

La columna "Rol que la abre" no está escrita en el controlador: sale de los `requestMatchers`
de `config/SeguridadConfig.java` de cada microservicio. Tres lecturas de ella:

- **"público"** son los dos únicos endpoints sin token, `.permitAll()`: el registro y el inicio
  de sesión de `ms-usuarios`. Todo lo demás exige token.
- **"cualquier token válido"** es `.authenticated()`: sirve el token de una persona (`ADMIN` o
  `USUARIO`) y también el token interno de rol `SERVICIO` que usan `ms-reservas` y
  `ms-reportes` para leer el catálogo (sección 4).
- Un rol nombrado, como `ADMIN`, es `hasRole` o `hasAnyRole`: cualquier otro rol recibe
  `403 SIN_PERMISO` sin llegar al controlador.

Ojo con una distinción que la tabla no puede mostrar: que un endpoint esté abierto no significa
que devuelva lo mismo a todos. `GET /api/canchas` lo abre cualquier token, pero el `USUARIO`
solo ve las canchas activas y el `ADMIN` las ve todas; ese filtro es negocio y vive en
`CanchaService`, no en la cadena de seguridad.

---

## 3. Anatomía de una petición: un ejemplo completo

En vez de describir las capas en abstracto, seguimos **una sola petición real** de principio a
fin: un usuario con `usuarioId = 7` y rol `USUARIO` reserva la cancha 3 el 12 de septiembre de
2026 a las 10:00.

### 3.1 Lo que sale del navegador

```http
POST /api/reservas HTTP/1.1
Host: localhost:3000
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Iiwicm9sIjoiVVNVQVJJTyJ9...

{ "canchaId": 3, "fecha": "2026-09-12", "horaInicio": "10:00" }
```

El cuerpo tiene exactamente tres campos. No lleva `usuarioId`: ese dato sale del token, no del
cliente. Tampoco lleva `horaFin` ni `estado`: los pone el servidor.

### 3.2 Las ocho paradas del camino

| # | Dónde | Archivo | Qué hace exactamente |
|---|---|---|---|
| 1 | devServer del shell, que es la página abierta en el navegador | `frontend/shell/webpack.config.js:91` | El navegador nunca nombra un microservicio: pide `/api/...` al mismo sitio del que descargó la página, `localhost:3000`. Ese `proxy` reenvía la llamada al gateway por la red interna de Docker. `frontend/mf-reservas/webpack.config.js` tiene su propio proxy idéntico, que solo actúa si el remote se abre suelto en `localhost:3001` |
| 2 | Gateway Nginx | `infra/nginx/gateway.conf` | El prefijo `/api/reservas` decide el destino: `proxy_pass` hacia `ms-reservas:8080` |
| 3 | Filtro de token | `ms-reservas · config/FiltroToken.java:46` | Lee el encabezado `Authorization`, quita el prefijo `Bearer ` y valida la firma con `JWT_SECRET` usando `service/TokenService.java`. Si el token vale, deja `usuarioId = 7` como *principal* y `ROLE_USUARIO` como *authority* dentro del `SecurityContextHolder`. Si no vale, no autentica a nadie y deja seguir la cadena |
| 4 | Cadena de seguridad | `ms-reservas · config/SeguridadConfig.java:56` | La línea `requestMatchers(HttpMethod.POST, "/api/reservas").hasAnyRole("ADMIN", "USUARIO")` decide si pasa. Sin token o con token inválido → `401 NO_AUTENTICADO` (línea 69). Con rol que no está en la lista → `403 SIN_PERMISO` (línea 74). Si falla, la petición termina aquí y el controlador ni se entera |
| 5 | Controlador | `ms-reservas · controller/ReservaController.java:105` | `@Valid @RequestBody ReservaRequest` dispara las anotaciones del DTO. Si `fecha` viniera como `"12/09/2026"`, Spring lanza `MethodArgumentNotValidException` y el manejador responde `400`. El método tiene **dos líneas**: llama al servicio y envuelve el resultado en `201 CREATED`. No decide nada de negocio |
| 6 | Servicio | `ms-reservas · service/ReservaService.java:65` | Las nueve comprobaciones de negocio, en el orden de la sección 5.3. `usuarioAutenticado()` (línea 200) lee el `7` que el paso 3 dejó en el contexto |
| 7 | Repositorio y cliente HTTP | `repository/ReservaRepository.java` y `service/CanchasClient.java:44` | El repositorio es lo único que toca `reservas_db`; el cliente, lo único que sale por HTTP hacia `ms-canchas`. Ninguna otra clase hace una cosa ni la otra. Cómo está escrita esa consulta: sección 6 |
| 8 | Mapper | `ms-reservas · mapper/ReservaMapper.java:28` | Convierte la entidad `Reserva` ya guardada en un `ReservaResponse`: formatea `fecha` como `AAAA-MM-DD`, `horaInicio` y `horaFin` como `HH:mm`, y calcula el estado visible (línea 50) |

Respuesta:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{ "id": 41, "usuarioId": 7, "canchaId": 3, "fecha": "2026-09-12",
  "horaInicio": "10:00", "horaFin": "11:00", "estado": "CONFIRMADA" }
```

`horaFin` es `11:00` porque el bloque dura una hora fija (RN-01, `ReservaService.java:38` y
`:68`), y `usuarioId` es `7` porque salió del token, no del cuerpo.

### 3.3 El mismo ejemplo cuando falla

Si ese bloque de las 10:00 ya estaba reservado, el paso 6 se detiene en
`ms-reservas · service/ReservaService.java:101`:

```java
throw new BloqueOcupadoException("El bloque horario ya esta reservado");
```

La excepción sube hasta `ms-reservas · exception/ManejadorExcepciones.java:124`, la única
clase que traduce excepciones a respuestas HTTP:

```java
@ExceptionHandler(BloqueOcupadoException.class)
... respuesta(HttpStatus.CONFLICT, BLOQUE_OCUPADO, excepcion.getMessage());
```

y el cliente recibe:

```http
HTTP/1.1 409 Conflict

{ "codigo": "BLOQUE_OCUPADO", "mensaje": "El bloque horario ya esta reservado" }
```

Ese es el patrón de **todos** los errores del sistema: el servicio lanza una excepción propia
con nombre de negocio (las once de `ms-reservas` están en su carpeta `exception/`), y una única
clase anotada con `@RestControllerAdvice` decide el código HTTP y el `codigo` del contrato.
Nunca sale un stacktrace ni un mensaje crudo de Spring. Cada microservicio tiene su manejador
en `exception/ManejadorExcepciones.java`, y ahí está la lista completa de traducciones, un
`@ExceptionHandler` por caso.

### 3.4 Resumen del camino

```
navegador → devServer (proxy) → gateway Nginx → FiltroToken → SeguridadConfig
          → Controller (@Valid) → Service (reglas RN-xx) → Repository / Client HTTP
          → Mapper → JSON

cualquier excepción del trayecto → ManejadorExcepciones → { codigo, mensaje }
```

Las capas 3 a 8 son idénticas en los cuatro microservicios: cambian los nombres de las clases,
no el recorrido. `ms-reportes` es la única excepción parcial: no tiene `repository/` ni
`entity/` porque no tiene base, y en su lugar el paso 7 lo hace `client/`.

Ningún servicio guarda sesión: el token se valida en cada petición, con la firma, sin
consultar a `ms-usuarios` y sin estado compartido. El token de persona lo emite
`ms-usuarios · service/TokenService.java` con `sub = usuarioId`, claim `rol` y vigencia de
**8 horas** (`jwt.vigencia-horas=8`).

---

## 4. Cómo se comunican entre ellos

Solo hay tres flechas, todas de **lectura** y todas por REST:

```
ms-reservas ──► ms-canchas    GET /api/canchas/{canchaId}           horario, existencia, estado
                              GET /api/canchas/{canchaId}/bloqueos  mantenimiento del día

ms-reportes ──► ms-canchas    GET /api/canchas                      catálogo completo
            ──► ms-reservas   GET /api/reservas                     todas las reservas
```

La clase que hace la llamada es la única puerta de salida HTTP del servicio:
`ms-reportes · client/CanchasClient.java`, `ms-reportes · client/ReservasClient.java` y
`ms-reservas · service/CanchasClient.java`. Ninguna otra clase del backend hace una llamada
HTTP saliente.

**Cómo se autentica una llamada interna:**

1. El servicio llamante emite un **token nuevo en cada llamada** con `EmisorTokenServicio`:
   JWT HS256 firmado con el mismo `JWT_SECRET`, `rol = SERVICIO`, **sin** claim `sub` y con
   `exp` de 5 minutos.
2. Lo envía en `Authorization: Bearer` desde su cliente HTTP.
3. El servicio destino lo valida con su propio `FiltroToken`, igual que cualquier otro token.
4. El rol `SERVICIO` solo abre las rutas de lectura declaradas en el contrato y recibe la
   vista completa del recurso, incluidas las canchas con `activa = false`. En cualquier ruta
   de escritura responde `403 SIN_PERMISO`.

Dos reglas que no se rompen: **ningún servicio propaga el token del usuario final**, y emitir
tokens `SERVICIO` no obliga a aceptarlos — `ms-reportes` los emite y los rechaza si le llegan.

---

## 5. Validaciones

Cada dato que entra pasa por hasta cuatro filtros, siempre en este orden. Lo importante es que
cada nivel tiene **un lugar fijo en el código**: si una validación no está donde dice esta
tabla, no existe.

| Nivel | Dónde vive | Qué comprueba | Código que devuelve |
|---|---|---|---|
| 1. DTO | `dto/<Nombre>Request.java`, con anotaciones de `jakarta.validation` | Forma y obligatoriedad de cada campo | `400 DATOS_INVALIDOS` |
| 2. Mapper | `mapper/<Nombre>Mapper.java`, parseo estricto | Fechas imposibles que el patrón dejó pasar | `400 DATOS_INVALIDOS` |
| 3. Servicio | `service/<Nombre>Service.java`, reglas RN-xx | Negocio: propiedad, solapamientos, límites | `400`, `403`, `404`, `409` |
| 4. Base | `infra/postgres/`, `CHECK` e índices únicos | Carreras entre dos peticiones simultáneas | `409` (o `500`) |

El nivel 1 lo dispara el `@Valid` del controlador; los niveles 2 y 3 los ejecuta el servicio;
el nivel 4 lo aplica PostgreSQL. Todos terminan en el mismo lugar:
`exception/ManejadorExcepciones.java` del microservicio.

### 5.1 Nivel 1 — en el DTO

Ejemplo concreto, `ms-reservas · dto/ReservaRequest.java`:

```java
public record ReservaRequest(
        @NotNull @Positive Long canchaId,                                  // línea 19
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$",        // línea 20
                message = "debe tener formato AAAA-MM-DD") String fecha,
        @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",     // línea 22
                message = "debe tener formato HH:mm") String horaInicio) { }
```

Si llega `{ "canchaId": 0, "fecha": "12/09/2026", "horaInicio": "10:00" }`, el `@Valid` del
controlador falla antes de entrar al servicio y el manejador
(`ManejadorExcepciones.java:46`) responde `400 DATOS_INVALIDOS` con el detalle del campo.

Los cinco DTO de entrada del sistema:

| DTO (archivo) | Validaciones |
|---|---|
| `ms-reservas · dto/ReservaRequest.java` | `canchaId` `@NotNull @Positive`; `fecha` `@Pattern` `AAAA-MM-DD`; `horaInicio` `@Pattern` `HH:mm`. No declara `usuarioId` —sale del claim `sub`— ni `id` ni `estado` |
| `ms-canchas · dto/CanchaRequest.java` | `nombre` `@NotBlank @Size(max = 80)`; `deporte` `@Pattern PADEL\|TENIS\|BASQUET`; `horaApertura` y `horaCierre` `@Pattern HH:mm` |
| `ms-canchas · dto/BloqueoRequest.java` | `fecha`, `horaInicio` y `horaFin` con patrón; `motivo` `@NotBlank @Size(max = 200)` |
| `ms-usuarios · dto/RegistroRequest.java` | `nombre @NotBlank @Size(max = 80)`; `email @NotBlank @Email @Size(max = 120)`; `password @NotBlank @Size(min = 8, max = 100)` |
| `ms-usuarios · dto/LoginRequest.java` | `email @NotBlank @Email`; `password @NotBlank`, **a propósito sin `@Size`**: una clave que no cumple la política actual debe terminar en `401`, no en `400`, para no revelar políticas ni la existencia de la cuenta |

Los dos DTO de cambio de estado —`ms-canchas · dto/CambioEstadoCanchaRequest.java` y
`ms-usuarios · dto/CambioEstadoRequest.java`— tienen un solo campo booleano con `@NotNull`.

### 5.2 Nivel 2 — en el mapper

El `@Pattern` solo comprueba la forma, así que `"2026-02-31"` lo pasa: son cuatro dígitos,
guion, dos, guion, dos. Por eso el parseo real ocurre en
`ms-reservas · mapper/ReservaMapper.java:24`, con `ResolverStyle.STRICT`:

```java
private static final DateTimeFormatter FECHA =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
```

y el método `aFecha` (línea 58) atrapa el `DateTimeParseException` y lanza
`FormatoInvalidoException`, que el manejador traduce a `400 DATOS_INVALIDOS`
(`ManejadorExcepciones.java:89`). `ms-canchas` hace lo mismo en `mapper/BloqueoMapper.java`.

### 5.3 Nivel 3 — en el servicio, caso por caso

**Alta de reserva** — `ms-reservas · service/ReservaService.java:65`, método `crear`. Las nueve
comprobaciones, con la línea exacta de cada una:

| Orden | Línea | Validación | Respuesta |
|---|---|---|---|
| 1 | 66-67 | Parseo estricto de `fecha` y `horaInicio` (nivel 2) | `400 DATOS_INVALIDOS` |
| 2 | 71 | RN-01: `horaInicio` debe ser hora en punto (minutos `00`) | `400 DATOS_INVALIDOS` |
| 3 | 76 | El bloque no puede haber ocurrido ya | `400 DATOS_INVALIDOS` |
| 4 | 81-84 | La cancha existe y está activa — **llamada HTTP a `ms-canchas`**; inexistente e inactiva responden lo mismo | `404 NO_ENCONTRADO` |
| 5 | 88 | El bloque cabe dentro del horario de atención de la cancha | `400 DATOS_INVALIDOS` |
| 6 | 99 | RN-02: el bloque no está reservado — consulta local a `reservas_db` | `409 BLOQUE_OCUPADO` |
| 7 | 106 | RN-06: el usuario no superó el límite de activas — consulta local | `409 LIMITE_RESERVAS` |
| 8 | 114 | El bloque no está en mantenimiento — **segunda llamada HTTP a `ms-canchas`** | `409 BLOQUE_OCUPADO` |
| 9 | 118-120 | `INSERT` con `estado = CONFIRMADA` | `201` |

El orden de 6-7-8 es deliberado: los tres devuelven `409`, así que entre ellos el orden no
cambia el contrato, y las dos comprobaciones locales van antes que la que cuesta red. El
rechazo más frecuente —el bloque ya reservado— no gasta una llamada HTTP.

**Cancelación** — mismo archivo, método `cancelar` (línea 158): la reserva existe
(`404 NO_ENCONTRADO`, línea 159) → propiedad de la reserva (`403 SIN_PERMISO`, línea 163,
RN-03) → reserva ya ocurrida (`409 RESERVA_PASADA`, línea 168, RN-04) → estado distinto de
`CONFIRMADA` (`409 RESERVA_NO_CANCELABLE`, línea 173). Ese orden también es deliberado:
responder `409` a quien no es dueño le revelaría información sobre una reserva ajena. Cancelar
**no borra la fila**: la línea 179 cambia el `estado` a `CANCELADA`, y el bloque queda libre
(RN-05, RN-08).

**Inicio de sesión** — `ms-usuarios · service/AutenticacionService.java:38`: correo
inexistente (línea 41), contraseña incorrecta (línea 45) y usuario con `activo = false`
(línea 48) lanzan la **misma** `CredencialesInvalidasException` con la misma constante
`MENSAJE_RECHAZO`, y salen como el mismo `401`, para que nadie pueda enumerar cuentas
registradas. La contraseña se compara con BCrypt; la base nunca guarda texto plano.

**Catálogo** — `ms-canchas · service/CanchaService.java`: nombre repetido al crear (línea 68)
y al editar (línea 84) → `409 NOMBRE_DUPLICADO`; `horaCierre` no posterior a `horaApertura`
(línea 106) → `400`; cancha inexistente (línea 119) → `404`. `GET /api/canchas` filtra **por
rol, sin parámetro**: el `ADMIN` ve todas, el `USUARIO` solo las `activa = true` y recibe
`404` en una inactiva.

**Bloqueos** — `ms-canchas · service/BloqueoService.java:56`, método `crear`: `horaFin`
posterior a `horaInicio` (línea 63, `400`), fecha no pasada (línea 67, `400`), franja dentro
del horario de atención (línea 72, `400`) y sin solaparse con otro bloqueo (línea 79,
`409 BLOQUEO_DUPLICADO`). Todas las de `400` se evalúan antes que la de `409`: un cuerpo mal
formado reportado como conflicto sería engañoso.

**Usuarios** — `ms-usuarios · service/UsuarioService.java`: correo repetido al registrar
(línea 39) → `409 EMAIL_DUPLICADO`; un administrador no puede inactivarse a sí mismo
(línea 66) → `AutoInactivacionException`.

**Reportes** — `ms-reportes · controller/ReporteController.java`: `desde` y `hasta` son
obligatorios, se parsean a mano con el mismo `uuuu-MM-dd` estricto (línea 36) en el método `parsear` (línea 125) y
`desde` posterior a `hasta` lanza `RangoInvalidoException` en `validarOrden` (línea 136) →
`400 DATOS_INVALIDOS`. Es el único caso en que la validación vive en el controlador y no en el
servicio: se parsea a mano en vez de dejar que Spring convierta a `LocalDate` para que un
parámetro mal escrito salga con el `codigo` del contrato y no con el error genérico de Spring.
Ambos extremos son inclusivos y no hay rango máximo.

### 5.4 Nivel 4 — en la base

El DDL está en `infra/postgres/`, un archivo por base: `02-ddl-usuarios.sql`, `03-ddl-canchas.sql` y `04-ddl-reservas.sql`. Ahí viven los `CHECK` sobre `estado` (`04:21`) y sobre `deporte` (`03:16`), y los
índices únicos `uq_cancha_nombre`, `uq_bloqueo_franja` y el **parcial**
`ux_reserva_bloque_confirmada`.

Ese último índice es el árbitro de RN-02 cuando dos altas simultáneas pasan las dos el paso 6:
la segunda viola la restricción, y `ManejadorExcepciones.java:152` atrapa la
`DataIntegrityViolationException` y la traduce al mismo `409 BLOQUE_OCUPADO`, no a un `500`.
Es la doble barrera: sin ella, una carrera saldría como error interno.

Es **parcial** —solo sobre `CONFIRMADA`— justamente para que una reserva cancelada deje de
bloquear el bloque (RN-05).

---

## 6. Cómo se consulta la base de datos

### 6.1 Dónde vive el acceso a datos

Solo dos carpetas de cada microservicio tocan la base, y siempre las mismas:

| Carpeta | Qué contiene | Archivos reales |
|---|---|---|
| `entity/` | Las clases que mapean una tabla, y los enum de sus columnas | `ms-usuarios`: `Usuario.java`, `Rol.java` · `ms-canchas`: `Cancha.java`, `BloqueoMantenimiento.java`, `Deporte.java` · `ms-reservas`: `Reserva.java`, `EstadoReserva.java` |
| `repository/` | Las interfaces con las consultas | `ms-usuarios`: `UsuarioRepository.java` · `ms-canchas`: `CanchaRepository.java`, `BloqueoRepository.java` · `ms-reservas`: `ReservaRepository.java` |

`ms-reportes` no tiene ninguna de las dos: no tiene base. Su equivalente es `client/`, que
pide los datos por HTTP.

Quién llama a quién: **solo las clases de `service/` usan un repositorio**. Un controlador
nunca inyecta un repositorio, y un repositorio nunca aparece en un mapper ni en un DTO. Si
quiere saber qué consultas existen contra una tabla, la lista completa son los métodos
declarados en su interfaz de `repository/`: no hay SQL suelto en ninguna otra parte del
backend.

### 6.2 La entidad: qué anotaciones se usaron

`ms-reservas · entity/Reserva.java`, que mapea la tabla `reserva` creada por
`infra/postgres/04-ddl-reservas.sql`:

```java
@Entity                                                    // línea 21
@Table(name = "reserva")                                   // línea 22
public class Reserva {

    @Id                                                    // línea 25
    @GeneratedValue(strategy = GenerationType.IDENTITY)    // línea 26
    @Column(name = "id")
    private Long id;

    @Column(name = "usuario_id", nullable = false)         // línea 30
    private Long usuarioId;

    @Column(name = "cancha_id", nullable = false)
    private Long canchaId;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Enumerated(EnumType.STRING)                           // línea 46
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoReserva estado;

    protected Reserva() { }                                // línea 50, requerido por JPA
```

Las seis anotaciones que aparecen en todo el backend, todas de `jakarta.persistence`:

| Anotación | Para qué | Detalle en este proyecto |
|---|---|---|
| `@Entity` | Marca la clase como mapeada a una tabla | Una por tabla; no hay entidades sin tabla |
| `@Table(name = "...")` | Nombre exacto de la tabla | Se declara siempre, aunque coincida con el nombre de la clase, para no depender de la estrategia de nombres de Hibernate |
| `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Clave primaria generada por PostgreSQL | `IDENTITY` porque las columnas del DDL son `GENERATED ... AS IDENTITY`: el valor lo pone la base, no Hibernate |
| `@Column(name, nullable, length, unique)` | Nombre y restricciones de la columna | El nombre se escribe siempre porque las columnas van en `snake_case` y los campos Java en `camelCase`: `usuario_id` ↔ `usuarioId` |
| `@Enumerated(EnumType.STRING)` | Guarda el enum como texto, no como número | Obligatorio: la columna tiene un `CHECK` con los valores en texto (`ck_reserva_estado`). Con el `ORDINAL` por defecto se guardaría `0` y el `CHECK` fallaría |

Tres decisiones que valen para las tres entidades:

- **No hay `@ManyToOne` ni `@JoinColumn` en ninguna parte.** `reserva.usuario_id` y
  `reserva.cancha_id` son `Long` sueltos (`Reserva.java:30` y `:33`) porque esas filas viven
  en otra base. Una asociación JPA exigiría una clave foránea entre bases, que es justo lo que
  la arquitectura prohíbe.
- **Constructor sin argumentos `protected`** (`Reserva.java:50`): JPA lo necesita para
  instanciar la fila leída, y `protected` evita que el resto del código lo use por error. El
  constructor público (línea 54) es el que obliga a dar todos los campos.
- **Getters escritos a mano y `set` solo donde hay que cambiar algo.** `Reserva` expone un
  único `setEstado` (línea 92), porque cancelar es lo único que muta una reserva. No se usa
  Lombok ni `@Data`.

Las otras dos entidades siguen el mismo patrón: `Cancha.java` con `@Column(name =
"cancha_id")` en el `@Id` y `@Enumerated(EnumType.STRING)` sobre `deporte`; `Usuario.java` con
`@Column(name = "password_hash", nullable = false, length = 72)` y `@Enumerated` sobre `rol`.

### 6.3 El repositorio: consultas derivadas del nombre

`ms-reservas · repository/ReservaRepository.java` es una **interfaz**, no una clase: no tiene
implementación escrita por nadie. Spring Data genera el código a partir del nombre de cada
método al arrancar.

```java
public interface ReservaRepository extends JpaRepository<Reserva, Long> {   // línea 16
```

`JpaRepository<Reserva, Long>` significa "repositorio de la entidad `Reserva`, cuya clave es
`Long`", y ya trae hechos `save`, `findById`, `findAll` y `deleteById` — de ahí sale el
`reservaRepository.save(reserva)` del alta y el `findById` de la cancelación. No lleva
`@Repository`: Spring Data la detecta por heredar de `JpaRepository`.

Lo demás son **consultas derivadas**: el nombre del método *es* la consulta. La que usa el
paso 6 del alta de reserva (`ReservaRepository.java:26`):

```java
boolean existsByCanchaIdAndFechaAndHoraInicioAndEstado(
        Long canchaId, LocalDate fecha, LocalTime horaInicio, EstadoReserva estado);
```

se lee por partes: `exists` → devuelve booleano; `ByCanchaId` `And Fecha` `And HoraInicio`
`And Estado` → cuatro condiciones de igualdad, en el mismo orden que los parámetros. Equivale
a preguntar si hay alguna fila de `reserva` con ese `cancha_id`, esa `fecha`, esa
`hora_inicio` y ese `estado`. No hay que escribir el SQL ni mantenerlo.

Las cinco consultas de reservas y dónde se usan:

| Método (archivo:línea) | Qué devuelve | Quién lo llama |
|---|---|---|
| `findByCanchaIdAndFechaAndEstado` (`ReservaRepository.java:19`) | Reservas `CONFIRMADA` de una cancha en un día | `service/DisponibilidadService` (HU-01) |
| `existsByCanchaIdAndFechaAndHoraInicioAndEstado` (`:26`) | Si el bloque ya está tomado | `ReservaService.java:99` (RN-02) |
| `contarActivas` (`:44`) | Cuántas reservas activas tiene el usuario | `ReservaService.java:106` (RN-06) |
| `findByUsuarioIdOrderByFechaDescHoraInicioDesc` (`:48`) | Historial propio, lo más reciente primero | `ReservaService.java:130` (HU-03) |
| `findAllByOrderByFechaDescHoraInicioDesc` (`:51`) | Listado global con el mismo orden | `ReservaService.java:139` (HU-04) |

El `OrderByFechaDescHoraInicioDesc` del nombre es el `ORDER BY`: no hace falta ordenar en Java.

### 6.4 El único `@Query` del proyecto

Cuando la condición no cabe en el nombre del método, se escribe JPQL a mano. Pasa una sola
vez, en `ReservaRepository.java:38`, para RN-06:

```java
@Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.usuarioId = :usuarioId
          AND r.estado = ec.ups.dae.reservas.entity.EstadoReserva.CONFIRMADA
          AND (r.fecha > :hoy OR (r.fecha = :hoy AND r.horaInicio > :ahora))
        """)
long contarActivas(@Param("usuarioId") Long usuarioId, @Param("hoy") LocalDate hoy,
                   @Param("ahora") LocalTime ahora);
```

Dos cosas que conviene notar:

- Es **JPQL, no SQL**: `FROM Reserva r` nombra la *entidad* y `r.usuarioId` el *campo Java*,
  no la tabla `reserva` ni la columna `usuario_id`. Hibernate hace la traducción.
- `@Param("hoy")` liga el `:hoy` del texto con el argumento del método. Es lo que evita
  concatenar valores en la consulta.

Se escribió así porque la condición "activa" mezcla dos columnas —una fecha futura, o la fecha
de hoy con hora posterior a la actual— y ningún nombre de método derivado la expresaría de
forma legible.

### 6.5 Transacciones y conexión

La transacción se abre en el **servicio**, no en el repositorio ni en el controlador:

- `@Transactional` sobre `ReservaService.crear` (línea 64) y `cancelar` (línea 157): leen y
  escriben, así que todo el método es una sola unidad. Si el paso 8 lanza `BloqueOcupadoException`,
  no queda nada a medias.
- `@Transactional(readOnly = true)` sobre `listarMias` (línea 128) y `listarTodas` (línea 137):
  marca la transacción como de solo lectura.

Los datos de conexión no están en el código, sino en
`backend/ms-reservas/src/main/resources/application.properties`:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/reservas_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:reservas_user}
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
spring.jpa.open-in-view=false
```

Cada microservicio apunta a **su** base con **su** usuario, y los valores se pueden sobrescribir
por variable de entorno desde `docker-compose.yml`. Dos ajustes importan:

- `ddl-auto=validate`: Hibernate **no crea ni modifica** tablas; solo comprueba al arrancar que
  cada entidad calza con el esquema real. Si alguien agrega un campo a `Reserva` que no existe
  en el DDL, el contenedor no levanta. El esquema lo manda `infra/postgres/`, y cuando no
  calzan se corrige la entidad, nunca el DDL.
- `open-in-view=false`: la sesión de JPA se cierra al terminar el método del servicio. Por eso
  el mapper trabaja sobre objetos ya cargados y no puede disparar consultas por accidente
  mientras arma la respuesta.

---

## 7. Qué pasa cuando algo no responde

Configuración común de los clientes HTTP: **2 s de conexión, 5 s de lectura, sin reintentos y
sin cortacircuitos**. La política es fallar rápido y por completo.

| Qué falla | Qué ocurre | Qué recibe el usuario |
|---|---|---|
| **`ms-canchas`, visto desde `ms-reservas`** | El `RestClient` corta a los 2 s / 5 s; `CanchasClient` atrapa la `RuntimeException` y lanza `CatalogoNoDisponibleException` | `500 ERROR_INTERNO`. No se puede reservar ni consultar disponibilidad: sin el horario de atención no hay con qué validar el bloque |
| **`ms-canchas` responde `404`** | Es el único código que sí se traduce: `CanchaNoEncontradaException` | `404 NO_ENCONTRADO`. Es información legítima, no un fallo de infraestructura |
| **`ms-canchas` responde `401`, `403` o `5xx`** | Se envuelve como `CatalogoNoDisponibleException`; el detalle real queda en el log | `500 ERROR_INTERNO`. Un `401` ahí significa que **nuestro** token de servicio está mal firmado o vencido: defecto de configuración propio, no error del cliente final |
| **`ms-canchas` o `ms-reservas`, vistos desde `ms-reportes`** | Igual, y además está decidido que no hay reporte parcial | `500 ERROR_INTERNO`. Nunca un reporte con ceros: un `0 %` en todas las canchas es indistinguible de un mes sin reservas |
| **PostgreSQL no responde** | La excepción cae en la red de seguridad `@ExceptionHandler(Exception.class)` | `500 ERROR_INTERNO` con mensaje fijo; el stacktrace solo en el log |
| **Un microservicio caído, visto desde el gateway** | Nginx devuelve su `502` **crudo**: no traduce al contrato, porque no es un microservicio y no lo habla | El `clienteApi` del frontend lo normaliza a `{ codigo: "ERROR_INTERNO", mensaje: "No se pudo contactar al servicio" }` y `MensajeError` lo pinta |
| **Un microservicio no existe al arrancar el gateway** | Nginx resuelve por DNS cada `proxy_pass` **al arrancar**, no en cada petición: aborta con `host not found in upstream`. Por eso el gateway declara `depends_on` de los cuatro | El contenedor no levanta. Es el fallo ruidoso al arrancar, elegido a propósito sobre el `502` silencioso en caliente |
| **Un microfrontend remoto no carga** | No es un error HTTP, sino un fallo de descarga o de render del módulo federado: lo captura `BordeError.jsx` | "Modulo no disponible"; el resto del shell sigue funcionando |

**Un caso que no es fallo, y conviene distinguirlo:** si la cancha está inactiva,
`DisponibilidadService` **no** llama al endpoint de bloqueos y devuelve todos los bloques
ocupados. El resultado ya está determinado; la segunda llamada no cambiaría nada y solo
agregaría latencia y una vía más de fallo.

**Por qué no hay reintentos:** el caso frecuente es el servicio caído, no un parpadeo de red.
Reintentar multiplicaría la espera del usuario sin cambiar el resultado.

**Por qué el gateway no pone su propio tiempo de espera:** `ms-reservas` y `ms-reportes` ya
cortan a los 5 s y responden `500 ERROR_INTERNO`, que sí está en el contrato. Un umbral propio
de Nginx podría dispararse antes y convertir ese `500` del contrato en un `504` que no lo es.

---

## 8. Reglas de comunicación que no se rompen

- Un microservicio **nunca** lee la base de otro. No es disciplina: `init.sql` crea un usuario
  por base y revoca los permisos cruzados, así que `reservas_user` no puede leer `canchas_db`
  aunque alguien escriba la consulta.
- Por eso `reserva.usuario_id` y `reserva.cancha_id` son `BIGINT` sin clave foránea: esas
  filas viven en otra base.
- Toda integración es REST, y la hace una sola clase por dependencia.
- El error de un servicio ajeno no se reenvía: se envuelve y sale como `500 ERROR_INTERNO`.
- El frontend nunca llama a un microservicio por su puerto: siempre `/api` y siempre a través
  del gateway.
