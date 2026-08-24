import { publicar } from "./clienteApi";

// Las dos rutas publicas de ms-usuarios, las unicas que consume el shell
// (design seccion 6.1). Ninguna lleva Authorization.

export function iniciarSesion(email, password) {
  return publicar("/api/usuarios/sesiones", { email: email, password: password });
}

export function registrarUsuario(nombre, email, password) {
  return publicar("/api/usuarios", { nombre: nombre, email: email, password: password });
}
