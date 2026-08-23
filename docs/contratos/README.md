# Contratos de integración — CONGELADOS

Estos nombres de campo son la única fuente de verdad para microservicios y
microfrontends. **No renombrar, no abreviar, no traducir.**

Si alguien necesita un campo nuevo, se agrega primero AQUI y se avisa al grupo.
Cambiar un nombre aquí obliga a revisar todas las specs que ya lo usan.

## Campos acordados

| Concepto | Campo JSON | Tipo / valores | Servicio dueño |
|---|---|---|---|
| Identificador de reserva | `id` | number | ms-reservas |
| Estado de la reserva | `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | ms-reservas |
| Fecha de la reserva | `fecha` | string `AAAA-MM-DD` | ms-reservas |
| Hora de inicio | `horaInicio` | string `HH:mm` | ms-reservas |
| Hora de fin | `horaFin` | string `HH:mm` | ms-reservas |
| Identificador de cancha | `canchaId` | number | ms-canchas |
| Nombre de cancha | `nombre` | string | ms-canchas |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | ms-canchas |
| Hora de apertura de la cancha | `horaApertura` | string `HH:mm` | ms-canchas |
| Hora de cierre de la cancha | `horaCierre` | string `HH:mm` | ms-canchas |
| Cancha activa | `activa` | boolean | ms-canchas |
| Identificador de bloqueo | `bloqueoId` | number | ms-canchas |
| Motivo del bloqueo | `motivo` | string | ms-canchas |
| Identificador de usuario | `usuarioId` | number | ms-usuarios |
| Nombre de usuario | `nombre` | string | ms-usuarios |
| Correo de acceso | `email` | string | ms-usuarios |
| Contraseña | `password` | string — **solo en request, NUNCA en respuesta** | ms-usuarios |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | ms-usuarios |
| Usuario activo | `activo` | boolean | ms-usuarios |
| Token de sesión | `token` | string | ms-usuarios |
| Usuario de la sesión | `usuario` | objeto `UsuarioResponse` | ms-usuarios |
| Lista de bloques del día | `bloques` | arreglo de objetos | ms-reservas |
| Bloque libre | `disponible` | boolean | ms-reservas |
| Inicio del rango consultado | `desde` | string `AAAA-MM-DD` | ms-reportes |
| Fin del rango consultado | `hasta` | string `AAAA-MM-DD` | ms-reportes |
| Lista de filas del reporte | `items` | arreglo de objetos | ms-reportes |
| Horas reservadas en el rango | `horasReservadas` | number | ms-reportes |
| Horas disponibles en el rango | `horasDisponibles` | number | ms-reportes |
| Total de reservas en el rango | `totalReservas` | number | ms-reportes |
| Total de cancelaciones en el rango | `totalCancelaciones` | number | ms-reportes |
| Porcentaje de ocupación | `porcentajeOcupacion` | number (0-100) | ms-reportes |

Notas de uso:

- `horaApertura`, `horaCierre`, `horaInicio` y `horaFin` usan el mismo formato `HH:mm`.
- `password` se acepta únicamente en el cuerpo de las peticiones de registro e inicio de
  sesión. Se persiste como hash **BCrypt** (el de Spring Security); la base nunca guarda
  texto plano y ninguna respuesta serializa este campo.
- Un bloqueo de mantenimiento se representa con `bloqueoId`, `canchaId`, `fecha`,
  `horaInicio`, `horaFin` y `motivo`.

## Payloads congelados

### Inicio de sesión — `LoginResponse`

```json
{
  "token": "...",
  "usuario": { "usuarioId": 1, "nombre": "Ana", "email": "ana@demo.ec", "rol": "USUARIO", "activo": true }
}
```

### Disponibilidad — `DisponibilidadResponse`

```json
{
  "canchaId": 1,
  "fecha": "2026-08-24",
  "horaApertura": "07:00",
  "horaCierre": "22:00",
  "bloques": [
    { "horaInicio": "07:00", "horaFin": "08:00", "disponible": true },
    { "horaInicio": "08:00", "horaFin": "09:00", "disponible": false }
  ]
}
```

`disponible` es `false` cuando el bloque tiene una reserva en estado `CONFIRMADA` o cuando
cae dentro de un bloqueo de mantenimiento de esa cancha y fecha. En cualquier otro caso es
`true`.

### Reportes — envoltura común

Los tres reportes comparten la forma `{ "desde": ..., "hasta": ..., "items": [...] }`.

`ReporteOcupacionResponse` — cada elemento de `items`:

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horasReservadas": 12, "horasDisponibles": 45, "porcentajeOcupacion": 26.7 }
```

