import { Component } from "react";

// D-10: unico componente de clase del shell. Un React.lazy que rechaza propaga
// el error al render, y en React 18 solo un borde de error de clase lo
// intercepta; un try/catch alrededor del import() no ve un fallo de render del
// remote ya cargado.
class BordeError extends Component {
  constructor(props) {
    super(props);
    this.state = { fallo: false };
  }

  static getDerivedStateFromError() {
    return { fallo: true };
  }

  componentDidCatch(error) {
    // El detalle queda en la consola del navegador; al usuario se le muestra el
    // mensaje del layout, sin stacktrace.
    console.error("Fallo el modulo remoto", error);
  }

  componentDidUpdate(propsAnteriores) {
    // Al cambiar de modulo se reintenta: sin esto, un remote caido dejaria el
    // borde en estado de fallo para siempre.
    if (propsAnteriores.clave !== this.props.clave && this.state.fallo) {
      this.setState({ fallo: false });
    }
  }

  render() {
    if (this.state.fallo) {
      return (
        <section className="contenido-modulo">
          <p className="aviso" role="alert">
            Modulo no disponible. Intente mas tarde o elija otro modulo.
          </p>
        </section>
      );
    }

    return this.props.children;
  }
}

export default BordeError;
