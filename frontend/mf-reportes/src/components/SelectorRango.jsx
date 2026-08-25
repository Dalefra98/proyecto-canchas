// HU-01: el rango es uno solo y vive fuera del menu interno, compartido por los
// tres reportes (P-03). Los dos campos son de tipo date, que entrega el valor en
// el formato AAAA-MM-DD del contrato sin que el remote lo reformatee.
//
// D-08: la unica validacion de cliente es estructural, y la hace ReportesApp
// antes de disparar la consulta: los dos campos obligatorios. NO se compara
// desde con hasta aqui. Esa regla vive en ms-reportes, que responde
// 400 DATOS_INVALIDOS con su propio mensaje, y duplicarla crearia dos fuentes de
// verdad.
function SelectorRango({ rango, cargando, onCambiarRango, onConsultar }) {
  return (
    <form
      className="mfrep-rango"
      onSubmit={(evento) => {
        evento.preventDefault();
        onConsultar();
      }}
    >
      <label>
        desde
        <input
          type="date"
          value={rango.desde}
          onChange={(evento) => onCambiarRango("desde", evento.target.value)}
        />
      </label>
      <label>
        hasta
        <input
          type="date"
          value={rango.hasta}
          onChange={(evento) => onCambiarRango("hasta", evento.target.value)}
        />
      </label>
      {/* Deshabilitado mientras la consulta viaja, para no encadenar llamadas
          repetidas (HU-01). */}
      <button type="submit" className="mfrep-boton-consultar" disabled={cargando}>
        {cargando ? "Consultando..." : "Consultar"}
      </button>
    </form>
  );
}

export default SelectorRango;
