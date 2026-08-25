# Spec 09 — mf-reportes (remote de Module Federation) · tasks.md

Basado en `requirements.md` (C1 aprobado el 24/08/2026) y `design.md` (C2 aprobado el
24/08/2026).

**Seis tareas**, el reparto que fijó P-10: una por reporte, más andamiaje, capa `api` y Compose.
Cada una cabe en un commit y deja el proyecto compilando.

Reglas de ejecución (`CLAUDE.md` §0.3): **una tarea a la vez**. Al terminar una, detenerse y
esperar aprobación. No encadenar tareas.

Todos los comandos se ejecutan desde la raíz del repositorio, en PowerShell. Nada de `npm`,
`node` ni `npx` en el host: solo Docker (`CLAUDE.md` §1).

---

## T1 — Andamiaje del remote y guardia de rol

**Qué hace.** Crea `frontend/mf-reportes` con su configuración completa y el módulo expuesto en su
forma mínima: `package.json` (§3.4), `webpack.config.js` con `ModuleFederationPlugin` (§3.1),
`devServer` y `watchOptions` (§3.2) y el `proxy` de una sola entrada (§3.3); `.babelrc`,
`public/index.html`, `src/index.js` con solo `import("./bootstrap")`, `src/bootstrap.jsx` con el
aviso estático (D-02), `src/estilos.css` con el prefijo `mfrep-` (D-14) y `src/ReportesApp.jsx`
con la guardia de rol (§4.4) y un contenedor vacío para el resto del módulo.

**Requisitos que cubre.** HU-07 completa; HU-06 en su parte de props y guardia de rol; D-01, D-02,
D-13, D-14.

**No hace.** No crea `src/api/`, ni pantallas, ni el servicio de Compose.

**Verificación.**

```powershell
docker run --rm -v "${PWD}/frontend/mf-reportes:/app" -w /app node:20-alpine sh -c "npm install && npx webpack build --mode development"
```

Debe terminar en `compiled successfully` y generar `dist/remoteEntry.js`.

---

## T2 — Servicio `mf-reportes` en `docker-compose.yml`

**Qué hace.** Agrega el servicio `mf-reportes` con el patrón de `shell`, `mf-reservas` y
`mf-administracion` (§9): imagen `node:20-alpine`, `command` de `npm install` más
`webpack serve`, los dos volúmenes, `ports: "3003:3003"` y `depends_on` **solo** de `ms-reportes`
con `condition: service_started` (P-08). Declara `mf_reportes_node_modules` en la sección
`volumes`.

**Requisitos que cubre.** HU-08 completa; E-11.

**No hace.** No toca el `shell` (no se le agrega `depends_on`) ni ningún otro servicio.
`docker-compose.yml` es el **único** archivo modificado fuera de `frontend/mf-reportes`.

**Verificación.**

```powershell
docker compose up -d --build mf-reportes
docker compose logs --tail=50 mf-reportes
curl.exe -I http://localhost:3003/remoteEntry.js
curl.exe -I http://localhost:3001/remoteEntry.js
curl.exe -I http://localhost:3002/remoteEntry.js
```

Los tres `curl.exe` deben responder `200`: el tercer remote no puede romper a los dos anteriores.
En el navegador, un `ADMIN` entra a Reportes en `http://localhost:3000` y ve el módulo montado —el
contenedor vacío de T1—, **no** el mensaje de módulo no disponible del `BordeError`.

---

## T3 — Capa `api`, selector de rango y navegación interna

**Qué hace.** Crea `src/api/clienteApi.js` recortado a `GET` (D-04) y `src/api/reportesApi.js` con
las tres funciones (§6.1); `src/components/MensajeError.jsx`, `src/components/SelectorRango.jsx`
(§5.1) y `src/components/NavegacionInterna.jsx` (§6.3). Completa el estado de `ReportesApp.jsx`
—`vista`, `rango`, `consulta` con su `intento` y `avisoRango`— y el envoltorio `ejecutar` del
`401` (§4.1). Mientras no exista la pantalla de la vista activa, `ReportesApp` pinta el aviso de
"elija un rango y consulte".

**Requisitos que cubre.** HU-01 completa; HU-10 en su parte de menú, vista inicial y conservación
del rango; HU-06 en su parte de `token`, `apiBaseUrl` y `onLogout`; HU-09 en la normalización del
error; D-03 a D-08, D-12.

**No hace.** No crea ninguna de las tres pantallas: ninguna ruta se llama todavía.

**Verificación.**

```powershell
docker compose restart mf-reportes
docker compose logs --tail=50 mf-reportes
curl.exe -I http://localhost:3003/remoteEntry.js
```

En el navegador, con un `ADMIN`: los tres botones del menú cambian la vista activa; pulsar
consultar con un campo vacío muestra el aviso del rango y **no** genera ninguna petición en la
pestaña de red; el rango escrito se conserva al cambiar de reporte.

