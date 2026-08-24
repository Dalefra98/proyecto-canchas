// HU-05, design 6.2. El ADMIN ve los tres modulos; el USUARIO solo Reservas.
// Ocultar una opcion no es control de acceso: el permiso real lo aplica cada
// microservicio con el token. Por eso App vuelve a validar el rol antes de
// montar.
export const MODULOS = [
  { clave: "mfReservas", etiqueta: "Reservas", roles: ["ADMIN", "USUARIO"] },
  { clave: "mfAdministracion", etiqueta: "Administracion", roles: ["ADMIN"] },
  { clave: "mfReportes", etiqueta: "Reportes", roles: ["ADMIN"] }
];

export function modulosDelRol(rol) {
  return MODULOS.filter((modulo) => modulo.roles.includes(rol));
}

function MenuModulos({ rol, vista, onAbrirModulo, onIrAInicio }) {
  return (
    <nav className="menu-modulos">
      <button
        type="button"
        className={vista === "bienvenida" ? "activo" : ""}
        onClick={onIrAInicio}
      >
        Inicio
      </button>
      {modulosDelRol(rol).map((modulo) => (
        <button
          key={modulo.clave}
          type="button"
          className={vista === modulo.clave ? "activo" : ""}
          onClick={() => onAbrirModulo(modulo.clave)}
        >
          {modulo.etiqueta}
        </button>
      ))}
    </nav>
  );
}

export default MenuModulos;
