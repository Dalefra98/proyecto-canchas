import { useEffect, useState } from "react";
import { obtenerReservas } from "../api/reportesApi";
import IndicadorDemanda from "./IndicadorDemanda";
import MensajeError from "./MensajeError";

// HU-03: numero de reservas por cancha y por deporte en el rango consultado.
// Solo lectura: pide su ruta y pinta lo que llega.
//
// Seccion 4.2: guarda reporte y error. El cargando NO es suyo: vive en
// ReportesApp y se enciende y apaga con el onCargando recibido (D-16).
function PantallaReservas({ consulta, apiBaseUrl, token, ejecutar, onCargando }) {
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

    async function consultarReservas() {
      onCargando(true);
      const resultado = await ejecutar(() =>
        obtenerReservas(apiBaseUrl, token, consulta.desde, consulta.hasta)
      );

      if (!vigente) {
        return;
      }

      onCargando(false);

      // D-07: un error NO borra el reporte anterior. Ante un 401, ejecutar ya
      // llamo a onLogout y devuelve datos y error en null: no se pinta nada
      // porque el remote se esta desmontando (seccion 4.1).
      if (resultado.error !== null) {
        setError(resultado.error);
        return;
      }

      if (resultado.datos !== null) {
        setReporte(resultado.datos);
        setError(null);
      }
    }

    consultarReservas();

    return () => {
      vigente = false;
    };
  }, [consulta]);

  return (
    <section className="mfrep-pantalla">
      <h3>Reservas por periodo</h3>
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
                campo="totalReservas"
                etiqueta="reservas"
              />

              <table className="mfrep-tabla">
                <thead>
                  <tr>
                    <th scope="col">canchaId</th>
                    <th scope="col">nombre</th>
                    <th scope="col">deporte</th>
                    <th scope="col">totalReservas</th>
                  </tr>
                </thead>
                <tbody>
                  {/* P-04: la columna deporte por fila es lo que satisface el "por
                      cancha y por deporte" del PDF. No hay total agrupado por
                      deporte: seria un numero que la API no devolvio. */}
                  {reporte.items.map((item) => (
                    <tr key={item.canchaId}>
                      <td>{item.canchaId}</td>
                      <td>{item.nombre}</td>
                      <td>{item.deporte}</td>
                      <td className="mfrep-numero">{item.totalReservas}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* HU-03: sin esta nota, el numero se lee como "todas las reservas
                  registradas". RN-08 es la que separa los tres reportes. */}
              <p className="mfrep-nota">
                totalReservas cuenta las reservas CONFIRMADA y FINALIZADA, y
                excluye las CANCELADA, que tienen su propio reporte.
              </p>
            </>
          )}
        </>
      )}
    </section>
  );
}

export default PantallaReservas;
