// HU-14: las tres pantallas del modulo, con menu interno propio. Los bloqueos no
// son una opcion: viven dentro de la pantalla de Canchas, anidados en la cancha
// seleccionada (P-01, D-17).
//
// No cambia la URL ni usa enrutador: son botones que cambian el estado "vista"
// de AdminApp (P-05 de la spec 06). Tampoco repite el menu de modulos del shell,
// ni ofrece Inicio ni cierre de sesion.
const OPCIONES = [
  { clave: "canchas", etiqueta: "Canchas" },
  { clave: "reservas", etiqueta: "Reservas" },
  { clave: "usuarios", etiqueta: "Usuarios" }
];

function NavegacionInterna({ vista, onCambiarVista }) {
  return (
    <nav className="mfa-navegacion">
      {OPCIONES.map((opcion) => (
        <button
          key={opcion.clave}
          type="button"
          className={
            vista === opcion.clave
              ? "mfa-navegacion-opcion mfa-activa"
              : "mfa-navegacion-opcion"
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
