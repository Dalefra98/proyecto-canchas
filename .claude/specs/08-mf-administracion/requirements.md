# Spec 08 — mf-administracion (remote de Module Federation) · requirements.md

Estado: **C1 — APROBADO** el 24/08/2026 ("Apruebo requisitos de la spec 08").

Las diez preguntas abiertas (P-01 a P-10) fueron **respondidas por el responsable el
24/08/2026** y están incorporadas al cuerpo de este documento; las decisiones y sus motivos
quedan en §10, para la defensa del proyecto.

La compuerta C2 (`design.md`) sigue **pendiente**: no se escribe código de producción hasta que
el diseño esté aprobado por escrito (`CLAUDE.md` §6).

Fuentes leídas: `CLAUDE.md`, `docs/contratos/README.md`,
`docs/Alcance_Funcional_Reserva_Canchas_v2.pdf` (secciones 3.1, 3.2, 3.3.3, 3.3.4, 3.4, 3.5,
4.1, 4.4, 6, 7), `.claude/specs/02-ms-usuarios/`, `.claude/specs/03-ms-canchas/`,
`.claude/specs/04-ms-reservas/`, `.claude/specs/06-shell-module-federation/`,
`.claude/specs/07-mf-reservas/`, `docker-compose.yml`, `frontend/shell/` y
`frontend/mf-reservas/` ya entregados.

## 1. Objetivo

Implementar `frontend/mf-administracion`: el **segundo remote** de Module Federation del
proyecto. Es el módulo **Administración** del PDF, disponible únicamente para el rol `ADMIN`.

El PDF lo define en dos lugares que hay que leer juntos:

| Fuente | Funcionalidad de Administración |
|---|---|
| PDF §3.2 (módulos y pantallas) | "Gestión de canchas — ABM de canchas: nombre, deporte, horario de atención, estado" |
| PDF §3.2 | "Gestión de reservas — Listado global de reservas con opción de cancelar cualquiera" |
| PDF §3.1 (roles y permisos) | "Gestionar catálogo de canchas (crear/editar/inactivar)" — módulo Administración |
| PDF §3.1 | "Gestionar horarios y bloqueos de mantenimiento" — módulo Administración |
| PDF §3.1 | "Gestionar usuarios (activar/inactivar)" — módulo Administración |

Las filas de §3.1 que no tienen pantalla propia en §3.2 (bloqueos de mantenimiento y usuarios)
**sí están dentro del alcance del módulo Administración**, porque §3.1 las asigna a este módulo
de forma explícita y el contrato ya congeló sus rutas.

**Tres pantallas, con menú interno del remote (P-01):**

| Pantalla | Contenido |
|---|---|
| Canchas | Listado del catálogo, alta, edición, cambio de estado y, **anidados dentro de la cancha seleccionada**, sus bloqueos de mantenimiento (HU-01 a HU-07) |
| Reservas | Listado global de reservas con opción de cancelar cualquiera (HU-08) |
| Usuarios | Listado de usuarios con opción de activar e inactivar (HU-09) |

Los bloqueos no tienen pantalla hermana: un bloqueo siempre pertenece a una cancha y su ruta es
anidada (`/api/canchas/{canchaId}/bloqueos`), así que una pantalla suelta obligaría a un selector
de cancha que duplica el listado que ya está al lado (P-01).

El remote se llama `mfAdministracion`, expone `./AdminApp`, corre en el puerto 3002 y recibe del
shell exactamente las cuatro props del contrato congelado. **No** tiene sesión propia, **no**
pinta cabecera ni menú (eso es del shell, spec 06) y **no** implementa ninguna pantalla de
reservas de usuario final ni de reportes.

## 2. Entregables de la spec

| Entregable | Ruta | Fuente |
|---|---|---|
| E-01 | `frontend/mf-administracion/package.json` | `CLAUDE.md` §3 |
| E-02 | `frontend/mf-administracion/webpack.config.js` con `ModuleFederationPlugin` | contrato |
| E-03 | `frontend/mf-administracion/.babelrc` (o equivalente ya usado en `mf-reservas`) | patrón de la spec 07 |
| E-04 | `frontend/mf-administracion/public/index.html` | patrón de la spec 07 |
| E-05 | `frontend/mf-administracion/src/index.js` — solo `import("./bootstrap")` | `CLAUDE.md` §3 |
| E-06 | `frontend/mf-administracion/src/bootstrap.jsx` | `CLAUDE.md` §3 |
| E-07 | `frontend/mf-administracion/src/App.jsx` — el componente expuesto como `./AdminApp` | contrato |
| E-08 | `frontend/mf-administracion/src/api/` — única capa que hace `fetch` | `CLAUDE.md` §4 |
| E-09 | `frontend/mf-administracion/src/components/` — las pantallas | `CLAUDE.md` §4 |
| E-10 | `frontend/mf-administracion/src/estilos.css` — CSS plano | `CLAUDE.md` §3 |
| E-11 | Servicio `mf-administracion` en `docker-compose.yml` y su volumen anónimo | PDF §4.4 |

`docker-compose.yml` es el **único** archivo que se modifica fuera de
`frontend/mf-administracion` (mismo criterio que P-08 de la spec 07).

## 3. Restricciones técnicas heredadas

| Aspecto | Valor | Fuente |
|---|---|---|
| Ruta en el repo | `frontend/mf-administracion` | `CLAUDE.md` §4 |
| Nombre Module Federation | `mfAdministracion` | contrato, "Contrato Module Federation" |
| Módulo expuesto | `./AdminApp` | contrato |
| Puerto | 3002 | contrato |
| React | 18 (`18.3.1`, la versión del shell y de `mf-reservas`) | `CLAUDE.md` §3 |
| Empaquetador | Webpack 5 con `ModuleFederationPlugin` | `CLAUDE.md` §3 |
| `shared` | `react` y `react-dom` con `singleton: true` | `CLAUDE.md` §3 |
| Arranque | `src/index.js` -> `import("./bootstrap")` | `CLAUDE.md` §3 |
| Llamadas HTTP | rutas relativas bajo `/api`, única capa `src/api/` | `CLAUDE.md` §3 y §4 |
| Autenticación | `token` recibido por prop, nunca leído del almacenamiento del navegador | contrato de props, D-12 de la spec 06 |
| Enrutador | **ninguno**: la pantalla activa es estado de React | P-05 de la spec 06 |
| Pantallas | tres —Canchas, Reservas, Usuarios—, con menú interno del remote y los bloqueos anidados en la cancha seleccionada | P-01 |
| Formularios de alta y edición | **en la misma pantalla del listado**, no en pantalla aparte | P-10 |
| Estilos | CSS plano, sin librerías de UI externas | `CLAUDE.md` §3 |
| Lenguaje | JavaScript, **sin** TypeScript | `CLAUDE.md` §3 |
| Idioma | identificadores en español sin tildes; textos en español | `CLAUDE.md` §7 |
| Instalación de dependencias | solo por Docker (`node:20-alpine`) | `CLAUDE.md` §1 |
| Watcher | `watchOptions: { poll: 1000, ignored: /node_modules/ }` | `docs/bitacora.md` |
| Ejecución suelta en `localhost:3002` | **no** hay aplicación usable: el remote solo publica `remoteEntry.js` | P-04 de la spec 07 |

Props que el remote **recibe** del shell, tal como quedaron congeladas el 23/08/2026:

