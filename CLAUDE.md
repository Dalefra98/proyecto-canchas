# CLAUDE.md — Proyecto Canchas Deportivas

Este archivo es la única fuente de verdad del proyecto. Si algo no está aquí ni en
`docs/contratos/`, **no existe**: detente y pregunta antes de asumirlo.

---

## 0. REGLAS QUE NO SE ROMPEN

1. **No inventes.** Si falta un dato (nombre de campo, endpoint, regla, versión),
   detente y pregunta. Está prohibido "asumir un valor razonable".
2. **No amplíes el alcance.** Implementa exactamente lo pedido en la tarea activa.
   No agregues endpoints, entidades, campos, validaciones ni pantallas de más.
3. **Una tarea a la vez.** Trabaja solo la tarea indicada de `tasks.md`. Al terminar,
   detente y espera aprobación. No encadenes tareas.
4. **No toques archivos fuera de la spec activa.** Si crees que hay que modificar otro
   módulo, dilo y espera confirmación.
5. **No crees archivos que nadie pidió**: nada de `RESUMEN.md`, `NOTES.md`, `README`
   nuevos, scripts auxiliares ni ejemplos.
6. **Si un comando falla dos veces, detente** y reporta el error literal. No pruebes
   soluciones alternativas en cadena.
7. **Cierra cada respuesta** con la lista exacta de archivos creados o modificados.
   Nada más: sin resúmenes largos, sin celebraciones, sin "próximos pasos" que nadie pidió.
8. **Si detectas una contradicción** entre este archivo, `docs/contratos/` y lo que te
   pido en el chat: no elijas tú. Señálala y pregunta.

---

## 1. ENTORNO — no hay nada instalado en esta máquina

No existen JDK, Maven, Node, npm ni psql en el host. **Nunca ejecutes** `mvn`, `java`,
`javac`, `npm`, `npx`, `node` ni `psql` directamente. Solo Docker.

Comandos permitidos:

```bash
# Compilar un microservicio (el volumen m2repo cachea las dependencias entre corridas;
# sin el, la descarga completa desde repo.maven.apache.org corta el handshake TLS)
docker run --rm -v "${PWD}:/app" -v m2repo:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q clean package -DskipTests

# Dependencias de un microfrontend
docker run --rm -v "${PWD}:/app" -w /app node:20-alpine npm install

# Levantar / reconstruir un servicio
docker compose up -d --build <servicio>

# Logs
docker compose logs --tail=50 <servicio>

# Base de datos
docker compose exec postgres psql -U <usuario> -d <base> -c "<sql>"
```

**Dockerfile — patrón oficial para los cuatro microservicios.** Las specs 03, 04 y 05 lo
copian tal cual, cambiando solo el nombre del `.jar`. No se usa `dependency:go-offline`: esa
capa descarga todo el árbol de dependencias sin caché y es la que falla con esta red.

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/<artifactId>-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

El `--mount=type=cache,target=/root/.m2` es el equivalente del volumen `m2repo` dentro del
build de imagen: `docker compose build` no monta volúmenes, así que sin él cada build
redescarga todo.

Sistema operativo del desarrollador: **Windows con PowerShell**. Usa `curl.exe`, no `curl`.
Para copiar archivos: `Copy-Item`, no `cp`.

---

## 2. PRODUCTO

Sistema web de reserva de canchas deportivas (pádel, tenis, básquet).

**Roles**
- `USUARIO`: consulta disponibilidad, crea reservas, cancela las propias, ve su historial.
- `ADMIN`: gestiona el catálogo de canchas, horarios, bloqueos de mantenimiento y usuarios;
  cancela cualquier reserva; ve los reportes.

**Reglas de negocio — fuente única de verdad**

| ID | Regla |
|---|---|
| RN-01 | La reserva es sobre una cancha, una fecha y un bloque horario de 1 hora. |
| RN-02 | No se puede reservar un bloque ya ocupado en la misma cancha. |
| RN-03 | El usuario solo cancela sus propias reservas; el admin cancela cualquiera. |
| RN-04 | Solo se cancela una reserva cuya fecha y hora de inicio no hayan ocurrido. |
| RN-05 | Cancelar libera el bloque para otro usuario. |
| RN-06 | Límite configurable de reservas activas simultáneas por usuario (default 3). |
| RN-07 | Solo el admin crea, edita o inactiva canchas y define su horario de atención. |
| RN-08 | Estados de reserva: `CONFIRMADA`, `CANCELADA`, `FINALIZADA`. |

**Prohibido implementar o proponer**: pasarela de pagos, notificaciones (email/SMS/push),
reservas recurrentes, torneos o ligas, app móvil nativa, reportes BI o exportación analítica.

---

## 3. STACK — no negociable

