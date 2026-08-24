import { useState } from "react";
import MensajeError from "./MensajeError";

// HU-01. Valida solo campos obligatorios vacios (D-05): el formato del correo y
// la longitud de la contrasena los valida ms-usuarios, que devuelve 400
// DATOS_INVALIDOS con su propio mensaje.
function PantallaSesion({ aviso, onIniciarSesion, onIrARegistro }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [faltantes, setFaltantes] = useState({ email: false, password: false });
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento) {
    evento.preventDefault();

    // El error del servidor se limpia ANTES de validar: si no, el 401 anterior
    // convive con los avisos de campo obligatorio del intento nuevo.
    setError(null);

    const emailVacio = email.trim() === "";
    const passwordVacio = password === "";
    setFaltantes({ email: emailVacio, password: passwordVacio });
    if (emailVacio || passwordVacio) {
      return;
    }

    setEnviando(true);
    try {
      await onIniciarSesion(email.trim(), password);
    } catch (fallo) {
      setError({ codigo: fallo.codigo, mensaje: fallo.mensaje });
      // Se conserva el email escrito y se limpia la contrasena.
      setPassword("");
      setEnviando(false);
    }
  }

  return (
    <main className="pantalla-acceso">
      <h1>Reserva de Canchas Deportivas</h1>
      <h2>Iniciar sesion</h2>

      {aviso ? (
        <p className="aviso" role="status">
          {aviso}
        </p>
      ) : null}

      <form onSubmit={enviar} noValidate>
        <label htmlFor="email">Correo</label>
        <input
          id="email"
          name="email"
          type="text"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
        />
        {faltantes.email ? <span className="campo-invalido">El correo es obligatorio</span> : null}

        <label htmlFor="password">Contrasena</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(evento) => setPassword(evento.target.value)}
        />
        {faltantes.password ? (
          <span className="campo-invalido">La contrasena es obligatoria</span>
        ) : null}

        <MensajeError error={error} />

        <button type="submit" disabled={enviando}>
          {enviando ? "Ingresando..." : "Ingresar"}
        </button>
      </form>

      <button type="button" className="enlace" onClick={onIrARegistro}>
        Crear cuenta
      </button>
    </main>
  );
}

export default PantallaSesion;