```jsx
<AdminApp usuario={{ usuarioId, nombre, rol }} token="..." apiBaseUrl="/api" onLogout={fn} />
```

El shell ya declara `mfAdministracion@http://localhost:3002/remoteEntry.js` en su
`webpack.config.js` y ya restringe la opción "Administracion" del menú al rol `ADMIN`
(`MenuModulos.jsx`). Esta spec **no** toca el shell.

## 4. Historias de usuario y criterios de aceptación

### HU-01 — Ver el catálogo completo de canchas (PDF §3.3.4, RN-07)

Como administrador, necesito ver todas las canchas con su horario y su estado, para gestionarlas.

- **CUANDO** se abra la pantalla de gestión de canchas, **ENTONCES** el remote hará
  `GET /api/canchas` y mostrará cada cancha con `canchaId`, `nombre`, `deporte`, `horaApertura`,
  `horaCierre` y `activa`.
- **CUANDO** el usuario en sesión tenga `rol = ADMIN`, **ENTONCES** el listado incluirá también
  las canchas con `activa = false`: `GET /api/canchas` filtra por rol sin parámetro de consulta y
  al `ADMIN` le devuelve todas (contrato, D-05 de la spec 03).
- **CUANDO** el remote muestre el `deporte`, **ENTONCES** usará exactamente `PADEL`, `TENIS` y
  `BASQUET`, sin traducir ni abreviar (contrato).
- **CUANDO** el remote muestre las horas, **ENTONCES** usará `horaApertura` y `horaCierre` en
  formato `HH:mm` tal como llegan, sin recalcular ni reformatear.
- **CUANDO** el listado esté cargando, **ENTONCES** se mostrará un aviso de carga.
- **CUANDO** no haya ninguna cancha, **ENTONCES** se mostrará un aviso de listado vacío: la
  respuesta es `200` con arreglo vacío, nunca `404`.
- **CUANDO** el remote pinte el listado, **ENTONCES** **no** enviará ningún parámetro de filtrado
  ni de paginación: el contrato no declara ninguno.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-02 — Crear una cancha (PDF §3.3.4, RN-07)

Como administrador, necesito dar de alta una cancha con su deporte y su horario de atención.

- **CUANDO** el administrador pida crear una cancha, **ENTONCES** el formulario pedirá exactamente
  `nombre`, `deporte`, `horaApertura` y `horaCierre`, y **ningún** campo más.
- **CUANDO** el formulario se muestre, **ENTONCES** aparecerá **en la misma pantalla del
  listado**, que sigue visible: el administrador no pierde el contexto al dar de alta (P-10).
- **CUANDO** se arme el cuerpo, **ENTONCES** **no** incluirá `canchaId` ni `activa`: el
  identificador lo genera la base y el estado se cambia solo con
  `PATCH /api/canchas/{canchaId}/estado` (S-02 y S-03 de la spec 03).
- **CUANDO** el administrador confirme, **ENTONCES** el remote hará `POST /api/canchas` con el
  cuerpo exacto `{ "nombre": ..., "deporte": ..., "horaApertura": "HH:mm", "horaCierre": "HH:mm" }`.
- **CUANDO** el selector de `deporte` se pinte, **ENTONCES** ofrecerá únicamente los tres valores
  del contrato y no una entrada de texto libre.
- **CUANDO** la respuesta sea `201`, **ENTONCES** se mostrará el aviso de cancha creada y se
  refrescará el listado de HU-01 con la cancha nueva ya presente y `activa = true`.
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de confirmar quedará deshabilitado,
  para que un doble clic no intente dos altas.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido junto al formulario, sin borrar lo que el administrador escribió. Es el caso
  de una hora mal formada o de `horaCierre` que no es posterior a `horaApertura`.
- **SI** la respuesta es `409` con `codigo = NOMBRE_DUPLICADO`, **ENTONCES** se mostrará el
  `mensaje` recibido y el formulario quedará abierto para corregir el `nombre`.
- **SI** la respuesta es `403` con `codigo = SIN_PERMISO`, **ENTONCES** se mostrará el `mensaje`
  recibido. No debería ocurrir, porque el shell solo monta este remote para `ADMIN`.
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se permitirá reintentar sin recargar la página.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-03 — Editar una cancha (PDF §3.3.4, RN-07)

Como administrador, necesito corregir el nombre, el deporte o el horario de atención de una
cancha existente.

- **CUANDO** el administrador pida editar una cancha, **ENTONCES** el formulario llegará
  **precargado** con el `nombre`, el `deporte`, la `horaApertura` y la `horaCierre` actuales de
  esa cancha, tomados del listado ya cargado.
- **CUANDO** el formulario de edición se muestre, **ENTONCES** aparecerá **en la misma pantalla
  del listado**, que sigue visible, y el administrador podrá cancelar la edición sin cambiar de
  pantalla (P-10).
- **CUANDO** el administrador confirme, **ENTONCES** el remote hará
  `PUT /api/canchas/{canchaId}` con los **cuatro** campos, incluso los que no cambiaron: el `PUT`
  reemplaza los cuatro campos editables (D-11 de la spec 03).
- **CUANDO** se arme el cuerpo, **ENTONCES** **no** incluirá `activa`: el `PUT` no toca el estado
  y un cuerpo con ese campo no tendría dónde entrar (D-11 de la spec 03).
- **CUANDO** la respuesta sea `200`, **ENTONCES** se mostrará el aviso de cancha actualizada y se
  refrescará el listado.
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de confirmar quedará deshabilitado.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido junto al formulario, sin borrar lo editado.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se refrescará el listado: la cancha fue borrada o el `canchaId` ya no existe.
- **SI** la respuesta es `409` con `codigo = NOMBRE_DUPLICADO`, **ENTONCES** se mostrará el
  `mensaje` recibido y el formulario quedará abierto.
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se permitirá reintentar.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-04 — Activar e inactivar una cancha (PDF §3.3.4, RN-07)

Como administrador, necesito inactivar una cancha para que deje de ofrecerse, y volver a
activarla.

- **CUANDO** una cancha tenga `activa = true`, **ENTONCES** se ofrecerá la acción de **inactivar**;
  **CUANDO** tenga `activa = false`, **ENTONCES** se ofrecerá la de **activar**.
- **CUANDO** el administrador ejecute la acción, **ENTONCES** el remote hará
  `PATCH /api/canchas/{canchaId}/estado` con el cuerpo exacto `{ "activa": true }` o
  `{ "activa": false }`, siempre con el campo presente: un cuerpo sin `activa` responde `400`
  (`CambioEstadoCanchaRequest` usa `Boolean` con `@NotNull`).
- **CUANDO** la respuesta sea `200`, **ENTONCES** se refrescará el listado con el nuevo valor de
  `activa`.
- **CUANDO** se inactive una cancha, **ENTONCES** el remote **no** cancelará ninguna reserva ni
  llamará a ninguna ruta de `/api/reservas`: inactivar y cancelar son dos operaciones distintas y
  el contrato no declara ningún efecto en cascada.
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de esa fila quedará deshabilitado.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se refrescará el listado.
- **CUANDO** el administrador ejecute la acción, **ENTONCES** **no** habrá paso de confirmación:
  el cambio es reversible con un clic y el listado muestra el nuevo estado al instante. La
  confirmación se reserva para lo irreversible —eliminar un bloqueo (HU-07) y cancelar una
  reserva (HU-08)— (P-02).
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-05 — Ver los bloqueos de mantenimiento de una cancha (PDF §3.1, RN-07)

