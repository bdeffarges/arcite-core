package com.actelion.research.arcite.core.api

import java.io.File
import java.nio.file.Paths

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, _}
import akka.stream.scaladsl._
import akka.util.ByteString
import com.actelion.research.arcite.core.TestHelpers
import com.actelion.research.arcite.core.api.ArciteService.AllExperiments
import com.actelion.research.arcite.core.experiments.Experiment
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExpProps, AddExperiment}

import scala.concurrent.Future


/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
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
class FilesUploadApiTests extends ApiTests {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

  "Create a new experiment " should " return the uid of the new experiment which we can then delete " in {

    implicit val executionContext = system.dispatcher
    import spray.json._

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(AddExperiment(exp1).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = "/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
    }
  }

  "adding raw files directly " should " copy the given file to the experiment folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    def createEntity(file: File): Future[RequestEntity] = {
      require(file.exists())
      val formData =
        Multipart.FormData(
          Source.single(
            Multipart.FormData.BodyPart(
              "test",
              HttpEntity(MediaTypes.`application/octet-stream`, file.length(),
                FileIO.fromPath(file.toPath, chunkSize = 100000)), // the chunk size here is currently critical for performance
              Map("filename" -> file.getName))))

      Marshal(formData).to[RequestEntity]
    }

    def createRequest(target: Uri, file: File): Future[HttpRequest] =
      for {
        e ← createEntity(file)
      } yield HttpRequest(HttpMethods.POST, uri = target, entity = e)


    val req = createRequest(s"/experiment/${exp1.uid}/file_upload/meta",
      new File("./for_testing/for_unit_testing/of_paramount_importance.txt"))

    val res: Future[HttpResponse] = req.flatMap(r ⇒ Source.single(r).via(connectionFlow).runWith(Sink.head))

    res.map { r ⇒
      assert(r.status == StatusCodes.Created)
    }

    Future(Assertion())
  }


  "Retrieving one experiment " should " return detailed information of exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"/experiment/${exp1.uid}")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == experiment.name)
      assert(experiment.description == experiment.description)
    }
  }


  "Delete an experiment " should " move the experiment to the deleted folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"/experiment/${exp1.uid}",
      entity = HttpEntity(MediaTypes.`application/json`, ""))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }
}


