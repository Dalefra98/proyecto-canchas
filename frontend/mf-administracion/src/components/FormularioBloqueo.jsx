import MensajeError from "./MensajeError";

// HU-06. Los cuatro campos de la seccion 5.3. No lleva selector de cancha: la
// cancha es la seleccionada en el listado de al lado (P-01, D-17), y canchaId
// viaja en la ruta, no en el cuerpo.
//
// Es controlado: los campos viven en el estado de PanelBloqueos (seccion 4.3).
// Este componente no llama a la API.
function FormularioBloqueo({ formulario, error, enviando, onCambiar, onGuardar, onCancelar }) {
  function manejarEnvio(evento) {
    evento.preventDefault();
    onGuardar();
  }

  return (
    <form className="mfa-formulario" onSubmit={manejarEnvio}>
      <h5>Nuevo bloqueo</h5>

      {/* type="date" entrega AAAA-MM-DD y no permite un dia inexistente como
          2026-02-31; el parseo estricto lo repite ms-canchas (D-04 spec 03). */}
      <label>
        Fecha
        <input
          type="date"
          value={formulario.fecha}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("fecha", evento.target.value)}
        />
      </label>

      <label>
        Hora de inicio
        <input
          type="time"
          value={formulario.horaInicio}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("horaInicio", evento.target.value)}
        />
      </label>

      <label>
        Hora de fin
        <input
          type="time"
          value={formulario.horaFin}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("horaFin", evento.target.value)}
        />
      </label>

      <label>
        Motivo
        <input
          type="text"
          value={formulario.motivo}
          maxLength={200}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("motivo", evento.target.value)}
        />
      </label>

      {/* D-10: no se comprueba aqui el orden de la franja ni el solapamiento con
          los bloqueos ya listados. Esas reglas viven en ms-canchas y llegan como
          400 DATOS_INVALIDOS o 409 BLOQUEO_DUPLICADO. */}
      <MensajeError error={error} />

      <div className="mfa-acciones">
        <button type="submit" disabled={enviando}>
          Registrar bloqueo
        </button>
        <button type="button" disabled={enviando} onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

export default FormularioBloqueo;
