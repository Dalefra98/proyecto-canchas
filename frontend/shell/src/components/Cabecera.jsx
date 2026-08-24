// HU-04. No conoce los remotes: solo muestra quien esta en sesion y ofrece
// salir.
function Cabecera({ usuario, onCerrarSesion }) {
  return (
    <header className="cabecera">
      <span className="titulo">Reserva de Canchas Deportivas</span>
      <span className="usuario-en-sesion">
        {usuario.nombre} ({usuario.rol})
      </span>
      <button type="button" onClick={onCerrarSesion}>
        Cerrar sesion
      </button>
    </header>
  );
}

export default Cabecera;
