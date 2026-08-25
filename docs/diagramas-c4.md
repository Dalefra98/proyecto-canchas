# Diagramas C4 — Sistema de Reserva de Canchas Deportivas

Tres niveles del modelo C4: **contexto**, **contenedores** y **componentes**. El nivel 4 —código—
no se incluye: lo cubre la sección de estructura de cada `design.md` en `.claude/specs/`, que ya
lista clase por clase.

Todo lo que aparece aquí sale de `docker-compose.yml`, `docs/contratos/README.md`, `CLAUDE.md` §3
y §4, los `design.md` de las specs 04, 06 y 10, y el árbol de clases y archivos del repositorio.
No hay ningún componente ni relación inventados.

## Nota sobre la notación

Los diagramas usan **`flowchart` con subgrafos**, no la sintaxis `C4Context` / `C4Container` /
`C4Component` de Mermaid. Dos motivos:

1. **`flowchart` renderiza en cualquier visor y en cualquier versión de Mermaid.** Las directivas
   C4 dependen de soporte específico, y este documento tiene que verse igual en GitHub, en el
   editor de quien lo lea y en el informe.
2. Esa sintaxis no permite distinguir **tipos de relación** con trazos distintos, y en este sistema
   esa distinción es justamente lo que más se malinterpreta: la descarga de los `remoteEntry.js`
   por el navegador no es lo mismo que una llamada a la API, y el acceso de desarrollo por los
   puertos `8082`–`8085` no es lo mismo que el camino de la aplicación.

La convención de trazos es la misma en los tres niveles:

- **línea continua** — camino de la aplicación en ejecución.
- **línea punteada** — descarga de estáticos por el navegador.
- **línea gruesa** — acceso de desarrollo, no usado por la aplicación.

Dentro de los diagramas el texto va **sin tildes**, para que ningún visor de Mermaid falle al
renderizar. El texto explicativo fuera de los bloques sí las lleva.

---

## Nivel 1 — Contexto

```mermaid
flowchart TB
    U["Usuario final<br/>Reserva canchas y gestiona<br/>sus propias reservas"]
    A["Administrador<br/>Gestiona canchas, bloqueos,<br/>usuarios y ve los reportes"]

    S["<b>Sistema de Reserva de Canchas Deportivas</b><br/><br/>Reserva de canchas de padel, tenis y basquet<br/>en bloques horarios de una hora.<br/>Catalogo, bloqueos de mantenimiento y<br/>reportes de ocupacion."]

    U -->|"Consulta disponibilidad, crea<br/>y cancela sus reservas"| S
    A -->|"Administra el catalogo, cancela<br/>cualquier reserva, consulta reportes"| S

    style S fill:#1168bd,stroke:#0b4884,color:#ffffff
    style U fill:#08427b,stroke:#052e56,color:#ffffff
    style A fill:#08427b,stroke:#052e56,color:#ffffff
```

Dos actores y una sola caja. El `USUARIO` consulta disponibilidad, crea reservas y cancela **las
suyas**; el `ADMIN` gestiona el catálogo de canchas y sus horarios, los bloqueos de mantenimiento y
los usuarios, cancela **cualquier** reserva y es el único que ve los reportes.

**No hay ningún sistema externo, y es deliberado.** `CLAUDE.md` §2 deja fuera de alcance de forma
explícita la pasarela de pagos, las notificaciones por correo, SMS o push, las reservas
recurrentes, los torneos y la app móvil nativa. El sistema no integra con nada fuera de sí mismo:
no hay proveedor de identidad externo, ni servicio de correo, ni API de terceros.

---

## Nivel 2 — Contenedores

