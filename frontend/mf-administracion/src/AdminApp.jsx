// Modulo expuesto como "./AdminApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. Es un componente, no un createRoot: el shell lo
// monta dentro de su propio arbol (D-01).
//
// T1 deja solo el andamiaje: la vista activa, la guardia de rol, el envoltorio
// del 401 y las tres pantallas llegan en T3.
function AdminApp({ usuario }) {
  return (
    <section>
      <h2>Administracion</h2>
      <p>Sesion de: {usuario.nombre}</p>
    </section>
  );
}

export default AdminApp;
