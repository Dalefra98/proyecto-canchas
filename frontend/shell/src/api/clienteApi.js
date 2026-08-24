// Unica pieza del shell que llama fetch (CLAUDE.md seccion 4). Siempre con
// rutas relativas bajo /api: el devServer las proxya hacia el microservicio.

const ERROR_SIN_CUERPO = {
  codigo: "ERROR_INTERNO",
  mensaje: "No se pudo contactar al servicio"
};

// D-04: toda respuesta de error sale de aqui con la forma { codigo, mensaje }
// del contrato, incluso cuando el cuerpo no la trae (un 502 del proxy, una red
// cortada). Asi los componentes solo conocen una forma de error.
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
// decida: clienteApi no interpreta el 401 (D-07), porque solo el llamador sabe
// si habia sesion.
export class ErrorApi extends Error {
  constructor(estado, codigo, mensaje) {
    super(mensaje);
    this.name = "ErrorApi";
    this.estado = estado;
    this.codigo = codigo;
    this.mensaje = mensaje;
  }
}

async function pedir(ruta, opciones) {
  const metodo = opciones.metodo;
  const cuerpo = opciones.cuerpo;
  const token = opciones.token;

  const encabezados = { "Content-Type": "application/json" };
  if (token) {
    encabezados.Authorization = "Bearer " + token;
  }

  let respuesta;
  try {
    respuesta = await fetch(ruta, {
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

export function obtener(ruta, token) {
  return pedir(ruta, { metodo: "GET", token: token });
}

export function publicar(ruta, cuerpo, token) {
  return pedir(ruta, { metodo: "POST", cuerpo: cuerpo, token: token });
}
