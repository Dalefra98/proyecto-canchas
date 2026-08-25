import { createRoot } from "react-dom/client";

// D-02: este microfrontend se usa desde el shell. Abierto suelto en
// http://localhost:3003 solo publica su remoteEntry.js y muestra este aviso:
// sin el shell no hay token, usuario ni onLogout que entregarle. No monta
// ReportesApp ni inventa credenciales de desarrollo.
function AvisoRemote() {
  return (
    <main>
      <h1>Modulo Reportes</h1>
      <p>
        Este microfrontend no se usa por separado: el shell lo carga en tiempo de
        ejecucion y le entrega la sesion.
      </p>
      <p>
        Abra <a href="http://localhost:3000">http://localhost:3000</a> e inicie
        sesion con una cuenta ADMIN para usar el modulo Reportes.
      </p>
    </main>
  );
}

const contenedor = document.getElementById("root");
createRoot(contenedor).render(<AvisoRemote />);
