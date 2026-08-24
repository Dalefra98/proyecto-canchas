// Modulo expuesto como "./ReservasApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. En T1 solo confirma que el remote se monta y
// que las props llegan; las tres pantallas se agregan en T3 a T6.
function ReservasApp({ usuario, token, apiBaseUrl, onLogout }) {
  return (
    <section>
      <h2>Modulo Reservas</h2>
      <p>Sesion de: {usuario.nombre}</p>
    </section>
  );
}

export default ReservasApp;