Como administrador, necesito ver las franjas bloqueadas por mantenimiento de una cancha.

- **CUANDO** el administrador seleccione una cancha del listado de HU-01, **ENTONCES** los
  bloqueos de esa cancha se mostrarán **dentro de la misma pantalla de Canchas**, sin un selector
  de cancha propio ni una pantalla hermana: la cancha ya está seleccionada en el listado (P-01).
- **CUANDO** el administrador abra los bloqueos de una cancha, **ENTONCES** el remote hará
  `GET /api/canchas/{canchaId}/bloqueos` y mostrará cada bloqueo con `bloqueoId`, `fecha`,
  `horaInicio`, `horaFin` y `motivo`.
- **CUANDO** el remote llame a esa ruta, **ENTONCES** **no** enviará el parámetro `fecha`: es
  opcional en el contrato y esta pantalla muestra **siempre todos** los bloqueos de la cancha. El
  parámetro existe para que `ms-reservas` calcule la disponibilidad de un día, no para esta
  pantalla, y con los volúmenes del proyecto un filtro no aporta (P-03).
- **CUANDO** se muestre el listado de bloqueos, **ENTONCES** **no** habrá filtro por `fecha` ni
  por ningún otro campo (P-03).
- **CUANDO** la cancha no tenga bloqueos, **ENTONCES** se mostrará un aviso de listado vacío: la
  respuesta es `200` con arreglo vacío.
- **CUANDO** una cancha tenga `activa = false`, **ENTONCES** sus bloqueos se consultan igual: el
  listado no filtra por estado de la cancha (D-12 y D-16 de la spec 03).
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido. No debería ocurrir, porque el remote no envía `fecha` (P-03), pero el
  contrato declara el código y se maneja igual.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido: la cancha no existe.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-06 — Registrar un bloqueo de mantenimiento (PDF §3.1, RN-07)

Como administrador, necesito bloquear una franja de una cancha por mantenimiento, para que nadie
la reserve.

- **CUANDO** el administrador registre un bloqueo, **ENTONCES** el formulario pedirá exactamente
  `fecha`, `horaInicio`, `horaFin` y `motivo`, y **ningún** campo más.
- **CUANDO** el formulario se muestre, **ENTONCES** aparecerá junto al listado de bloqueos de la
  cancha seleccionada, dentro de la pantalla de Canchas, y la cancha destino será esa: no habrá
  un selector de cancha en el formulario (P-01, P-10).
- **CUANDO** se arme el cuerpo, **ENTONCES** **no** incluirá `canchaId` —viaja en la ruta— ni
  `bloqueoId` —lo genera la base—.
- **CUANDO** el administrador confirme, **ENTONCES** el remote hará
  `POST /api/canchas/{canchaId}/bloqueos` con el cuerpo exacto
  `{ "fecha": "AAAA-MM-DD", "horaInicio": "HH:mm", "horaFin": "HH:mm", "motivo": "..." }`.
- **CUANDO** la respuesta sea `201`, **ENTONCES** se mostrará el aviso de bloqueo registrado y se
  refrescará el listado de HU-05.
- **CUANDO** el bloqueo quede registrado, **ENTONCES** el remote **no** recalculará ninguna
  disponibilidad ni marcará bloques ocupados por su cuenta: la disponibilidad la resuelve
  `ms-reservas` (HU-01 de la spec 04).
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de confirmar quedará deshabilitado.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido junto al formulario. Es el caso de una fecha inexistente como `2026-02-31`
  (D-04 de la spec 03) o de una franja mal formada (D-10 de la spec 03).
