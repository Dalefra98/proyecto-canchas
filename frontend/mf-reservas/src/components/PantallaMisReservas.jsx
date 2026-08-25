import { useEffect, useRef, useState } from "react";
import { cancelarReserva, listarMisReservas } from "../api/reservasApi";
import ConfirmacionCancelacion from "./ConfirmacionCancelacion";
import FilaReserva from "./FilaReserva";
import MensajeError from "./MensajeError";

// HU-03: historial completo. Se pintan todas las reservas, en todos los estados,
// en el orden recibido (fecha y horaInicio descendentes, D-09 de la spec 04) y
// sin ningun filtro (P-05). El remote no reordena ni separa proximas de pasadas.
//
// La ruta no acepta parametros de filtrado ni de paginacion: el contrato no
// declara ninguno.
function PantallaMisReservas({ canchas, apiBaseUrl, token, ejecutar, onAviso }) {
  const [reservas, setReservas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [cancelacionPendiente, setCancelacionPendiente] = useState(null);
  const [cancelandoId, setCancelandoId] = useState(null);

  // El usuario puede cambiar de vista mientras una peticion viaja: sin esto se
  // escribiria estado de un componente que ya no esta en el arbol.
  const montado = useRef(true);
  useEffect(() => {
    return () => {
      montado.current = false;
    };
  }, []);

  async function cargarReservas() {
    setCargando(true);
    setError(null);

    const resultado = await ejecutar(() => listarMisReservas(apiBaseUrl, token));

    if (!montado.current) {
      return;
    }

    setCargando(false);
    setError(resultado.error);
    if (resultado.datos !== null) {
      setReservas(resultado.datos);
    }
  }

  useEffect(() => {
    cargarReservas();
  }, []);

  function pedirConfirmacion(id) {
    setCancelacionPendiente(id);
    setError(null);
  }

  // El usuario rechaza: no se llama a nada y el listado queda igual (HU-05).
  function rechazarConfirmacion() {
    setCancelacionPendiente(null);
  }

  async function confirmarCancelacion(id) {
    setCancelandoId(id);
    setError(null);

    // PATCH sin cuerpo (§5.6): la ruta ya expresa la operacion.
    const resultado = await ejecutar(() => cancelarReserva(apiBaseUrl, token, id));

    if (!montado.current) {
      return;
    }

    setCancelandoId(null);
    setCancelacionPendiente(null);

    if (resultado.datos !== null) {
      // RN-05: el bloque queda libre y vuelve a aparecer disponible en la grilla
      // de HU-01, que se reconsulta al volver a la vista de disponibilidad.
      onAviso(
        "Reserva " + resultado.datos.id + " cancelada (" + resultado.datos.estado + ")."
      );
      cargarReservas();
      return;
    }

    setError(resultado.error);

    // §7: RESERVA_PASADA, RESERVA_NO_CANCELABLE y NO_ENCONTRADO significan que lo
    // que se ve en pantalla ya no es lo que hay en el servidor, asi que se
    // recarga el listado. SIN_PERMISO no lo necesita: el listado es de reservas
    // propias y lo que llego sigue siendo valido.
    if (
      resultado.error !== null &&
      (resultado.error.codigo === "RESERVA_PASADA" ||
        resultado.error.codigo === "RESERVA_NO_CANCELABLE" ||
        resultado.error.codigo === "NO_ENCONTRADO")
    ) {
      cargarReservas();
    }
  }

  function buscarCancha(canchaId) {
    return canchas.find((cancha) => String(cancha.canchaId) === String(canchaId));
  }

  const reservaPorConfirmar =
    cancelacionPendiente === null
      ? undefined
      : reservas.find((reserva) => reserva.id === cancelacionPendiente);

  return (
    <div className="mfr-pantalla">
      <h3>Mis reservas</h3>

      {cargando ? <p className="mfr-cargando">Cargando reservas...</p> : null}
      <MensajeError error={error} />

      {reservaPorConfirmar ? (
        <ConfirmacionCancelacion
          reserva={reservaPorConfirmar}
          cancha={buscarCancha(reservaPorConfirmar.canchaId)}
          cancelando={cancelandoId === reservaPorConfirmar.id}
          onConfirmar={confirmarCancelacion}
          onRechazar={rechazarConfirmacion}
        />
      ) : null}

      {/* Un usuario sin reservas recibe un 200 con arreglo vacio, nunca un 404:
          se muestra el aviso de listado vacio y no un error (HU-03). */}
      {!cargando && error === null && reservas.length === 0 ? (
        <p className="mfr-listado-vacio">Todavia no tiene reservas.</p>
      ) : null}

      {reservas.length > 0 ? (
        <ul className="mfr-listado">
          {reservas.map((reserva) => (
            <FilaReserva
              key={reserva.id}
              reserva={reserva}
              cancha={buscarCancha(reserva.canchaId)}
              cancelando={cancelandoId === reserva.id}
              onCancelar={pedirConfirmacion}
            />
          ))}
        </ul>
      ) : null}
    </div>
  );
}

export default PantallaMisReservas;
