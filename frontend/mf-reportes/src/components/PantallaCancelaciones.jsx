import { useEffect, useState } from "react";
import { obtenerCancelaciones } from "../api/reportesApi";
import MensajeError from "./MensajeError";

// HU-04: numero de cancelaciones por cancha en el rango consultado. Solo
// lectura: pide su ruta y pinta lo que llega.
//
// P-05: esta pantalla NO lleva indicador de mayor y menor demanda. La cancha con
// mas cancelaciones no es la de mayor demanda, asi que IndicadorDemanda no se
// importa aqui.
//
// Seccion 4.2: guarda reporte y error. El cargando NO es suyo: vive en
// ReportesApp y se enciende y apaga con el onCargando recibido (D-16).
function PantallaCancelaciones({ consulta, apiBaseUrl, token, ejecutar, onCargando }) {
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

    async function consultarCancelaciones() {
      onCargando(true);
      const resultado = await ejecutar(() =>
        obtenerCancelaciones(apiBaseUrl, token, consulta.desde, consulta.hasta)
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

    consultarCancelaciones();

    return () => {
      vigente = false;
    };
  }, [consulta]);

  return (
    <section className="mfrep-pantalla">
      <h3>Cancelaciones por periodo</h3>
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
              <table className="mfrep-tabla">
                {/* D-17: etiquetas legibles; los nombres de campo del JSON
                    siguen intactos abajo. */}
                <thead>
                  <tr>
                    <th scope="col">Cancha</th>
                    <th scope="col">Nombre</th>
                    <th scope="col">Cancelaciones</th>
                  </tr>
                </thead>
                <tbody>
                  {/* Sin columna deporte: las filas de este reporte no lo traen y
                      el remote no lo completa desde otra llamada (HU-04). */}
                  {reporte.items.map((item) => (
                    <tr key={item.canchaId}>
                      <td>{item.canchaId}</td>
                      <td>{item.nombre}</td>
                      <td className="mfrep-numero">{item.totalCancelaciones}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* HU-04: sin esta nota, el rango se lee como "cancelaciones hechas
                  en estas fechas". reservas_db no almacena la fecha de
                  cancelacion. */}
              <p className="mfrep-nota">
                El rango filtra por la fecha de la reserva cancelada, no por la
                fecha en que se cancelo.
              </p>
            </>
          )}
        </>
      )}
    </section>
  );
}

export default PantallaCancelaciones;
