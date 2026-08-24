// P-09: vista inicial de los dos roles. El shell no elige por el usuario que
// modulo abrir primero, y ningun remote se descarga hasta que se elija uno.
function PantallaBienvenida({ usuario }) {
  return (
    <section className="bienvenida">
      <h2>Bienvenido, {usuario.nombre}</h2>
      <p>Elija un modulo en el menu para comenzar.</p>
    </section>
  );
}

export default PantallaBienvenida;
