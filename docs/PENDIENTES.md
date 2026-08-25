# Pendientes — guía para quien continúa el proyecto

Este archivo se puede leer sin haber trabajado antes en el proyecto. Para levantar el sistema,
las credenciales, los puertos y la estructura del repositorio, ve primero al
[`README.md`](../README.md); aquí no se repite nada de eso.

Otras dos lecturas útiles antes de tocar algo: [`bitacora.md`](bitacora.md), que registra qué se
pidió y qué se corrigió en cada compuerta de cada spec, y
[`contratos/README.md`](contratos/README.md), que es el contrato congelado.

**Sobre la numeración E1 a E6.** Los entregables se numeran según el §5 del documento de alcance
(`Alcance_Funcional_Reserva_Canchas_v2.pdf`). De ese documento, en el repositorio solo están
registrados por su número **E3** —la documentación OpenAPI, en la bitácora— y **E5** —el cierre
documental, en el `tasks.md` de la spec 01—. El resto de asignaciones de este archivo las puso el
responsable; si alguna no coincide con el PDF, corrígela aquí.

---

## 1. Estado actual

| Spec | Contenido | Entregable | Estado |
|---|---|---|---|
| 01 | Modelo de datos, DDL versionado, seed y contratos congelados | E5 (cierre documental) | **Completa** |
| 02 | `ms-usuarios`: registro, inicio de sesión, JWT y gestión de usuarios | E2, E3 | **Completa** |
| 03 | `ms-canchas`: catálogo, horario de atención y bloqueos de mantenimiento | E2, E3 | **Completa** |
| 04 | `ms-reservas`: disponibilidad, creación, historial y cancelación; RN-02 a RN-06 | E2, E3 | **Completa** |
| 05 | `ms-reportes`: ocupación, reservas y cancelaciones por período, sin base propia | E2, E3 | **Completa** |
| 06 | `shell`: host de Module Federation, sesión, menú y borde de error | E4 | **Completa** |
| 07 | `mf-reservas`: disponibilidad, nueva reserva y mis reservas | E4 | **Completa** |
| 08 | `mf-administracion`: canchas, bloqueos, reservas globales y usuarios | E4 | **Completa** |
| 09 | `mf-reportes`: los tres reportes, solo `ADMIN` | E4 | **Completa** |
| 10 | Gateway Nginx: punto de entrada único del tráfico `/api` | E4 | **Completa** |

Las diez specs pasaron sus tres compuertas —requisitos, diseño y tareas— y todas sus tareas
quedaron ejecutadas y verificadas con salida real, registrada en la bitácora.

**El código está terminado y verificado. Lo que queda es documentación, pruebas formales y
preparación de la sustentación: no hay que programar nada más para entregar.**

---

## 2. Lo que falta

| # | Trabajo | Entregable | Estimado | Responsable |
|---|---|---|---|---|
| 1 | Ejecutar y documentar las 14 pruebas de extremo a extremo (§3) | parte de E1 | 1,5 h | |
| 2 | Redactar el documento de arquitectura | E1 | 3 a 4 h | |
| 3 | Redactar el manual de despliegue | E5 | 30 min | |
| 4 | Preparar la presentación | E6 | 1,5 h | |
| 5 | Ensayar la demostración | E6 | 1 h | |

**Total estimado: entre 7,5 y 8,5 horas.**

Notas de dependencia entre trabajos:

- El **1** va primero: las capturas que produce son las evidencias del **2**, y los escenarios que
  se ensayan en el **5** son los mismos.
- Para el **2** ya existe material hecho: [`diagramas-c4.md`](diagramas-c4.md) tiene siete
  diagramas Mermaid más el anexo de despliegue (figura 4), y [`workspace.dsl`](workspace.dsl) el
  mismo modelo en Structurizr. La bitácora aporta las decisiones con su motivo, que es lo que un
  documento de arquitectura necesita para justificar por qué el sistema es así y no de otra forma.