- **SI** la respuesta es `409` con `codigo = BLOQUEO_DUPLICADO`, **ENTONCES** se mostrará el
  `mensaje` recibido: la franja ya está bloqueada, sea por duplicado exacto o por solapamiento
  parcial, que comparten código (D-09 de la spec 03).
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido: la cancha no existe.
- **SI** la respuesta es `500` con `codigo = ERROR_INTERNO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se permitirá reintentar; el bloqueo no se registró.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-07 — Eliminar un bloqueo de mantenimiento (PDF §3.1, RN-07)

Como administrador, necesito quitar un bloqueo cuando el mantenimiento terminó o se registró por
error.

- **CUANDO** el administrador pida eliminar un bloqueo, **ENTONCES** el remote pedirá una
  **confirmación explícita** antes de llamar a la API, indicando la `fecha`, la franja y el
  `motivo` del bloqueo: la eliminación es irreversible.
- **CUANDO** el administrador rechace la confirmación, **ENTONCES** no se hará ninguna llamada y
  el listado quedará igual.
- **CUANDO** el administrador confirme, **ENTONCES** el remote hará
  `DELETE /api/canchas/{canchaId}/bloqueos/{id}` con el `bloqueoId` recibido y **sin cuerpo**.
- **CUANDO** la respuesta sea `204`, **ENTONCES** se refrescará el listado y el bloqueo ya no
  aparecerá. La respuesta `204` **no trae cuerpo**: el remote no intentará leer un JSON de ella.
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de esa fila quedará deshabilitado.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se refrescará el listado: el bloqueo ya no existe.
- **SI** la respuesta es `403` con `codigo = SIN_PERMISO`, **ENTONCES** se mostrará el `mensaje`
  recibido.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-08 — Listado global de reservas y cancelación de cualquiera (PDF §3.2, §3.3.3, RN-03, RN-04, RN-05)

Como administrador, necesito ver todas las reservas del sistema y poder cancelar cualquiera, por
ejemplo ante un mantenimiento imprevisto.

- **CUANDO** se abra la pantalla de gestión de reservas, **ENTONCES** el remote hará
  `GET /api/reservas` y mostrará **todas** las reservas del sistema, en todos los estados, cada
  una con `id`, `usuarioId`, `canchaId`, `fecha`, `horaInicio`, `horaFin` y `estado`.
- **CUANDO** el listado llegue, **ENTONCES** se mostrará en el orden recibido: `fecha`
  descendente y, dentro de la misma fecha, `horaInicio` descendente (D-09 de la spec 04). El
  remote **no** reordena.
- **CUANDO** el remote consuma esta ruta, **ENTONCES** **no** enviará ningún parámetro de
  filtrado ni de paginación: el contrato no declara ninguno.
- **CUANDO** se muestre el listado, **ENTONCES** ofrecerá un filtro por `estado` con las opciones
  `CONFIRMADA`, `CANCELADA`, `FINALIZADA` y **"Todos"**, que es el valor **inicial**. El filtrado
  es **en el navegador**, sobre lo ya recibido: `GET /api/reservas` no acepta parámetros y no se
  le inventa uno (P-04).
- **CUANDO** se muestre el listado, **ENTONCES** **no** habrá filtro por cancha, por usuario ni
  por fecha: el listado ya muestra el nombre de la cancha y el volumen no lo justifica (P-04).
- **CUANDO** se muestren los estados, **ENTONCES** serán exactamente `CONFIRMADA`, `CANCELADA` y
  `FINALIZADA`, sin traducir ni abreviar (contrato).
- **CUANDO** una reserva llegue con `estado = FINALIZADA`, **ENTONCES** se mostrará así tal cual:
  es un estado derivado que calcula `ms-reservas` al leer (D-02 de la spec 04) y el remote **no**
  lo recalcula comparando fechas.
- **CUANDO** el remote muestre la cancha de una reserva, **ENTONCES** resolverá su `nombre` y su
  `deporte` con el catálogo de `GET /api/canchas`, pedido **una sola vez** por pantalla: la
  respuesta de `/api/reservas` trae solo `canchaId` (mismo criterio de HU-04 de la spec 07).
- **SI** un `canchaId` no aparece en el catálogo recibido, **ENTONCES** se mostrará el `canchaId`
  tal cual, sin inventar un nombre.
- **CUANDO** el remote muestre el usuario de una reserva, **ENTONCES** resolverá su `nombre` con
  el listado de `GET /api/usuarios`, pedido **una sola vez** por pantalla y nunca una llamada por
  fila: `GET /api/reservas` trae solo `usuarioId`, y el `ADMIN` necesita saber a quién le está
  cancelando la reserva (P-05).
- **SI** un `usuarioId` no aparece en ese listado, **ENTONCES** se mostrará el `usuarioId` tal
  cual, sin inventar un nombre (P-05).
- **SI** la llamada a `GET /api/usuarios` falla, **ENTONCES** el listado de reservas se muestra
  igual, con el `usuarioId` en lugar del nombre y el aviso del error: un fallo al resolver
  nombres no oculta las reservas ni impide cancelarlas.
- **CUANDO** una reserva tenga `estado = CONFIRMADA`, **ENTONCES** se ofrecerá la acción de
  cancelar; **CUANDO** tenga `estado = CANCELADA` o `FINALIZADA`, **ENTONCES** **no** se ofrecerá
  (RN-04, precedencia C-02 de la spec 04).
- **CUANDO** el administrador pulse cancelar, **ENTONCES** el remote pedirá una **confirmación
  explícita** antes de llamar a la API, indicando la cancha, la `fecha` y el bloque de la reserva
  (mismo criterio de P-06 de la spec 07).
- **CUANDO** el administrador confirme, **ENTONCES** el remote hará
  `PATCH /api/reservas/{id}/cancelacion` con el `id` de la reserva y **sin cuerpo**: el contrato
  no declara ningún campo de entrada.
- **CUANDO** la respuesta sea `200`, **ENTONCES** se mostrará el aviso de cancelación y se
  refrescará el listado, de modo que la reserva pase a `CANCELADA` (RN-05: el bloque queda libre).
- **CUANDO** el administrador cancele la reserva de otro usuario, **ENTONCES** la operación es
  válida y no responde `403`: RN-03 se lo permite y `ms-reservas` ya lo implementa.
- **CUANDO** se evalúe si una reserva es cancelable, **ENTONCES** el remote se guiará **solo** por
  el `estado` recibido; la validación real de RN-03 y RN-04 la aplica `ms-reservas`, y ocultar un
  botón no es control de acceso.
- **CUANDO** se cancele una reserva, **ENTONCES** el remote **no** enviará ninguna notificación al
  usuario afectado: no hay notificaciones (`CLAUDE.md` §2, PDF §3.5).
- **CUANDO** no haya reservas, **ENTONCES** se mostrará un aviso de listado vacío.
- **SI** la respuesta es `409` con `codigo = RESERVA_PASADA`, **ENTONCES** se mostrará el
  `mensaje` recibido y se refrescará el listado (RN-04).
- **SI** la respuesta es `409` con `codigo = RESERVA_NO_CANCELABLE`, **ENTONCES** se mostrará el
  `mensaje` recibido y se refrescará el listado.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se refrescará el listado.
- **SI** la respuesta es `403` con `codigo = SIN_PERMISO`, **ENTONCES** se mostrará el `mensaje`
  recibido.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-09 — Gestión de usuarios: activar e inactivar (PDF §3.1)

Como administrador, necesito ver los usuarios registrados y activarlos o inactivarlos.

- **CUANDO** se abra la pantalla de usuarios, **ENTONCES** el remote hará `GET /api/usuarios` y
  mostrará cada usuario con `usuarioId`, `nombre`, `email`, `rol` y `activo`.
- **CUANDO** el remote muestre un usuario, **ENTONCES** **no** mostrará ni pedirá `password` en
  ninguna pantalla: ese campo solo existe en las peticiones de registro e inicio de sesión, que
  son del shell (contrato).
- **CUANDO** el remote muestre el `rol`, **ENTONCES** usará exactamente `ADMIN` y `USUARIO`.
  `SERVICIO` nunca aparece en una respuesta de la API (contrato) y el remote no lo contempla.
- **CUANDO** un usuario tenga `activo = true`, **ENTONCES** se ofrecerá la acción de
  **inactivar**; **CUANDO** tenga `activo = false`, **ENTONCES** la de **activar**.
- **CUANDO** el administrador ejecute la acción, **ENTONCES** el remote hará
  `PATCH /api/usuarios/{usuarioId}/estado` con el cuerpo exacto `{ "activo": true }` o
  `{ "activo": false }`, siempre con el campo presente: un cuerpo sin `activo` responde `400`.
- **CUANDO** la respuesta sea `200`, **ENTONCES** se refrescará el listado con el nuevo valor de
  `activo`.
- **CUANDO** el remote gestione usuarios, **ENTONCES** **no** creará usuarios, **no** editará
  `nombre`, `email` ni `rol` y **no** eliminará ninguno: el contrato solo declara `GET` y el
  `PATCH` de estado. El alta la hace el registro público del shell.
- **CUANDO** la fila sea la del **propio administrador en sesión** —su `usuarioId` coincide con el
  de la prop `usuario`—, **ENTONCES** la acción de inactivar **se ofrece igual**, pero con una
  **confirmación explícita** que advierta que se está inactivando a sí mismo (P-06).
- **CUANDO** el administrador rechace esa confirmación, **ENTONCES** no se hará ninguna llamada y
  el listado quedará igual.
- **CUANDO** el remote decida si ofrecer la acción, **ENTONCES** **no** ocultará la fila propia ni
  deshabilitará su botón: el contrato permite la operación y ocultarla sería inventar una regla
  que ningún microservicio aplica (P-06).
- **CUANDO** la petición esté en curso, **ENTONCES** el botón de esa fila quedará deshabilitado.
- **SI** la respuesta es `400` con `codigo = DATOS_INVALIDOS`, **ENTONCES** se mostrará el
  `mensaje` recibido.
- **SI** la respuesta es `404` con `codigo = NO_ENCONTRADO`, **ENTONCES** se mostrará el `mensaje`
  recibido y se refrescará el listado.
- **SI** la respuesta es `403` con `codigo = SIN_PERMISO`, **ENTONCES** se mostrará el `mensaje`
  recibido.
- **SI** la respuesta es `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** se aplica HU-10.

### HU-10 — Props recibidas del shell y sesión ajena

Como equipo, necesito que el remote use exactamente las cuatro props del contrato y no invente su
propia sesión.

- **CUANDO** el shell monte `./AdminApp`, **ENTONCES** el remote leerá exactamente `usuario`
  (`usuarioId`, `nombre`, `rol`), `token`, `apiBaseUrl` y `onLogout`, y ninguna prop más.
- **CUANDO** el remote llame a la API, **ENTONCES** compondrá la URL con el `apiBaseUrl` recibido
  (valor literal `"/api"`), nunca con una URL absoluta ni con un nombre de contenedor.
