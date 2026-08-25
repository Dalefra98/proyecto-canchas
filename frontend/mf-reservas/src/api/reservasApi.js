import { obtener, publicar, parchear } from "./clienteApi";

// Las cuatro rutas de ms-reservas que consume este remote (diseno seccion 6.1).
// Nombres de parametro y de campo tal como los congelo el contrato.

export function consultarDisponibilidad(apiBaseUrl, token, canchaId, fecha) {
  const consulta =
    "?canchaId=" + encodeURIComponent(canchaId) + "&fecha=" + encodeURIComponent(fecha);
  return obtener(apiBaseUrl, "/reservas/disponibilidad" + consulta, token);
}

// Cuerpo de tres campos (diseno seccion 5.2). horaFin lo calcula ms-reservas,
// usuarioId sale del token y estado lo fija el servicio: no se envian.
export function crearReserva(apiBaseUrl, token, canchaId, fecha, horaInicio) {
  return publicar(
    apiBaseUrl,
    "/reservas",
    { canchaId: canchaId, fecha: fecha, horaInicio: horaInicio },
    token
  );
}

export function listarMisReservas(apiBaseUrl, token) {
  return obtener(apiBaseUrl, "/reservas/mias", token);
}

// Sin cuerpo: el contrato no declara ningun campo de entrada.
export function cancelarReserva(apiBaseUrl, token, id) {
  return parchear(apiBaseUrl, "/reservas/" + id + "/cancelacion", token);
}
