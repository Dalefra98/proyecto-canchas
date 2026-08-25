// P-09 y D-10: barra proporcional en CSS plano, sin librerias de graficos. El
// ancho es el propio porcentajeOcupacion sobre 100, tal como llega: no se
// redondea ni se recalcula, porque ms-reportes ya lo devolvio con un decimal y
// redondeo HALF_UP (contrato).
//
// La barra ACOMPANA al numero, no lo reemplaza: la celda de al lado sigue
// mostrando el valor. Por eso es decorativa para un lector de pantalla.
function BarraPorcentaje({ valor }) {
  return (
    <span className="mfrep-barra" aria-hidden="true">
      <span className="mfrep-barra-relleno" style={{ width: valor + "%" }} />
    </span>
  );
}

export default BarraPorcentaje;