- **CUANDO** el remote llame a la API, **ENTONCES** enviará `Authorization: Bearer <token>` con el
  `token` de la prop, que llega sin el prefijo `Bearer ` (HU-07 de la spec 06).
- **CUANDO** el remote necesite la sesión, **ENTONCES** **no** leerá `sessionStorage` ni
  `localStorage`: el dueño de la sesión es el shell (D-12 de la spec 06).
- **CUANDO** cualquier llamada responda `401` con `codigo = NO_AUTENTICADO`, **ENTONCES** el
  remote invocará `onLogout()` y no pintará su propia pantalla de inicio de sesión (P-08 de la
  spec 06).
- **CUANDO** el `token` de la prop cambie, **ENTONCES** las llamadas siguientes usarán el valor
  nuevo: el remote no guarda una copia aparte.
- **CUANDO** el remote se pinte, **ENTONCES** **no** dibujará cabecera, menú de módulos ni botón
  de cierre de sesión: el layout es del shell (spec 06, E-07 y E-08).
- **CUANDO** el remote reciba `usuario.rol` distinto de `ADMIN`, **ENTONCES** pintará un aviso
  propio de módulo no disponible y **no llamará a ninguna ruta**: sin esa comprobación, todas las
  llamadas responderían `403` y el administrador vería una pantalla llena de errores en lugar de
  un mensaje claro (P-07).
- **CUANDO** esa comprobación se implemente, **ENTONCES** quedará escrita como **comportamiento
  defensivo, no como control de acceso**: el control real es el token que cada microservicio
  valida, y el shell ya restringe el módulo al `ADMIN` (HU-05 de la spec 06).

### HU-11 — Integración como remote de Module Federation (PDF §4.1, rúbrica §6)

Como equipo, necesito que este microfrontend se pueda desarrollar y desplegar por separado y que
el shell lo cargue en tiempo de ejecución.

- **CUANDO** se configure `ModuleFederationPlugin`, **ENTONCES** declarará
  `name: "mfAdministracion"` y `exposes` con la clave exacta `"./AdminApp"`: los dos nombres del
  contrato congelado.
- **CUANDO** se declare `shared`, **ENTONCES** `react` y `react-dom` irán con `singleton: true` y
  con las **mismas versiones** que el shell (`18.3.1`).
- **CUANDO** el remote se sirva, **ENTONCES** publicará `http://localhost:3002/remoteEntry.js`,
  que es la URL que el shell ya declara en su `webpack.config.js`.
- **CUANDO** alguien abra `http://localhost:3002` en el navegador, **ENTONCES** **no** verá una
  aplicación usable, y eso **es lo correcto, no un defecto** (P-04 de la spec 07).
- **CUANDO** el `ADMIN` entre al módulo Administración con el remote levantado, **ENTONCES** se
  descargará `remoteEntry.js` y se montará la pantalla del remote **en lugar** del mensaje de
  módulo no disponible del borde de error del shell.
- **CUANDO** el remote se monte, **ENTONCES** la consola del navegador no mostrará errores de
  React duplicado ni de `hooks` inválidos.
- **CUANDO** el remote esté montado, **ENTONCES** `mf-reservas` seguirá funcionando igual: los dos
  remotes conviven en el mismo shell y comparten la misma instancia de React.
- **CUANDO** se defina el arranque, **ENTONCES** `src/index.js` solo hará `import("./bootstrap")`
  (`CLAUDE.md` §3), y el módulo expuesto será un componente de React que recibe las props, no un
  `ReactDOM.render`.

### HU-12 — El remote corre en el entorno local (PDF §4.4)

Como equipo, necesito levantar el remote junto al resto del sistema con Docker Compose.

- **CUANDO** se instalen las dependencias, **ENTONCES** será con
  `docker run --rm -v "${PWD}:/app" -w /app node:20-alpine npm install`, nunca con `npm` en el
  host (`CLAUDE.md` §1).
- **CUANDO** se levante el entorno, **ENTONCES** el remote correrá como servicio
  `mf-administracion` de `docker-compose.yml`, con el **mismo patrón de los servicios `shell` y
  `mf-reservas`**: imagen `node:20-alpine`,
  `command: sh -c "npm install && npx webpack serve --mode development"`, volumen del código,
  volumen anónimo `mf_administracion_node_modules` para `node_modules` y `ports: "3002:3002"`.
- **CUANDO** se configure el `devServer`, **ENTONCES** llevará `host: "0.0.0.0"` y
  `allowedHosts: "all"`, o el navegador del host no alcanzará el servidor dentro del contenedor.
- **CUANDO** se configure el watcher, **ENTONCES** llevará
  `watchOptions: { poll: 1000, ignored: /node_modules/ }` (bitácora, T3 de la spec 06).
- **CUANDO** se declare el `proxy` del `devServer`, **ENTONCES** apuntará a **nombres de
  contenedor** (`http://ms-usuarios:8080`, `http://ms-canchas:8080`, `http://ms-reservas:8080`),
  al contrario de la URL de navegador del `remoteEntry.js` (P-02 de la spec 06).
- **CUANDO** el shell monte este remote, **ENTONCES** sus rutas relativas `/api/...` las proxya el
  `devServer` del **shell**, porque el código corre en el origen `http://localhost:3000`.
- **CUANDO** se agregue el servicio al `docker-compose.yml`, **ENTONCES** ese será el **único**
  archivo modificado fuera de `frontend/mf-administracion`.
- **CUANDO** se declare el `depends_on` del servicio, **ENTONCES** serán **`ms-usuarios`,
  `ms-canchas` y `ms-reservas`** con `condition: service_started`, los tres microservicios que
  este remote consume, y **no** `ms-reportes` (P-08).
- **CUANDO** el shell se levante, **ENTONCES** **no** declarará `depends_on` de este remote: el
  `remoteEntry.js` lo descarga el navegador al entrar al módulo (P-08 de la spec 07).
- **CUANDO** se verifique una tarea, **ENTONCES** la **primera** comprobación será
  `curl.exe http://localhost:3002/remoteEntry.js`: si no responde `200`, el problema está en el
  remote y no en la integración.
- **CUANDO** se verifique una tarea, **ENTONCES** también se comprobará que
  `curl.exe http://localhost:3001/remoteEntry.js` sigue respondiendo `200` y que el módulo
  Reservas sigue montando: los dos remotes conviven y el segundo no puede romper al primero.
- **CUANDO** la tarea tenga interacción, **ENTONCES** hará falta además el recorrido por
  navegador con un usuario `ADMIN`: iniciar sesión en `http://localhost:3000`, entrar a
  Administración y ejercitar la pantalla de la tarea. Un `compiled successfully` no prueba que la
  aplicación funcione (bitácora, T5 de la spec 06).

### HU-13 — Errores uniformes y sin datos inventados

Como equipo, necesito que el remote muestre siempre el error que devolvió el microservicio.

- **CUANDO** una llamada falle con un cuerpo de error del contrato, **ENTONCES** el remote
  mostrará el `mensaje` recibido, sin reescribirlo ni traducirlo.
- **CUANDO** el remote decida qué hacer ante un error, **ENTONCES** lo hará por el `codigo`, no
  por el texto del `mensaje`.
- **CUANDO** el remote muestre cualquier dato de una cancha, un bloqueo, una reserva o un usuario,
  **ENTONCES** usará los campos exactos del contrato y **no** calculará ni completará ninguno que
  la API no haya devuelto.
