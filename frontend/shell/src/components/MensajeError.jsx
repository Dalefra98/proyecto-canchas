// Pinta un error ya normalizado por clienteApi. No interpreta el codigo: de eso
// se encarga quien hizo la llamada (D-07).
function MensajeError({ error }) {
  if (!error) {
    return null;
  }

  return (
    <p className="mensaje-error" role="alert">
      {error.mensaje}
    </p>
  );
}

export default MensajeError;
