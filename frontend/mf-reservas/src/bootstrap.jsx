import { createRoot } from "react-dom/client";

// P-04: este microfrontend se usa desde el shell. Abierto suelto en
// http://localhost:3001 solo publica su remoteEntry.js y muestra este aviso:
// sin el shell no hay token, usuario ni onLogout que entregarle. No monta
// ReservasApp ni inventa credenciales de desarrollo.
function AvisoRemote() {
  return (
    <main>
      <h1>Modulo Reservas</h1>
      <p>
        Este microfrontend no se usa por separado: el shell lo carga en tiempo de
        ejecucion y le entrega la sesion.
      </p>
      <p>
        Abra <a href="http://localhost:3000">http://localhost:3000</a> e inicie
        sesion para usar el modulo Reservas.
      </p>
    </main>
  );
}

const contenedor = document.getElementById("root");
createRoot(contenedor).render(<AvisoRemote />);