`ReporteReservasResponse` — cada elemento de `items`:

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "totalReservas": 12 }
```

`ReporteCancelacionesResponse` — cada elemento de `items`:

```json
{ "canchaId": 1, "nombre": "Padel 1", "totalCancelaciones": 3 }
```

## Rutas REST congeladas

Convención: `/api/<dominio>/<recurso>`, plural y minúsculas. Los códigos de error de cada
respuesta son los de la tabla "Formato de error".

| Verbo | Ruta | Rol | Respuestas |
|---|---|---|---|
| POST | `/api/usuarios/sesiones` | público | 200, 400, 401 |
| POST | `/api/usuarios` | público | 201, 400, 409 |
| GET | `/api/usuarios` | ADMIN | 200, 401, 403 |
| PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | 200, 400, 401, 403, 404 |
| GET | `/api/canchas` | ADMIN, USUARIO | 200, 401 |
| GET | `/api/canchas/{canchaId}` | ADMIN, USUARIO | 200, 401, 404 |
| POST | `/api/canchas` | ADMIN | 201, 400, 401, 403, 409 |
| PUT | `/api/canchas/{canchaId}` | ADMIN | 200, 400, 401, 403, 404, 409 |
| PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | 200, 400, 401, 403, 404 |
| GET | `/api/canchas/{canchaId}/bloqueos?fecha` | ADMIN, USUARIO | 200, 400, 401, 404 |
| POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | 201, 400, 401, 403, 404, 409 |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | 204, 401, 403, 404 |
| GET | `/api/reservas/disponibilidad?canchaId&fecha` | ADMIN, USUARIO | 200, 400, 401, 404 |
| POST | `/api/reservas` | USUARIO | 201, 400, 401, 404, 409 |
| GET | `/api/reservas` | ADMIN | 200, 401, 403 |
| GET | `/api/reservas/mias` | USUARIO | 200, 401 |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | 200, 401, 403, 404, 409 |
| GET | `/api/reportes/ocupacion?desde&hasta` | ADMIN | 200, 400, 401, 403 |
| GET | `/api/reportes/reservas?desde&hasta` | ADMIN | 200, 400, 401, 403 |
| GET | `/api/reportes/cancelaciones?desde&hasta` | ADMIN | 200, 400, 401, 403 |

Notas de las rutas de canchas:

- `GET /api/canchas` y `GET /api/canchas/{canchaId}` **filtran por rol, sin parámetro de
  consulta**: el `ADMIN` recibe todas las canchas y el `USUARIO` solo las que tienen
  `activa = true`. Para un `USUARIO`, una cancha inactiva responde `404 NO_ENCONTRADO`.
- `GET /api/canchas/{canchaId}/bloqueos` acepta el parámetro **opcional** `fecha` en
  formato `AAAA-MM-DD`. Sin él devuelve todos los bloqueos de la cancha; con él, solo los
  de ese día. Un `fecha` con formato inválido responde `400 DATOS_INVALIDOS`.

## Formato de error (todos los microservicios)

```json
{ "codigo": "BLOQUE_OCUPADO", "mensaje": "El bloque horario ya esta reservado" }
```

| Situación | HTTP | `codigo` |
|---|---|---|
| Validación de entrada | 400 | `DATOS_INVALIDOS` |
| Sin token o token inválido | 401 | `NO_AUTENTICADO` |
| Sin permiso para la operación | 403 | `SIN_PERMISO` |
| Recurso inexistente | 404 | `NO_ENCONTRADO` |
| Email ya registrado | 409 | `EMAIL_DUPLICADO` |
| Nombre de cancha ya registrado | 409 | `NOMBRE_DUPLICADO` |
| Bloqueo ya registrado en esa franja | 409 | `BLOQUEO_DUPLICADO` |
| Bloque ya reservado (RN-02) | 409 | `BLOQUE_OCUPADO` |
| Límite de reservas activas (RN-06) | 409 | `LIMITE_RESERVAS` |
| Reserva ya ocurrida (RN-04) | 409 | `RESERVA_PASADA` |
| Error no previsto en el servidor | 500 | `ERROR_INTERNO` |

## Contrato Module Federation

| Microfrontend | Nombre | Módulo expuesto | Puerto |
|---|---|---|---|
| shell | `shell` (host) | — | 3000 |
| mf-reservas | `mfReservas` | `./ReservasApp` | 3001 |
| mf-administracion | `mfAdministracion` | `./AdminApp` | 3002 |
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

Props que el shell entrega a todo remote:

```jsx
<RemoteApp usuario={{ id, nombre, rol }} apiBaseUrl="/api" onLogout={fn} />
```

## Registro de cambios

| Fecha | Cambio | Quién | Specs afectadas |
|---|---|---|---|
| | Versión inicial | | — |
| 23/08/2026 | Se congelan los campos de usuario (`email`, `password`, `activo`), cancha (`horaApertura`, `horaCierre`, `activa`) y bloqueo (`bloqueoId`, `motivo`); `password` solo en request y con hash BCrypt | David Aristega | 01-modelo-y-contratos |
| 23/08/2026 | Se agrega la sección "Rutas REST congeladas" con los 19 endpoints de los cuatro microservicios | David Aristega | 01-modelo-y-contratos |
| 23/08/2026 | Se congelan los payloads `LoginResponse`, `DisponibilidadResponse` y los tres reportes (`desde`, `hasta`, `items`), con sus campos `token`, `usuario`, `bloques`, `disponible`, `horasReservadas`, `horasDisponibles`, `totalReservas`, `totalCancelaciones` | David Aristega | 01-modelo-y-contratos |
| 23/08/2026 | Se agrega `GET /api/canchas/{canchaId}` (ADMIN, USUARIO) y se amplía `GET /api/canchas/{canchaId}/bloqueos` a ADMIN y USUARIO, para que ms-reservas calcule disponibilidad | David Aristega | 01-modelo-y-contratos |
| 23/08/2026 | Se agrega el código de error `EMAIL_DUPLICADO` (HTTP 409) | David Aristega | 01-modelo-y-contratos |
| 23/08/2026 | Se agrega el código de error `ERROR_INTERNO` (HTTP 500) para toda excepción no prevista; lo usan los cuatro microservicios | David Aristega | 02-ms-usuarios y siguientes |
| 23/08/2026 | Se agrega el código de error `NOMBRE_DUPLICADO` (HTTP 409) por la restricción `uq_cancha_nombre`; `POST /api/canchas` y `PUT /api/canchas/{canchaId}` suman `409` a sus respuestas | David Aristega | 03-ms-canchas |
| 23/08/2026 | Se agrega el código de error `BLOQUEO_DUPLICADO` (HTTP 409) por la restricción `uq_bloqueo_franja`; `POST /api/canchas/{canchaId}/bloqueos` suma `409` | David Aristega | 03-ms-canchas |
| 23/08/2026 | `GET /api/canchas/{canchaId}/bloqueos` acepta el parámetro opcional `fecha` (`AAAA-MM-DD`) y suma `400`, para que ms-reservas calcule la disponibilidad de un día sin traer todos los bloqueos | David Aristega | 03-ms-canchas |
| 23/08/2026 | `GET /api/canchas` y `GET /api/canchas/{canchaId}` filtran por rol sin parámetro: el USUARIO solo ve canchas `activa = true` y recibe `404` en una inactiva; el ADMIN ve todas | David Aristega | 03-ms-canchas |
