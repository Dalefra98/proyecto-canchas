// HU-05 y P-08 de la spec 05: el listado de canchas con mayor y menor demanda no
// tiene endpoint propio. Se resuelve ordenando en el cliente los items ya
// recibidos, sin llamar a ninguna ruta nueva.
//
// D-09: ordena una COPIA al pintar. El estado de la pantalla conserva el orden
// del catalogo que devolvio ms-reportes (D-10 de la spec 05), asi que ordenar el
// estado cambiaria tambien la tabla.
//
// D-11: usa solo el campo que recibe, tal como llega. No mezcla metricas ni
// inventa una formula propia. Ocupacion usa porcentajeOcupacion y reservas usa
// totalReservas (P-05).
function canchasConValor(items, campo, valor) {
  // P-06: todas las empatadas, no solo la primera. Ocultar un empate seria dar
  // un dato incompleto.
  return items.filter((item) => item[campo] === valor);
}

function listaDeNombres(items) {
  return items.map((item) => item.nombre).join(", ");
}

function IndicadorDemanda({ items, campo, etiqueta }) {
  // Con items vacio no se pinta nada: la pantalla ya muestra su aviso de reporte
  // sin datos y aqui no hay ninguna cancha que destacar (HU-05).
  if (items.length === 0) {
    return null;
  }

  const valores = items.map((item) => item[campo]);
  const maximo = Math.max(...valores);
  const minimo = Math.min(...valores);

  const mayores = canchasConValor(items, campo, maximo);
  const menores = canchasConValor(items, campo, minimo);

  return (
    <dl className="mfrep-demanda">
      <div className="mfrep-demanda-fila">
        <dt>Mayor demanda</dt>
        {/* Con una sola cancha en items, esa misma es la mayor y la menor, y asi
            se muestra: el indicador no se oculta por eso (HU-05). */}
        <dd>
          {listaDeNombres(mayores)} ({maximo} {etiqueta})
        </dd>
      </div>
      <div className="mfrep-demanda-fila">
        <dt>Menor demanda</dt>
        <dd>
          {listaDeNombres(menores)} ({minimo} {etiqueta})
        </dd>
      </div>
    </dl>
  );
}

export default IndicadorDemanda;
