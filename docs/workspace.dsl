workspace "Sistema de Reserva de Canchas Deportivas" "Modelo C4 del sistema: contexto, contenedores y componentes. Derivado de docker-compose.yml, docs/contratos/README.md y los design.md de las specs 04, 06 y 10." {

    model {

        usuario = person "Usuario final" "Consulta disponibilidad, crea sus reservas, cancela las propias y ve su historial."
        admin   = person "Administrador" "Gestiona el catalogo de canchas, sus horarios, los bloqueos de mantenimiento y los usuarios; cancela cualquier reserva y consulta los reportes."

        sistema = softwareSystem "Sistema de Reserva de Canchas" "Reserva de canchas de padel, tenis y basquet en bloques horarios de una hora, con catalogo, bloqueos de mantenimiento y reportes de ocupacion." {

            shell = container "shell" "Host de Module Federation. Inicio de sesion, registro, menu de modulos y estado de la sesion. Monta los tres remotes segun el rol." "React 18, Webpack 5 (host)" "Microfrontend" {
                pantallaSesion    = component "PantallaSesion" "Formulario de inicio de sesion." "React"
                pantallaRegistro  = component "PantallaRegistro" "Formulario de registro de un usuario nuevo." "React"
                pantallaBienvenida = component "PantallaBienvenida" "Pantalla inicial tras iniciar sesion." "React"
                cabecera          = component "Cabecera" "Nombre del usuario, su rol y el cierre de sesion." "React"
                menuModulos       = component "MenuModulos" "Menu de modulos. Solo muestra los que corresponden al rol." "React"
                contenedorRemoto  = component "ContenedorRemoto" "React.lazy y Suspense por modulo. Entrega al remote las cuatro props del contrato: usuario, token, apiBaseUrl y onLogout." "React"
                bordeError        = component "BordeError" "Borde de error por remote. Unico componente de clase: componentDidCatch. Si un remote no carga, muestra el aviso y conserva sesion y menu." "React"
                mensajeError      = component "MensajeError" "Presentacion uniforme de un error." "React"
                clienteApi        = component "clienteApi" "Unica pieza que llama a fetch. Anade el encabezado Authorization Bearer y ante un 401 en una llamada autenticada cierra la sesion." "JavaScript"
                usuariosApi       = component "usuariosApi" "Llamadas de sesion y registro." "JavaScript"
                almacenSesion     = component "almacenSesion" "Lee, guarda y borra la sesion en sessionStorage." "JavaScript"
            }

            mfReservas = container "mf-reservas" "Consulta de disponibilidad, creacion de reservas y historial propio." "React 18, Webpack 5 (remote mfReservas)" "Microfrontend"

            mfAdministracion = container "mf-administracion" "Gestion de canchas, bloqueos de mantenimiento, reservas de todos los usuarios y usuarios." "React 18, Webpack 5 (remote mfAdministracion)" "Microfrontend"

            mfReportes = container "mf-reportes" "Ocupacion por cancha, reservas y cancelaciones por periodo. Solo para el rol ADMIN." "React 18, Webpack 5 (remote mfReportes)" "Microfrontend"

            gateway = container "gateway" "Punto de entrada unico del trafico /api. Enruta por prefijo de dominio hacia los cuatro microservicios. Todo lo que no es /api responde 404." "Nginx" "Gateway"

            msUsuarios = container "ms-usuarios" "Registro, inicio de sesion y emision del JWT; listado de usuarios y cambio de su estado." "Spring Boot 3.5.3, Java 21" "Microservicio"

            msCanchas = container "ms-canchas" "Catalogo de canchas, horario de atencion y bloqueos de mantenimiento. Filtra por rol: el USUARIO solo ve canchas activas." "Spring Boot 3.5.3, Java 21" "Microservicio"

            msReservas = container "ms-reservas" "Disponibilidad por cancha y fecha, creacion de reservas, historial y cancelacion. Aqui viven las reglas de negocio." "Spring Boot 3.5.3, Java 21" "Microservicio" {
                filtroToken       = component "FiltroToken y SeguridadConfig" "Valida el JWT entrante y aplica los permisos por rol. Rechaza un token de rol SERVICIO entrante." "Spring Security"
                reservaController = component "ReservaController" "Disponibilidad, creacion, listado global, historial propio y cancelacion." "Spring MVC"
                reservaService    = component "ReservaService" "RN-02 solapamiento, RN-03 propiedad de la reserva, RN-04 reserva ya ocurrida y RN-06 limite de reservas activas." "Spring"
                disponibilidadService = component "DisponibilidadService" "Arma los bloques horarios de una cancha en una fecha." "Spring"
                canchasClient     = component "CanchasClient" "Unica pieza que llama a ms-canchas. Pide el catalogo y los bloqueos." "RestClient"
                emisorTokenServicio = component "EmisorTokenServicio" "Emite el token con rol SERVICIO para las llamadas salientes." "jjwt"
                reservaRepository = component "ReservaRepository" "Acceso a la tabla de reservas." "Spring Data JPA"
                reservaMapper     = component "ReservaMapper" "Mapeo manual entre entidad y DTO." "Java"
            }

            msReportes = container "ms-reportes" "Ocupacion, reservas y cancelaciones por periodo. No tiene base de datos: arma los reportes consultando por HTTP a ms-canchas y ms-reservas." "Spring Boot 3.5.3, Java 21" "Microservicio"

            usuariosDb = container "usuarios_db" "Usuarios, con la contrasena guardada como hash BCrypt, y su rol." "PostgreSQL 16" "Database"

            canchasDb = container "canchas_db" "Canchas con su deporte y horario de atencion, y bloqueos de mantenimiento." "PostgreSQL 16" "Database"

            reservasDb = container "reservas_db" "Reservas con su fecha, bloque horario y estado: CONFIRMADA, CANCELADA o FINALIZADA." "PostgreSQL 16" "Database"
        }

        # --- Actores ---------------------------------------------------------
        usuario -> shell "Consulta disponibilidad, reserva y cancela sus reservas" "HTTPS"
        admin   -> shell "Administra el catalogo y los usuarios, y consulta los reportes" "HTTPS"

        # --- Module Federation: el shell integra los tres remotes -------------
        # El navegador descarga el remoteEntry.js de cada remote de su propio
        # puerto (3001, 3002, 3003). Se modela como integracion del shell.
        contenedorRemoto -> mfReservas "Integra en tiempo de ejecucion (Module Federation)" "HTTP"
        contenedorRemoto -> mfAdministracion "Integra en tiempo de ejecucion (Module Federation)" "HTTP"
        contenedorRemoto -> mfReportes "Integra en tiempo de ejecucion (Module Federation)" "HTTP"

        # --- Los remotes llaman a la API a traves del shell -------------------
        # Montados en el shell, usan rutas relativas /api, que el navegador
        # resuelve contra el origen del shell.
        mfReservas       -> shell "Llama a la API por rutas relativas /api" "HTTP"
        mfAdministracion -> shell "Llama a la API por rutas relativas /api" "HTTP"
        mfReportes       -> shell "Llama a la API por rutas relativas /api" "HTTP"

        # --- Del shell al gateway --------------------------------------------
        clienteApi -> gateway "Proxya /api por la red interna de Docker" "HTTP"

        # --- El gateway reparte por prefijo -----------------------------------
        gateway -> msUsuarios "Enruta /api/usuarios" "HTTP"
        gateway -> msCanchas  "Enruta /api/canchas" "HTTP"
        gateway -> msReportes "Enruta /api/reportes" "HTTP"
        # La cuarta, hacia ms-reservas, se declara mas abajo contra
        # reservaController: Structurizr la resume a gateway -> ms-reservas en la
        # vista de Contenedores. Declararla dos veces dibujaria dos flechas.

        # --- Entre microservicios: siempre REST, nunca la base del otro -------
        canchasClient -> msCanchas "Pide el catalogo y los bloqueos, con token de rol SERVICIO" "HTTP"
        msReportes    -> msCanchas "Pide el catalogo, con token de rol SERVICIO" "HTTP"
        msReportes    -> msReservas "Pide las reservas del periodo, con token de rol SERVICIO" "HTTP"

        # --- Cada microservicio con base, a la suya y solo a la suya ----------
        msUsuarios        -> usuariosDb "Lee y escribe con el usuario usuarios_user" "JDBC"
        msCanchas         -> canchasDb "Lee y escribe con el usuario canchas_user" "JDBC"
        reservaRepository -> reservasDb "Lee y escribe con el usuario reservas_user" "JDBC"
        # ms-reportes no aparece aqui: no tiene base de datos.

        # --- Componentes del shell -------------------------------------------
        pantallaSesion   -> usuariosApi "Envia las credenciales"
        pantallaRegistro -> usuariosApi "Envia el registro"
        usuariosApi      -> clienteApi "Delega la llamada HTTP"
        pantallaSesion   -> almacenSesion "Guarda la sesion tras el 200"
        clienteApi       -> almacenSesion "Borra la sesion ante un 401"
        menuModulos      -> contenedorRemoto "Selecciona el modulo activo"
        bordeError       -> contenedorRemoto "Envuelve al contenedor: uno por remote"
        cabecera         -> almacenSesion "Cierra la sesion"
        pantallaSesion   -> mensajeError "Muestra el error de credenciales"
        pantallaRegistro -> mensajeError "Muestra el error de registro"
        bordeError       -> mensajeError "Muestra 'Modulo no disponible'"
        pantallaBienvenida -> menuModulos "Ofrece los modulos del rol"

        # --- Componentes de ms-reservas --------------------------------------
        gateway               -> reservaController "Enruta /api/reservas" "HTTP"
        filtroToken           -> reservaController "Deja pasar la peticion ya autenticada"
        reservaController     -> reservaService "Crear, listar y cancelar"
        reservaController     -> disponibilidadService "Consultar disponibilidad"
        reservaController     -> reservaMapper "Convierte entidad a DTO"
        reservaService        -> reservaRepository "Consulta y persiste reservas"
        disponibilidadService -> reservaRepository "Consulta las reservas del dia"
        reservaService        -> canchasClient "Valida que la cancha exista y este activa"
        disponibilidadService -> canchasClient "Pide horario de atencion y bloqueos"
        canchasClient         -> emisorTokenServicio "Pide el token de rol SERVICIO"
    }

    views {

        systemContext sistema "Contexto" "Los dos actores y el sistema como una sola caja. No hay sistemas externos: pagos, notificaciones, reservas recurrentes y torneos estan fuera de alcance." {
            include *
            autolayout lr
        }

        container sistema "Contenedores" "Los cuatro microfrontends, el gateway, los cuatro microservicios y las tres bases. ms-reportes no tiene base: consume a ms-canchas y ms-reservas por HTTP." {
            include *
            autolayout lr
        }

        component msReservas "ComponentesMsReservas" "Capas reales de ms-reservas: controller, service, repository, cliente HTTP, mapper y seguridad. CanchasClient vive en service porque este microservicio si tiene base propia." {
            include *
            autolayout lr
        }

        component shell "ComponentesShell" "Componentes reales del shell. BordeError envuelve a ContenedorRemoto, uno por remote: el fallo de un modulo no tumba la sesion ni los otros dos." {
            include *
            autolayout lr
        }

        styles {
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "Container" {
                color #ffffff
            }
            element "Microfrontend" {
                background #1168bd
                color #ffffff
                shape WebBrowser
            }
            element "Gateway" {
                background #8b5a2b
                color #ffffff
            }
            element "Microservicio" {
                background #438dd5
                color #ffffff
            }
            element "Database" {
                shape Cylinder
                background #2e7d32
                color #ffffff
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
        }
    }
}

# =============================================================================
# COMO USAR ESTE ARCHIVO
# =============================================================================
#
# 1. Abrir https://playground.structurizr.com en el navegador.
# 2. Borrar el contenido de ejemplo del editor de la izquierda.
# 3. Pegar este archivo completo.
# 4. El diagrama se dibuja solo. Si hay un error de sintaxis, el playground lo
#    senala con el numero de linea.
# 5. Cambiar de vista con el desplegable de la parte superior. Hay cuatro:
#       Contexto              -> nivel 1 de C4
#       Contenedores          -> nivel 2 de C4
#       ComponentesMsReservas -> nivel 3, un microservicio por dentro
#       ComponentesShell      -> nivel 3, el host de Module Federation por dentro
# 6. Exportar con el boton de descarga: PNG o SVG.
#
# -----------------------------------------------------------------------------
# POR QUE ESTE ARCHIVO Y ADEMAS docs/diagramas-c4.md
# -----------------------------------------------------------------------------
#
# Los dos describen el mismo sistema y ninguno sustituye al otro:
#
#   - Structurizr (este archivo): el modelo se define UNA vez y las cuatro
#     vistas se derivan de el. Anadir un contenedor o una relacion actualiza
#     todas las vistas a la vez. Es la fuente para revisar la arquitectura.
#   - Mermaid (docs/diagramas-c4.md): se incrusta en Markdown y se renderiza en
#     GitHub y en cualquier visor, y de ahi salen las imagenes para el informe
#     en Word, donde Structurizr no se puede incrustar.
#
# Si se modifica la arquitectura hay que tocar los dos.
#
# -----------------------------------------------------------------------------
# DOS DETALLES DEL MODELO QUE CONVIENE SABER AL LEERLO
# -----------------------------------------------------------------------------
#
# 1. Cinco relaciones estan declaradas desde un COMPONENTE y no desde su
#    contenedor: clienteApi -> gateway, contenedorRemoto -> cada remote,
#    gateway -> reservaController, canchasClient -> msCanchas y
#    reservaRepository -> reservasDb. Structurizr las resume automaticamente al
#    nivel de contenedor en la vista de Contenedores, donde se ven como
#    shell -> gateway, shell -> cada remote, gateway -> ms-reservas,
#    ms-reservas -> ms-canchas y ms-reservas -> reservas_db.
#
#    Declararlas al nivel mas fino permite que aparezcan tambien en las vistas
#    de componentes. Lo que NO hay que hacer es declararlas dos veces, una al
#    componente y otra al contenedor: eso dibuja dos flechas paralelas entre los
#    mismos dos contenedores.
#
# 2. NO hay ninguna flecha de un microservicio hacia la base de otro, y
#    ms-reportes no tiene ninguna flecha hacia PostgreSQL. Es la regla de
#    CLAUDE.md seccion 3: cada microservicio tiene su base y su usuario, y la
#    integracion entre servicios es siempre REST. Si alguna vez aparece una
#    flecha asi en el diagrama, es que alguien rompio esa regla en el codigo.