- **CUANDO** un error se muestre, **ENTONCES** la pantalla no quedará en blanco y la navegación
  interna seguirá funcionando.
- **SI** la respuesta no trae `codigo` ni `mensaje` (por ejemplo, la petición no llegó al
  microservicio), **ENTONCES** el remote mostrará un aviso propio de fallo de comunicación y
  permitirá reintentar, sin pintar un stacktrace ni el objeto de error crudo.

### HU-14 — Navegación interna del módulo (P-01, P-10)

Como administrador, necesito moverme entre las tres pantallas del módulo sin salir de él.

- **CUANDO** el remote se monte, **ENTONCES** pintará un **menú interno propio** con exactamente
  tres opciones: **Canchas**, **Reservas** y **Usuarios** (P-01).
- **CUANDO** ese menú se pinte, **ENTONCES** **no** duplicará el menú de módulos del shell ni
  ofrecerá Reservas de usuario final, Reportes, Inicio ni cierre de sesión: eso es del shell
  (spec 06, E-07 y E-08).
- **CUANDO** el remote se monte, **ENTONCES** la pantalla inicial será **Canchas**: es la que el
  PDF §3.2 nombra primero y la única que el criterio de aceptación §7.2 exige.
- **CUANDO** el administrador cambie de pantalla, **ENTONCES** la pantalla activa será estado de
  React, **sin** enrutador y sin cambiar la URL del navegador (P-05 de la spec 06).
- **CUANDO** el administrador vuelva a una pantalla ya visitada, **ENTONCES** sus datos se piden
  de nuevo a la API: el remote no cachea listados entre cambios de pantalla, para que un cambio
  hecho en otra pantalla no se muestre desactualizado.
- **CUANDO** haya un formulario abierto y el administrador cambie de pantalla, **ENTONCES** el
  formulario se descarta sin llamar a la API: no se guarda nada a medias.
- **CUANDO** el administrador esté en la pantalla de Canchas, **ENTONCES** los bloqueos de la
  cancha seleccionada se muestran dentro de esa misma pantalla y volver al listado no cambia de
  pantalla (P-01, HU-05).

## 5. Reglas de negocio cubiertas

| ID | Regla | Cobertura en esta spec |
|---|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora | **No aplica** — este remote no crea reservas; solo las lista y las cancela (HU-08) |
| RN-02 | No se puede reservar un bloque ya ocupado | **No aplica** — no hay creación de reservas aquí |
| RN-03 | El usuario cancela solo sus reservas; el admin cancela cualquiera | **Cubierta en su mitad de administrador** — el listado global ofrece cancelar cualquier reserva `CONFIRMADA` (HU-08). La mitad de usuario vive en `mf-reservas` (spec 07). La valida `ms-reservas` |
| RN-04 | Solo se cancela una reserva que aún no ha ocurrido | **Presentada** — no se ofrece cancelar una reserva `FINALIZADA` ni `CANCELADA`, y el `409 RESERVA_PASADA` se muestra (HU-08). La valida `ms-reservas` |
| RN-05 | Cancelar libera el bloque | **Presentada** — tras cancelar se refresca el listado y la reserva pasa a `CANCELADA`; la liberación del bloque se observa en la disponibilidad de `mf-reservas` (HU-08) |
| RN-06 | Límite configurable de reservas activas | **No aplica** — el límite se evalúa al crear una reserva, y aquí no se crea ninguna |
| RN-07 | Solo el admin crea, edita o inactiva canchas y define su horario de atención | **Cubierta como pantalla** — es el corazón de este remote: alta, edición, cambio de estado y bloqueos de mantenimiento (HU-01 a HU-07). El permiso real lo aplica `ms-canchas` con el token; que el shell oculte el módulo a un `USUARIO` no es control de acceso |
| RN-08 | Estados `CONFIRMADA`, `CANCELADA`, `FINALIZADA` | **Presentada** — los tres se muestran con su nombre exacto en el listado global; `FINALIZADA` llega ya calculada por `ms-reservas` (D-02 de la spec 04) y el remote no la recalcula (HU-08) |

Ninguna regla de negocio se **implementa** en el frontend: todas las validaciones viven en
`ms-canchas`, `ms-reservas` y `ms-usuarios`. Lo que hace este remote es presentar su resultado y
no ofrecer acciones que la regla ya prohíbe.

## 6. Contrato REST consumido

Nombres tomados literalmente de `docs/contratos/README.md`.

### 6.1 Rutas

| Verbo | Ruta | Rol | Respuestas | Historia |
|---|---|---|---|---|
| GET | `/api/canchas` | ADMIN, USUARIO | 200, 401 | HU-01, HU-08 |
| POST | `/api/canchas` | ADMIN | 201, 400, 401, 403, 409 | HU-02 |
| PUT | `/api/canchas/{canchaId}` | ADMIN | 200, 400, 401, 403, 404, 409 | HU-03 |
| PATCH | `/api/canchas/{canchaId}/estado` | ADMIN | 200, 400, 401, 403, 404 | HU-04 |
| GET | `/api/canchas/{canchaId}/bloqueos?fecha` | ADMIN, USUARIO | 200, 400, 401, 404 | HU-05 |
| POST | `/api/canchas/{canchaId}/bloqueos` | ADMIN | 201, 400, 401, 403, 404, 409 | HU-06 |
| DELETE | `/api/canchas/{canchaId}/bloqueos/{id}` | ADMIN | 204, 401, 403, 404 | HU-07 |
| GET | `/api/reservas` | ADMIN | 200, 401, 403 | HU-08 |
| PATCH | `/api/reservas/{id}/cancelacion` | ADMIN, USUARIO | 200, 401, 403, 404, 409 | HU-08 |
| GET | `/api/usuarios` | ADMIN | 200, 401, 403 | HU-09, y HU-08 para resolver el `nombre` del usuario de cada reserva (P-05) |
| PATCH | `/api/usuarios/{usuarioId}/estado` | ADMIN | 200, 400, 401, 403, 404 | HU-09 |

El remote **no** consume `GET /api/canchas/{canchaId}` (el listado ya trae todos los campos),
ni `GET /api/reservas/disponibilidad`, ni `POST /api/reservas`, ni `GET /api/reservas/mias`
(son de `mf-reservas`), ni `POST /api/usuarios/sesiones`, ni `POST /api/usuarios` (son del
shell), ni ninguna ruta de `/api/reportes` (son de `mf-reportes`, spec 09).

### 6.2 Campos

