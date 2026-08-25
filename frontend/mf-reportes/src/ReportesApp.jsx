import { useCallback, useState } from "react";
import { ErrorApi } from "./api/clienteApi";
import NavegacionInterna from "./components/NavegacionInterna";
import PantallaOcupacion from "./components/PantallaOcupacion";
import SelectorRango from "./components/SelectorRango";
import "./estilos.css";

// Modulo expuesto como "./ReportesApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. Es un componente, no un createRoot: el shell lo
// monta dentro de su propio arbol (D-01).
//
// Modulo de solo lectura (PDF seccion 3.3.5): no crea, no edita y no cancela
// nada. Sus unicas llamadas son las tres rutas GET de /api/reportes.
function ReportesApp({ usuario, token, apiBaseUrl, onLogout }) {
  const [vista, setVista] = useState("ocupacion");
  // rango es lo que el administrador esta escribiendo; consulta es lo que se
  // pidio de verdad. Separarlos es lo que permite que cambiar las fechas sin
  // pulsar consultar no altere la tabla en pantalla (HU-10, D-05).
  const [rango, setRango] = useState({ desde: "", hasta: "" });
  const [consulta, setConsulta] = useState(null);
  const [avisoRango, setAvisoRango] = useState(null);
  // D-16: el cargando vive aqui, no en la pantalla. ReportesApp ya es dueno de
  // vista, rango y consulta, que son el ciclo completo de una consulta, y el
  // estado de carga es parte de ese ciclo. Baja por props al SelectorRango, que
  // deshabilita su boton, y a la pantalla activa, que lo enciende y lo apaga.
  const [cargando, setCargando] = useState(false);

  // Seccion 4.1: unico punto del remote que decide que hacer con un 401. Las
  // tres rutas de este remote son autenticadas (seccion 6.1), asi que un 401
  // solo puede significar token vencido. Se comprueba el estado HTTP y no el
  // codigo, porque el estado es el que fija el contrato.
  //
  // Devuelve { datos, error } en vez de lanzar, para que las pantallas traten
  // los dos caminos igual. Ante el 401 devuelve error null a proposito: ya se
  // llamo onLogout(), el shell esta borrando la sesion y va a desmontar el
  // remote; pintar un mensaje seria un parpadeo sobre una pantalla que ya no
  // existe.
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

  // P-07 y D-13: comportamiento defensivo, no control de acceso. El control real
  // es el token que valida ms-reportes en sus tres rutas, y el shell ya restringe
  // el modulo al ADMIN. Se comprueba aqui, antes de montar nada, para que no
  // salga ni una llamada: sin esto las tres responderian 403 y la pantalla se
  // llenaria de errores en vez de un mensaje claro.
  if (usuario.rol !== "ADMIN") {
    return (
      <section className="mfrep-modulo">
        <h2>Reportes</h2>
        <p className="mfrep-aviso" role="alert">
          Este modulo esta disponible solo para el rol ADMIN.
        </p>
      </section>
    );
  }

  // Escribir en un campo cambia el rango y NO toca la consulta: la tabla en
  // pantalla sigue siendo la del rango con el que se consulto (HU-10, F-04).
  function cambiarRango(campo, valor) {
    setAvisoRango(null);
    setRango((anterior) => ({ ...anterior, [campo]: valor }));
  }

  // P-02: no se llama a nada hasta que el administrador pulsa consultar, y solo
  // se llama la ruta del reporte visible. D-06: el intento sube en cada
  // pulsacion para que reintentar tras un 500 con el mismo rango vuelva a
  // disparar la consulta.
  function consultar() {
    if (rango.desde === "" || rango.hasta === "") {
      setAvisoRango("Indique la fecha desde y la fecha hasta para consultar.");
      return;
    }

    setAvisoRango(null);
    setConsulta((anterior) => ({
      desde: rango.desde,
      hasta: rango.hasta,
      intento: anterior === null ? 1 : anterior.intento + 1
    }));
  }

  // D-05: cambiar de reporte conserva el rango escrito y pone la consulta en
  // null. Asi la pantalla nueva se monta sin llamar a nada y hay que pulsar
  // consultar otra vez (HU-10, F-03).
  //
  // D-16: el cargando se apaga en el MISMO paso. Salir de una pantalla mientras
  // su consulta viaja dejaria el boton deshabilitado por una carga de una
  // pantalla ya desmontada, que nunca va a avisar que termino.
  function cambiarVista(claveVista) {
    setConsulta(null);
    setAvisoRango(null);
    setCargando(false);
    setVista(claveVista);
  }

  return (
    <section className="mfrep-modulo">
      <h2>Reportes</h2>
      <SelectorRango
        rango={rango}
        cargando={cargando}
        onCambiarRango={cambiarRango}
        onConsultar={consultar}
      />
      {avisoRango === null ? null : (
        <p className="mfrep-aviso" role="alert">
          {avisoRango}
        </p>
      )}
      <NavegacionInterna vista={vista} onCambiarVista={cambiarVista} />
      {vista === "ocupacion" ? (
        <PantallaOcupacion
          consulta={consulta}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
          onCargando={setCargando}
        />
      ) : (
        // Las pantallas de Reservas y Cancelaciones llegan en T5 y T6. Hasta
        // entonces esas dos vistas no llaman a ninguna ruta.
        <p className="mfrep-vacio">Elija un rango de fechas y pulse Consultar.</p>
      )}
    </section>
  );
}

export default ReportesApp;
