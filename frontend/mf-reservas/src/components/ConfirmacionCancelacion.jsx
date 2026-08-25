// P-06, D-12: paso de confirmacion propio, no window.confirm. Cancelar es
// irreversible y el segundo intento responde 409 RESERVA_NO_CANCELABLE, asi que
// la confirmacion muestra la cancha, la fecha y el bloque de la reserva para que
// el usuario vea cual esta cancelando.
//
// No llama a la API: devuelve la respuesta del usuario a la pantalla.
function ConfirmacionCancelacion({ reserva, cancha, cancelando, onConfirmar, onRechazar }) {
  return (
    <div className="mfr-confirmacion" role="alertdialog">
      <p>
        Va a cancelar la reserva de{" "}
        <strong>
          {cancha ? cancha.nombre + " (" + cancha.deporte + ")" : "Cancha " + reserva.canchaId}
        </strong>{" "}
        del {reserva.fecha}, de {reserva.horaInicio} a {reserva.horaFin}. Esta accion no se puede
        deshacer.
      </p>

      <div className="mfr-acciones">
        <button
          type="button"
          className="mfr-boton"
          disabled={cancelando}
          onClick={() => onConfirmar(reserva.id)}
        >
          {cancelando ? "Cancelando..." : "Si, cancelar la reserva"}
        </button>
        <button
          type="button"
          className="mfr-boton-secundario"
          disabled={cancelando}
          onClick={onRechazar}
        >
          No, volver al listado
        </button>
      </div>
    </div>
  );
}

export default ConfirmacionCancelacion;
