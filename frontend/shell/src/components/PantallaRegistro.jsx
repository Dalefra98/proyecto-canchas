import { useState } from "react";
import MensajeError from "./MensajeError";

// HU-02. Solo nombre, email y password: POST /api/usuarios es publico y rol y
// activo los fija ms-usuarios.
function PantallaRegistro({ onRegistrar, onVolver }) {
  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [faltantes, setFaltantes] = useState({ nombre: false, email: false, password: false });
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  // El 409 EMAIL_DUPLICADO se muestra junto al campo email; cualquier otro
  // error va al bloque general.
  const errorEmail = error !== null && error.codigo === "EMAIL_DUPLICADO" ? error : null;
  const errorGeneral = errorEmail === null ? error : null;

  async function enviar(evento) {
    evento.preventDefault();

    // Igual que en PantallaSesion: el error del servidor —aqui, el 409 junto al
    // campo email— se limpia antes de validar, no despues.
    setError(null);

    const nombreVacio = nombre.trim() === "";
    const emailVacio = email.trim() === "";
    const passwordVacio = password === "";
    setFaltantes({ nombre: nombreVacio, email: emailVacio, password: passwordVacio });
    if (nombreVacio || emailVacio || passwordVacio) {
      return;
    }

    setEnviando(true);
    try {
      await onRegistrar(nombre.trim(), email.trim(), password);
      // Con exito, App cambia de vista: no se limpia nada aqui.
    } catch (fallo) {
      setError({ codigo: fallo.codigo, mensaje: fallo.mensaje });
      setPassword("");
      setEnviando(false);
    }
  }

  return (
    <main className="pantalla-acceso">
      <h1>Reserva de Canchas Deportivas</h1>
      <h2>Crear cuenta</h2>

      <form onSubmit={enviar} noValidate>
        <label htmlFor="nombre">Nombre</label>
        <input
          id="nombre"
          name="nombre"
          type="text"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
        />
        {faltantes.nombre ? <span className="campo-invalido">El nombre es obligatorio</span> : null}

        <label htmlFor="email">Correo</label>
        <input
          id="email"
          name="email"
          type="text"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
        />
        {faltantes.email ? <span className="campo-invalido">El correo es obligatorio</span> : null}
        {errorEmail ? <span className="campo-invalido">{errorEmail.mensaje}</span> : null}

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

        <MensajeError error={errorGeneral} />

        <button type="submit" disabled={enviando}>
          {enviando ? "Registrando..." : "Registrarme"}
        </button>
      </form>

      <button type="button" className="enlace" onClick={onVolver}>
        Ya tengo cuenta, iniciar sesion
      </button>
    </main>
  );
}

export default PantallaRegistro;
