import MensajeError from "./MensajeError";

// D-08: un unico formulario para alta y edicion, distinguidos por formulario.modo.
// Los cuatro campos y sus validaciones son identicos; lo unico que cambia es el
// valor inicial y la ruta a la que lo envia PantallaCanchas.
//
// Es controlado: los campos viven en el estado de PantallaCanchas (seccion 4.2) y
// aqui solo se pintan. Este componente no llama a la API (seccion 8.1).
//
// P-10: se pinta en la misma pantalla del listado, que sigue visible.
const DEPORTES = ["PADEL", "TENIS", "BASQUET"];

function FormularioCancha({ formulario, error, enviando, onCambiar, onGuardar, onCancelar }) {
  function manejarEnvio(evento) {
    evento.preventDefault();
    onGuardar();
  }

  return (
    <form className="mfa-formulario" onSubmit={manejarEnvio}>
      <h4>{formulario.modo === "alta" ? "Nueva cancha" : "Editar cancha"}</h4>

      <label>
        Nombre
        <input
          type="text"
          value={formulario.nombre}
          maxLength={80}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("nombre", evento.target.value)}
        />
      </label>

      <label>
        Deporte
        <select
          value={formulario.deporte}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("deporte", evento.target.value)}
        >
          {/* Valores exactos del contrato: no hay texto libre. */}
          {DEPORTES.map((deporte) => (
            <option key={deporte} value={deporte}>
              {deporte}
            </option>
          ))}
        </select>
      </label>

      {/* type="time" entrega HH:mm, el formato congelado, sin formatear nada. */}
      <label>
        Hora de apertura
        <input
          type="time"
          value={formulario.horaApertura}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("horaApertura", evento.target.value)}
        />
      </label>

      <label>
        Hora de cierre
        <input
          type="time"
          value={formulario.horaCierre}
          required
          disabled={enviando}
          onChange={(evento) => onCambiar("horaCierre", evento.target.value)}
        />
      </label>

      {/* D-10: no se valida aqui que horaCierre sea posterior a horaApertura ni
          que el nombre no se repita. Esas reglas viven en ms-canchas y su 400 o
          su 409 se muestra aqui abajo, con el formulario abierto. */}
      <MensajeError error={error} />

      <div className="mfa-acciones">
        <button type="submit" disabled={enviando}>
          {formulario.modo === "alta" ? "Crear" : "Guardar"}
        </button>
        <button type="button" disabled={enviando} onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

export default FormularioCancha;
