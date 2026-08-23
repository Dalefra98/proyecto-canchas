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
Tiempo invertido: 1 HORA

**Observación:** el script de automatización no se ejecutó y los archivos quedaron
vacíos; se detectó al verificar tamaños en bytes, no a simple vista en el editor.

---

## Spec 01 — Modelo de datos y contratos

Responsable: ____________________

| N.º | Fecha | Compuerta | Intención del prompt | ¿Se aceptó? | Corrección aplicada |
|---|---|---|---|---|---|
| 1 | | C1 | | | |
| 2 | | C2 | | | |
| 3 | | C3 | | | |