```mermaid
flowchart TB
    NAV["Navegador<br/>[Chrome, Edge, Firefox]"]
    DEV["Desarrollador<br/>[acceso directo, no la aplicacion]"]

    subgraph FRONT["Microfrontends — React 18.3.1 + Webpack 5 (Module Federation)"]
        SHELL["shell :3000<br/>[host]<br/>Login, registro, menu y sesion.<br/>Monta los tres remotes."]
        MFRES["mf-reservas :3001<br/>[remote mfReservas]<br/>Disponibilidad, nueva reserva,<br/>mis reservas."]
        MFADM["mf-administracion :3002<br/>[remote mfAdministracion]<br/>Canchas, bloqueos, reservas<br/>de todos y usuarios."]
        MFREP["mf-reportes :3003<br/>[remote mfReportes]<br/>Ocupacion, reservas y<br/>cancelaciones. Solo ADMIN."]
    end

    GW["gateway :80<br/>[Nginx]<br/>Punto de entrada unico del trafico /api.<br/>Enruta por prefijo de dominio.<br/>Publicado en 8090 solo para verificacion."]

    subgraph BACK["Microservicios — Spring Boot 3.5.3, Java 21"]
        MSU["ms-usuarios :8080<br/>Registro, inicio de sesion,<br/>emision del JWT y gestion<br/>de usuarios."]
        MSC["ms-canchas :8080<br/>Catalogo de canchas, horario<br/>de atencion y bloqueos<br/>de mantenimiento."]
        MSR["ms-reservas :8080<br/>Disponibilidad, creacion,<br/>historial y cancelacion<br/>de reservas."]
        MSREP["ms-reportes :8080<br/>Ocupacion, reservas y<br/>cancelaciones por periodo.<br/><b>Sin base de datos.</b>"]
    end

    subgraph PG["postgres :5432 — PostgreSQL 16"]
        DBU[("usuarios_db<br/>usuario: usuarios_user")]
        DBC[("canchas_db<br/>usuario: canchas_user")]
        DBR[("reservas_db<br/>usuario: reservas_user")]
    end

    ADM["adminer :8081<br/>Cliente web de PostgreSQL.<br/>Herramienta de desarrollo."]

    NAV -->|"HTTPS/HTTP<br/>abre la aplicacion"| SHELL
    NAV -.->|"descarga remoteEntry.js"| MFRES
    NAV -.->|"descarga remoteEntry.js"| MFADM
    NAV -.->|"descarga remoteEntry.js"| MFREP

    MFRES -->|"rutas relativas /api<br/>montado en el shell"| SHELL
    MFADM -->|"rutas relativas /api<br/>montado en el shell"| SHELL
    MFREP -->|"rutas relativas /api<br/>montado en el shell"| SHELL

    SHELL -->|"devServer.proxy /api<br/>red interna de Docker"| GW

    GW -->|"/api/usuarios"| MSU
    GW -->|"/api/canchas"| MSC
    GW -->|"/api/reservas"| MSR
    GW -->|"/api/reportes"| MSREP

    MSU -->|"JDBC"| DBU
    MSC -->|"JDBC"| DBC
    MSR -->|"JDBC"| DBR

    MSR -->|"HTTP, token rol SERVICIO<br/>catalogo y bloqueos"| MSC
    MSREP -->|"HTTP, token rol SERVICIO"| MSC
    MSREP -->|"HTTP, token rol SERVICIO"| MSR

    DEV ==>|"Swagger UI y curl.exe<br/>8082 / 8083 / 8084 / 8085"| BACK
    DEV ==>|"curl.exe :8090"| GW
    DEV ==>|":8081"| ADM
    ADM ==>|"JDBC"| PG

    style SHELL fill:#1168bd,stroke:#0b4884,color:#ffffff
    style GW fill:#8b5a2b,stroke:#5c3c1d,color:#ffffff
    style MSREP fill:#438dd5,stroke:#2e6295,color:#ffffff
    style NAV fill:#08427b,stroke:#052e56,color:#ffffff
    style DEV fill:#6b6b6b,stroke:#404040,color:#ffffff
```

Los once servicios del `docker-compose.yml`. Las cinco relaciones que más se malinterpretan quedan
separadas a propósito:

- **El navegador descarga los `remoteEntry.js` directamente de `3001`, `3002` y `3003`** (líneas
  punteadas). Los estáticos de los remotes no pasan por el gateway.
- **Las llamadas a la API de los remotes van al shell, no a sus propios puertos.** Un remote
  montado en el shell hace `fetch("/api/...")` con ruta relativa, y el navegador la resuelve contra
  el origen del shell (`localhost:3000`). El `devServer.proxy` de cada remote solo interviene si
  alguien lo abre suelto en su puerto.
- **El shell proxya `/api` al gateway por la red interna de Docker** (`http://gateway:80`), no por
  un puerto del host.
- **`ms-reportes` no tiene base de datos.** Arma sus tres reportes consultando por HTTP a
  `ms-canchas` y `ms-reservas`.
- **No hay ni una flecha de un microservicio a la base de otro.** Cada base tiene su propio usuario
  de PostgreSQL, y la integración entre servicios es siempre REST (`CLAUDE.md` §3).

Las líneas gruesas son **acceso de desarrollo**: Swagger UI y `curl.exe` por los puertos
`8082`–`8085`, el `8090` del gateway y Adminer en `8081`. La aplicación no usa ninguno de ellos;
existen para documentación, verificación y demostración.

---

## Nivel 3 — Componentes

Un diagrama por microservicio y uno del shell. Las capas son las reales del repositorio, con la
cadena que fija `CLAUDE.md` §4: `controller` → `service` → `repository` → `entity`, con DTOs
separados de las entidades y mapper manual.

### ms-usuarios

```mermaid
flowchart TB
    subgraph MSU["ms-usuarios"]
        subgraph CFG["config"]
            FT["FiltroToken<br/>Valida el JWT entrante"]
            SEC["SeguridadConfig<br/>Rutas publicas y por rol"]
            OA["OpenApiConfig"]
        end
        CTRL["controller<br/><b>UsuarioController</b><br/>POST sesiones, POST usuarios,<br/>GET usuarios, PATCH estado"]
        subgraph SRV["service"]
            AUT["AutenticacionService<br/>Verifica credenciales BCrypt"]
            USR["UsuarioService<br/>Registro, listado, cambio de estado"]
            TOK["TokenService<br/>Firma y lectura del JWT"]
        end
        REPO["repository<br/><b>UsuarioRepository</b>"]
        ENT["entity<br/>Usuario, Rol"]
        MAP["mapper<br/>UsuarioMapper (manual)"]
        DTO["dto<br/>LoginRequest, LoginResponse,<br/>RegistroRequest, UsuarioResponse,<br/>CambioEstadoRequest, ErrorResponse"]
        EXC["exception<br/><b>ManejadorExcepciones</b><br/>CredencialesInvalidas, EmailDuplicado,<br/>UsuarioNoEncontrado, AutoInactivacion"]
    end
    DB[("usuarios_db")]

    FT --> CTRL
    CTRL --> AUT
    CTRL --> USR
    AUT --> TOK
    AUT --> REPO
    USR --> REPO
    REPO --> ENT
    CTRL --> MAP
    MAP --> DTO
    CTRL -.-> EXC
    REPO --> DB

    style CTRL fill:#438dd5,stroke:#2e6295,color:#ffffff
    style EXC fill:#b85450,stroke:#82302c,color:#ffffff
```

Es el único microservicio que **emite** tokens: `TokenService` los firma con `JWT_SECRET` tras que
`AutenticacionService` verifique la contraseña contra su hash BCrypt. `ManejadorExcepciones`
traduce toda excepción de negocio al formato `{codigo, mensaje}` del contrato; nunca sale un
stacktrace al cliente.

### ms-canchas

