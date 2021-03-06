package com.idorsia.research.arcite.core.api

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.Success
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.AllFilesInformation
import com.idorsia.research.arcite.core.transforms.{TransformCompletionFeedback, TransformCompletionStatus}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/11/10.
  *
  */
class ApiTests extends AsyncFlatSpec with Matchers
  with ExpJsonProto with TransfJsonProto
  with LazyLogging with Eventually {

  val config = ConfigFactory.load()

//  val host = config.getString("http.host")
//  val port = config.getInt("http.port")

  val host = "arcite-api-edge.idorsia.com"
  val port = 80

  val urlPrefix = "/api/v1"

  protected var transStatus: Map[String, Boolean] = Map.empty

  protected var filesInfo: Option[AllFilesInformation] = None

  implicit var system: ActorSystem = null
  implicit var materializer: ActorMaterializer = null

  def checkTransformStatus(transf: String): Unit = {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform/${transf}"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._

    println(transStatus)

    responseFuture.map { r ⇒
      if (r.status == StatusCodes.OK) {
        val fb = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
          .parseJson.convertTo[Option[TransformCompletionFeedback]]
        transStatus += transf -> (fb.isDefined && fb.get.status == TransformCompletionStatus.SUCCESS)
      }
    }
  }

  override def withFixture(test: NoArgAsyncTest) = {
    system = ActorSystem()
    materializer = ActorMaterializer()
    complete {
      super.withFixture(test) // Invoke the test function
    } lastly {
      system.terminate()
    }
  }

  //todo seems like a hack, should be improved.
  def getAllFilesForExperiment(exp: String): Unit = {
    implicit val executionContext = system.dispatcher
    println(s"asking for files for experiment $exp...")

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val response: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${exp}/files"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._
    response.map { r ⇒
      assert(r.status == StatusCodes.OK)

      try {
        filesInfo = Some(r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8") //todo improve, the HttpEntity.Strict will not work with long results, il will instead be a Default.
          .parseJson.convertTo[AllFilesInformation])
      } catch {
        case exc: Exception ⇒
          println(exc.toString)
      }
    }
  }
}
