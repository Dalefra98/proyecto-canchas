// Pinta un error ya normalizado por clienteApi. No interpreta el codigo ni lo
// traduce: el mensaje se muestra tal como lo devolvio el microservicio (HU-09).
// Clase con prefijo mfrep- para no chocar con el CSS del shell ni con el de los
// otros dos remotes (D-14).
function MensajeError({ error }) {
  if (!error) {
    return null;
  }

  return (
    <p className="mfrep-mensaje-error" role="alert">
      {error.mensaje}
    </p>
  );
}

export default MensajeError;