| Concepto | Campo | Tipo / valores | Uso en el remote |
|---|---|---|---|
| Identificador de cancha | `canchaId` | number | clave del listado, ruta de edición, estado y bloqueos, y enlace con las reservas |
| Nombre de cancha | `nombre` | string | columna del listado, campo del formulario y nombre de la cancha en el listado global de reservas |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` | columna del listado y selector del formulario |
| Hora de apertura de la cancha | `horaApertura` | string `HH:mm` | columna del listado y campo del formulario |
| Hora de cierre de la cancha | `horaCierre` | string `HH:mm` | columna del listado y campo del formulario |
| Cancha activa | `activa` | boolean | columna del listado, condición de la acción y cuerpo del `PATCH` de estado |
| Identificador de bloqueo | `bloqueoId` | number | clave del listado de bloqueos y ruta del `DELETE` |
| Motivo del bloqueo | `motivo` | string | columna del listado de bloqueos y campo del formulario |
| Identificador de reserva | `id` | number | clave del listado global y ruta de `PATCH /api/reservas/{id}/cancelacion` |
| Estado de la reserva | `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` | columna del listado y condición para ofrecer cancelar |
| Fecha | `fecha` | string `AAAA-MM-DD` | columna y campo del bloqueo, columna de la reserva y parámetro opcional del listado de bloqueos |
| Hora de inicio | `horaInicio` | string `HH:mm` | campo del bloqueo y columna de la reserva |
| Hora de fin | `horaFin` | string `HH:mm` | campo del bloqueo y columna de la reserva |
| Identificador de usuario | `usuarioId` | number | clave del listado de usuarios, ruta del `PATCH` de estado, enlace de cada reserva con su usuario y comparación con el de la prop `usuario` (P-06) |
| Nombre de usuario | `nombre` | string | columna del listado de usuarios y nombre del usuario en el listado global de reservas (P-05); llega también en la prop `usuario` |
| Correo de acceso | `email` | string | columna del listado de usuarios |
| Contraseña | `password` | string | **no se usa en ninguna pantalla de este remote** |
| Rol de usuario | `rol` | `ADMIN` \| `USUARIO` | columna del listado de usuarios; llega también en la prop `usuario` |
| Usuario activo | `activo` | boolean | columna del listado, condición de la acción y cuerpo del `PATCH` de estado |
| Token de sesión | `token` | string | llega por prop; va en `Authorization: Bearer <token>` |
| Código de error | `codigo` | ver §6.4 | selecciona la reacción del remote |
| Mensaje de error | `mensaje` | string | se muestra tal cual |

### 6.3 Payloads consumidos

Cancha devuelta por `GET /api/canchas`, `POST /api/canchas`, `PUT /api/canchas/{canchaId}` y
`PATCH /api/canchas/{canchaId}/estado`:

```json
{ "canchaId": 1, "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00", "activa": true }
```

Cuerpo de `POST /api/canchas` y de `PUT /api/canchas/{canchaId}`:

```json
{ "nombre": "Padel 1", "deporte": "PADEL", "horaApertura": "07:00", "horaCierre": "22:00" }
```

Cuerpo de `PATCH /api/canchas/{canchaId}/estado`:

```json
{ "activa": false }
```

Bloqueo devuelto por `GET` y `POST` de `/api/canchas/{canchaId}/bloqueos`:

```json
{ "bloqueoId": 3, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "10:00", "horaFin": "12:00", "motivo": "Mantenimiento de piso" }
```

Cuerpo de `POST /api/canchas/{canchaId}/bloqueos`:

```json
{ "fecha": "2026-08-24", "horaInicio": "10:00", "horaFin": "12:00", "motivo": "Mantenimiento de piso" }
```

Reserva devuelta por `GET /api/reservas` y `PATCH /api/reservas/{id}/cancelacion`:

```json
{ "id": 7, "usuarioId": 2, "canchaId": 1, "fecha": "2026-08-24", "horaInicio": "09:00", "horaFin": "10:00", "estado": "CONFIRMADA" }
```

Usuario devuelto por `GET /api/usuarios` y `PATCH /api/usuarios/{usuarioId}/estado`
(`UsuarioResponse`, sin `password`):

```json
{ "usuarioId": 1, "nombre": "Ana", "email": "ana@demo.ec", "rol": "USUARIO", "activo": true }
```

Cuerpo de `PATCH /api/usuarios/{usuarioId}/estado`:

```json
{ "activo": false }
```

### 6.4 Códigos de error que el remote interpreta

| Situación | HTTP | `codigo` | Qué hace el remote |
|---|---|---|---|
| Validación de entrada, hora u orden de franja inválidos, fecha inexistente | 400 | `DATOS_INVALIDOS` | muestra `mensaje` junto al formulario |
| Token vencido o inválido | 401 | `NO_AUTENTICADO` | invoca `onLogout()` (HU-10) |
| Sin permiso | 403 | `SIN_PERMISO` | muestra `mensaje` |
| Cancha, bloqueo, reserva o usuario inexistente | 404 | `NO_ENCONTRADO` | muestra `mensaje` y refresca la vista |
| Nombre de cancha ya registrado | 409 | `NOMBRE_DUPLICADO` | muestra `mensaje` y deja el formulario abierto |
| Franja ya bloqueada, exacta o solapada (D-09 de la spec 03) | 409 | `BLOQUEO_DUPLICADO` | muestra `mensaje` y deja el formulario abierto |
| Reserva ya ocurrida (RN-04) | 409 | `RESERVA_PASADA` | muestra `mensaje` y refresca el listado |
| Reserva que ya no está `CONFIRMADA` | 409 | `RESERVA_NO_CANCELABLE` | muestra `mensaje` y refresca el listado |
| Error no previsto en el servidor | 500 | `ERROR_INTERNO` | muestra `mensaje` y deja reintentar |

`EMAIL_DUPLICADO`, `BLOQUE_OCUPADO` y `LIMITE_RESERVAS` **no** aparecen en este remote: el
primero es del registro (shell) y los otros dos, de la creación de reservas (`mf-reservas`).

### 6.5 Contrato Module Federation

| Microfrontend | Nombre | Módulo expuesto | Puerto |
|---|---|---|---|
| shell | `shell` (host) | — | 3000 |
| mf-reservas | `mfReservas` | `./ReservasApp` | 3001 |
| **mf-administracion** | **`mfAdministracion`** | **`./AdminApp`** | **3002** |
| mf-reportes | `mfReportes` | `./ReportesApp` | 3003 |

## 7. Dependencias de esta spec

| Depende de | Estado | Para qué |
|---|---|---|
| `ms-canchas` (spec 03) | cerrada y levantada | catálogo, alta, edición, estado y las tres rutas de bloqueos |
| `ms-reservas` (spec 04) | cerrada y levantada | listado global y cancelación de cualquier reserva |
| `ms-usuarios` (spec 02) | cerrada y levantada | listado de usuarios y cambio de su estado; emite el `token` |
| `frontend/shell` (spec 06) | cerrada y levantada | declara `mfAdministracion@http://localhost:3002/remoteEntry.js`, restringe el módulo al `ADMIN` y entrega las cuatro props |
| `frontend/mf-reservas` (spec 07) | cerrada y levantada | patrón de `package.json`, `webpack.config.js`, capa `src/api/` y servicio de Compose que esta spec repite |
| `mf-reportes` | **no existe** | no se toca aquí: es la spec 09 |

## 8. Criterios de aceptación del PDF que esta spec cierra

| Criterio del PDF §7 | Aporte de esta spec |
|---|---|
| 2. "El administrador puede gestionar el catálogo de canchas y cancelar cualquier reserva del sistema" | Se cierra por completo: HU-01 a HU-04 (catálogo) y HU-08 (cancelación de cualquier reserva) |
| 5. "Al menos un shell y dos microfrontends remotos integrados mediante Module Federation" | Se cierra: con `mf-reservas` (spec 07) y este remote son dos remotes integrados |

## 9. Fuera de alcance de esta spec

- **`mf-reportes`**: sus pantallas, su `webpack.config.js` y su servicio de Compose. Es la spec 09.
- **Las tres rutas de `/api/reportes`** y cualquier indicador de ocupación, reservas por período o
  cancelaciones: son de `mf-reportes`.