- Para el **3**, la tabla de puertos y los pasos de arranque ya están en el
  [`README.md`](../README.md) §3 y §4; el manual puede remitir allí y añadir lo que falte.

---

## 3. Las 14 pruebas de extremo a extremo

Los escenarios salen del documento de alcance y de lo ya verificado en las specs 04, 07 y 08. Se
ejecutan **en el navegador sobre `http://localhost:3000`**, no con `curl.exe`: lo que se
documenta es el sistema completo funcionando, no un endpoint.

Los **tres marcados con ★** son los que un evaluador pide con más probabilidad. Si el tiempo se
acorta, esos tres no se negocian.

| # | Escenario | Resultado esperado | Resultado | Evidencia |
|---|---|---|---|---|
| 1 | Registrar un usuario nuevo desde el shell | Se crea con rol `USUARIO` y puede iniciar sesión. Un correo ya registrado responde `409 EMAIL_DUPLICADO` | | |
| 2 | Iniciar sesión con credenciales válidas y con una contraseña incorrecta | Válidas: entra y ve el menú de su rol. Incorrecta: `401` y mensaje de credenciales inválidas, sin entrar | | |
| 3 | Consultar la disponibilidad de una cancha en una fecha | Se muestran los bloques de una hora entre `horaApertura` y `horaCierre`, marcados como libres u ocupados | | |
| 4 | Crear una reserva sobre un bloque libre | `201`, la reserva queda en estado `CONFIRMADA` y el bloque pasa a ocupado en la grilla | | |
| 5 | ★ **Reservar un bloque ya ocupado en la misma cancha** | `409 BLOQUE_OCUPADO` (RN-02). La pantalla muestra el mensaje del error y refresca la grilla; no se crea nada | | |
| 6 | Con tres reservas activas, intentar una cuarta | `409 LIMITE_RESERVAS` (RN-06). El límite es configurable con `RESERVAS_MAX_ACTIVAS`, 3 por omisión | | |
| 7 | Cancelar una reserva propia futura | `200`, la reserva queda `CANCELADA` y **el bloque vuelve a estar disponible** para otro usuario (RN-05) | | |
| 8 | Como `USUARIO`, intentar cancelar la reserva de otro | `403 SIN_PERMISO` (RN-03). El `ADMIN`, en cambio, cancela cualquier reserva | | |
| 9 | Intentar cancelar una reserva cuya fecha y hora de inicio ya pasaron | `409 RESERVA_PASADA` (RN-04) | | |
| 10 | Crear o editar una cancha como `ADMIN`, y luego intentarlo como `USUARIO` | `ADMIN`: se guarda. `USUARIO`: no ve el módulo Administración, y la llamada directa responde `403 SIN_PERMISO` (RN-07) | | |
| 11 | Registrar un bloqueo de mantenimiento sobre una franja | Esa franja deja de ofrecerse como disponible en la consulta de disponibilidad | | |
| 12 | Consultar los tres reportes con un rango de fechas, como `ADMIN` y como `USUARIO` | `ADMIN`: ocupación con `porcentajeOcupacion`, reservas y cancelaciones del período. `USUARIO`: no ve el módulo Reportes. Un rango con `desde` posterior a `hasta` responde `400 DATOS_INVALIDOS` | | |
| 13 | ★ **Los tres remotes cargados en el shell** | Con sesión de `ADMIN`, los tres módulos se abren y funcionan sin recargar la página. En la pestaña de red del navegador se ven los tres `remoteEntry.js` descargados de `3001`, `3002` y `3003` | | |
| 14 | ★ **Detener un microservicio con el sistema en marcha** | `docker compose stop ms-reportes`: el módulo Reportes muestra su error y **el resto sigue funcionando** —Reservas y Administración responden con normalidad—. El gateway sigue en pie y devuelve `502` para ese dominio. Al volver a arrancarlo, el módulo funciona de nuevo | | |

