// Unica pieza del remote que llama fetch (CLAUDE.md seccion 4). La ruta se
// compone siempre con el apiBaseUrl que el shell entrega por prop ("/api"),
// nunca con una URL absoluta ni con un nombre de contenedor.

const ERROR_SIN_CUERPO = {
  codigo: "ERROR_INTERNO",
  mensaje: "No se pudo contactar al servicio"
};

// Diseno seccion 5.10: toda respuesta de error sale de aqui con la forma
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
// un unico envoltorio de AdminApp (diseno seccion 4.1).
export class ErrorApi extends Error {
  constructor(estado, codigo, mensaje) {
    super(mensaje);
    this.name = "ErrorApi";
    this.estado = estado;
    this.codigo = codigo;
    this.mensaje = mensaje;
  }
}

// D-05: el token llega por parametro en cada llamada, no en una variable de
// modulo. Es una prop que puede cambiar, y una copia guardada se quedaria con el
// valor viejo tras un cambio de sesion.
async function pedir(apiBaseUrl, ruta, opciones) {
  const metodo = opciones.metodo;
  const cuerpo = opciones.cuerpo;
  const token = opciones.token;

  const encabezados = {};
  if (token) {
    encabezados.Authorization = "Bearer " + token;
  }
  // Sin cuerpo no se declara Content-Type: la cancelacion es un PATCH sin
  // cuerpo y el borrado de un bloqueo es un DELETE sin cuerpo (seccion 5.5).
  if (cuerpo !== undefined) {
    encabezados["Content-Type"] = "application/json";
  }

  let respuesta;
  try {
    respuesta = await fetch(apiBaseUrl + ruta, {
      method: metodo,
      headers: encabezados,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo)
    });
  } catch (error) {
    // Sin respuesta: microservicio caido, proxy sin destino o red cortada.
    throw new ErrorApi(0, ERROR_SIN_CUERPO.codigo, ERROR_SIN_CUERPO.mensaje);
  }

  if (!respuesta.ok) {
    const error = await leerError(respuesta);
    throw new ErrorApi(respuesta.status, error.codigo, error.mensaje);
  }

  // El borrado de un bloqueo responde 204 sin cuerpo: no hay JSON que leer
  // (seccion 5.5).
  if (respuesta.status === 204) {
    return null;
  }

  try {
    return await respuesta.json();
  } catch (error) {
    // Un 2xx que no trae el JSON esperado es un fallo de integracion, no un
    // error del usuario.
    throw new ErrorApi(respuesta.status, ERROR_SIN_CUERPO.codigo, ERROR_SIN_CUERPO.mensaje);
  }
}

export function obtener(apiBaseUrl, ruta, token) {
  return pedir(apiBaseUrl, ruta, { metodo: "GET", token: token });
}

export function publicar(apiBaseUrl, ruta, cuerpo, token) {
  return pedir(apiBaseUrl, ruta, { metodo: "POST", cuerpo: cuerpo, token: token });
}

export function reemplazar(apiBaseUrl, ruta, cuerpo, token) {
  return pedir(apiBaseUrl, ruta, { metodo: "PUT", cuerpo: cuerpo, token: token });
}

// El cuerpo es opcional: los dos PATCH de estado lo llevan (seccion 5.2 y 5.4) y
// el de cancelacion no (seccion 5.5).
export function parchear(apiBaseUrl, ruta, cuerpo, token) {
  return pedir(apiBaseUrl, ruta, { metodo: "PATCH", cuerpo: cuerpo, token: token });
}

export function eliminar(apiBaseUrl, ruta, token) {
  return pedir(apiBaseUrl, ruta, { metodo: "DELETE", token: token });
}