- **Las pantallas de usuario final** (disponibilidad, nueva reserva, mis reservas): ya existen en
  `mf-reservas` y no se duplican aquí.
- **Crear reservas desde la administración**: el PDF §3.1 dice explícitamente que "Crear una
  reserva" es del usuario final, y §3.2 solo asigna a Administración el listado global con opción
  de cancelar.
- **Crear, editar o eliminar usuarios, y cambiar su `rol` o su `password`**: el contrato solo
  declara `GET /api/usuarios` y `PATCH /api/usuarios/{usuarioId}/estado`. El alta es el registro
  público del shell.
- **Eliminar canchas**: no existe `DELETE /api/canchas/{canchaId}` en el contrato. Inactivar es la
  única baja prevista (RN-07, PDF §3.3.4).
- **Editar un bloqueo de mantenimiento**: el contrato solo declara alta, listado y borrado. Para
  corregir uno se elimina y se registra de nuevo.
- **Modificar el shell**: ya declara este remote, ya restringe el módulo al `ADMIN` y ya entrega
  las props. Si algo obligara a tocarlo, se detiene la tarea y se avisa (`CLAUDE.md` §0.4).
- **Modificar `backend/`, `infra/postgres/` o `docs/contratos/README.md`**: esta spec no necesita
  ningún campo, ruta ni código de error nuevo.
- **Ejecutar el remote como aplicación independiente en el navegador**: no hay `bootstrap` con
  props de desarrollo ni token de prueba (P-04 de la spec 07).
- **Cálculo local de reglas de negocio**: el remote no recalcula `FINALIZADA`, no deduce
  disponibilidad a partir de los bloqueos y no comprueba solapamientos antes de enviar. Todo llega
  resuelto del microservicio.
- **El gateway Nginx** y la eliminación de los mapeos `8082`–`8085`: quedan para la sección de
  integración, con la decisión ya escrita en §8 de la spec 06.
- **Enrutador, gestor de estado global, librería de UI, TypeScript, tema oscuro, i18n** y
  cualquier dependencia npm que no exija React 18 + Webpack 5 + Module Federation.
- **Pruebas automatizadas de frontend**: ninguna spec anterior las incluyó y el PDF §5 no las pide
  como entregable.
- **Reservas recurrentes, pagos, notificaciones, torneos, app móvil nativa y reportes BI**:
  prohibidos por el PDF §3.5 y `CLAUDE.md` §2.
- **Diseño responsive avanzado, animaciones y accesibilidad** más allá de HTML semántico: la
  rúbrica del PDF §6 no las puntúa.

---


## 10. Decisiones tomadas (P-01 a P-10, respondidas el 24/08/2026)

**P-01 — Tres pantallas con menú interno, y los bloqueos anidados. Salida (b).** Canchas —con los
bloqueos dentro de la cancha seleccionada—, Reservas y Usuarios, con un menú interno del remote.
Motivo: un bloqueo siempre pertenece a una cancha y su ruta es anidada
(`/api/canchas/{canchaId}/bloqueos`); una pantalla suelta de bloqueos obligaría a un selector de
cancha que duplica el listado que ya está al lado. La salida (c) habría dejado fuera dos
funcionalidades que el PDF §3.1 asigna explícitamente a este módulo.

**P-02 — El cambio de estado de una cancha no lleva confirmación. Salida (a).** Es reversible con
un clic y el listado muestra el estado al instante. La confirmación se reserva para lo
irreversible: eliminar un bloqueo (HU-07) y cancelar una reserva (HU-08).

**P-03 — El listado de bloqueos no ofrece filtro por fecha. Salida (a).** Siempre todos los
bloqueos de la cancha. Motivo: con los volúmenes del proyecto un filtro no aporta, y el parámetro
`fecha` existe para que `ms-reservas` calcule disponibilidad, no para esta pantalla.

**P-04 — Filtro por `estado` en el listado global, con "Todos" por defecto. Salida (b).** El
filtrado es en el navegador, sobre lo ya recibido. Motivo: el listado global crece con cada
reserva del sistema y lo que el `ADMIN` busca casi siempre son las `CONFIRMADA`, las únicas
cancelables. **Sin** filtro por cancha: el listado ya muestra el nombre y el volumen no lo
justifica.

**P-05 — El `usuarioId` de cada reserva se resuelve a `nombre`. Salida (b).** Con
`GET /api/usuarios`, una sola llamada por pantalla y nunca una por fila, mostrando el `usuarioId`
tal cual cuando no aparezca en ese listado. Motivo: mismo criterio que HU-04 de la spec 07 con el
catálogo de canchas —un número no dice nada— y el `ADMIN` necesita saber a quién le está
cancelando la reserva.

**P-06 — El administrador puede inactivarse a sí mismo, con advertencia. Salida (b).** La acción
se ofrece igual sobre la fila propia, con una confirmación que advierte la consecuencia. Motivo:
el contrato lo permite y ocultar la acción sería inventar una regla que ningún microservicio
aplica; la advertencia es honesta sobre el efecto sin bloquear nada.

**P-07 — Un `rol` distinto de `ADMIN` ve un aviso propio, sin llamadas. Salida (b).** Motivo: si
el rol no es `ADMIN`, todas las llamadas responderían `403` y el usuario vería una pantalla llena
de errores en vez de un mensaje claro. Queda escrito como **comportamiento defensivo, no como
control de acceso**: el control real sigue siendo el token que valida cada microservicio.

**P-08 — `depends_on`: `ms-usuarios`, `ms-canchas` y `ms-reservas`.** Con
`condition: service_started`, los tres microservicios que este remote consume. **No**
`ms-reportes`.

**P-09 — Una tarea por pantalla. Salida (a).** Más la de andamiaje y la de Compose: unas siete
tareas en total. Afecta solo a `tasks.md`, que se escribe después de aprobar el diseño.

**P-10 — Los formularios van en la misma pantalla del listado. Salida (a).** A diferencia de la
spec 07, aquí el `ADMIN` alterna entre ver el listado y editar filas: sacar el formulario a otra
pantalla le haría perder el contexto en cada operación.

## 11. Supuestos

**Sin supuestos.** Los diez datos que faltaban se preguntaron como P-01 a P-10 y están
respondidos por el responsable el 24/08/2026 en §10; ninguno se rellenó con un valor inventado.

Todo lo demás salió de una fuente verificable: el nombre del remote, su módulo expuesto y su
puerto del "Contrato Module Federation"; las cuatro props del contrato congelado el 23/08/2026;
las once rutas, sus parámetros, sus cuerpos y sus códigos de error de `docs/contratos/README.md`
y de los DTO ya implementados en las specs 02, 03 y 04; el filtrado por rol de `GET /api/canchas`
(D-05 de la spec 03); el `PUT` que no toca `activa` (D-11 de la spec 03); el `Boolean` con
`@NotNull` de los dos `PATCH` de estado; el `BLOQUEO_DUPLICADO` compartido por duplicado y
solapamiento (D-09 de la spec 03); el orden del listado global (D-09 de la spec 04); `FINALIZADA`
calculada al leer (D-02 de la spec 04); el `204` sin cuerpo del borrado de bloqueos; y el patrón
de servicio de Compose, el `poll` del watcher y la obligación de verificar en navegador
(`docs/bitacora.md` y specs 06 y 07).