Sobre la prueba 14: el `502` del gateway ya se verificó en la T7 de la spec 10, con el gateway en
pie y `/api/canchas` respondiendo `200` mientras `ms-reportes` estaba detenido. Lo que falta es
**verlo desde el navegador**, que es lo que el evaluador va a pedir.

---

## 4. Evidencias

### Capturas que ya existen

En `docs/Capturas/` hay **26 archivos**. Salvo uno, tienen nombre autogenerado —`{GUID}.png`—, así
que **no se puede saber a qué prueba corresponde cada uno sin abrirlos**. Primer trabajo de esta
sección: abrirlos, identificarlos y renombrarlos con un nombre que diga qué muestran.

| Archivo | Qué muestra | Spec o prueba de origen |
|---|---|---|
| `evidencia de E3.png` | Documentación OpenAPI (Swagger UI) | E3 | 
| `SC4{D91691E7-...}.png` | | |
| `sc4t9{C4DABA6A-...}.png` | | |
| `image.png` | | |
| `preview.webp` | | |
| Los 22 restantes con nombre `{GUID}.png` | | |

Convención sugerida para renombrarlas, para que el orden alfabético coincida con el del informe:
`prueba-NN-descripcion.png` para las 14 pruebas y `figura-N-descripcion.png` para las figuras de
arquitectura.

### Capturas que faltan tomar

| # | Qué debe mostrar | Cómo obtenerla |
|---|---|---|
| 1 | Los once servicios en `Up` y `postgres` en `(healthy)` | `docker compose ps` en PowerShell, con la ventana lo bastante ancha para que no se corten las columnas |
| 2 | Bloque ocupado rechazado (prueba 5 ★) | Dos usuarios: el primero reserva un bloque; el segundo intenta el mismo y aparece el mensaje de `BLOQUE_OCUPADO`. Capturar **la pantalla con el mensaje visible**, no la consola |
| 3 | Los tres `remoteEntry.js` descargados (prueba 13 ★) | Pestaña **Red** del navegador en `localhost:3000`, filtro `remoteEntry`, con las tres filas de `3001`, `3002` y `3003` y su código `200` |
| 4 | Un microservicio detenido y el resto en marcha (prueba 14 ★) | `docker compose stop ms-reportes`, capturar el módulo Reportes con su error **y** otro módulo funcionando en la misma sesión |
| 5 | El `502` del gateway | `curl.exe -i http://localhost:8090/api/reportes/ocupacion?desde=...&hasta=...` con el microservicio detenido |
| 6 | Swagger UI de los cuatro microservicios | `http://localhost:8082/swagger-ui/index.html` y los tres siguientes, mostrando los códigos de error declarados en cada endpoint |
| 7 | Las tres bases separadas, cada una con su usuario | Adminer en `http://localhost:8081`, o `docker compose exec postgres psql -U <usuario> -c "\l"` |
| 8 | Los cuatro diagramas de arquitectura | Exportar de `playground.structurizr.com` pegando [`workspace.dsl`](workspace.dsl), o renderizar los Mermaid de [`diagramas-c4.md`](diagramas-c4.md) |
| 9 | Reserva creada y luego cancelada, con el bloque liberado (prueba 7) | Tres capturas seguidas: grilla con el bloque libre, reserva creada, y grilla otra vez con el bloque libre tras cancelar |
| 10 | Un `USUARIO` sin acceso a Administración ni Reportes (pruebas 10 y 12) | Iniciar sesión como `usuario@canchas.ec` y capturar el menú con un solo módulo |

**Regla al tomar evidencias: prioriza los casos que fallan como se espera.** Recrear un escenario
de error cuesta bastante más que repetir uno de éxito —hay que dejar el sistema en un estado
concreto: un bloque ya reservado por otro, tres reservas activas, una reserva con fecha pasada, un
contenedor detenido—, mientras que una pantalla que funciona se vuelve a capturar en segundos. Si
durante las pruebas se te presenta un error esperado, captúralo en ese momento aunque no fuera el
turno de esa prueba.

