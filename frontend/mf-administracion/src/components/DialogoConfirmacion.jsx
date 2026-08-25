// D-12: paso de confirmacion propio, no window.confirm, que bloquea el hilo, no
// se puede estilar y no muestra el detalle de la operacion.
//
// Reutilizado por los tres casos irreversibles del modulo: eliminar un bloqueo
// (HU-07), cancelar cualquier reserva (HU-08) y la advertencia de la
// autoinactivacion del propio ADMIN (P-06). Cada llamador arma su propio texto.
//
// No llama a la API: devuelve la respuesta del usuario a la pantalla.
function DialogoConfirmacion({
  mensaje,
  textoConfirmar,
  textoRechazar,
  enviando,
  onConfirmar,
  onRechazar
}) {
  return (
    <div className="mfa-confirmacion" role="alertdialog">
      <p>{mensaje}</p>

      <div className="mfa-acciones">
        <button type="button" disabled={enviando} onClick={onConfirmar}>
          {textoConfirmar}
        </button>
        <button type="button" disabled={enviando} onClick={onRechazar}>
          {textoRechazar}
        </button>
      </div>
    </div>
  );
}

export default DialogoConfirmacion;
