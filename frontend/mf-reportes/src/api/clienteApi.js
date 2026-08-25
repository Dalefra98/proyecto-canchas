// Unica pieza del remote que llama fetch (CLAUDE.md seccion 4). La ruta se
// compone siempre con el apiBaseUrl que el shell entrega por prop ("/api"),
// nunca con una URL absoluta ni con un nombre de contenedor.
//
// D-04: replicado desde mf-administracion y recortado a GET. Este modulo es de
// solo lectura (PDF seccion 3.3.5): copiar publicar, reemplazar, parchear y
// eliminar dejaria cuatro funciones muertas que sugieren que escribe algo.

const ERROR_SIN_CUERPO = {
  codigo: "ERROR_INTERNO",
  mensaje: "No se pudo contactar al servicio"
};

// Diseno seccion 5.5: toda respuesta de error sale de aqui con la forma
// { codigo, mensaje } del contrato, incluso cuando el cuerpo no la trae (un 502
// del proxy, una red cortada). Asi los componentes solo conocen una forma.
async function leerError(respuesta) {
  try {
    const cuerpo = await respuesta.json();
    if (cuerpo && cuerpo.codigo && cuerpo.mensaje) {
      return { codigo: cuerpo.codigo, mensaje: cuerpo.mensaje };
    }
    return ERROR_SIN_CUERPO;
  } catch (error) {
    return ERROR_SIN_CUERPO;
  }
}

// Error de negocio o de transporte ya normalizado. Se lanza para que el llamador
// decida: clienteApi no interpreta el 401. En este remote esa decision vive en
// un unico envoltorio de ReportesApp (diseno seccion 4.1).
export class ErrorApi extends Error {
  constructor(estado, codigo, mensaje) {
    super(mensaje);
    this.name = "ErrorApi";
    this.estado = estado;
    this.codigo = codigo;
    this.mensaje = mensaje;
  }
}

// D-03: el token llega por parametro en cada llamada, no en una variable de
// modulo. Es una prop que puede cambiar, y una copia guardada se quedaria con el
// valor viejo tras un cambio de sesion.
export async function obtener(apiBaseUrl, ruta, token) {
  const encabezados = {};
  if (token) {
    encabezados.Authorization = "Bearer " + token;
  }

  let respuesta;
  try {
    respuesta = await fetch(apiBaseUrl + ruta, {
      method: "GET",
      headers: encabezados
    });
  } catch (error) {
    // Sin respuesta: microservicio caido, proxy sin destino o red cortada.
    throw new ErrorApi(0, ERROR_SIN_CUERPO.codigo, ERROR_SIN_CUERPO.mensaje);
  }

  if (!respuesta.ok) {
    const error = await leerError(respuesta);
    throw new ErrorApi(respuesta.status, error.codigo, error.mensaje);
  }

  try {
    return await respuesta.json();
  } catch (error) {
    // Un 200 que no trae el JSON esperado es un fallo de integracion, no un
    // error del usuario. Las tres rutas de reportes siempre responden con
    // cuerpo: no hay 204 que atender aqui.
    throw new ErrorApi(respuesta.status, ERROR_SIN_CUERPO.codigo, ERROR_SIN_CUERPO.mensaje);
  }
}
