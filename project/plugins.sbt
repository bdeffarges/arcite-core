logLevel := Level.Warn
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.3")

libraryDependencies += "com.spotify" % "docker-client" % "3.5.13"