Lee `.claude/specs/$ARGUMENTS/requirements.md` (ya aprobado) y genera SOLO
`.claude/specs/$ARGUMENTS/design.md`.

Reglas:
- No escribas NADA de código en este paso.
- Incluye: modelo de datos (tabla de columnas y restricciones), DTOs con validaciones,
  tabla de endpoints con rol requerido, tabla de excepciones a códigos HTTP, y una tabla
  de decisiones de diseño con la alternativa descartada.
- Verifica campo por campo contra docs/contratos/README.md. Si algún nombre no coincide,
  detente y dímelo en vez de renombrarlo.
- Ninguna consulta puede acceder a tablas de otro microservicio.

Termina y espera mi aprobación. No generes tareas.