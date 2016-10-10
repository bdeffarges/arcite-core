package com.actelion.research.arcite.core.api


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.actelion.research.arcite.core.experiments.ManageExperiments
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future


/**
  * Created by deffabe1 on 2/29/16.
  */
object Main extends App {
  println(args.mkString(" ; "))
  println(s"config environment file: ${System.getProperty("config.resource")}")

  // start cluster actor sytem
  ManageTransformCluster.defaultTransformClusterStartFromConf()

  // start experiments actor system
  ManageExperiments.startActorSystemForExperiments()

  // Gets the host and a port from the configuration
  val config = ConfigFactory.load()

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val system = ActorSystem("rest-api", config)

  implicit val ec = system.dispatcher //bindAndHandle requires an implicit ExecutionContext

  import scala.concurrent.duration.{Duration, FiniteDuration}
  val t = config.getString("akka.http.server.request-timeout")
  val d = Duration(t)
  val requestTimeout = FiniteDuration(d.length, d.unit)

  val api = new RestApi(system, requestTimeout).routes // the RestApi provides a Route

  implicit val materializer = ActorMaterializer()

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(api, host, port) //Starts the HTTP server

  val log = Logging(system.eventStream, "arcite...")

  bindingFuture.map { serverBinding ⇒
    log.info(s"RestApi bound to ${serverBinding.localAddress} ")
  }.onFailure {

    case ex: Exception ⇒
      log.error(ex, "Failed to bind to {}:{}!", host, port)
      system.terminate()
  }

  // wait for the user to stop the server
//  log.debug("Press <enter> to exit.")

//  Console.in.read.toChar
  //   gracefully shut down the server
//  log.debug("shutting down server, unbinding port.")
//  bindingFuture.flatMap(_.unbind()).onComplete(_ ⇒ system.terminate())
}



