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

**Hallazgos de entorno (aplican a todas las specs):**
- `pg_isready` responde OK mientras Postgres aun ejecuta los scripts de
  `docker-entrypoint-initdb.d`. El `healthcheck` del compose da un falso positivo,
  por lo que un microservicio con `depends_on: service_healthy` puede arrancar antes
  de que existan las tablas. Pendiente de resolver en la spec 02.
- En Git Bash, `psql -f /docker-entrypoint-initdb.d/...` requiere `MSYS_NO_PATHCONV=1`
  para que no se traduzca la ruta.
