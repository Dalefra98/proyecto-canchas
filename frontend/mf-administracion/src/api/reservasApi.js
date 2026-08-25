import { obtener, parchear } from "./clienteApi";

// Las dos rutas de ms-reservas que consume este remote (diseno seccion 6.1).

// Listado global, solo ADMIN. No acepta parametros de filtrado ni de paginacion:
// el filtro por estado se aplica en el navegador al pintar (D-09).
export function listarReservas(apiBaseUrl, token) {
  return obtener(apiBaseUrl, "/reservas", token);
}

// Sin cuerpo: el contrato no declara ningun campo de entrada (seccion 5.5). El
// ADMIN puede cancelar cualquier reserva (RN-03) y quien lo valida es
// ms-reservas con el token.
export function cancelarReserva(apiBaseUrl, token, id) {
  return parchear(apiBaseUrl, "/reservas/" + id + "/cancelacion", undefined, token);
}
