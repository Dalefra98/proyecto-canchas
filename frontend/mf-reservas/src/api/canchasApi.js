import { obtener } from "./clienteApi";

// Unica ruta de ms-canchas que consume este remote (diseno seccion 6.1). Sirve
// para el filtro por deporte, el selector de cancha y para resolver canchaId a
// nombre en el listado de reservas. El filtrado por rol lo aplica ms-canchas: un
// USUARIO solo recibe las canchas con activa = true.
export function listarCanchas(apiBaseUrl, token) {
  return obtener(apiBaseUrl, "/canchas", token);
}
