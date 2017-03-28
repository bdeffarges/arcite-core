logLevel := Level.Warn

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0-M8")

addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.2")

libraryDependencies += "com.spotify" % "docker-client" % "7.0.2"