---

## T4 — Pantalla de Ocupación, barra e indicador de demanda

**Qué hace.** Crea `src/components/PantallaOcupacion.jsx` (§4.2), con su llamada a
`GET /api/reportes/ocupacion`, la tabla de las seis columnas, la etiqueta del período devuelto,
la nota de `horasDisponibles` y los estados de carga, error y reporte sin datos. Crea
`src/components/BarraPorcentaje.jsx` (P-09, D-10) y `src/components/IndicadorDemanda.jsx`
(D-09, D-11), este último parametrizado por métrica para reutilizarlo en T5.

**Requisitos que cubre.** HU-02 completa; HU-05 en su mitad de `porcentajeOcupacion`, incluidos el
empate y el reporte vacío; HU-09 en su parte de `500` sin reporte parcial.

**No hace.** No agrupa ni totaliza por `deporte` (P-04).

**Verificación.**

```powershell
docker compose restart mf-reportes
docker compose logs --tail=50 mf-reportes
```

En el navegador, con un `ADMIN` y al menos una reserva creada desde `mf-reservas` dentro del
rango: la tabla muestra las columnas del contrato con sus nombres exactos, la barra acompaña al
número sin reemplazarlo y el indicador destaca la cancha de mayor y la de menor
`porcentajeOcupacion`. Con `desde` posterior a `hasta`, se muestra el `mensaje` del
`400 DATOS_INVALIDOS` y la tabla anterior no se borra (D-07).

---

## T5 — Pantalla de Reservas por período

**Qué hace.** Crea `src/components/PantallaReservas.jsx` (§4.2), con su llamada a
`GET /api/reportes/reservas`, la tabla de `canchaId`, `nombre`, `deporte` y `totalReservas`, la
nota de que se cuentan `CONFIRMADA` y `FINALIZADA` y se excluyen `CANCELADA`, y el
`IndicadorDemanda` de T4 aplicado a `totalReservas`.

**Requisitos que cubre.** HU-03 completa; HU-05 en su mitad de `totalReservas`; RN-08 en su parte
de trazabilidad.

**No hace.** No suma totales por `deporte` (P-04) y no reordena `items` en el estado (D-09).

**Verificación.**

```powershell
docker compose restart mf-reportes
docker compose logs --tail=50 mf-reportes
```

En el navegador, con un `ADMIN`: cambiar a Reservas conserva el rango y **no** dispara la consulta
hasta pulsar consultar (HU-10). Cancelar una reserva desde `mf-administracion` y volver a
consultar baja `totalReservas` de esa cancha.

---

## T6 — Pantalla de Cancelaciones por período

**Qué hace.** Crea `src/components/PantallaCancelaciones.jsx` (§4.2), con su llamada a
`GET /api/reportes/cancelaciones`, la tabla de `canchaId`, `nombre` y `totalCancelaciones` y la
nota de que el rango filtra por la `fecha` de la reserva cancelada, no por la fecha en que se
canceló.

**Requisitos que cubre.** HU-04 completa; cierra HU-10 con las tres pantallas montadas y HU-09 en
las tres rutas.

**No hace.** No muestra `deporte` —las filas no lo traen— y **no** pinta indicador de demanda
(P-05).

**Verificación.**

```powershell
docker compose restart mf-reportes
docker compose logs --tail=50 mf-reportes
curl.exe -I http://localhost:3001/remoteEntry.js
curl.exe -I http://localhost:3002/remoteEntry.js
curl.exe -I http://localhost:3003/remoteEntry.js
```

Recorrido final por navegador con un `ADMIN`: los tres reportes se consultan con el mismo rango,
la cancelación hecha en T5 aparece contada aquí, la consola no muestra errores de React duplicado
ni de `hooks` inválidos, y los módulos Reservas y Administración siguen funcionando.

---

## Verificación transversal de toda tarea

Vale para las seis (§10 del diseño):

| Nivel | Comprobación |
|---|---|
| 1 | `curl.exe http://localhost:3003/remoteEntry.js` responde `200` |
| 2 | `curl.exe http://localhost:3001/remoteEntry.js` y `curl.exe http://localhost:3002/remoteEntry.js` siguen respondiendo `200` |
| 3 | `docker compose logs --tail=50 mf-reportes` sin errores de compilación |
| 4 | Recorrido por navegador con un `ADMIN` sobre la pantalla de la tarea |
| 5 | Consola del navegador sin errores de React duplicado ni de `hooks` inválidos |

Un `compiled successfully` no prueba que la aplicación funcione (bitácora, T5 de la spec 06): toda
tarea con interacción exige el nivel 4. Los reportes solo muestran números si hay reservas en el
rango: generarlas con `mf-reservas` es parte del recorrido, porque el seed no las trae y esta spec
no lo toca.

Si un comando falla dos veces, detenerse y reportar el error literal (`CLAUDE.md` §0.6).
