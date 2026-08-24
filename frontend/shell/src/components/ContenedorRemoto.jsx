import { lazy, Suspense } from "react";
import BordeError from "./BordeError";

// D-11: los React.lazy se crean una sola vez, a nivel de modulo. Crearlos dentro
// del render devolveria un componente nuevo en cada pintada: React lo trataria
// como otro tipo, desmontaria y volveria a descargar el remote.
const REMOTOS = {
  mfReservas: lazy(() => import("mfReservas/ReservasApp")),
  mfAdministracion: lazy(() => import("mfAdministracion/AdminApp")),
  mfReportes: lazy(() => import("mfReportes/ReportesApp"))
};

// HU-06 y HU-07. Entrega las cuatro props del contrato congelado y nada mas.
// D-12: el token viaja por prop; ningun remote lo lee de sessionStorage, para
// que el shell siga siendo el dueno de la sesion.
function ContenedorRemoto({ clave, usuario, token, onLogout }) {
  const RemoteApp = REMOTOS[clave];

  return (
    <section className="contenido-modulo">
      <BordeError clave={clave}>
        <Suspense fallback={<p>Cargando modulo...</p>}>
          <RemoteApp usuario={usuario} token={token} apiBaseUrl="/api" onLogout={onLogout} />
        </Suspense>
      </BordeError>
    </section>
  );
}

export default ContenedorRemoto;