---

## 5. Cómo seguir programando, si hace falta

- **Crea una spec nueva con sus tres compuertas.** No se improvisa sobre código cerrado. El orden
  es `requirements.md` → aprobación escrita → `design.md` → aprobación escrita → `tasks.md`, y las
  tareas se ejecutan **una a una**, deteniéndose al terminar cada una (`CLAUDE.md` §6 y §0.3).
- **Si el cambio toca el contrato, modifícalo primero y avisa al equipo.**
  [`contratos/README.md`](contratos/README.md) es la fuente única de verdad: se cambia allí, se
  añade la fila correspondiente en su registro de cambios y se comunica antes de escribir código.
  Un campo renombrado sin avisar rompe a la vez un microservicio y un microfrontend.
- **Si el cambio toca un microservicio ya cerrado, incluye una tarea de regresión** que vuelva a
  verificar lo que ese servicio ya hacía. Ocurrió en la spec 04, que tuvo que modificar
  `ms-canchas` para el rol `SERVICIO`: lo que no se vuelve a probar, se rompe en silencio.
- **Registra las iteraciones en la [bitácora](bitacora.md) el mismo día.** Reconstruir después qué
  se pidió, qué se corrigió y por qué es mucho más caro que anotarlo en el momento, y la bitácora
  es material de la sustentación.
- **Ejecuta Claude Code siempre desde la raíz del proyecto**, no desde `backend/` ni desde
  `frontend/`: desde una subcarpeta no ve `CLAUDE.md` ni las specs, y empieza a inventar. Se
  comprueba con **`/context`**, que muestra qué archivos tiene cargados.

---

## 6. Antes de entregar

Lista de verificación. Cada casilla se marca solo cuando se ha comprobado de verdad, no cuando
parece que debería estar bien.

- [ ] **Un clonado limpio levanta el sistema con tres comandos.** En una carpeta nueva:
      `git clone`, `Copy-Item .env.example .env`, `docker compose up -d --build`. Probarlo de
      verdad, no suponerlo.
- [ ] **`.env` no está versionado** y `.env.example` sí, con valores que sirven para desarrollo
      local.
- [ ] **El esquema está en modo validación**: `spring.jpa.hibernate.ddl-auto=validate` en los
      microservicios con base. Hibernate no crea ni altera tablas; el esquema lo manda el DDL de
      `infra/postgres/`.
- [ ] **Ningún microservicio consulta tablas de otro.** Cada uno con su base y su usuario de
      PostgreSQL; la integración entre servicios es REST. `ms-reportes` no tiene base.
- [ ] **Los tres remotes se integran en el shell** y el fallo de uno no tumba a los otros dos ni
      cierra la sesión.
- [ ] **La API documenta sus códigos de error**: cada endpoint declara en Swagger los códigos que
      puede devolver, y los `codigo` coinciden con la tabla del contrato.
- [ ] **Las 14 pruebas de §3 ejecutadas**, con su columna de resultado rellenada.
- [ ] **Todas las figuras del informe con capturas propias del sistema funcionando.** Ninguna
      imagen de internet, ningún diagrama de otro proyecto.
- [ ] **La bitácora tiene fechas reales** y la columna "Tiempo invertido" completada en las diez
      specs.
- [ ] **La demostración ensayada de principio a fin**, cronometrada, con el sistema ya levantado
      antes de empezar a exponer.

**Dos cosas que conviene tener resueltas antes de la sustentación, porque en directo salen mal:**
levantar el sistema **antes** de empezar a exponer —la primera construcción no es inmediata— y
revisar que ningún contenedor de otro proyecto ocupe los puertos de la tabla del
[`README.md`](../README.md) §3. Eso último ya pasó una vez durante el desarrollo, con un `404`
ajeno que parecía un fallo del sistema.
