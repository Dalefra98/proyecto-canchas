import { useState } from "react";
import { ErrorApi } from "./api/clienteApi";
import { iniciarSesion, registrarUsuario } from "./api/usuariosApi";
import * as almacenSesion from "./sesion/almacenSesion";
import PantallaSesion from "./components/PantallaSesion";
import PantallaRegistro from "./components/PantallaRegistro";
import Cabecera from "./components/Cabecera";
import MenuModulos, { modulosDelRol } from "./components/MenuModulos";
import PantallaBienvenida from "./components/PantallaBienvenida";
import ContenedorRemoto from "./components/ContenedorRemoto";

// D-08: el estado de la sesion vive aqui y baja por props.
// D-09: el mapeo de LoginResponse a la prop usuario son estos tres campos.
// email y activo llegan en la respuesta y se descartan (design 4.3).
function aSesion(loginResponse) {
  return {
    token: loginResponse.token,
    usuario: {
      usuarioId: loginResponse.usuario.usuarioId,
      nombre: loginResponse.usuario.nombre,
      rol: loginResponse.usuario.rol
    }
  };
}

function esLoginResponseValido(loginResponse) {
  return (
    loginResponse !== null &&
    typeof loginResponse === "object" &&
    typeof loginResponse.token === "string" &&
    loginResponse.token !== "" &&
    loginResponse.usuario !== null &&
    typeof loginResponse.usuario === "object" &&
    typeof loginResponse.usuario.rol === "string"
  );
}

function App() {
  // Rehidratacion en F5: si sessionStorage trae una sesion valida se entra
  // directo; si esta corrupta, almacenSesion la borra y devuelve null (D-06).
  const [sesion, setSesion] = useState(() => almacenSesion.leer());
  const [vista, setVista] = useState(() => (almacenSesion.leer() ? "bienvenida" : "sesion"));
  const [avisoSesion, setAvisoSesion] = useState(null);

  async function manejarInicioSesion(email, password) {
    const loginResponse = await iniciarSesion(email, password);

    // Un 200 que no trae token o rol es un fallo de integracion: no se abre
    // sesion (design 5.3).
    if (!esLoginResponseValido(loginResponse)) {
      throw new ErrorApi(200, "ERROR_INTERNO", "No se pudo contactar al servicio");
    }

    const sesionNueva = aSesion(loginResponse);
    almacenSesion.guardar(sesionNueva);
    setAvisoSesion(null);
    setSesion(sesionNueva);
    setVista("bienvenida");
  }

  // HU-02: el 201 no trae token, asi que no se abre sesion. Se vuelve al inicio
  // de sesion con el aviso de registro correcto.
  async function manejarRegistro(nombre, email, password) {
    await registrarUsuario(nombre, email, password);
    setAvisoSesion("Su cuenta fue creada. Ya puede iniciar sesion.");
    setVista("sesion");
  }

  function irARegistro() {
    setAvisoSesion(null);
    setVista("registro");
  }

  function irASesion() {
    setVista("sesion");
  }

  function cerrarSesionConAviso(aviso) {
    almacenSesion.borrar();
    setSesion(null);
    setVista("sesion");
    setAvisoSesion(aviso);
  }

  // HU-04. Sin parametros: es la funcion que va al onClick de Cabecera y a la
  // prop onLogout de los remotes, y React invoca ambos con el evento del clic.
  // Recibir ese evento como si fuera el aviso lo terminaba guardando en el
  // estado, y React no puede pintar un SyntheticEvent como texto.
  // Sin ninguna llamada HTTP: el contrato no declara ruta de cierre de sesion.
  function cerrarSesion() {
    cerrarSesionConAviso(null);
  }

  // P-08. Un 401 en una llamada ya autenticada significa token vencido: se
  // cierra la sesion con aviso. Hoy no tiene llamador dentro del shell, porque
  // sus dos rutas son publicas (design 6.1); lo estrenan los remotes al recibir
  // un 401 y llamar onLogout.
  function manejarSesionExpirada() {
    cerrarSesionConAviso("Su sesion expiro. Vuelva a iniciar sesion.");
  }

  function abrirModulo(clave) {
    setVista(clave);
  }

  function irAInicio() {
    setVista("bienvenida");
  }

  if (sesion === null) {
    if (vista === "registro") {
      return <PantallaRegistro onRegistrar={manejarRegistro} onVolver={irASesion} />;
    }

    return (
      <PantallaSesion
        aviso={avisoSesion}
        onIniciarSesion={manejarInicioSesion}
        onIrARegistro={irARegistro}
      />
    );
  }

  const usuario = sesion.usuario;

  // HU-05: un rol que no es ADMIN ni USUARIO no monta ningun remote. No deberia
  // ocurrir —ms-usuarios solo emite esos dos y SERVICIO nunca se persiste—, y
  // por eso se avisa en lugar de intentar adivinar un menu.
  if (!modulosDelRol(usuario.rol).length) {
    return (
      <div className="aplicacion">
        <Cabecera usuario={usuario} onCerrarSesion={cerrarSesion} />
        <p className="aviso" role="alert">
          El rol {usuario.rol} no tiene modulos asignados en esta aplicacion.
        </p>
      </div>
    );
  }

  // Se valida el rol antes de montar, no solo al pintar el menu: ocultar una
  // opcion no es control de acceso (HU-05).
  const moduloPedido = modulosDelRol(usuario.rol).find((modulo) => modulo.clave === vista);
  const vistaEfectiva = moduloPedido ? moduloPedido.clave : "bienvenida";

  return (
    <div className="aplicacion">
      <Cabecera usuario={usuario} onCerrarSesion={cerrarSesion} />
      <MenuModulos
        rol={usuario.rol}
        vista={vistaEfectiva}
        onAbrirModulo={abrirModulo}
        onIrAInicio={irAInicio}
      />
      {vistaEfectiva === "bienvenida" ? (
        <PantallaBienvenida usuario={usuario} />
      ) : (
        <ContenedorRemoto
          clave={vistaEfectiva}
          usuario={usuario}
          token={sesion.token}
          onLogout={cerrarSesion}
        />
      )}
    </div>
  );
}

export default App;
