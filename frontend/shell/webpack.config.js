const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const { ModuleFederationPlugin } = require("webpack").container;

const paquete = require("./package.json");

module.exports = {
  entry: "./src/index.js",
  output: {
    path: path.resolve(__dirname, "dist"),
    // "auto" es obligatorio en un host: sin ella el shell pide los chunk de un
    // remote a su propio origen y la carga del remote falla.
    publicPath: "auto",
    uniqueName: "shell",
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
      name: "shell",
      // El host no expone ningun modulo (contrato Module Federation).
      remotes: {
        // URLs del NAVEGADOR, nunca nombres de contenedor: el navegador esta
        // fuera de la red de Docker y solo alcanza los puertos publicados.
        mfReservas: "mfReservas@http://localhost:3001/remoteEntry.js",
        mfAdministracion: "mfAdministracion@http://localhost:3002/remoteEntry.js",
        mfReportes: "mfReportes@http://localhost:3003/remoteEntry.js"
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
    port: 3000,
    // Dentro del contenedor, escuchar solo en localhost deja al navegador del
    // host sin acceso.
    host: "0.0.0.0",
    allowedHosts: "all",
    hot: true,
    client: {
      // El socket de recarga lo abre el navegador: su URL es la del host.
      webSocketURL: "ws://localhost:3000/ws",
      overlay: {
        // El BordeError ya captura y muestra el fallo de un remote: el overlay
        // taparia la pantalla con un error ya manejado y en la demo pareceria
        // un fallo lo que es el comportamiento esperado de HU-06. Los errores
        // siguen saliendo en la consola del navegador.
        runtimeErrors: false
      }
    },
    // Destino del proxy: el gateway Nginx, un unico nombre de contenedor (spec 10).
    // Lo ejecuta webpack serve DENTRO de la red de Docker, al contrario de las URLs de
    // los remotes, que son del navegador. Este archivo ya no sabe donde vive cada
    // microservicio: el reparto por dominio vive en infra/nginx/gateway.conf y en
    // ningun otro lado.
    // Los cuatro context se conservan tal cual (D-8): son los que dicen que consume
    // este microfrontend.
    // webpack-dev-server 5 exige la forma de arreglo.
    proxy: [
      { context: ["/api/usuarios"], target: "http://gateway:80" },
      { context: ["/api/canchas"], target: "http://gateway:80" },
      { context: ["/api/reservas"], target: "http://gateway:80" },
      { context: ["/api/reportes"], target: "http://gateway:80" }
    ]
  }
};
