// Pinta un error ya normalizado por clienteApi. No interpreta el codigo ni lo
// traduce: el mensaje se muestra tal como lo devolvio el microservicio (HU-13).
// Clase con prefijo mfa- para no chocar con el CSS del shell ni con el de
// mf-reservas (D-16).
function MensajeError({ error }) {
  if (!error) {
    return null;
  }

  return (
    <p className="mfa-mensaje-error" role="alert">
      {error.mensaje}
    </p>
  );
}

export default MensajeError;
