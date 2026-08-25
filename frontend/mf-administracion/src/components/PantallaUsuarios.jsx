import { useEffect, useState } from "react";
import { cambiarEstadoUsuario, listarUsuarios } from "../api/usuariosApi";
import DialogoConfirmacion from "./DialogoConfirmacion";
import MensajeError from "./MensajeError";

// HU-09. Estado de la seccion 4.5 del diseño.
//
// El contrato solo declara GET /api/usuarios y el PATCH de estado: aqui no se
// crean, ni se editan, ni se eliminan usuarios, y password no aparece en ninguna
// parte (no viene en UsuarioResponse y esta pantalla no lo pide).
function PantallaUsuarios({ usuario, apiBaseUrl, token, ejecutar }) {
  const [usuarios, setUsuarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [confirmacion, setConfirmacion] = useState(null);
  const [usuarioIdEnCambio, setUsuarioIdEnCambio] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);
  const [aviso, setAviso] = useState(null);

  async function cargarUsuarios() {
    setCargando(true);
    const resultado = await ejecutar(() => listarUsuarios(apiBaseUrl, token));
    if (resultado.datos !== null) {
      setUsuarios(resultado.datos);
    }
    setError(resultado.error);
    setCargando(false);
  }

  useEffect(() => {
    let vigente = true;

    async function cargarAlMontar() {
      const resultado = await ejecutar(() => listarUsuarios(apiBaseUrl, token));
      // El remote se puede desmontar mientras la peticion viaja: sin esto se
      // escribiria estado de un componente que ya no esta en el arbol.
      if (!vigente) {
        return;
      }
      if (resultado.datos !== null) {
        setUsuarios(resultado.datos);
      }
      setError(resultado.error);
      setCargando(false);
    }

    cargarAlMontar();

    return () => {
      vigente = false;
    };
  }, []);

  // D-14: se envia el valor contrario al que devolvio la API para esa fila, no
  // el de un interruptor con estado propio. Es activo, del usuario, no activa.
  async function cambiarEstado(usuarioDeLaFila) {
    setUsuarioIdEnCambio(usuarioDeLaFila.usuarioId);
    setErrorAccion(null);
    setAviso(null);
    const resultado = await ejecutar(() =>
      cambiarEstadoUsuario(
        apiBaseUrl,
        token,
        usuarioDeLaFila.usuarioId,
        !usuarioDeLaFila.activo
      )
    );
    setUsuarioIdEnCambio(null);
    setConfirmacion(null);

    if (resultado.error !== null) {
      setErrorAccion(resultado.error);
      // 404: el usuario ya no existe. Se refresca igual.
      if (resultado.error.codigo === "NO_ENCONTRADO") {
        cargarUsuarios();
      }
      return;
    }

    setAviso(
      "Usuario " +
        usuarioDeLaFila.nombre +
        (usuarioDeLaFila.activo ? " inactivado." : " activado.")
    );
    cargarUsuarios();
  }

  // P-06: la accion sobre la fila propia se ofrece igual —el contrato lo permite
  // y ocultarla seria inventar una regla que ningun microservicio aplica—, pero
  // pasa por una confirmacion que advierte la consecuencia. En cualquier otra
  // fila la llamada sale directa.
  function accionar(usuarioDeLaFila) {
    if (usuarioDeLaFila.usuarioId === usuario.usuarioId && usuarioDeLaFila.activo) {
      setConfirmacion(usuarioDeLaFila);
      setErrorAccion(null);
      setAviso(null);
      return;
    }
    cambiarEstado(usuarioDeLaFila);
  }

  // Rechazar no hace ninguna llamada y el listado queda igual.
  function rechazarConfirmacion() {
    setConfirmacion(null);
  }

  return (
    <section className="mfa-pantalla">
      <h3>Usuarios</h3>

      {aviso ? <p className="mfa-aviso-exito">{aviso}</p> : null}
      <MensajeError error={error} />
      <MensajeError error={errorAccion} />

      {confirmacion !== null ? (
        <DialogoConfirmacion
          mensaje={
            "Va a inactivar su propia cuenta (" +
            confirmacion.nombre +
            ", " +
            confirmacion.email +
            "). Quedara sin acceso al sistema y necesitara que otro administrador la reactive."
          }
          textoConfirmar={
            usuarioIdEnCambio === confirmacion.usuarioId
              ? "Inactivando..."
              : "Si, inactivar mi cuenta"
          }
          textoRechazar="No, volver al listado"
          enviando={usuarioIdEnCambio !== null}
          onConfirmar={() => cambiarEstado(confirmacion)}
          onRechazar={rechazarConfirmacion}
        />
      ) : null}

      {cargando ? <p>Cargando usuarios...</p> : null}

      {!cargando && usuarios.length === 0 ? <p>No hay usuarios registrados.</p> : null}

      {usuarios.length > 0 ? (
        <table className="mfa-tabla">
          <thead>
            <tr>
              <th>usuarioId</th>
              <th>Nombre</th>
              <th>Email</th>
              <th>Rol</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.map((usuarioDeLaFila) => (
              <tr key={usuarioDeLaFila.usuarioId}>
                <td>{usuarioDeLaFila.usuarioId}</td>
                <td>{usuarioDeLaFila.nombre}</td>
                <td>{usuarioDeLaFila.email}</td>
                <td>{usuarioDeLaFila.rol}</td>
                <td>{usuarioDeLaFila.activo ? "Activo" : "Inactivo"}</td>
                <td>
                  <button
                    type="button"
                    disabled={
                      usuarioIdEnCambio === usuarioDeLaFila.usuarioId || confirmacion !== null
                    }
                    onClick={() => accionar(usuarioDeLaFila)}
                  >
                    {usuarioDeLaFila.activo ? "Inactivar" : "Activar"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

export default PantallaUsuarios;
