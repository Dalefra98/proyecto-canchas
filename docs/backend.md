# Cómo funciona el backend

Qué hace cada microservicio, cómo se comunican entre ellos, qué validan y qué ocurre cuando
uno no responde. Todo lo que se afirma aquí está en el código; las rutas son verificables.

Documentos que este **no** repite:

- Estilos, patrones y el porqué de cada decisión: [`arquitectura.md`](arquitectura.md).
- Nombres de campo, rutas y códigos de error congelados: [`contratos/README.md`](contratos/README.md).
- Cómo levantar y verificar el sistema: [`../README.md`](../README.md).
- Diagramas C4: [`diagramas-c4.md`](diagramas-c4.md).

---

## 1. Mapa general

```
NAVEGADOR
    │  llamadas relativas a /api
    ▼
devServer de cada microfrontend (3000/3001/3002/3003)
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

| Servicio | Responsabilidad | Base | Rutas |
|---|---|---|---|
| `ms-usuarios` | Registro, inicio de sesión, listado e inactivación de usuarios. **Es el único que emite el token de persona** | `usuarios_db` — tabla `usuario` | `POST /api/usuarios/sesiones`, `POST /api/usuarios`, `GET /api/usuarios`, `PATCH /api/usuarios/{usuarioId}/estado` |
| `ms-canchas` | Catálogo de canchas y bloqueos de mantenimiento (RN-07) | `canchas_db` — tablas `cancha` y `bloqueo_mantenimiento` | 5 rutas de `/api/canchas` y 3 de `/api/canchas/{canchaId}/bloqueos` |
| `ms-reservas` | Disponibilidad y ciclo de vida de la reserva: RN-01 a RN-06 y RN-08 | `reservas_db` — tabla `reserva` | `GET /disponibilidad`, `POST /`, `GET /`, `GET /mias`, `PATCH /{id}/cancelacion` |
| `ms-reportes` | Agrega ocupación, reservas y cancelaciones. **No tiene base de datos ni siquiera JPA en su `pom.xml`** | — | `GET /api/reportes/ocupacion`, `/reservas`, `/cancelaciones` |

---

## 3. Anatomía de una petición

Dentro de cualquiera de los cuatro servicios, una petición atraviesa siempre lo mismo:

```
petición HTTP
   │
   ▼
FiltroToken            config/FiltroToken.java
   │                   lee "Authorization: Bearer", valida el JWT LOCALMENTE con
   │                   JWT_SECRET, deja usuarioId como principal y rol como authority
   ▼
SeguridadConfig        config/SeguridadConfig.java
   │                   SessionCreationPolicy.STATELESS
   │                   requestMatchers(...).hasAnyRole(...)
   │                   sin token o token inválido → 401 NO_AUTENTICADO
   │                   rol incorrecto             → 403 SIN_PERMISO
   ▼
Controller             controller/
   │                   @Valid sobre el DTO → 400 DATOS_INVALIDOS
   │                   solo recibe y devuelve; no decide nada de negocio
   ▼
Service                service/
   │                   las reglas RN-xx, cada una con su ID en un comentario
   │  ├─► Repository   repository/ → su propia base, y solo la suya
   │  └─► Client HTTP  client/ o service/ → otro microservicio, si hace falta
   ▼
Mapper                 mapper/
   │                   entidad → DTO, formato HH:mm, estado FINALIZADA derivado
   ▼
respuesta JSON

  ── si algo lanzó una excepción ──► exception/ManejadorExcepciones.java
                                     @RestControllerAdvice → { codigo, mensaje }
