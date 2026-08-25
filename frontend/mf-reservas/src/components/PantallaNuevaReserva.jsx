import { useState } from "react";
import { crearReserva } from "../api/reservasApi";
import MensajeError from "./MensajeError";

// HU-02, P-01: la pantalla llega precargada desde el bloque elegido en la grilla
// y no permite editar ni un dato. Solo dos acciones: confirmar o volver.
//
// D-10: no vuelve a consultar la disponibilidad para comprobar que el bloque
// sigue libre. Una reconsulta no da ninguna garantia: entre ella y el POST el
// bloque puede ocuparse igual. La garantia real es el 409 BLOQUE_OCUPADO de
// ms-reservas.
function PantallaNuevaReserva({
  reservaPendiente,
  cancha,
  apiBaseUrl,
  token,
  ejecutar,
  onCreada,
  onVolver,
  onRefrescarGrilla
}) {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState(null);

  async function confirmar() {
    setEnviando(true);
    setError(null);

    // Cuerpo de tres campos (§5.2): horaFin lo calcula ms-reservas, usuarioId
    // sale del token y estado lo fija el servicio.
    const resultado = await ejecutar(() =>
      crearReserva(
        apiBaseUrl,
        token,
        reservaPendiente.canchaId,
        reservaPendiente.fecha,
        reservaPendiente.horaInicio
      )
    );

    if (resultado.datos !== null) {
      // 201: el aviso, la vuelta a la grilla y su reconsulta los hace
      // ReservasApp (D-11). No se toca "enviando": este componente se desmonta.
      onCreada(resultado.datos);
      return;
    }

    setEnviando(false);
    setError(resultado.error);

    // §7: el bloque ya reservado o bajo mantenimiento refresca la grilla, que es
    // donde se ve el bloque ya ocupado (RN-02). El mensaje se muestra aqui, que
    // es donde esta el usuario; la grilla queda reconsultada al volver.
    if (resultado.error !== null && resultado.error.codigo === "BLOQUE_OCUPADO") {
      onRefrescarGrilla();
    }
  }

  return (
    <div className="mfr-pantalla">
      <h3>Nueva reserva</h3>

      {/* HU-04: si la cancha no esta en el catalogo, se muestra el canchaId tal
          cual en vez de inventar un nombre. */}
      <dl className="mfr-resumen">
        <dt>Cancha</dt>
        <dd>{cancha ? cancha.nombre : "Cancha " + reservaPendiente.canchaId}</dd>

        <dt>Deporte</dt>
        <dd>{cancha ? cancha.deporte : "—"}</dd>

        <dt>Fecha</dt>
        <dd>{reservaPendiente.fecha}</dd>

        <dt>Bloque</dt>
        <dd>
          {reservaPendiente.horaInicio}–{reservaPendiente.horaFin}
        </dd>
      </dl>

      <MensajeError error={error} />

      <div className="mfr-acciones">
        {/* Deshabilitado mientras la peticion viaja: un doble clic no puede
            intentar dos veces el mismo bloque (HU-02). */}
        <button type="button" className="mfr-boton" disabled={enviando} onClick={confirmar}>
          {enviando ? "Confirmando..." : "Confirmar reserva"}
        </button>
        <button type="button" className="mfr-boton-secundario" onClick={onVolver}>
          Volver a la grilla
        </button>
      </div>
    </div>
  );
}

export default PantallaNuevaReserva;