```mermaid
flowchart TB
    subgraph MSC["ms-canchas"]
        subgraph CFG["config"]
            FT["FiltroToken<br/>Acepta ADMIN, USUARIO y SERVICIO"]
            SEC["SeguridadConfig<br/>Escritura solo ADMIN"]
            OA["OpenApiConfig"]
        end
        subgraph CTRL["controller"]
            CC["CanchaController<br/>Catalogo y estado"]
            BC["BloqueoController<br/>Bloqueos de mantenimiento"]
        end
        subgraph SRV["service"]
            CS["CanchaService<br/>Filtra por rol: el USUARIO<br/>solo ve canchas activas"]
            BS["BloqueoService"]
            TOK["TokenService"]
        end
        subgraph REPO["repository"]
            CR["CanchaRepository"]
            BR["BloqueoRepository"]
        end
        ENT["entity<br/>Cancha, BloqueoMantenimiento, Deporte"]
        MAP["mapper<br/>CanchaMapper, BloqueoMapper"]
        DTO["dto<br/>CanchaRequest, CanchaResponse,<br/>BloqueoRequest, BloqueoResponse,<br/>CambioEstadoCanchaRequest, ErrorResponse"]
        EXC["exception<br/><b>ManejadorExcepciones</b><br/>NombreDuplicado, BloqueoDuplicado,<br/>CanchaNoEncontrada, HorarioInvalido,<br/>FueraDeHorario, FechaPasada, FormatoInvalido"]
    end
    DB[("canchas_db")]

    FT --> CC
    FT --> BC
    CC --> CS
    BC --> BS
    CS --> CR
    BS --> BR
    BS --> CR
    CR --> ENT
    BR --> ENT
    CC --> MAP
    BC --> MAP
    MAP --> DTO
    CC -.-> EXC
    BC -.-> EXC
    CR --> DB
    BR --> DB

    style CC fill:#438dd5,stroke:#2e6295,color:#ffffff
    style BC fill:#438dd5,stroke:#2e6295,color:#ffffff
    style EXC fill:#b85450,stroke:#82302c,color:#ffffff
```

Dos controladores porque son dos recursos del contrato: las canchas y sus bloqueos anidados.
`CanchaService` filtra por rol sin parámetro de consulta —el `ADMIN` recibe todas las canchas y el
`USUARIO` solo las activas—, y trata el rol `SERVICIO` como al `ADMIN`, que es lo que permite a
`ms-reservas` y `ms-reportes` ver el catálogo completo.

### ms-reservas

```mermaid
flowchart TB
    subgraph MSR["ms-reservas"]
        subgraph CFG["config"]
            FT["FiltroToken<br/>Rechaza un SERVICIO entrante"]
            SEC["SeguridadConfig"]
            CH["ClienteHttpConfig<br/>RestClient con timeouts"]
            OA["OpenApiConfig"]
        end
        CTRL["controller<br/><b>ReservaController</b><br/>disponibilidad, POST, GET,<br/>GET mias, PATCH cancelacion"]
        subgraph SRV["service"]
            RS["ReservaService<br/>RN-02 solapamiento, RN-03 propiedad,<br/>RN-04 reserva pasada, RN-06 limite"]
            DS["DisponibilidadService<br/>Arma los bloques de una fecha"]
            CLI["CanchasClient<br/>Llama a ms-canchas"]
            EMI["EmisorTokenServicio<br/>Emite el token rol SERVICIO"]
            TOK["TokenService"]
        end
        REPO["repository<br/><b>ReservaRepository</b>"]
        ENT["entity<br/>Reserva, EstadoReserva"]
        MAP["mapper<br/>ReservaMapper"]
        DTO["dto<br/>ReservaRequest, ReservaResponse,<br/>DisponibilidadResponse, BloqueResponse,<br/>CanchaExterna, BloqueoExterno, ErrorResponse"]
        EXC["exception<br/><b>ManejadorExcepciones</b><br/>BloqueOcupado, LimiteReservas,<br/>ReservaPasada, ReservaAjena,<br/>ReservaNoCancelable, CatalogoNoDisponible"]
    end
    DB[("reservas_db")]
    MSC["ms-canchas"]

    FT --> CTRL
    CTRL --> RS
    CTRL --> DS
    RS --> REPO
    DS --> REPO
    RS --> CLI
    DS --> CLI
    CLI --> EMI
    EMI --> TOK
    CLI --> CH
    REPO --> ENT
    CTRL --> MAP
    MAP --> DTO
    CTRL -.-> EXC
    REPO --> DB
    CLI -->|"HTTP, token rol SERVICIO<br/>GET canchas y bloqueos"| MSC

    style CTRL fill:#438dd5,stroke:#2e6295,color:#ffffff
    style CLI fill:#8b5a2b,stroke:#5c3c1d,color:#ffffff
    style EXC fill:#b85450,stroke:#82302c,color:#ffffff
```

