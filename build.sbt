import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.ExecCmd

organization := "com.actelion.research.arcite"

name := "arcite-core"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.8"

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
  "local maven" at Path.userHome.absolutePath + "/.m2/repository/",
  Resolver.file("ivy local", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
  "Typesafe Repository" at "http://dl.bintray.com/typesafe/maven-releases/",
  "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "Sonatype Actelion snapshots" at "http://bioinfo.it.actelion.com/nexus/content/repositories/snapshots/",
  "Sonatype Actelion releases" at "http://bioinfo.it.actelion.com/nexus/content/repositories/releases/")

libraryDependencies ++= {
  val akkaVersion = "2.4.8"
  val sparkVersion = "1.6.2"
  val luceneVersion = "5.0.0"

  Seq(
    "org.specs2" %% "specs2-core" % "3.7" % "test",
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % "test",
    "com.github.agourlay" %% "cornichon" % "0.7.2" % "test",
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
    "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
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
    "org.scalacheck" %% "scalacheck" % "1.13.0" % "test"
  )
}

enablePlugins(JavaServerAppPackaging)
//enablePlugins(JavaAppPackaging)
//enablePlugins(DockerPlugin)

//enablePlugins(DockerSpotifyClientPlugin)

//mainClass in Compile := Some("com.actelion.research.arcite.core.api.Main")

//dockerCommands ++= Seq(
//  ExecCmd("RUN", "su"),
//  ExecCmd("RUN", "apt-get", "update"),
//  ExecCmd("RUN", "apt-get", "install", "netstat")
//)

mappings in Universal ++= {
  // optional example illustrating how to copy additional directory
  directory("scripts") ++
    // copy configuration files to config directory
    contentOf("src/main/resources").toMap.mapValues("config/" + _)
}

// add ’config’ directory first in the classpath of the start script,
// an alternative is to set the config file locations via CLI parameters
// when starting the application
scriptClasspath := Seq("../config/") ++ scriptClasspath.value


dockerExposedPorts := Seq(3585)