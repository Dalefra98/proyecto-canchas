// Una reserva del listado. No llama a la API: avisa a la pantalla, que coordina
// la confirmacion y la cancelacion.
//
// §6.3: el estado recibido es la unica condicion para ofrecer cancelar, y la
// regla vive en un solo punto. Ocultar el boton no es control de acceso: RN-03 y
// RN-04 las aplica ms-reservas, que responde 403 o 409 si alguien lo intenta
// igual.
function FilaReserva({ reserva, cancha, cancelando, onCancelar }) {
  const cancelable = reserva.estado === "CONFIRMADA";

  return (
    <li className="mfr-fila">
      {/* HU-04: el canchaId se resuelve con el catalogo de ReservasApp. Si la
          cancha no esta —fue inactivada y GET /api/canchas ya no la devuelve, o
          el catalogo fallo— se muestra el canchaId tal cual, sin inventar un
          nombre. */}
      <span className="mfr-fila-cancha">
        {cancha ? cancha.nombre + " (" + cancha.deporte + ")" : "Cancha " + reserva.canchaId}
      </span>
      <span className="mfr-fila-fecha">{reserva.fecha}</span>
      <span className="mfr-fila-bloque">
        {reserva.horaInicio}–{reserva.horaFin}
      </span>
      {/* Los tres estados del contrato, sin traducir ni abreviar. FINALIZADA
          llega ya resuelta de ms-reservas (D-02 de la spec 04): aqui no se
          deduce comparando fechas. */}
      <span className="mfr-fila-estado">{reserva.estado}</span>

      {cancelable ? (
        <button
          type="button"
          className="mfr-boton-secundario"
          disabled={cancelando}
          onClick={() => onCancelar(reserva.id)}
        >
          {cancelando ? "Cancelando..." : "Cancelar"}
        </button>
      ) : null}
    </li>
  );
}

export default FilaReserva;