Es donde viven las reglas de negocio: `ReservaService` implementa el solapamiento (RN-02), la
propiedad de la reserva (RN-03), la reserva ya ocurrida (RN-04) y el límite configurable de
reservas activas (RN-06). `CanchasClient` vive en `service/` y no en una capa `client/`, porque
este microservicio **sí** tiene base propia: la regla de `CLAUDE.md` §4 solo sustituye
`repository/` por `client/` en un microservicio sin base. `EmisorTokenServicio` acuña el token con
`rol = SERVICIO` para las llamadas salientes, y `FiltroToken` **rechaza** un `SERVICIO` entrante:
ningún cliente externo puede entrar con ese rol.

### ms-reportes

```mermaid
flowchart TB
    subgraph MSREP["ms-reportes — sin base de datos"]
        subgraph CFG["config"]
            FT["FiltroToken"]
            SEC["SeguridadConfig<br/>Las tres rutas solo ADMIN"]
            CH["ClienteHttpConfig<br/>RestClient con timeouts"]
            OA["OpenApiConfig"]
        end
        CTRL["controller<br/><b>ReporteController</b><br/>ocupacion, reservas, cancelaciones"]
        subgraph SRV["service"]
            RS["ReporteService<br/>Orquesta las dos llamadas<br/>y arma los tres reportes"]
            CO["CalculadoraOcupacion<br/>horasReservadas / horasDisponibles<br/>redondeo HALF_UP a un decimal"]
            EMI["EmisorTokenServicio"]
            TOK["TokenService"]
        end
        subgraph CLIENT["client — sustituye a repository"]
            CC["CanchasClient"]
            CR["ReservasClient"]
        end
        MAP["mapper<br/>ReporteMapper"]
        DTO["dto<br/>ReporteOcupacionResponse, OcupacionItem,<br/>ReporteReservasResponse, ReservasItem,<br/>ReporteCancelacionesResponse, CancelacionesItem,<br/>CanchaExterna, ReservaExterna, ErrorResponse"]
        EXC["exception<br/><b>ManejadorExcepciones</b><br/>RangoInvalido, CatalogoNoDisponible,<br/>ReservasNoDisponibles"]
    end
    MSC["ms-canchas"]
    MSR["ms-reservas"]

    FT --> CTRL
    CTRL --> RS
    RS --> CO
    RS --> CC
    RS --> CR
    CC --> EMI
    CR --> EMI
    EMI --> TOK
    CC --> CH
    CR --> CH
    CTRL --> MAP
    MAP --> DTO
    CTRL -.-> EXC
    CC -->|"HTTP, token rol SERVICIO"| MSC
    CR -->|"HTTP, token rol SERVICIO"| MSR

    style CTRL fill:#438dd5,stroke:#2e6295,color:#ffffff
    style CLIENT fill:#8b5a2b,stroke:#5c3c1d,color:#ffffff
    style EXC fill:#b85450,stroke:#82302c,color:#ffffff
```

