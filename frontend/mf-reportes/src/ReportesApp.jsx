import "./estilos.css";

// Modulo expuesto como "./ReportesApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. Es un componente, no un createRoot: el shell lo
// monta dentro de su propio arbol (D-01).
//
// Modulo de solo lectura (PDF seccion 3.3.5): no crea, no edita y no cancela
// nada. Sus unicas llamadas son las tres rutas GET de /api/reportes, que llegan
// en T3 a T6.
function ReportesApp({ usuario, token, apiBaseUrl, onLogout }) {
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

  // T1 entrega el andamiaje: el contenedor del modulo y la guardia de rol. El
  // menu interno, el selector de rango y la capa api llegan en T3; las tres
  // pantallas, en T4, T5 y T6.
  return (
    <section className="mfrep-modulo">
      <h2>Reportes</h2>
    </section>
  );
}

export default ReportesApp;
