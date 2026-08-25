const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const { ModuleFederationPlugin } = require("webpack").container;

const paquete = require("./package.json");

module.exports = {
  entry: "./src/index.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    // "auto" es obligatorio en un remote: sin ella los chunk se piden al origen
    // del shell (localhost:3000) y la carga del modulo falla.
    publicPath: "auto",
    uniqueName: "mfReportes",
    clean: true
  },
  resolve: {
    extensions: [".js", ".jsx"]
  },
  module: {
    rules: [
      {
        test: /\.jsx?$/,
        exclude: /node_modules/,
        use: "babel-loader"
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"]
      }
    ]
  },
  plugins: [
    new ModuleFederationPlugin({
      name: "mfReportes",
      filename: "remoteEntry.js",
      // Clave exacta del contrato congelado. El modulo expuesto es un
      // componente que recibe las cuatro props del shell, no un createRoot
      // (D-01).
      exposes: {
        "./ReportesApp": "./src/ReportesApp"
      },
      shared: {
        // La version se lee de package.json y no se escribe a mano: asi
        // requiredVersion no puede quedar desalineada de la dependencia real.
        // Sin singleton habria dos React en la pagina y los hooks fallarian.
        react: {
          singleton: true,
          requiredVersion: paquete.dependencies.react
        },
        "react-dom": {
          singleton: true,
          requiredVersion: paquete.dependencies["react-dom"]
        }
      }
    }),
    new HtmlWebpackPlugin({
      template: "./public/index.html"
    })
  ],
  // El bind mount de Windows no entrega eventos inotify dentro del contenedor:
  // sin sondeo, webpack nunca ve un archivo guardado y no recompila.
  watchOptions: {
    poll: 1000,
    ignored: /node_modules/
  },
  devServer: {
    port: 3003,
    // Dentro del contenedor, escuchar solo en localhost deja al navegador del
    // host sin acceso.
    host: "0.0.0.0",
    allowedHosts: "all",
    // El remoteEntry.js y sus chunk los pide una pagina servida en
    // localhost:3000: es otro origen y sin este encabezado el navegador
    // bloquea la descarga.
    headers: {
      "Access-Control-Allow-Origin": "*"
    },
    hot: true,
    client: {
      // El socket de recarga lo abre el navegador: su URL es la del host.
      webSocketURL: "ws://localhost:3003/ws",
      overlay: {
        // El BordeError del shell ya captura y muestra el fallo de un remote:
        // el overlay lo repetiria tapando la pantalla completa. Los errores
        // siguen saliendo en la consola del navegador.
        runtimeErrors: false
      }
    },
    // Destino del proxy: nombre de contenedor. Lo ejecuta webpack serve DENTRO
    // de la red de Docker, al contrario de la URL del remoteEntry.js, que es de
    // navegador.
    // Atiende solo cuando el remote se abre suelto en localhost:3003; montado
    // en el shell, proxya el devServer del shell.
    // D-15: una sola entrada. Este remote consume unicamente /api/reportes;
    // declarar los otros destinos sugeriria un acoplamiento que no existe.
    // webpack-dev-server 5 exige la forma de arreglo.
    proxy: [
      { context: ["/api/reportes"], target: "http://ms-reportes:8080" }
    ]
  }
};
