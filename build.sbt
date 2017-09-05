import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.Cmd

organization := "com.idorsia.research.arcite"

name := "arcite-core"

version := "1.70.5-SNAPSHOT"

scalaVersion := "2.11.8"

crossScalaVersions := Seq(scalaVersion.value,"2.12.1")

scmInfo := Some(
  ScmInfo(
    url("https://chiron.europe.actelion.com/stash/projects/ARC/repos/arcite-core/browse"),
    "scm:ssh://git@chiron.europe.actelion.com:7999/arc/arcite-core.git",
    Some("scm:git:git@chiron.europe.actelion.com:7999/arc/arcite-core.git")
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

credentials += Credentials("Sonatype Nexus Repository Manager", "bioinfo.it.actelion.com", "deployment", "biodeploy")

publishMavenStyle := true

publishTo := {
  val nexus = "http://bioinfo.it.actelion.com/nexus/content/repositories"
  if (version.value.toString.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "/snapshots")
  else
    Some("releases" at nexus + "/releases")
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
  val akkaVersion = "2.5.3"
  val akkaHttpVersion = "10.0.9"
  val luceneVersion = "6.5.0"

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
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
    "org.specs2" %% "specs2-core" % "3.8.9" % "test",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "com.github.agourlay" %% "cornichon" % "0.11.2" % "test",
    "org.json4s" %% "json4s-jackson" % "3.5.1" % "test")
}

enablePlugins(JavaServerAppPackaging)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

enablePlugins(DockerSpotifyClientPlugin)

mainClass in Compile := Some("com.idorsia.research.arcite.core.api.Main")

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
dockerCommands := Seq(
  Cmd("FROM", "openjdk:latest"),
  Cmd("MAINTAINER", "Bernard Deffarges bernard.deffarges@idorsia.com"),
  Cmd("RUN", "echo Europe/Berlin > /etc/timezone && dpkg-reconfigure --frontend noninteractive tzdata"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("COPY", "opt /opt"),
  Cmd("RUN", """chown -R daemon:daemon ."""),
  Cmd("EXPOSE", "8084 2551 2552 2553 2554 2555 2556 2557 2558"),
  Cmd("USER", "daemon"),
  Cmd("ENTRYPOINT", "bin/arcite-core"))

dockerRepository := Some("gaia:5000")
dockerAlias := DockerAlias(dockerRepository.value, Some("core"), packageName.value, Some(version.value))


licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

bashScriptExtraDefines += """addJava "-Dconfig.resource=$ARCITE_CONF""""