El único microservicio con capa `client/` en lugar de `repository/` y `entity/`, tal como fija
`CLAUDE.md` §4 para un servicio sin base propia: la cadena es `controller` → `service` → `client`.
No persiste nada. Si `ms-canchas` o `ms-reservas` fallan o agotan el tiempo de espera, la respuesta
es `500 ERROR_INTERNO`; **nunca un reporte parcial**.

### shell (host de Module Federation)

```mermaid
flowchart TB
    subgraph SHELL["shell — host"]
        IDX["index.js<br/>Solo import de bootstrap"]
        BOOT["bootstrap.jsx<br/>createRoot"]
        APP["<b>App.jsx</b><br/>Estado de sesion y modulo activo.<br/>Decide que se muestra y valida el rol."]
        subgraph COMP["components"]
            PS["PantallaSesion<br/>Inicio de sesion"]
            PR["PantallaRegistro"]
            PB["PantallaBienvenida"]
            CAB["Cabecera<br/>Usuario, rol y cierre de sesion"]
            MEN["MenuModulos<br/>Solo los modulos del rol"]
            CR["<b>ContenedorRemoto</b><br/>React.lazy + Suspense<br/>Entrega las cuatro props"]
            BE["<b>BordeError</b><br/>componentDidCatch<br/>Uno por remote"]
            ME["MensajeError"]
        end
        subgraph API["api"]
            CLI["clienteApi<br/>Unica pieza que llama a fetch.<br/>Anade Authorization Bearer.<br/>Un 401 cierra la sesion."]
            UAPI["usuariosApi<br/>sesiones y registro"]
        end
        SES["sesion<br/>almacenSesion<br/>Lee, guarda y borra en sessionStorage"]
    end

    R1["mfReservas/ReservasApp<br/>localhost:3001"]
    R2["mfAdministracion/AdminApp<br/>localhost:3002"]
    R3["mfReportes/ReportesApp<br/>localhost:3003"]
    GW["gateway"]

    IDX --> BOOT
    BOOT --> APP
    APP --> PS
    APP --> PR
    APP --> PB
    APP --> CAB
    APP --> MEN
    APP --> BE
    BE --> CR
    PS --> UAPI
    PR --> UAPI
    UAPI --> CLI
    APP --> SES
    CLI --> SES
    PS -.-> ME
    PR -.-> ME
    BE -.-> ME
    CR -.->|"carga el modulo expuesto"| R1
    CR -.->|"carga el modulo expuesto"| R2
    CR -.->|"carga el modulo expuesto"| R3
    CLI -->|"rutas relativas /api<br/>devServer.proxy"| GW

    style APP fill:#1168bd,stroke:#0b4884,color:#ffffff
    style BE fill:#b85450,stroke:#82302c,color:#ffffff
    style CR fill:#438dd5,stroke:#2e6295,color:#ffffff
```

`App` es dueño del estado de sesión y del módulo activo, y valida el rol antes de montar nada:
el `USUARIO` solo ve Reservas. **`BordeError` envuelve a `ContenedorRemoto`, y hay uno por
remote**: es el único componente de clase del shell, con `componentDidCatch`, y si el
`remoteEntry.js` de un remote no se descarga o el módulo falla al renderizar muestra "Módulo no
disponible" **conservando la sesión y el menú** — los otros dos módulos siguen funcionando.
`clienteApi` es la única pieza que llama a `fetch`: añade el `Authorization: Bearer` y, ante un
`401` en una llamada ya autenticada, cierra la sesión.

Los tres remotes comparten esta misma estructura —`index.js`, `bootstrap.jsx`, el componente
expuesto, `components/`, `api/` y `estilos.css` con prefijo propio de clases—, pero **no** llevan
`sesion/`: reciben `usuario` y `token` por props del shell y nunca leen el almacenamiento del
navegador.

---

## Anexo — Diagrama de despliegue (figura 4 del informe)

Fuera de la numeración C4: los tres niveles anteriores describen la **arquitectura**; este anexo
describe **dónde corre cada cosa**. Sale íntegramente de `docker-compose.yml`.

