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
| Identificador de usuario | `usuarioId` | number | ms-usuarios |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | ms-usuarios |
| Porcentaje de ocupación | `porcentajeOcupacion` | number (0-100) | ms-reportes |

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
| Bloque ya reservado (RN-02) | 409 | `BLOQUE_OCUPADO` |
| Límite de reservas activas (RN-06) | 409 | `LIMITE_RESERVAS` |
| Reserva ya ocurrida (RN-04) | 409 | `RESERVA_PASADA` |

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