import { useEffect, useState } from "react";
import { crearBloqueo, eliminarBloqueo, listarBloqueos } from "../api/canchasApi";
import DialogoConfirmacion from "./DialogoConfirmacion";
import FormularioBloqueo from "./FormularioBloqueo";
import MensajeError from "./MensajeError";

// HU-05 a HU-07. Estado de la seccion 4.3 del diseño.
//
// P-01 y D-17: se monta dentro de la pantalla de Canchas, con el canchaId de la
// cancha seleccionada por prop. No tiene selector de cancha propio: duplicaria
// el listado que ya esta al lado y podria quedar desincronizado con el.
const FORMULARIO_VACIO = {
  fecha: "",
  horaInicio: "",
  horaFin: "",
  motivo: ""
};

function PanelBloqueos({ cancha, apiBaseUrl, token, ejecutar, onCerrar }) {
  const [bloqueos, setBloqueos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [formulario, setFormulario] = useState(null);
  const [errorFormulario, setErrorFormulario] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState(null);
  const [bloqueoIdEnBorrado, setBloqueoIdEnBorrado] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);

  // HU-05, P-03: sin el parametro fecha. Se muestran siempre todos los bloqueos
  // de la cancha, y no hay filtro en la interfaz.
  async function cargarBloqueos() {
    setCargando(true);
    const resultado = await ejecutar(() => listarBloqueos(apiBaseUrl, token, cancha.canchaId));
    if (resultado.datos !== null) {
      setBloqueos(resultado.datos);
    }
    setError(resultado.error);
    setCargando(false);
  }

  // Se recarga tambien cuando cambia la cancha seleccionada: el panel no se
  // desmonta al elegir otra fila, solo recibe otro canchaId.
  useEffect(() => {
    let vigente = true;

    async function cargarAlMontar() {
      setCargando(true);
      const resultado = await ejecutar(() => listarBloqueos(apiBaseUrl, token, cancha.canchaId));
      if (!vigente) {
        return;
      }
      if (resultado.datos !== null) {
        setBloqueos(resultado.datos);
      }
      setError(resultado.error);
      setCargando(false);
    }

    cargarAlMontar();

    return () => {
      vigente = false;
    };
  }, [cancha.canchaId]);

  function abrirFormulario() {
    setFormulario(FORMULARIO_VACIO);
    setErrorFormulario(null);
  }

  function cerrarFormulario() {
    setFormulario(null);
    setErrorFormulario(null);
  }

  function cambiarCampo(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }));
  }

  // Cuerpo de cuatro campos (seccion 5.3). canchaId viaja en la ruta y bloqueoId
  // lo genera la base: no se envian.
  async function guardar() {
    const cuerpo = {
      fecha: formulario.fecha,
      horaInicio: formulario.horaInicio,
      horaFin: formulario.horaFin,
      motivo: formulario.motivo.trim()
    };

    setEnviando(true);
    setErrorFormulario(null);
    const resultado = await ejecutar(() =>
      crearBloqueo(apiBaseUrl, token, cancha.canchaId, cuerpo)
    );
    setEnviando(false);

    if (resultado.error !== null) {
      // 400 DATOS_INVALIDOS y 409 BLOQUEO_DUPLICADO: el formulario sigue abierto
      // con lo escrito (seccion 7).
      setErrorFormulario(resultado.error);
      return;
    }

    setFormulario(null);
    cargarBloqueos();
  }

  // HU-07: la confirmacion es obligatoria, el borrado es irreversible.
  function pedirConfirmacion(bloqueo) {
    setConfirmacion(bloqueo);
    setErrorAccion(null);
  }

  // Rechazar no hace ninguna llamada y el listado queda igual.
  function rechazarConfirmacion() {
    setConfirmacion(null);
  }

  async function confirmarBorrado() {
    const bloqueo = confirmacion;
    setBloqueoIdEnBorrado(bloqueo.bloqueoId);
    setErrorAccion(null);
    const resultado = await ejecutar(() =>
      eliminarBloqueo(apiBaseUrl, token, cancha.canchaId, bloqueo.bloqueoId)
    );
    setBloqueoIdEnBorrado(null);
    setConfirmacion(null);

    if (resultado.error !== null) {
      setErrorAccion(resultado.error);
      // 404: el bloqueo ya no existe. Se refresca igual.
      if (resultado.error.codigo === "NO_ENCONTRADO") {
        cargarBloqueos();
      }
      return;
    }

    // El 204 no trae cuerpo: clienteApi devolvio null y no hay nada que leer.
    cargarBloqueos();
  }

  return (
    <section className="mfa-panel-bloqueos">
      <h4>
        Bloqueos de mantenimiento — {cancha.nombre} ({cancha.deporte})
      </h4>

      <div className="mfa-acciones">
        <button type="button" disabled={formulario !== null} onClick={abrirFormulario}>
          Nuevo bloqueo
        </button>
        <button type="button" onClick={onCerrar}>
          Cerrar bloqueos
        </button>
      </div>

      <MensajeError error={error} />
      <MensajeError error={errorAccion} />

      {formulario !== null ? (
        <FormularioBloqueo
          formulario={formulario}
          error={errorFormulario}
          enviando={enviando}
          onCambiar={cambiarCampo}
          onGuardar={guardar}
          onCancelar={cerrarFormulario}
        />
      ) : null}

      {confirmacion !== null ? (
        <DialogoConfirmacion
          mensaje={
            "Va a eliminar el bloqueo del " +
            confirmacion.fecha +
            ", de " +
            confirmacion.horaInicio +
            " a " +
            confirmacion.horaFin +
            " (" +
            confirmacion.motivo +
            "). Esta accion no se puede deshacer."
          }
          textoConfirmar={
            bloqueoIdEnBorrado === confirmacion.bloqueoId
              ? "Eliminando..."
              : "Si, eliminar el bloqueo"
          }
          textoRechazar="No, volver al listado"
          enviando={bloqueoIdEnBorrado !== null}
          onConfirmar={confirmarBorrado}
          onRechazar={rechazarConfirmacion}
        />
      ) : null}

      {cargando ? <p>Cargando bloqueos...</p> : null}

      {!cargando && bloqueos.length === 0 ? (
        <p>Esta cancha no tiene bloqueos registrados.</p>
      ) : null}

      {bloqueos.length > 0 ? (
        <table className="mfa-tabla">
          <thead>
            <tr>
              <th>bloqueoId</th>
              <th>Fecha</th>
              <th>Desde</th>
              <th>Hasta</th>
              <th>Motivo</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {bloqueos.map((bloqueo) => (
              <tr key={bloqueo.bloqueoId}>
                <td>{bloqueo.bloqueoId}</td>
                <td>{bloqueo.fecha}</td>
                <td>{bloqueo.horaInicio}</td>
                <td>{bloqueo.horaFin}</td>
                <td>{bloqueo.motivo}</td>
                <td>
                  <button
                    type="button"
                    disabled={bloqueoIdEnBorrado === bloqueo.bloqueoId || confirmacion !== null}
                    onClick={() => pedirConfirmacion(bloqueo)}
                  >
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  );
}

export default PanelBloqueos;
