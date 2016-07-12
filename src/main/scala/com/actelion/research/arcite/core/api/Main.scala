package com.actelion.research.arcite.core.api


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.actelion.research.arcite.core.utils.Env
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

trait RequestTimeout {

  import scala.concurrent.duration._

  def requestTimeout(config: Config): Timeout = {
    val t = config.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}

/**
  * Created by deffabe1 on 2/29/16.
  */
object Main extends App with RequestTimeout {

  val config = ConfigFactory.load()

  // Gets the host and a port from the configuration
  val host = config.getString(s"${Env.getEnv()}.http.host")
  val port = config.getInt(s"${Env.getEnv()}.http.port")

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher //bindAndHandle requires an implicit ExecutionContext

  val api = new RestApi(system, requestTimeout(config)).routes // the RestApi provides a Route

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
  println("Press <enter> to exit.")
  Console.in.read.toChar
  // gracefully shut down the server
//  import system.dispatcher._
  bindingFuture.flatMap(_.unbind()).onComplete(_ ⇒ system.terminate())
}

