package com.actelion.research.arcite.core.api


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.{Allow, RawHeader}
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler}
import akka.stream.ActorMaterializer
import com.actelion.research.arcite.core.experiments.ExperimentActorsManager
import com.actelion.research.arcite.core.transforms.cluster.ManageTransformCluster
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future


/**
  * Created by deffabe1 on 2/29/16.
  */
object Main extends App {
  println(args.mkString(" ; "))
  println(s"config environment file: ${System.getProperty("config.resource")}")

  ManageTransformCluster.defaultTransformClusterStartFromConf()

  ExperimentActorsManager.startExperimentActorSystem()

  val config = ConfigFactory.load()

  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  implicit val system = ActorSystem("rest-api", config)

  implicit val ec = system.dispatcher

  import akka.http.scaladsl.model.StatusCodes._
  import akka.http.scaladsl.server.Directives._

  import scala.concurrent.duration.{Duration, FiniteDuration}
  val t = config.getString("akka.http.server.request-timeout")
  val d = Duration(t)
  val requestTimeout = FiniteDuration(d.length, d.unit)

  // create the top service actor (for children actor like the logging actor, many others sit under the Exp. Manager)
  private val arciteAppService = system.actorOf(AppServiceActorsManager.props(), "arcite_app_service")

  val api = new RestApi(system, requestTimeout).routes

  implicit val materializer = ActorMaterializer()

  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  implicit def rejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "
      respondWithHeaders(Allow(methods) +: corsHeaders) {
        options {
          complete(s"Supported methods : $names.")
        } ~
          complete(MethodNotAllowed, s"HTTP method not allowed, supported methods: $names!")
      }
    }.result()

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(api, host, port)

  val log = Logging(system.eventStream, "arcite...")

  bindingFuture.map { serverBinding ⇒
    log.info(s"RestApi bound to ${serverBinding.localAddress} ")
  }.onFailure {
    case ex: Exception ⇒
      log.error(ex, s"Failed to bind to $host:$port")
      system.terminate()
  }
}



