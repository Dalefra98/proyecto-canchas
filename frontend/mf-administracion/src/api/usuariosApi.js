import { obtener, parchear } from "./clienteApi";

// Las dos rutas de ms-usuarios que consume este remote (diseno seccion 6.1).

// Solo ADMIN. Sirve para la pantalla de usuarios y para resolver el usuarioId de
// cada reserva a su nombre en el listado global (P-05).
export function listarUsuarios(apiBaseUrl, token) {
  return obtener(apiBaseUrl, "/usuarios", token);
}

// activo siempre presente, por el mismo motivo que activa en las canchas: el DTO
// usa Boolean con @NotNull (seccion 5.4). Es activo, del usuario, no activa.
export function cambiarEstadoUsuario(apiBaseUrl, token, usuarioId, activo) {
  return parchear(apiBaseUrl, "/usuarios/" + usuarioId + "/estado", { activo: activo }, token);
}
