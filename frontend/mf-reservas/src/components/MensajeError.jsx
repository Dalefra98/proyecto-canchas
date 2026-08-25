// Pinta un error ya normalizado por clienteApi. No interpreta el codigo ni lo
// traduce: el mensaje se muestra tal como lo devolvio el microservicio (HU-09).
// Clase con prefijo mfr- para no chocar con el CSS global del shell (D-16).
function MensajeError({ error }) {
  if (!error) {
    return null;
  }

  return (
    <p className="mfr-mensaje-error" role="alert">
      {error.mensaje}
    </p>
  );
}

export default MensajeError;
