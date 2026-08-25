import { useState } from "react";
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
  onElegirBloque
}) {
  const [deporteFiltro, setDeporteFiltro] = useState("TODOS");
  const [canchaIdElegida, setCanchaIdElegida] = useState("");
  const [fecha, setFecha] = useState(fechaDeHoy);
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
  async function consultar() {
    if (canchaIdElegida === "" || fecha === "") {
      return;
    }

    setCargando(true);
    setError(null);

    const resultado = await ejecutar(() =>
      consultarDisponibilidad(apiBaseUrl, token, canchaIdElegida, fecha)
    );

    setCargando(false);
    setError(resultado.error);
    if (resultado.datos !== null) {
      setDisponibilidad(resultado.datos);
    }
  }

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
          onElegirBloque={onElegirBloque}
        />
      ) : null}
    </div>
  );
}

export default PantallaDisponibilidad;