### Backend
- Java 21, Spring Boot, Maven.
- **Versión fija para los cuatro microservicios: Spring Boot 3.5.3** (parent
  `spring-boot-starter-parent`), con `springdoc-openapi-starter-webmvc-ui` 2.8.6 y
  `io.jsonwebtoken` (jjwt) 0.12.6. Spring Initializr ya solo entrega la rama 4.x, así que el
  `<parent>` se corrige a mano. Ninguna spec vuelve a decidir estas versiones: si hay que
  cambiarlas, se cambia aquí primero y se avisa al grupo.
- Cuatro microservicios: `ms-usuarios`, `ms-canchas`, `ms-reservas`, `ms-reportes`.
- Capas: `controller` -> `service` -> `repository` -> `entity`. DTOs separados de entidades,
  con mapper **manual**.
- **Prohibido**: Lombok, MapStruct, `@Autowired` en campos, `@Data`, clases `Util` genéricas.
- Inyección por constructor. Validación con `jakarta.validation`.
- Documentación con `springdoc-openapi`. Cada endpoint declara sus códigos de error.
- `spring.jpa.hibernate.ddl-auto=validate`. El esquema lo manda el DDL versionado en
  `infra/postgres/`. Si la entidad no calza con el DDL, se corrige la entidad, no el DDL.

### Frontend
- React 18 + Webpack 5 con `ModuleFederationPlugin`. 1 host (`shell`) + 3 remotes.
- `react` y `react-dom` en `shared` con `singleton: true`.
- Todo microfrontend arranca con `src/index.js` -> `import("./bootstrap")`.
- Las URLs de los remotes son las del **navegador** (`http://localhost:3001/...`),
  nunca nombres de contenedor.
- Sin librerías de UI externas. CSS plano. Sin TypeScript.
- Todas las llamadas HTTP usan rutas relativas bajo `/api`.

### Base de datos
- PostgreSQL 16. Una base y un usuario por microservicio: `usuarios_db`, `canchas_db`,
  `reservas_db`.
- **Prohibido** que un microservicio lea tablas de otro. La integración es vía REST.
- `ms-reportes` no tiene base propia: consume `ms-canchas` y `ms-reservas` por HTTP.

---

## 4. ESTRUCTURA

```
backend/<ms-nombre>/src/main/java/ec/ups/dae/<dominio>/
  controller/  service/  repository/  entity/  dto/  mapper/  config/  exception/

frontend/<nombre>/
  src/index.js        # solo import("./bootstrap")
  src/bootstrap.jsx
  src/App.jsx
  src/components/
  src/api/            # única capa que hace fetch
  webpack.config.js
```

**Convenciones**
- Rutas: `/api/<dominio>/<recurso>`, plural y minúsculas.
- Códigos: `400` validación · `401` sin token · `403` sin permiso · `404` no existe ·
  `409` conflicto de negocio (solapamiento, límite, reserva pasada).
- Toda excepción de negocio se traduce en `@RestControllerAdvice`. Nunca un stacktrace
  al cliente.
- Cada método de servicio que implementa una regla lleva un comentario con su ID:
  `// RN-02: valida solapamiento`.

---

## 5. CONTRATOS CONGELADOS

Los nombres de campo JSON están en `docs/contratos/README.md`. **No renombrar, no abreviar,
no traducir, no inventar.** Si falta un campo, detente y pregunta.

| Concepto | Campo | Valores |
|---|---|---|
| Estado de reserva | `estado` | `CONFIRMADA` \| `CANCELADA` \| `FINALIZADA` |
| Fecha | `fecha` | `AAAA-MM-DD` |
| Bloque horario | `horaInicio` / `horaFin` | `HH:mm` |
| Deporte | `deporte` | `PADEL` \| `TENIS` \| `BASQUET` |
| Rol | `rol` | `ADMIN` \| `USUARIO` |
| Ocupación | `porcentajeOcupacion` | número 0-100 |

Contrato de props del shell hacia cualquier remote:

```jsx
<RemoteApp usuario={{ id, nombre, rol }} apiBaseUrl="/api" onLogout={fn} />
```

---

## 6. FLUJO DE TRABAJO — spec-driven con tres compuertas

Toda funcionalidad pasa por `.claude/specs/<NN-nombre>/` en este orden:

1. `requirements.md` -> **espera aprobación explícita** ("apruebo requisitos").
2. `design.md` -> **espera aprobación explícita** ("apruebo diseño").
3. `tasks.md` -> ejecutar una tarea, verificar con su comando, detenerse.

**Nunca escribas código de producción si la compuerta anterior no fue aprobada por escrito
en el chat.** Si te pido código sin spec aprobada, recuérdamelo antes de obedecer.

Antes de empezar cualquier tarea, lee en este orden: este archivo ->
`docs/contratos/README.md` -> `requirements.md` y `design.md` de la spec activa.

---

## 7. IDIOMA

Código, nombres de clases y variables en español sin tildes (`ReservaService`, `canchaId`).
Comentarios, mensajes de error y respuestas del chat en español.