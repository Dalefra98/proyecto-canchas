Revisa $ARGUMENTS contra CLAUDE.md y docs/contratos/README.md. No modifiques nada todavía.

Reporta en una tabla: hallazgo | archivo:línea | regla violada | corrección propuesta.

Revisa específicamente:
- Nombres de campo distintos a los del contrato.
- Uso de Lombok, MapStruct o @Autowired en campos.
- Consultas que toquen tablas de otro microservicio.
- Endpoints o campos que no aparezcan en design.md.
- Reglas RN-xx sin comentario que las identifique.
- Excepciones que no devuelvan el código HTTP declarado.

Espera mi visto bueno antes de corregir.