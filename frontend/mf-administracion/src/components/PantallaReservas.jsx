import { useEffect, useState } from "react";
import { listarCanchas } from "../api/canchasApi";
import { cancelarReserva, listarReservas } from "../api/reservasApi";
import { listarUsuarios } from "../api/usuariosApi";
import DialogoConfirmacion from "./DialogoConfirmacion";
import MensajeError from "./MensajeError";

// HU-08. Estado de la seccion 4.4 del diseño.
//
// P-04: filtro por estado con "Todos" por defecto, aplicado al pintar y no sobre
// el estado (D-09): GET /api/reservas no acepta parametros, asi que filtrar el
// arreglo obligaria a recargarlo entero en cada cambio de selector.
const OPCION_TODOS = "TODOS";
const ESTADOS = ["CONFIRMADA", "CANCELADA", "FINALIZADA"];

function PantallaReservas({ apiBaseUrl, token, ejecutar }) {
  const [reservas, setReservas] = useState([]);
  const [canchas, setCanchas] = useState([]);
  const [usuarios, setUsuarios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [errorApoyo, setErrorApoyo] = useState(null);
  const [filtroEstado, setFiltroEstado] = useState(OPCION_TODOS);
  const [confirmacion, setConfirmacion] = useState(null);
  const [idEnCancelacion, setIdEnCancelacion] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);
  const [aviso, setAviso] = useState(null);

  // Listado principal. Se muestra en el orden recibido: fecha descendente y,
  // dentro de la misma fecha, horaInicio descendente (D-09 de la spec 04). El
  // remote no reordena.
  async function cargarReservas() {
    setCargando(true);
    const resultado = await ejecutar(() => listarReservas(apiBaseUrl, token));
    if (resultado.datos !== null) {
      setReservas(resultado.datos);
    }
    setError(resultado.error);
    setCargando(false);
  }

  // D-13: las tres cargas salen en paralelo. Encadenarlas sumaria las tres
  // latencias y, peor, un fallo del catalogo impediria ver las reservas, que es
  // justo lo que HU-08 prohibe.
  useEffect(() => {
    let vigente = true;

    async function cargarAlMontar() {
      const promesaReservas = ejecutar(() => listarReservas(apiBaseUrl, token));
      const promesaCanchas = ejecutar(() => listarCanchas(apiBaseUrl, token));
      const promesaUsuarios = ejecutar(() => listarUsuarios(apiBaseUrl, token));

      const resultadoReservas = await promesaReservas;
      if (vigente) {
        if (resultadoReservas.datos !== null) {
          setReservas(resultadoReservas.datos);
        }
        setError(resultadoReservas.error);
        setCargando(false);
      }

      const resultadoCanchas = await promesaCanchas;
      const resultadoUsuarios = await promesaUsuarios;
      if (!vigente) {
        return;
      }
      if (resultadoCanchas.datos !== null) {
        setCanchas(resultadoCanchas.datos);
      }
      if (resultadoUsuarios.datos !== null) {
        setUsuarios(resultadoUsuarios.datos);
      }
      // Un fallo de una carga de apoyo se avisa, pero las reservas se muestran
      // igual con el identificador en lugar del nombre (HU-08).
      setErrorApoyo(resultadoCanchas.error || resultadoUsuarios.error);
    }

    cargarAlMontar();

    // El remote se puede desmontar mientras las peticiones viajan: sin esto se
    // escribiria estado de un componente que ya no esta en el arbol.
    return () => {
      vigente = false;
    };
  }, []);

  // D-11: se busca en la lista ya cargada, sin una llamada por fila. Si el
  // identificador no aparece, se muestra el numero tal cual: no se inventa un
  // nombre que la API no devolvio.
  function nombreDeCancha(canchaId) {
    const cancha = canchas.find((candidata) => candidata.canchaId === canchaId);
    return cancha ? cancha.nombre + " (" + cancha.deporte + ")" : String(canchaId);
  }

  function nombreDeUsuario(usuarioId) {
    const usuario = usuarios.find((candidato) => candidato.usuarioId === usuarioId);
    return usuario ? usuario.nombre : String(usuarioId);
  }

  // HU-08: solo las CONFIRMADA se pueden cancelar. Una CANCELADA ya lo esta y
  // una FINALIZADA ya ocurrio (RN-04). La validacion real es de ms-reservas:
  // ocultar un boton no es control de acceso.
  function pedirConfirmacion(reserva) {
    setConfirmacion(reserva);
    setErrorAccion(null);
    setAviso(null);
  }

  // Rechazar no hace ninguna llamada y el listado queda igual.
  function rechazarConfirmacion() {
    setConfirmacion(null);
  }

  async function confirmarCancelacion() {
    const reserva = confirmacion;
    setIdEnCancelacion(reserva.id);
    setErrorAccion(null);
    // Sin cuerpo: el contrato no declara ningun campo de entrada (seccion 5.5).
    const resultado = await ejecutar(() => cancelarReserva(apiBaseUrl, token, reserva.id));
    setIdEnCancelacion(null);
    setConfirmacion(null);

    if (resultado.error !== null) {
      // 409 RESERVA_PASADA, 409 RESERVA_NO_CANCELABLE y 404 refrescan el
      // listado: lo que se veia ya no era el estado real (seccion 7).
      setErrorAccion(resultado.error);
      cargarReservas();
      return;
    }

    setAviso("Reserva " + reserva.id + " cancelada.");
    cargarReservas();
  }

  // El filtro se aplica aqui, al pintar: reservas conserva todo lo recibido.
  const reservasVisibles =
    filtroEstado === OPCION_TODOS
      ? reservas
      : reservas.filter((reserva) => reserva.estado === filtroEstado);

  return (
    <section className="mfa-pantalla">
      <h3>Reservas</h3>

      <label>
        Estado
        <select value={filtroEstado} onChange={(evento) => setFiltroEstado(evento.target.value)}>
          <option value={OPCION_TODOS}>Todos</option>
          {/* Valores exactos del contrato, sin traducir ni abreviar. */}
          {ESTADOS.map((estado) => (
            <option key={estado} value={estado}>
              {estado}
            </option>
          ))}
        </select>
      </label>

      {aviso ? <p className="mfa-aviso-exito">{aviso}</p> : null}
      <MensajeError error={error} />
      <MensajeError error={errorApoyo} />
      <MensajeError error={errorAccion} />

      {confirmacion !== null ? (
        <DialogoConfirmacion
          mensaje={
            "Va a cancelar la reserva " +
            confirmacion.id +
            " de " +
            nombreDeUsuario(confirmacion.usuarioId) +
            " en " +
            nombreDeCancha(confirmacion.canchaId) +
            ", del " +
            confirmacion.fecha +
            " de " +
            confirmacion.horaInicio +
            " a " +
            confirmacion.horaFin +
            ". Esta accion no se puede deshacer."
          }
          textoConfirmar={
            idEnCancelacion === confirmacion.id ? "Cancelando..." : "Si, cancelar la reserva"
          }
          textoRechazar="No, volver al listado"
          enviando={idEnCancelacion !== null}
          onConfirmar={confirmarCancelacion}
          onRechazar={rechazarConfirmacion}
        />
      ) : null}

      {cargando ? <p>Cargando reservas...</p> : null}

      {!cargando && reservas.length === 0 ? <p>No hay reservas registradas.</p> : null}

      {!cargando && reservas.length > 0 && reservasVisibles.length === 0 ? (
        <p>Ninguna reserva con estado {filtroEstado}.</p>
      ) : null}

      {reservasVisibles.length > 0 ? (
        <table className="mfa-tabla">
          <thead>
            <tr>
              <th>id</th>
              <th>Usuario</th>
              <th>Cancha</th>
              <th>Fecha</th>
              <th>Desde</th>
              <th>Hasta</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {reservasVisibles.map((reserva) => (
              <tr key={reserva.id}>
                <td>{reserva.id}</td>
                <td>{nombreDeUsuario(reserva.usuarioId)}</td>
                <td>{nombreDeCancha(reserva.canchaId)}</td>
                <td>{reserva.fecha}</td>
                <td>{reserva.horaInicio}</td>
                <td>{reserva.horaFin}</td>
                <td>{reserva.estado}</td>
                <td>
                  {/* Solo las CONFIRMADA ofrecen la accion (HU-08). */}
                  {reserva.estado === "CONFIRMADA" ? (
                    <button
                      type="button"
                      disabled={idEnCancelacion === reserva.id || confirmacion !== null}
                      onClick={() => pedirConfirmacion(reserva)}
                    >
                      Cancelar
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

export default PantallaReservas;
