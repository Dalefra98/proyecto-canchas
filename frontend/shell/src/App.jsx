import { useState } from "react";
import { ErrorApi } from "./api/clienteApi";
import { iniciarSesion, registrarUsuario } from "./api/usuariosApi";
import * as almacenSesion from "./sesion/almacenSesion";
import PantallaSesion from "./components/PantallaSesion";
import PantallaRegistro from "./components/PantallaRegistro";

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

  // Vista provisional: la cabecera, el menu por rol y la pantalla de bienvenida
  // son de la tarea T5.
  return (
    <main>
      <h1>Reserva de Canchas Deportivas</h1>
      <p>
        Sesion activa de {sesion.usuario.nombre} ({sesion.usuario.rol}). Vista actual: {vista}.
      </p>
    </main>
  );
}

export default App;
