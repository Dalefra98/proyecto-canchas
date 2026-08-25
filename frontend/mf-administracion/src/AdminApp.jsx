import { useCallback, useState } from "react";
import { ErrorApi } from "./api/clienteApi";
import NavegacionInterna from "./components/NavegacionInterna";
import PantallaCanchas from "./components/PantallaCanchas";
import PantallaReservas from "./components/PantallaReservas";
import PantallaUsuarios from "./components/PantallaUsuarios";
import "./estilos.css";

// Modulo expuesto como "./AdminApp" (contrato congelado). Recibe las cuatro
// props del shell y ninguna mas. Es un componente, no un createRoot: el shell lo
// monta dentro de su propio arbol (D-01).
//
// D-06: no guarda catalogos compartidos. Cada pantalla pide sus propios datos al
// montarse, porque aqui el ADMIN escribe y un catalogo cacheado en la raiz
// mostraria el nombre viejo de una cancha recien editada (HU-14).
function AdminApp({ usuario, token, apiBaseUrl, onLogout }) {
  const [vista, setVista] = useState("canchas");

  // D-13 de la spec 07, aqui seccion 4.1: unico punto del remote que decide que
  // hacer con un 401. Las once rutas de este remote son autenticadas (seccion
  // 6.1), asi que un 401 solo puede significar token vencido. Se comprueba el
  // estado HTTP y no el codigo, porque el estado es el que fija el contrato.
  //
  // Devuelve { datos, error } en vez de lanzar, para que las pantallas traten
  // los dos caminos igual. Ante el 401 devuelve error null a proposito: ya se
  // llamo onLogout(), el shell esta borrando la sesion y va a desmontar el
  // remote; pintar un mensaje seria un parpadeo sobre una pantalla que ya no
  // existe.
  const ejecutar = useCallback(
    async (operacion) => {
      try {
        return { datos: await operacion(), error: null };
      } catch (error) {
        if (error instanceof ErrorApi && error.estado === 401) {
          onLogout();
          return { datos: null, error: null };
        }
        return { datos: null, error: { codigo: error.codigo, mensaje: error.mensaje } };
      }
    },
    [onLogout]
  );

  // P-07 y D-18: comportamiento defensivo, no control de acceso. El control real
  // es el token que valida cada microservicio, y el shell ya restringe el modulo
  // al ADMIN. Se comprueba aqui, antes de montar las pantallas, para que no
  // salga ni una llamada: sin esto todas responderian 403 y la pantalla se
  // llenaria de errores en vez de un mensaje claro.
  if (usuario.rol !== "ADMIN") {
    return (
      <section className="mfa-modulo">
        <h2>Administracion</h2>
        <p className="mfa-aviso" role="alert">
          Este modulo esta disponible solo para el rol ADMIN.
        </p>
      </section>
    );
  }

  // Al cambiar de vista, la pantalla anterior se desmonta y su estado se
  // descarta: formularios a medias y dialogos abiertos incluidos. Al volver,
  // pide sus datos de nuevo (HU-14, D-06).
  function cambiarVista(nuevaVista) {
    setVista(nuevaVista);
  }

  return (
    <section className="mfa-modulo">
      <h2>Administracion</h2>
      <p>Sesion de: {usuario.nombre}</p>

      <NavegacionInterna vista={vista} onCambiarVista={cambiarVista} />

      {vista === "canchas" ? (
        <PantallaCanchas apiBaseUrl={apiBaseUrl} token={token} ejecutar={ejecutar} />
      ) : null}
      {vista === "reservas" ? (
        <PantallaReservas apiBaseUrl={apiBaseUrl} token={token} ejecutar={ejecutar} />
      ) : null}
      {/* usuario baja tambien a esta pantalla: la fila propia se distingue
          comparando su usuarioId con el de la sesion (P-06). */}
      {vista === "usuarios" ? (
        <PantallaUsuarios
          usuario={usuario}
          apiBaseUrl={apiBaseUrl}
          token={token}
          ejecutar={ejecutar}
        />
      ) : null}
    </section>
  );
}

export default AdminApp;