```mermaid
flowchart TB
    NAV["<b>Navegador</b><br/>Chrome, Edge, Firefox"]
    DEVT["<b>Terminal / herramientas</b><br/>curl.exe, Swagger UI, cliente SQL"]

    subgraph HOST["Host Windows — Docker Desktop"]
        subgraph NET["Red proyecto-canchas_default (bridge)"]
            subgraph FRONT["Microfrontends — node:20-alpine, webpack serve"]
                SHELL["<b>shell</b><br/>canchas-shell<br/>escucha :3000"]
                MF1["<b>mf-reservas</b><br/>canchas-mf-reservas<br/>escucha :3001"]
                MF2["<b>mf-administracion</b><br/>canchas-mf-administracion<br/>escucha :3002"]
                MF3["<b>mf-reportes</b><br/>canchas-mf-reportes<br/>escucha :3003"]
            end

            GW["<b>gateway</b><br/>canchas-gateway<br/>nginx:alpine<br/>escucha :80 <i>solo interno</i>"]

            subgraph BACK["Microservicios — eclipse-temurin:21-jre-alpine"]
                MSU["<b>ms-usuarios</b><br/>canchas-ms-usuarios<br/>escucha :8080 <i>interno</i>"]
                MSC["<b>ms-canchas</b><br/>canchas-ms-canchas<br/>escucha :8080 <i>interno</i>"]
                MSR["<b>ms-reservas</b><br/>canchas-ms-reservas<br/>escucha :8080 <i>interno</i>"]
                MSP["<b>ms-reportes</b><br/>canchas-ms-reportes<br/>escucha :8080 <i>interno</i>"]
            end

            PG["<b>postgres</b><br/>canchas-postgres<br/>postgres:16-alpine<br/>escucha :5432"]
            ADM["<b>adminer</b><br/>canchas-adminer<br/>adminer:4<br/>escucha :8080"]
        end

        VOL[("<b>Volumen pgdata</b><br/>Datos de PostgreSQL.<br/>Sobrevive a docker compose down;<br/>se borra con down -v")]
        VOLN[("<b>4 volumenes anonimos</b><br/>shell_node_modules<br/>mf_reservas_node_modules<br/>mf_administracion_node_modules<br/>mf_reportes_node_modules")]
        BIND[["<b>Bind mounts</b><br/>./frontend/* con el codigo vivo<br/>./infra/nginx/gateway.conf :ro<br/>./infra/postgres/*.sql :ro"]]
    end

    NAV ==>|"3000:3000"| SHELL
    NAV ==>|"3001:3001"| MF1
    NAV ==>|"3002:3002"| MF2
    NAV ==>|"3003:3003"| MF3

    DEVT -.->|"8090:80"| GW
    DEVT -.->|"8082:8080"| MSU
    DEVT -.->|"8083:8080"| MSC
    DEVT -.->|"8084:8080"| MSR
    DEVT -.->|"8085:8080"| MSP
    DEVT -.->|"8081:8080"| ADM
    DEVT -.->|"5432:5432"| PG

    SHELL --->|"gateway:80"| GW
    MF1 --->|"gateway:80"| GW
    MF2 --->|"gateway:80"| GW
    MF3 --->|"gateway:80"| GW
    GW --->|"ms-usuarios:8080"| MSU
    GW --->|"ms-canchas:8080"| MSC
    GW --->|"ms-reservas:8080"| MSR
    GW --->|"ms-reportes:8080"| MSP
    MSU --->|"postgres:5432"| PG
    MSC --->|"postgres:5432"| PG
    MSR --->|"postgres:5432"| PG
    ADM --->|"postgres:5432"| PG

    PG --- VOL
    FRONT --- VOLN
    FRONT --- BIND
    GW --- BIND
    PG --- BIND

    style NAV fill:#08427b,stroke:#052e56,color:#ffffff
    style DEVT fill:#6b6b6b,stroke:#404040,color:#ffffff
    style SHELL fill:#1168bd,stroke:#0b4884,color:#ffffff
    style MF1 fill:#1168bd,stroke:#0b4884,color:#ffffff
    style MF2 fill:#1168bd,stroke:#0b4884,color:#ffffff
    style MF3 fill:#1168bd,stroke:#0b4884,color:#ffffff
    style GW fill:#8b5a2b,stroke:#5c3c1d,color:#ffffff
    style MSU fill:#438dd5,stroke:#2e6295,color:#ffffff
    style MSC fill:#438dd5,stroke:#2e6295,color:#ffffff
    style MSR fill:#438dd5,stroke:#2e6295,color:#ffffff
    style MSP fill:#438dd5,stroke:#2e6295,color:#ffffff
    style PG fill:#2e7d32,stroke:#1b4d20,color:#ffffff
    style ADM fill:#6b6b6b,stroke:#404040,color:#ffffff
    style VOL fill:#2e7d32,stroke:#1b4d20,color:#ffffff
```

