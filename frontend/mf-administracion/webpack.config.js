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
    uniqueName: "mfAdministracion",
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
      name: "mfAdministracion",
      filename: "remoteEntry.js",
      // Clave exacta del contrato congelado. El modulo expuesto es un
      // componente que recibe las cuatro props del shell, no un createRoot.
      exposes: {
        "./AdminApp": "./src/AdminApp"
      },
      shared: {
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
    port: 3002,
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
      webSocketURL: "ws://localhost:3002/ws",
      overlay: {
        // El BordeError del shell ya captura y muestra el fallo de un remote:
        // el overlay lo repetiria tapando la pantalla completa. Los errores
        // siguen saliendo en la consola del navegador.
        runtimeErrors: false
      }
    },
    // Destino del proxy: el gateway Nginx, un unico nombre de contenedor (spec 10).
    // Lo ejecuta webpack serve DENTRO de la red de Docker, al contrario de las URLs de
    // los remotes. Este archivo ya no sabe donde vive cada microservicio: el reparto
    // por dominio vive en infra/nginx/gateway.conf y en ningun otro lado.
    // Los context son los que dicen que consume este microfrontend: usuarios, canchas
    // y reservas, los tres microservicios de P-08 de la spec 08.
    // La cuarta entrada, /api/reportes, se elimino el 25/08/2026 (correccion de D-8):
    // era residual, anterior al gateway y nunca usada -este remote no llama a
    // ms-reportes-, y declararla sugeria un acoplamiento inexistente. Mismo criterio
    // que D-15 aplico en mf-reportes.
    // Atiende solo cuando el remote se abre suelto en localhost:3002; montado
    // en el shell, proxya el devServer del shell.
    // webpack-dev-server 5 exige la forma de arreglo.
    proxy: [
      { context: ["/api/usuarios"], target: "http://gateway:80" },
      { context: ["/api/canchas"], target: "http://gateway:80" },
      { context: ["/api/reservas"], target: "http://gateway:80" }
    ]
  }
};
