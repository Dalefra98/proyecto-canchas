import { useCallback, useEffect, useState } from "react";
import { listarCanchas } from "./api/canchasApi";
import { ErrorApi } from "./api/clienteApi";
import MensajeError from "./components/MensajeError";
import NavegacionInterna from "./components/NavegacionInterna";
import PantallaDisponibilidad from "./components/PantallaDisponibilidad";
import "./estilos.css";

// Modulo expuesto como "./ReservasApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. Es un componente, no un createRoot: el shell lo
// monta dentro de su propio arbol (D-01).
//
// D-06: todo el estado compartido vive aqui y baja por props. Tres pantallas y un
// arbol de dos niveles no justifican un Context.
function ReservasApp({ usuario, token, apiBaseUrl, onLogout }) {
  const [vista, setVista] = useState("disponibilidad");
  const [canchas, setCanchas] = useState([]);
  const [errorCatalogo, setErrorCatalogo] = useState(null);
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true);
  // §4.1 declara los seis campos de estado aqui. reservaPendiente se declara en
  // T3 y todavia no tiene quien lo escriba: nace en T5, cuando la grilla de T4
  // permita elegir un bloque libre (D-10). No es un olvido.
  const [reservaPendiente, setReservaPendiente] = useState(null);
  const [avisoExito, setAvisoExito] = useState(null);

  // D-13: unico punto del remote que decide que hacer con un 401. Todas las
  // rutas de este remote son autenticadas (§6.1), asi que un 401 solo puede
  // significar token vencido: nunca credenciales malas. Se comprueba el estado
  // HTTP y no el codigo, porque el estado es el que fija el contrato.
  //
  // Devuelve { datos, error } en vez de lanzar, para que las pantallas de T4 a
  // T6 traten los dos caminos igual. Ante el 401 devuelve error null a proposito:
  // ya se llamo onLogout(), el shell esta borrando la sesion y va a desmontar el
  // remote; pintar un mensaje de error seria un parpadeo sobre una pantalla que
  // ya no existe.
  const ejecutar = useCallback(
    async (operacion) => {
      try {
        return { datos: await operacion(), error: null };
      } catch (error) {
        if (error instanceof ErrorApi && error.estado === 401) {
          onLogout();
          return { datos: null, error: null };
        }
        return { datos: null, error: { codigo: error.codigo, mensaje: error.mensaje } };
      }
    },
    [onLogout]
  );

  // D-07: el catalogo se pide una sola vez al montar y lo comparten las tres
  // pantallas. La lista de dependencias esta vacia a proposito: token y
  // apiBaseUrl no van ahi porque si el token cambiara seria por un cambio de
  // sesion, y en ese caso el shell desmonta el remote entero. Incluirlos solo
  // agregaria llamadas repetidas al catalogo.
  useEffect(() => {
    let vigente = true;

    async function cargarCatalogo() {
      const resultado = await ejecutar(() => listarCanchas(apiBaseUrl, token));
      if (!vigente) {
        return;
      }
      if (resultado.datos !== null) {
        setCanchas(resultado.datos);
      }
      setErrorCatalogo(resultado.error);
      setCargandoCatalogo(false);
    }

    cargarCatalogo();

    // El remote se puede desmontar mientras la peticion viaja (el usuario cambia
    // de modulo en el shell): sin esto se escribiria estado de un componente que
    // ya no esta en el arbol.
    return () => {
      vigente = false;
    };
  }, []);

  // HU-04: el catalogo puede fallar y el remote sigue siendo usable. Cuando
  // errorCatalogo no es null, las pantallas muestran el canchaId en lugar del
  // nombre, pero no dejan de funcionar.
  function cambiarVista(nuevaVista) {
    setVista(nuevaVista);
    setAvisoExito(null);
  }

  return (
    <section className="mfr-modulo">
      <h2>Reservas</h2>
      <p>Sesion de: {usuario.nombre}</p>

      <NavegacionInterna vista={vista} onCambiarVista={cambiarVista} />

      {avisoExito ? <p className="mfr-aviso-exito">{avisoExito}</p> : null}
      {errorCatalogo ? <MensajeError error={errorCatalogo} /> : null}

      {/* La pantalla se monta tambien mientras el catalogo carga: sus selectores
          quedan deshabilitados hasta que llegue (§4.2). onElegirBloque no se pasa
          todavia: elegir un bloque libre escribe reservaPendiente y cambia de
          vista, y eso lo conecta T5 (D-10). */}
      {vista === "disponibilidad" ? (
        <PantallaDisponibilidad
          canchas={canchas}
          cargandoCatalogo={cargandoCatalogo}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
        />
      ) : null}

      {/* Marcador de posicion de T3, que T6 sustituye por PantallaMisReservas. */}
      {!cargandoCatalogo && vista === "misReservas" ? (
        <div>
          <h3>Mis reservas</h3>
          <p>{canchas.length} canchas en el catalogo compartido.</p>
        </div>
      ) : null}

      {vista === "nuevaReserva" && reservaPendiente !== null ? (
        <div>
          <h3>Nueva reserva</h3>
        </div>
      ) : null}
    </section>
  );
}

export default ReservasApp;
