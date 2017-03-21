import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.Cmd

organization := "com.actelion.research.arcite"

name := "arcite-core"

version := "1.25.0-SNAPSHOT"

scalaVersion := "2.11.8" // todo move to 2.12 once spark has moved available

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
  , "-Yclosure-elim"
  , "-Yinline"
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
  Resolver.sonatypeRepo("snapshots"))


libraryDependencies ++= {
  val akkaVersion = "2.4.16"
  val sparkVersion = "1.6.2"
  val luceneVersion = "6.4.0"

  Seq(
    "org.specs2" %% "specs2-core" % "3.7" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
    "com.github.agourlay" %% "cornichon" % "0.9.3" % "test",
    "org.json4s" %% "json4s-jackson" % "3.3.0" % "test",
    "com.typesafe.akka" %% "akka-kernel" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe.akka" %% "akka-kernel" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "3.0.0-RC1", // core, test, etc. should come as well as dependencies
    "com.typesafe.akka" %% "akka-http-testkit" % "3.0.0-RC1",
    "com.typesafe.akka" %% "akka-http-spray-json" % "3.0.0-RC1",
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "com.iheart" %% "ficus" % "1.4.0",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-graphx" % sparkVersion,
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "com.databricks" %% "spark-csv" % "1.4.0",
    "org.scalanlp" % "breeze_2.11" % "0.11.2",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    "org.apache.lucene" % "lucene-core" % luceneVersion,
    "org.apache.lucene" % "lucene-suggest" % luceneVersion,
    "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
    "org.apache.lucene" % "lucene-queries" % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
    "commons-io" % "commons-io" % "2.4" % "test",
    "org.iq80.leveldb" % "leveldb" % "0.7",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "org.scalacheck" %% "scalacheck" % "1.13.0" % "test",
    "io.kamon" %% "kamon-core" % "0.6.0",
    "io.kamon" %% "kamon-statsd" % "0.6.0",
    "io.kamon" %% "kamon-datadog" % "0.6.0")
}

enablePlugins(JavaServerAppPackaging)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

enablePlugins(DockerSpotifyClientPlugin)

mainClass in Compile := Some("com.actelion.research.arcite.core.api.Main")

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
  Cmd("MAINTAINER", "Bernard Deffarges bernard.deffarges@actelion.com"),
  Cmd("RUN", "echo Europe/Berlin > /etc/timezone && dpkg-reconfigure --frontend noninteractive tzdata"),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("COPY", "opt /opt"),
  Cmd("RUN", """chown -R daemon:daemon ."""),
  Cmd("EXPOSE", "8084 2551 2552 2553 2554 2555 2556 2557 2558"),
  Cmd("USER", "daemon"),
  Cmd("ENTRYPOINT","bin/arcite-core"))

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

bashScriptExtraDefines += """addJava "-Dconfig.resource=$ARCITE_CONF""""