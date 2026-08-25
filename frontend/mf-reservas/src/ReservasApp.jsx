import { useCallback, useEffect, useState } from "react";
import { listarCanchas } from "./api/canchasApi";
import { ErrorApi } from "./api/clienteApi";
import MensajeError from "./components/MensajeError";
import NavegacionInterna from "./components/NavegacionInterna";
import PantallaDisponibilidad from "./components/PantallaDisponibilidad";
import PantallaMisReservas from "./components/PantallaMisReservas";
import PantallaNuevaReserva from "./components/PantallaNuevaReserva";
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
  const [reservaPendiente, setReservaPendiente] = useState(null);
  const [avisoExito, setAvisoExito] = useState(null);
  // Campo agregado a §4.1 en T5: la cancha y la fecha consultadas y un contador
  // de reconsulta. Sobrevive al ida y vuelta a la pantalla de nueva reserva, que
  // desmonta PantallaDisponibilidad y se llevaria su estado. Nace en null y no
  // sale ni una llamada hasta que el usuario pulse Consultar (D-18).
  const [consultaGrilla, setConsultaGrilla] = useState(null);

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

  // Unico camino por el que se consulta la grilla: el boton de la pantalla no
  // llama a la API por su cuenta, pide una consulta aqui (D-18). El contador
  // crece siempre, para que consultar dos veces la misma cancha y la misma fecha
  // se distinga de no haber consultado nada.
  function solicitarConsulta(canchaId, fecha) {
    setConsultaGrilla((anterior) => ({
      canchaId: canchaId,
      fecha: fecha,
      refresco: anterior === null ? 1 : anterior.refresco + 1
    }));
  }

  // Reconsulta con los mismos canchaId y fecha: solo cambia el contador.
  function refrescarGrilla() {
    setConsultaGrilla((anterior) =>
      anterior === null ? null : { ...anterior, refresco: anterior.refresco + 1 }
    );
  }

  // D-10: elegir un bloque libre es el unico camino a la pantalla de nueva
  // reserva (P-01). La cancha y la fecha salen de la respuesta de
  // disponibilidad, no del selector.
  function elegirBloque(seleccion) {
    setReservaPendiente(seleccion);
    setAvisoExito(null);
    setVista("nuevaReserva");
  }

  // Volver sin crear nada: se descarta la seleccion y la grilla se reconsulta al
  // montarse con la misma cancha y fecha (HU-02).
  function volverAGrilla() {
    setReservaPendiente(null);
    setVista("disponibilidad");
  }

  // D-11: tras el 201 se vuelve a la grilla y se la reconsulta, de modo que el
  // bloque recien reservado aparezca ocupado sin que nadie toque nada. Es la
  // prueba visible de RN-02 en la demo. El aviso lleva los datos devueltos.
  function reservaCreada(reserva) {
    setAvisoExito(
      "Reserva " +
        reserva.id +
        " creada: " +
        reserva.fecha +
        " de " +
        reserva.horaInicio +
        " a " +
        reserva.horaFin +
        " (" +
        reserva.estado +
        ")."
    );
    refrescarGrilla();
    setReservaPendiente(null);
    setVista("disponibilidad");
  }

  return (
    <section className="mfr-modulo">
      <h2>Reservas</h2>
      <p>Sesion de: {usuario.nombre}</p>

      <NavegacionInterna vista={vista} onCambiarVista={cambiarVista} />

      {avisoExito ? <p className="mfr-aviso-exito">{avisoExito}</p> : null}
      {errorCatalogo ? <MensajeError error={errorCatalogo} /> : null}

      {/* La pantalla se monta tambien mientras el catalogo carga: sus selectores
          quedan deshabilitados hasta que llegue (§4.2). */}
      {vista === "disponibilidad" ? (
        <PantallaDisponibilidad
          canchas={canchas}
          cargandoCatalogo={cargandoCatalogo}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
          consultaGrilla={consultaGrilla}
          onSolicitarConsulta={solicitarConsulta}
          onElegirBloque={elegirBloque}
        />
      ) : null}

      {/* Se monta aunque el catalogo siga cargando o haya fallado: un fallo del
          catalogo no oculta las reservas, solo deja el canchaId en lugar del
          nombre (HU-04). */}
      {vista === "misReservas" ? (
        <PantallaMisReservas
          canchas={canchas}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
          onAviso={setAvisoExito}
        />
      ) : null}

      {/* Invariante de §4.1: a esta vista solo se llega con reservaPendiente. */}
      {vista === "nuevaReserva" && reservaPendiente !== null ? (
        <PantallaNuevaReserva
          reservaPendiente={reservaPendiente}
          cancha={canchas.find(
            (cancha) => String(cancha.canchaId) === String(reservaPendiente.canchaId)
          )}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
          onCreada={reservaCreada}
          onVolver={volverAGrilla}
          onRefrescarGrilla={refrescarGrilla}
        />
      ) : null}
    </section>
  );
}

export default ReservasApp;
