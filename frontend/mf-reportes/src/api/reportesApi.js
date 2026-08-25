import { obtener } from "./clienteApi";

// Las tres rutas del contrato congelado (diseno seccion 6.1), todas de rol ADMIN
// y todas GET. Este remote no llama ninguna otra ruta del sistema: ni
// /api/usuarios, ni /api/canchas, ni /api/reservas.
//
// desde y hasta son obligatorios en las tres y sus dos extremos son inclusivos.
// Se envian tal como llegan, sin reformatear ni convertir de zona horaria
// (HU-01). encodeURIComponent es la unica transformacion: el valor viaja en la
// URL.
function rutaConRango(reporte, desde, hasta) {
  return (
    "/reportes/" +
    reporte +
    "?desde=" +
    encodeURIComponent(desde) +
    "&hasta=" +
    encodeURIComponent(hasta)
  );
}

export function obtenerOcupacion(apiBaseUrl, token, desde, hasta) {
  return obtener(apiBaseUrl, rutaConRango("ocupacion", desde, hasta), token);
}

export function obtenerReservas(apiBaseUrl, token, desde, hasta) {
  return obtener(apiBaseUrl, rutaConRango("reservas", desde, hasta), token);
}

export function obtenerCancelaciones(apiBaseUrl, token, desde, hasta) {
  return obtener(apiBaseUrl, rutaConRango("cancelaciones", desde, hasta), token);
}
