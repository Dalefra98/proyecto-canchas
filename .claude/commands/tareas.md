Lee `.claude/specs/$ARGUMENTS/requirements.md` y `design.md` (ambos aprobados) y genera
SOLO `.claude/specs/$ARGUMENTS/tasks.md`.

Reglas:
- Entre 5 y 8 tareas. Cada tarea debe caber en un commit.
- Cada tarea incluye: qué hace, qué requisito cubre (HU-xx / RN-xx) y el comando Docker
  exacto que la verifica.
- Ordénalas de forma que cada una deje el proyecto compilando.
- No escribas código todavía.