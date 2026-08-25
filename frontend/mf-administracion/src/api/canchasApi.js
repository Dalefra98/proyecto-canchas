import { obtener, publicar, reemplazar, parchear, eliminar } from "./clienteApi";

// Las siete rutas de ms-canchas que consume este remote (diseno seccion 6.1).
// Nombres de campo tal como los congelo el contrato.

// Al ADMIN esta ruta le devuelve todas las canchas, incluidas las inactivas:
// el filtrado por rol lo aplica ms-canchas sin parametro de consulta.
export function listarCanchas(apiBaseUrl, token) {
  return obtener(apiBaseUrl, "/canchas", token);
}

// Cuerpo de cuatro campos (seccion 5.1). canchaId lo genera la base y activa se
// maneja solo con el PATCH de estado: no se envian.
export function crearCancha(apiBaseUrl, token, cancha) {
  return publicar(apiBaseUrl, "/canchas", cancha, token);
}

// El PUT reemplaza los cuatro campos editables y no toca activa (D-11 de la
// spec 03): se envian los cuatro aunque solo haya cambiado uno.
export function editarCancha(apiBaseUrl, token, canchaId, cancha) {
  return reemplazar(apiBaseUrl, "/canchas/" + canchaId, cancha, token);
}

// activa siempre presente: el DTO usa Boolean con @NotNull y un cuerpo sin el
// campo es 400, no un false implicito (seccion 5.2).
export function cambiarEstadoCancha(apiBaseUrl, token, canchaId, activa) {
  return parchear(apiBaseUrl, "/canchas/" + canchaId + "/estado", { activa: activa }, token);
}

// Sin el parametro opcional fecha: esta pantalla muestra siempre todos los
// bloqueos de la cancha (P-03).
export function listarBloqueos(apiBaseUrl, token, canchaId) {
  return obtener(apiBaseUrl, "/canchas/" + canchaId + "/bloqueos", token);
}

// Cuerpo de cuatro campos (seccion 5.3). canchaId viaja en la ruta y bloqueoId
// lo genera la base: no se envian.
export function crearBloqueo(apiBaseUrl, token, canchaId, bloqueo) {
  return publicar(apiBaseUrl, "/canchas/" + canchaId + "/bloqueos", bloqueo, token);
}

// El {id} de la ruta congelada se rellena con el bloqueoId del objeto. Responde
// 204 sin cuerpo y clienteApi devuelve null.
export function eliminarBloqueo(apiBaseUrl, token, canchaId, bloqueoId) {
  return eliminar(apiBaseUrl, "/canchas/" + canchaId + "/bloqueos/" + bloqueoId, token);
}
