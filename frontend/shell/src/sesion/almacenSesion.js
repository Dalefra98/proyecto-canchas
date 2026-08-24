// P-03: la sesion vive en sessionStorage. localStorage no se usa en ninguna
// parte del shell. El password nunca se guarda aqui.

const CLAVE_TOKEN = "canchas.token";
const CLAVE_USUARIO = "canchas.usuario";

const ROLES_VALIDOS = ["ADMIN", "USUARIO"];

function esUsuarioValido(usuario) {
  return (
    usuario !== null &&
    typeof usuario === "object" &&
    typeof usuario.usuarioId === "number" &&
    typeof usuario.nombre === "string" &&
    usuario.nombre !== "" &&
    ROLES_VALIDOS.includes(usuario.rol)
  );
}

export function borrar() {
  sessionStorage.removeItem(CLAVE_TOKEN);
  sessionStorage.removeItem(CLAVE_USUARIO);
}

// D-06: sessionStorage es editable a mano. Si falta una clave, el JSON esta
// corrupto o el rol no es valido, se borran las dos y se devuelve null: la
// sesion degrada a "sin sesion" en lugar de dejar la interfaz en un estado
// imposible.
export function leer() {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  const usuarioCrudo = sessionStorage.getItem(CLAVE_USUARIO);

  if (!token || !usuarioCrudo) {
    borrar();
    return null;
  }

  let usuario;
  try {
    usuario = JSON.parse(usuarioCrudo);
  } catch (error) {
    borrar();
    return null;
  }

  if (!esUsuarioValido(usuario)) {
    borrar();
    return null;
  }

  return {
    token: token,
    usuario: {
      usuarioId: usuario.usuarioId,
      nombre: usuario.nombre,
      rol: usuario.rol
    }
  };
}

export function guardar(sesion) {
  sessionStorage.setItem(CLAVE_TOKEN, sesion.token);
  sessionStorage.setItem(
    CLAVE_USUARIO,
    JSON.stringify({
      usuarioId: sesion.usuario.usuarioId,
      nombre: sesion.usuario.nombre,
      rol: sesion.usuario.rol
    })
  );
}
