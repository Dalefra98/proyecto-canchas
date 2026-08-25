import { useEffect, useState } from "react";
import { consultarDisponibilidad } from "../api/reservasApi";
import GrillaBloques from "./GrillaBloques";
import MensajeError from "./MensajeError";

// P-03, D-08: el filtro por deporte se aplica en el navegador sobre el catalogo
// ya cargado. GET /api/canchas no declara ningun parametro en el contrato
// congelado, asi que filtrar en el servidor obligaria a cambiar una spec cerrada.
const DEPORTES = ["TODOS", "PADEL", "TENIS", "BASQUET"];

// D-09: la fecha de hoy se arma con los campos locales de Date y no con
// toISOString(), que convierte a UTC: en Ecuador (UTC-5), de 19:00 a 23:59
// devolveria el dia siguiente y la grilla abriria en una fecha que el usuario no
// eligio. Funcion local de esta pantalla: CLAUDE.md §3 prohibe las clases Util.
function fechaDeHoy() {
  const hoy = new Date();
  const anio = hoy.getFullYear();
  const mes = String(hoy.getMonth() + 1).padStart(2, "0");
  const dia = String(hoy.getDate()).padStart(2, "0");
  return anio + "-" + mes + "-" + dia;
}

function PantallaDisponibilidad({
  canchas,
  cargandoCatalogo,
  apiBaseUrl,
  token,
  ejecutar,
  consultaGrilla,
  onSolicitarConsulta,
  onElegirBloque
}) {
  const [deporteFiltro, setDeporteFiltro] = useState("TODOS");
  // Los selectores abren con la consulta que sobrevivio al ida y vuelta a la
  // pantalla de nueva reserva, y con hoy la primera vez (D-09).
  const [canchaIdElegida, setCanchaIdElegida] = useState(() =>
    consultaGrilla === null ? "" : String(consultaGrilla.canchaId)
  );
  const [fecha, setFecha] = useState(() =>
    consultaGrilla === null ? fechaDeHoy() : consultaGrilla.fecha
  );
  const [disponibilidad, setDisponibilidad] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState(null);

  const canchasFiltradas =
    deporteFiltro === "TODOS"
      ? canchas
      : canchas.filter((cancha) => cancha.deporte === deporteFiltro);

  // §4.2: si canchaIdElegida no es "", existe en el catalogo filtrado. Al cambiar
  // de deporte, la cancha elegida puede quedar fuera del filtro: se limpia la
  // seleccion en vez de dejar un valor que el selector ya no muestra.
  function cambiarDeporte(nuevoDeporte) {
    setDeporteFiltro(nuevoDeporte);
    setCanchaIdElegida("");
  }

  const canchaElegida = canchas.find(
    (cancha) => String(cancha.canchaId) === String(canchaIdElegida)
  );

  // D-18: la consulta se dispara con un boton y no en cada cambio de selector,
  // que generaria llamadas con una seleccion a medias.
  //
  // D-19: no se validan formatos. Lo unico que se comprueba es que la seleccion
  // este completa, para no gastar una llamada inutil (§5.1). La fecha pasada se
  // consulta con normalidad: es legitimo (P-02).
  function consultar() {
    if (canchaIdElegida === "" || fecha === "") {
      return;
    }
    onSolicitarConsulta(canchaIdElegida, fecha);
  }

  // La llamada sale de un solo sitio: este efecto. El boton no llama a la API,
  // solicita la consulta a ReservasApp, que escribe consultaGrilla; el efecto la
  // atiende. Asi la reconsulta tras el 201 (D-11) y la consulta manual recorren
  // exactamente el mismo camino.
  //
  // Depende de canchaId, fecha y refresco: un cambio solo del contador dispara
  // la consulta con los mismos parametros, que es justo lo que D-11 necesita.
  const canchaConsultada = consultaGrilla === null ? null : consultaGrilla.canchaId;
  const fechaConsultada = consultaGrilla === null ? null : consultaGrilla.fecha;
  const refrescoConsultado = consultaGrilla === null ? null : consultaGrilla.refresco;

  useEffect(() => {
    // null: todavia no se consulto nada y la grilla esta vacia (D-18).
    if (canchaConsultada === null) {
      return undefined;
    }

    let vigente = true;

    async function consultarDisponibilidadDeLaGrilla() {
      setCargando(true);
      setError(null);

      const resultado = await ejecutar(() =>
        consultarDisponibilidad(apiBaseUrl, token, canchaConsultada, fechaConsultada)
      );

      if (!vigente) {
        return;
      }

      setCargando(false);
      setError(resultado.error);
      if (resultado.datos !== null) {
        setDisponibilidad(resultado.datos);
      }
    }

    consultarDisponibilidadDeLaGrilla();

    // La pantalla se puede desmontar mientras la peticion viaja: el usuario
    // elige un bloque y pasa a nueva reserva.
    return () => {
      vigente = false;
    };
  }, [canchaConsultada, fechaConsultada, refrescoConsultado]);

  return (
    <div className="mfr-pantalla">
      <h3>Consulta de disponibilidad</h3>

      <div className="mfr-filtros">
        <label className="mfr-campo">
          Deporte
          <select
            value={deporteFiltro}
            disabled={cargandoCatalogo}
            onChange={(evento) => cambiarDeporte(evento.target.value)}
          >
            {DEPORTES.map((deporte) => (
              <option key={deporte} value={deporte}>
                {deporte === "TODOS" ? "Todos" : deporte}
              </option>
            ))}
          </select>
        </label>

        <label className="mfr-campo">
          Cancha
          <select
            value={canchaIdElegida}
            disabled={cargandoCatalogo}
            onChange={(evento) => setCanchaIdElegida(evento.target.value)}
          >
            <option value="">Elija una cancha</option>
            {canchasFiltradas.map((cancha) => (
              <option key={cancha.canchaId} value={cancha.canchaId}>
                {cancha.nombre} ({cancha.deporte})
              </option>
            ))}
          </select>
        </label>

        <label className="mfr-campo">
          Fecha
          <input
            type="date"
            value={fecha}
            onChange={(evento) => setFecha(evento.target.value)}
          />
        </label>

        <button
          type="button"
          className="mfr-boton"
          disabled={cargando || cargandoCatalogo || canchaIdElegida === "" || fecha === ""}
          onClick={consultar}
        >
          Consultar
        </button>
      </div>

      {cargandoCatalogo ? <p>Cargando catalogo de canchas...</p> : null}
      {cargando ? <p className="mfr-cargando">Consultando disponibilidad...</p> : null}
      <MensajeError error={error} />

      {disponibilidad !== null && !cargando ? (
        <GrillaBloques
          disponibilidad={disponibilidad}
          // HU-04: si el catalogo fallo o la cancha no esta en el, se muestra el
          // canchaId de la respuesta tal cual, sin inventar un nombre.
          nombreCancha={canchaElegida ? canchaElegida.nombre : "Cancha " + disponibilidad.canchaId}
          // D-10: la cancha y la fecha salen de la respuesta, no del selector:
          // son las que se consultaron de verdad.
          onElegirBloque={(bloque) =>
            onElegirBloque({
              canchaId: disponibilidad.canchaId,
              fecha: disponibilidad.fecha,
              horaInicio: bloque.horaInicio,
              horaFin: bloque.horaFin
            })
          }
        />
      ) : null}
    </div>
  );
}

export default PantallaDisponibilidad;
