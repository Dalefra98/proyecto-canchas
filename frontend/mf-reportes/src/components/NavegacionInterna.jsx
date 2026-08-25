// HU-10: los tres reportes del modulo, con menu interno propio (P-01). No
// cambia la URL ni usa enrutador: son botones que cambian el estado "vista" de
// ReportesApp (P-05 de la spec 06). Tampoco repite el menu de modulos del shell,
// ni ofrece Inicio ni cierre de sesion.
//
// El orden y la vista inicial son los de HU-10: Ocupacion primero, que es la
// funcionalidad que el PDF seccion 3.1 nombra para este modulo.
const OPCIONES = [
  { clave: "ocupacion", etiqueta: "Ocupacion" },
  { clave: "reservas", etiqueta: "Reservas" },
  { clave: "cancelaciones", etiqueta: "Cancelaciones" }
];

function NavegacionInterna({ vista, onCambiarVista }) {
  return (
    <nav className="mfrep-navegacion">
      {OPCIONES.map((opcion) => (
        <button
          key={opcion.clave}
          type="button"
          className={
            vista === opcion.clave
              ? "mfrep-navegacion-opcion mfrep-activa"
              : "mfrep-navegacion-opcion"
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
