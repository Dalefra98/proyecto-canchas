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
  devServer: {
    port: 3000,
    // Dentro del contenedor, escuchar solo en localhost deja al navegador del
    // host sin acceso.
    host: "0.0.0.0",
    allowedHosts: "all",
    hot: true,
    client: {
      // El socket de recarga lo abre el navegador: su URL es la del host.
      webSocketURL: "ws://localhost:3000/ws"
    },
    // Destino del proxy: nombres de contenedor. Lo ejecuta webpack serve DENTRO
    // de la red de Docker, al contrario de las URLs de los remotes.
    // webpack-dev-server 5 exige la forma de arreglo.
    proxy: [
      { context: ["/api/usuarios"], target: "http://ms-usuarios:8080" },
      { context: ["/api/canchas"], target: "http://ms-canchas:8080" },
      { context: ["/api/reservas"], target: "http://ms-reservas:8080" },
      { context: ["/api/reportes"], target: "http://ms-reportes:8080" }
    ]
  }
};