```

Ningún servicio guarda sesión: el token se valida en cada petición, con la firma, sin
consultar a `ms-usuarios` y sin estado compartido. El token de persona lo emite
`ms-usuarios/service/TokenService.java` con `sub = usuarioId`, claim `rol` y vigencia de
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
`ms-reportes/client/CanchasClient.java`, `ms-reportes/client/ReservasClient.java` y, en
`ms-reservas`, `service/CanchasClient.java`.

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

Cuatro niveles, siempre en este orden:

```
1. DTO       jakarta.validation     forma y obligatoriedad     → 400
2. Mapper    parseo estricto        fechas imposibles          → 400
3. Service   reglas RN-xx           negocio                    → 400 / 403 / 404 / 409
4. Base      CHECK e índices únicos carreras y último filtro    → 409 (o 500)
```

### 5.1 En el DTO

| DTO | Validaciones |
|---|---|
| `ReservaRequest` | `canchaId` `@NotNull @Positive`; `fecha` `@Pattern` `AAAA-MM-DD`; `horaInicio` `@Pattern` `HH:mm`. No declara `usuarioId` —sale del claim `sub`— ni `id` ni `estado` |
| `CanchaRequest` | `nombre` `@NotBlank @Size(max = 80)`; `deporte` `@Pattern PADEL\|TENIS\|BASQUET`; `horaApertura` y `horaCierre` `@Pattern HH:mm` |
| `BloqueoRequest` | `fecha`, `horaInicio` y `horaFin` con patrón; `motivo` `@NotBlank @Size(max = 200)` |
| `RegistroRequest` | `nombre @NotBlank @Size(max = 80)`; `email @NotBlank @Email @Size(max = 120)`; `password @NotBlank @Size(min = 8, max = 100)` |
| `LoginRequest` | `email @NotBlank @Email`; `password @NotBlank`, **a propósito sin `@Size`**: una clave que no cumple la política actual debe terminar en `401`, no en `400`, para no revelar políticas ni la existencia de la cuenta |

### 5.2 En el mapper

El `@Pattern` solo comprueba la forma, así que acepta `2026-02-31`. Por eso `ReservaMapper` y
su equivalente en `ms-canchas` parsean con `DateTimeFormatter` y `ResolverStyle.STRICT`, y
lanzan `FormatoInvalidoException` → `400 DATOS_INVALIDOS`.

### 5.3 En el servicio, por caso de uso

**Alta de reserva** — `ms-reservas/service/ReservaService.crear()`:

| Orden | Validación | Respuesta |
|---|---|---|
| 1 | Parseo estricto de `fecha` y `horaInicio` | `400 DATOS_INVALIDOS` |
| 2 | RN-01: `horaInicio` debe ser hora en punto (minutos `00`) | `400 DATOS_INVALIDOS` |
| 3 | El bloque no puede haber ocurrido ya | `400 DATOS_INVALIDOS` |
| 4 | La cancha existe y está activa — **llamada HTTP a `ms-canchas`**; inexistente e inactiva responden lo mismo | `404 NO_ENCONTRADO` |
| 5 | El bloque cabe dentro del horario de atención de la cancha | `400 DATOS_INVALIDOS` |
| 6 | RN-02: el bloque no está reservado — consulta local | `409 BLOQUE_OCUPADO` |
| 7 | RN-06: el usuario no superó el límite de activas — consulta local | `409 LIMITE_RESERVAS` |
| 8 | El bloque no está en mantenimiento — **llamada HTTP a `ms-canchas`** | `409 BLOQUE_OCUPADO` |
| 9 | `INSERT` con `estado = CONFIRMADA` | `201` |

El orden de 6-7-8 es deliberado: los tres devuelven `409`, así que entre ellos el orden no
cambia el contrato, y las dos comprobaciones locales van antes que la que cuesta red. El
rechazo más frecuente —el bloque ya reservado— no gasta una llamada HTTP.

**Cancelación** — `ReservaService`: propiedad de la reserva (`403 SIN_PERMISO`, RN-03) →
reserva ya ocurrida (`409 RESERVA_PASADA`, RN-04) → estado distinto de `CONFIRMADA`
(`409 RESERVA_NO_CANCELABLE`). Ese orden también es deliberado: responder `409` a quien no es
dueño le revelaría información sobre una reserva ajena. Cancelar **no borra la fila**: cambia
el `estado` a `CANCELADA`, y el bloque queda libre (RN-05, RN-08).

**Inicio de sesión** — `ms-usuarios/service/AutenticacionService`: correo inexistente,
contraseña incorrecta y usuario con `activo = false` se rechazan con **el mismo `401` y el
mismo mensaje**, para que nadie pueda enumerar cuentas registradas. La contraseña se compara
con BCrypt; la base nunca guarda texto plano.

**Catálogo** — `ms-canchas/service/CanchaService`: nombre repetido → `409 NOMBRE_DUPLICADO`;
`horaCierre` no posterior a `horaApertura` → `400`. `GET /api/canchas` filtra **por rol, sin
parámetro**: el `ADMIN` ve todas, el `USUARIO` solo las `activa = true` y recibe `404` en una
inactiva.

**Bloqueos** — `ms-canchas/service/BloqueoService`: `horaFin` posterior a `horaInicio`
(`400`), fecha no pasada (`400`), franja dentro del horario de atención (`400`) y sin
solaparse con otro bloqueo (`409 BLOQUEO_DUPLICADO`). Todas las de `400` se evalúan antes
que la de `409`: un cuerpo mal formado reportado como conflicto sería engañoso.

**Usuarios** — `ms-usuarios/service/UsuarioService`: correo repetido → `409 EMAIL_DUPLICADO`;
un administrador no puede inactivarse a sí mismo → `AutoInactivacionException`.

**Reportes** — `ms-reportes/controller/ReporteController`: `desde` y `hasta` son obligatorios,
se parsean con `ISO_LOCAL_DATE` estricto y `desde` posterior a `hasta` lanza
`RangoInvalidoException` → `400 DATOS_INVALIDOS`. Ambos extremos son inclusivos y no hay
rango máximo.

### 5.4 En la base

`CHECK` sobre `estado` y `deporte`, `uq_cancha_nombre`, `uq_bloqueo_franja` y el índice único
**parcial** `ux_reserva_bloque_confirmada`.

Ese último índice es el árbitro de RN-02 cuando dos altas simultáneas pasan la comprobación
del paso 6: la segunda viola la restricción y `ManejadorExcepciones` traduce la
`DataIntegrityViolationException` al mismo `409 BLOQUE_OCUPADO`, no a un `500`. Es la doble
barrera: sin ella, una carrera saldría como error interno.

Es **parcial** —solo sobre `CONFIRMADA`— justamente para que una reserva cancelada deje de
bloquear el bloque (RN-05).

---

## 6. Qué pasa cuando algo no responde

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

## 7. Reglas de comunicación que no se rompen

- Un microservicio **nunca** lee la base de otro. No es disciplina: `init.sql` crea un usuario
  por base y revoca los permisos cruzados, así que `reservas_user` no puede leer `canchas_db`
  aunque alguien escriba la consulta.
- Por eso `reserva.usuario_id` y `reserva.cancha_id` son `BIGINT` sin clave foránea: esas
  filas viven en otra base.
- Toda integración es REST, y la hace una sola clase por dependencia.
- El error de un servicio ajeno no se reenvía: se envuelve y sale como `500 ERROR_INTERNO`.
- El frontend nunca llama a un microservicio por su puerto: siempre `/api` y siempre a través
  del gateway.