Los once contenedores viven en una sola red bridge, `proyecto-canchas_default`, creada por Compose
a partir del nombre de la carpeta. Dentro de ella se llaman por **nombre de servicio**
(`gateway:80`, `ms-canchas:8080`, `postgres:5432`); fuera, solo existen los puertos publicados.

**Los tres tipos de acceso, marcados con trazos distintos:**

- **Flecha gruesa — lo que el navegador alcanza:** solo `3000`–`3003`. `3000` es la URL por la que
  se entra al sistema; `3001`, `3002` y `3003` existen porque el navegador descarga de ellos el
  `remoteEntry.js` de cada remote.
- **Flecha punteada — acceso de desarrollo:** `8090` del gateway (verificación y demostración),
  `8082`–`8085` (Swagger UI y `curl.exe`), `8081` (Adminer) y `5432` (cliente SQL). **La aplicación
  no usa ninguno de estos siete puertos.**
- **Flecha continua fina — tráfico interno de la red**, sin publicar: es el camino real de la
  aplicación en ejecución.

**Puertos que existen solo dentro de la red:** el gateway escucha en el **80**, y los cuatro
microservicios en el **8080**. Ninguno de esos cinco valores es alcanzable desde el host: lo que se
publica son los mapeos `8090:80` y `8082:8080` a `8085:8080`, es decir puertos del host distintos
apuntando al puerto interno. Un microservicio no es alcanzable "en el 8080" desde Windows, y el
gateway no es alcanzable "en el 80".

**Almacenamiento.** `pgdata` es el único volumen con datos que importan: sobrevive a
`docker compose down` y se borra con `down -v`, que es lo que hace que los scripts de
`infra/postgres/` vuelvan a ejecutarse. Los cuatro volúmenes de `node_modules` existen para que la
instalación de dependencias del contenedor no quede tapada por la carpeta inexistente del host. Los
bind mounts llevan el código vivo de los microfrontends —por eso `webpack serve` recompila al
guardar— y, de solo lectura, la configuración del gateway y los scripts SQL.

---

## Lo que estos diagramas no representan

- **El nivel 4 de C4 (código).** Clase por clase, con firmas y atributos, está en la sección de
  estructura de cada `design.md`.
- **El `depends_on` entre servicios**, es decir el orden de arranque: está en
  `docker-compose.yml` y explicado en el §8.2 del `design.md` de la spec 10. Los volúmenes y los
  puertos publicados sí están, en el anexo de despliegue.
- **El modelo de datos**: tablas, columnas y restricciones. Vive en `infra/postgres/` como DDL
  versionado y en el `design.md` de cada microservicio.
- **La secuencia temporal de un caso de uso.** Estos son diagramas estáticos: muestran qué habla
  con qué, no en qué orden. Un `C4Dynamic` de "crear una reserva" —el recorrido completo desde el
  navegador hasta la comprobación de solapamiento— no está en el alcance de este archivo.
