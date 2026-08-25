import { useEffect, useState } from "react";
import { obtenerOcupacion } from "../api/reportesApi";
import BarraPorcentaje from "./BarraPorcentaje";
import IndicadorDemanda from "./IndicadorDemanda";
import MensajeError from "./MensajeError";

// HU-02: porcentaje de ocupacion por cancha en el rango consultado. Solo lectura:
// pide su ruta y pinta lo que llega, sin recalcular nada.
//
// Seccion 4.2: guarda reporte y error. El cargando NO es suyo: vive en
// ReportesApp y se enciende y apaga con el onCargando recibido (D-16).
function PantallaOcupacion({ consulta, apiBaseUrl, token, ejecutar, onCargando }) {
  const [reporte, setReporte] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    // P-02: sin consulta no sale ninguna llamada. Es el estado al montar y
    // tambien al volver a esta pantalla desde otra (D-05).
    if (consulta === null) {
      return undefined;
    }

    // Si el administrador vuelve a consultar antes de que llegue la respuesta
    // anterior, esta bandera descarta la vieja: sin ella, la respuesta que
    // termine ultima pintaria, aunque sea la de un rango que ya cambio.
    let vigente = true;

    async function consultarOcupacion() {
      onCargando(true);
      const resultado = await ejecutar(() =>
        obtenerOcupacion(apiBaseUrl, token, consulta.desde, consulta.hasta)
      );

      if (!vigente) {
        return;
      }

      onCargando(false);

      // D-07: un error NO borra el reporte anterior. Se muestran los dos, y la
      // etiqueta del periodo que devolvio la respuesta impide confundirlos.
      // Ante un 401, ejecutar ya llamo a onLogout y devuelve datos y error en
      // null: no se pinta nada porque el remote se esta desmontando (seccion 4.1).
      if (resultado.error !== null) {
        setError(resultado.error);
        return;
      }

      if (resultado.datos !== null) {
        setReporte(resultado.datos);
        setError(null);
      }
    }

    consultarOcupacion();

    return () => {
      vigente = false;
    };
  }, [consulta]);

  return (
    <section className="mfrep-pantalla">
      <h3>Ocupacion por cancha</h3>
      <MensajeError error={error} />

      {reporte === null ? (
        <p className="mfrep-vacio">Elija un rango de fechas y pulse Consultar.</p>
      ) : (
        <>
          {/* HU-10: el periodo que se muestra es el que DEVOLVIO la respuesta, no
              el que esta escrito en los campos del selector. */}
          <p className="mfrep-periodo">
            Periodo consultado: {reporte.desde} a {reporte.hasta}
          </p>

          {reporte.items.length === 0 ? (
            <p className="mfrep-vacio">
              No hay canchas para mostrar en este periodo.
            </p>
          ) : (
            <>
              <IndicadorDemanda
                items={reporte.items}
                campo="porcentajeOcupacion"
                etiqueta="% de ocupacion"
              />

              <table className="mfrep-tabla">
                <thead>
                  <tr>
                    <th scope="col">canchaId</th>
                    <th scope="col">nombre</th>
                    <th scope="col">deporte</th>
                    <th scope="col">horasReservadas</th>
                    <th scope="col">horasDisponibles</th>
                    <th scope="col">porcentajeOcupacion</th>
                  </tr>
                </thead>
                <tbody>
                  {reporte.items.map((item) => (
                    <tr key={item.canchaId}>
                      <td>{item.canchaId}</td>
                      <td>{item.nombre}</td>
                      {/* Los tres valores del contrato, sin traducir ni abreviar. */}
                      <td>{item.deporte}</td>
                      <td className="mfrep-numero">{item.horasReservadas}</td>
                      <td className="mfrep-numero">{item.horasDisponibles}</td>
                      <td className="mfrep-celda-porcentaje">
                        {/* El numero tal como llega, con su decimal: el redondeo
                            HALF_UP ya lo hizo ms-reportes (HU-02). */}
                        <span className="mfrep-numero">{item.porcentajeOcupacion}</span>
                        <BarraPorcentaje valor={item.porcentajeOcupacion} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* HU-02: sin esta nota, horasDisponibles se lee como "horas
                  realmente ofertadas". */}
              <p className="mfrep-nota">
                horasDisponibles es (horaCierre - horaApertura) por el numero de
                dias del rango, y no descuenta los bloqueos de mantenimiento.
              </p>
            </>
          )}
        </>
      )}
    </section>
  );
}

export default PantallaOcupacion;
