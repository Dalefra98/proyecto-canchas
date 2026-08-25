// D-17: dos opciones, no tres. "Nueva reserva" no aparece aqui porque solo se
// llega eligiendo un bloque libre en la grilla (P-01): una opcion de menu la
// abriria sin reservaPendiente y romperia el invariante de §4.1.
//
// No cambia la URL ni usa enrutador: son botones que cambian el estado "vista"
// de ReservasApp (P-05 de la spec 06).
const OPCIONES = [
  { clave: "disponibilidad", etiqueta: "Disponibilidad" },
  { clave: "misReservas", etiqueta: "Mis reservas" }
];

function NavegacionInterna({ vista, onCambiarVista }) {
  return (
    <nav className="mfr-navegacion">
      {OPCIONES.map((opcion) => (
        <button
          key={opcion.clave}
          type="button"
          className={
            vista === opcion.clave ? "mfr-navegacion-opcion mfr-activa" : "mfr-navegacion-opcion"
          }
          onClick={() => onCambiarVista(opcion.clave)}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </nav>
  );
}

export default NavegacionInterna;
