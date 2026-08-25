import { useEffect, useState } from "react";
import {
  cambiarEstadoCancha,
  crearCancha,
  editarCancha,
  listarCanchas
} from "../api/canchasApi";
import FormularioCancha from "./FormularioCancha";
import MensajeError from "./MensajeError";
import PanelBloqueos from "./PanelBloqueos";

// HU-01 a HU-04. Estado de la seccion 4.2 del diseño.
//
// D-06: pide el catalogo al montarse y lo vuelve a pedir tras cada escritura
// (D-15), en vez de actualizar el arreglo en memoria: asi se ve lo que el
// servidor tiene, incluido lo que cambio otro administrador.
const FORMULARIO_ALTA = {
  modo: "alta",
  canchaId: null,
  nombre: "",
  deporte: "PADEL",
  horaApertura: "",
  horaCierre: ""
};

function PantallaCanchas({ apiBaseUrl, token, ejecutar }) {
  const [canchas, setCanchas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [formulario, setFormulario] = useState(null);
  const [errorFormulario, setErrorFormulario] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [canchaIdEnCambio, setCanchaIdEnCambio] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);
  const [aviso, setAviso] = useState(null);
  // P-01: canchaId cuyos bloqueos se muestran; null = panel cerrado. El panel
  // vive dentro de esta pantalla, no es una vista aparte.
  const [canchaSeleccionada, setCanchaSeleccionada] = useState(null);

  // HU-01: al ADMIN la ruta le devuelve todas las canchas, incluidas las
  // activa = false. El remote no filtra nada ni reordena.
  async function cargarCanchas() {
    setCargando(true);
    const resultado = await ejecutar(() => listarCanchas(apiBaseUrl, token));
    if (resultado.datos !== null) {
      setCanchas(resultado.datos);
    }
    setError(resultado.error);
    setCargando(false);
  }

  useEffect(() => {
    let vigente = true;

    async function cargarAlMontar() {
      const resultado = await ejecutar(() => listarCanchas(apiBaseUrl, token));
      // El remote se puede desmontar mientras la peticion viaja (el usuario
      // cambia de pantalla o de modulo): sin esto se escribiria estado de un
      // componente que ya no esta en el arbol.
      if (!vigente) {
        return;
      }
      if (resultado.datos !== null) {
        setCanchas(resultado.datos);
      }
      setError(resultado.error);
      setCargando(false);
    }

    cargarAlMontar();

    return () => {
      vigente = false;
    };
  }, []);

  function abrirAlta() {
    setFormulario(FORMULARIO_ALTA);
    setErrorFormulario(null);
    setAviso(null);
  }

  // HU-03: el formulario llega precargado con los valores actuales, tomados del
  // listado ya cargado. No se pide la cancha de nuevo.
  function abrirEdicion(cancha) {
    setFormulario({
      modo: "edicion",
      canchaId: cancha.canchaId,
      nombre: cancha.nombre,
      deporte: cancha.deporte,
      horaApertura: cancha.horaApertura,
      horaCierre: cancha.horaCierre
    });
    setErrorFormulario(null);
    setAviso(null);
  }

  function cerrarFormulario() {
    setFormulario(null);
    setErrorFormulario(null);
  }

  function cambiarCampo(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }));
  }

  // Cuerpo de cuatro campos (seccion 5.1). No lleva canchaId ni activa: el
  // identificador lo genera la base y el estado va por su PATCH dedicado.
  // El PUT manda los cuatro aunque solo haya cambiado uno (D-11 de la spec 03).
  async function guardar() {
    const cuerpo = {
      nombre: formulario.nombre.trim(),
      deporte: formulario.deporte,
      horaApertura: formulario.horaApertura,
      horaCierre: formulario.horaCierre
    };
    const esAlta = formulario.modo === "alta";

    setEnviando(true);
    setErrorFormulario(null);
    const resultado = await ejecutar(() =>
      esAlta
        ? crearCancha(apiBaseUrl, token, cuerpo)
        : editarCancha(apiBaseUrl, token, formulario.canchaId, cuerpo)
    );
    setEnviando(false);

    if (resultado.error !== null) {
      // 400 DATOS_INVALIDOS y 409 NOMBRE_DUPLICADO: el formulario no se cierra y
      // conserva lo escrito (seccion 7).
      setErrorFormulario(resultado.error);
      // El 404 de una cancha que ya no existe tambien refresca el listado.
      if (resultado.error.codigo === "NO_ENCONTRADO") {
        cargarCanchas();
      }
      return;
    }

    if (resultado.datos !== null) {
      setAviso(
        esAlta
          ? "Cancha " + resultado.datos.nombre + " creada."
          : "Cancha " + resultado.datos.nombre + " actualizada."
      );
    }
    setFormulario(null);
    cargarCanchas();
  }

  // HU-04, P-02: sin confirmacion, el cambio es reversible con un clic.
  // D-14: se envia el valor contrario al que devolvio la API para esa fila, no
  // el de un interruptor con estado propio.
  async function cambiarEstado(cancha) {
    setCanchaIdEnCambio(cancha.canchaId);
    setErrorAccion(null);
    setAviso(null);
    const resultado = await ejecutar(() =>
      cambiarEstadoCancha(apiBaseUrl, token, cancha.canchaId, !cancha.activa)
    );
    setCanchaIdEnCambio(null);

    if (resultado.error !== null) {
      setErrorAccion(resultado.error);
      if (resultado.error.codigo === "NO_ENCONTRADO") {
        cargarCanchas();
      }
      return;
    }

    setAviso(
      "Cancha " + cancha.nombre + (cancha.activa ? " inactivada." : " activada.")
    );
    cargarCanchas();
  }

  // El panel recibe la cancha del listado ya cargado: su nombre y su deporte
  // salen de ahi, no de una llamada aparte.
  const canchaDeBloqueos = canchas.find((cancha) => cancha.canchaId === canchaSeleccionada);

  return (
    <section className="mfa-pantalla">
      <h3>Canchas</h3>

      <button type="button" disabled={formulario !== null} onClick={abrirAlta}>
        Nueva cancha
      </button>

      {aviso ? <p className="mfa-aviso-exito">{aviso}</p> : null}
      <MensajeError error={error} />
      <MensajeError error={errorAccion} />

      {formulario !== null ? (
        <FormularioCancha
          formulario={formulario}
          error={errorFormulario}
          enviando={enviando}
          onCambiar={cambiarCampo}
          onGuardar={guardar}
          onCancelar={cerrarFormulario}
        />
      ) : null}

      {cargando ? <p>Cargando canchas...</p> : null}

      {!cargando && canchas.length === 0 ? <p>No hay canchas registradas.</p> : null}

      {canchas.length > 0 ? (
        <table className="mfa-tabla">
          <thead>
            <tr>
              <th>canchaId</th>
              <th>Nombre</th>
              <th>Deporte</th>
              <th>Apertura</th>
              <th>Cierre</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {canchas.map((cancha) => (
              <tr key={cancha.canchaId}>
                <td>{cancha.canchaId}</td>
                <td>{cancha.nombre}</td>
                <td>{cancha.deporte}</td>
                <td>{cancha.horaApertura}</td>
                <td>{cancha.horaCierre}</td>
                <td>{cancha.activa ? "Activa" : "Inactiva"}</td>
                <td>
                  <button
                    type="button"
                    disabled={formulario !== null || canchaIdEnCambio !== null}
                    onClick={() => abrirEdicion(cancha)}
                  >
                    Editar
                  </button>
                  <button
                    type="button"
                    disabled={canchaIdEnCambio === cancha.canchaId}
                    onClick={() => cambiarEstado(cancha)}
                  >
                    {cancha.activa ? "Inactivar" : "Activar"}
                  </button>
                  <button type="button" onClick={() => setCanchaSeleccionada(cancha.canchaId)}>
                    Bloqueos
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {/* P-01: los bloqueos se pintan dentro de esta misma pantalla, bajo el
          listado, para la cancha elegida. Cerrarlos no cambia de vista. Si la
          cancha desaparece del catalogo tras una recarga, el panel no se pinta. */}
      {canchaSeleccionada !== null && canchaDeBloqueos !== undefined ? (
        <PanelBloqueos
          cancha={canchaDeBloqueos}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
          onCerrar={() => setCanchaSeleccionada(null)}
        />
      ) : null}
    </section>
  );
}

export default PantallaCanchas;
