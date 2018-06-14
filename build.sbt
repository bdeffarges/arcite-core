import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.Cmd

organization := "com.idorsia.research.arcite"

name := "arcite-core"

version := "1.87.19"

scalaVersion := "2.11.8"

crossScalaVersions := Seq(scalaVersion.value, "2.12.4")

scmInfo := Some(
  ScmInfo(
    url("https://chiron.idorsia.com/stash/projects/ARC/repos/arcite-core/browse"),
    "scm:ssh://git@chiron.idorsia.com:7999/arc/arcite-core.git",
    Some("scm:git:git@chiron.idorsia.com:7999/arc/arcite-core.git")
  )
)

// These options will be used for *all* versions.
scalacOptions ++= Seq(
  "-deprecation"
  , "-unchecked"
  , "-encoding", "UTF-8"
  , "-Xlint"
  //  , "-Yclosure-elim"
  //  , "-Yinline"
  , "-Xverify"
  , "-feature"
  , "-language:postfixOps"
)

credentials += Credentials("Sonatype Nexus Repository Manager", "nexus.idorsia.com", "deployment", "biodeploy")

publishMavenStyle := true

publishTo := {
  val nexus = "http://nexus.idorsia.com/repository/"
  if (version.value.toString.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "idorsia-snapshots")
  else
    Some("releases" at nexus + "idorsia-releases")
}

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.file("ivy local", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  Resolver.bintrayRepo("typesafe", "maven-releases"),
  Resolver.jcenterRepo,
  Resolver.sonatypeRepo("public"),
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  MavenRepository("mvn-repository", "https://mvnrepository.com/artifact/"),
  MavenRepository("Artima Maven Repository", "http://repo.artima.com/releases/"))


libraryDependencies ++= {
  val akkaVersion = "2.5.12"
  val akkaHttpVersion = "10.1.1"
  val luceneVersion = "6.5.0"
  val akkaManagementVersion = "0.12.0"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-agent" % akkaVersion,
    "com.typesafe.akka" %% "akka-camel" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-osgi" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.apache.lucene" % "lucene-core" % luceneVersion,
    "org.apache.lucene" % "lucene-suggest" % luceneVersion,
    "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
    "org.apache.lucene" % "lucene-queries" % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.scalanlp" %% "breeze" % "0.13",
    "org.iq80.leveldb" % "leveldb" % "0.9",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "commons-io" % "commons-io" % "2.5",
    "io.swagger" % "swagger-jaxrs" % "1.5.18",
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.13.0",
    "javax.xml.bind" % "jaxb-api" % "2.3.0", //https://github.com/swagger-akka-http/swagger-akka-http/issues/62
    "org.slf4j" % "slf4j-simple" % "1.7.25",
    "ch.megard" %% "akka-http-cors" % "0.2.2",
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
    "org.specs2" %% "specs2-core" % "3.8.9" % "test",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "com.github.agourlay" %% "cornichon" % "0.11.2" % "test",
    "org.json4s" %% "json4s-jackson" % "3.5.1" % "test",
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management" % akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-marathon-api" % akkaManagementVersion)
}

enablePlugins(JavaServerAppPackaging)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

enablePlugins(DockerSpotifyClientPlugin)

mainClass in Compile := Some("com.idorsia.research.arcite.core.Main")

mappings in Universal ++= {
  // optional example illustrating how to copy additional directory
  directory("scripts") ++
    // copy configuration files to config directory
    contentOf("src/main/resources").toMap.mapValues("config/" + _)
}

javaOptions in Universal ++= Seq(
  // -J params will be added as jvm parameters
  "-J-Xmx1G",
  "-J-Xms256m"

  //   others will be added as app parameters
  // should be given as variable by docker run

  // you can access any build setting/task here
  //  s"-version=${version.value}"
)

// all arcite docker will work as user/group arcite/arcite
// to make sure it's always the same user we need to use uid and gid.
// so to create the group, use:
//sudo addgroup -gid 987654 arcite
// and to create the user:
// sudo useradd --home-dir /home/arcite --uid 987654 --gid 987654 arcite
// of course the uid and gid have to map with those below in all the docker files.
// the owner of the arcite path on the host should be arcite:arcite.
dockerCommands := Seq(
  Cmd("FROM", "openjdk:latest"),
  Cmd("MAINTAINER", "Bernard Deffarges bernard.deffarges@idorsia.com"),
  Cmd("RUN", "echo Europe/Berlin > /etc/timezone && dpkg-reconfigure --frontend noninteractive tzdata"),
  Cmd("RUN", "addgroup -gid 987654 arcite"),
  Cmd("RUN", "useradd --home-dir /home/arcite --uid 987654 --gid 987654 arcite"),
  Cmd("RUN", "mkdir /home/arcite && chown arcite:arcite /home/arcite"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("COPY", "opt /opt"),
  Cmd("RUN", """chown -R arcite:arcite ."""),
  Cmd("EXPOSE", "8084"),
  Cmd("USER", "arcite"),
  Cmd("ENTRYPOINT", "bin/arcite-core"))

dockerRepository := Some("nexus-docker.idorsia.com")

dockerAlias := DockerAlias(dockerRepository.value, Some("arcite"), packageName.value, Some(version.value))


licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

bashScriptExtraDefines += """addJava "-Dconfig.resource=$ARCITE_CONF""""
bashScriptExtraDefines += """addJava "-Dlaunch=$LAUNCH""""
bashScriptExtraDefines += """addJava "-Dport=$PORT""""