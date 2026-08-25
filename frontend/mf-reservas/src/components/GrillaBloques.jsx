// HU-01: pinta los bloques de una hora de la respuesta de disponibilidad.
//
// No llama a la API y no recalcula nada: los bloques se pintan en el orden
// recibido (§5.3) y el estado visual sale de "disponible" tal como llego. El
// horario del encabezado es horaApertura-horaCierre de la respuesta, nunca un
// rango fijo en codigo.
function GrillaBloques({ disponibilidad, nombreCancha, onElegirBloque }) {
  // Solo un bloque con disponible === true se puede elegir (§5.3). Los ocupados
  // van deshabilitados: es la barrera de presentacion de RN-02, la de verdad la
  // aplica ms-reservas con su 409 BLOQUE_OCUPADO.
  function elegir(bloque) {
    if (!bloque.disponible || typeof onElegirBloque !== "function") {
      return;
    }
    onElegirBloque(bloque);
  }

  return (
    <div className="mfr-grilla">
      <h4 className="mfr-grilla-encabezado">
        {nombreCancha} — {disponibilidad.fecha} — horario {disponibilidad.horaApertura} a{" "}
        {disponibilidad.horaCierre}
      </h4>

      {disponibilidad.bloques.length === 0 ? (
        <p className="mfr-grilla-vacia">La cancha no tiene bloques para esta fecha.</p>
      ) : (
        <ul className="mfr-grilla-bloques">
          {disponibilidad.bloques.map((bloque) => (
            <li key={bloque.horaInicio}>
              <button
                type="button"
                className={
                  bloque.disponible ? "mfr-bloque mfr-bloque-libre" : "mfr-bloque mfr-bloque-ocupado"
                }
                disabled={!bloque.disponible}
                onClick={() => elegir(bloque)}
              >
                <span className="mfr-bloque-horas">
                  {bloque.horaInicio}–{bloque.horaFin}
                </span>
                <span className="mfr-bloque-estado">
                  {bloque.disponible ? "Libre" : "Ocupado"}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default GrillaBloques;
