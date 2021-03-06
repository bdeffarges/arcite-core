package com.idorsia.research.arcite.core.api

import java.io.File
import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, RequestEntity, _}
import akka.stream.scaladsl._
import akka.util.ByteString
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentUID}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{AddExperiment, CloneExperimentNewProps}
import com.idorsia.research.arcite.core.utils.{FileInformation, RmFile}
import spray.json._

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
class FilesUploadApiTests extends ApiTests with ExpJsonProto {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

  var exp1Uid: Option[String] = None // because the uid is created on the server.
  var clonedExp: Option[String] = None


  "Create a new experiment " should " return the uid of the new experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonText = AddExperiment(exp1).toJson.prettyPrint
    val jsonRequest = ByteString(jsonText)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)

      exp1Uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid)

      assert(exp1Uid.isDefined)
    }
  }


  "adding meta files directly " should " copy the given file to the experiment folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    def createEntity(file: File): RequestEntity = {
      require(file.exists())
      val formData = Multipart.FormData.fromPath("fileupload",
        ContentTypes.`application/octet-stream`, file.toPath, 100000) // the chunk size here is currently critical for performance

      formData.toEntity()
    }

    def createRequest(target: Uri, file: File): HttpRequest = HttpRequest(HttpMethods.POST, uri = target, entity = createEntity(file))

    val req = createRequest(s"$urlPrefix/experiment/${exp1Uid.get}/file_upload/meta",

      new File("./for_testing/hello-world.txt"))

    val res: Future[HttpResponse] = Source.single(req).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created)
    }
  }


  "adding raw files directly " should " copy the given file to the experiment folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    def createEntity(file: File): RequestEntity = {
      require(file.exists())
      val formData = Multipart.FormData.fromPath("fileupload",
        ContentTypes.`application/octet-stream`, file.toPath, 100000) // the chunk size here is currently critical for performance

      formData.toEntity()
    }

    def createRequest(target: Uri, file: File): HttpRequest = HttpRequest(HttpMethods.POST, uri = target, entity = createEntity(file))

    val req = createRequest(s"$urlPrefix/experiment/${exp1Uid.get}/file_upload/raw",
      new File("./for_testing/for_unit_testing/raw_data_measurements.tsv"))

    val res: Future[HttpResponse] = Source.single(req).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created)
    }
  }


  "Clone an experiment " should " return the uid of the new experiment " in {

    implicit val executionContext = system.dispatcher


    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonArgs = CloneExperimentNewProps(s"cloned-${UUID.randomUUID().toString}",
      "experiment cloned", TestHelpers.owner3).toJson.toString()
    logger.info(jsonArgs)

    val jsonRequest = ByteString(jsonArgs)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/experiment/${exp1Uid.get}/clone",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)

      clonedExp = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid)

      assert(clonedExp.isDefined)
    }
  }


  "retrieve meta data from exp " should " return meta data of original exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${exp1Uid.get}/files/meta")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val foldInf: Set[FileInformation] = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[FileInformation]]

      assert(foldInf.toList.head.name == "of_paramount_importance.txt")
    }

  }


  "retrieve meta data from cloned exp " should " return the same meta data (as it is a symlink) as the original exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${clonedExp.get}/files/meta")).via(connectionFlow).runWith(Sink.head)


    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val foldInf: Set[FileInformation] = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[FileInformation]]

      assert(foldInf.toList.head.name == "of_paramount_importance.txt")
    }

  }


  "now we can delete the uploaded file, that " should " work. " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(RmFile("of_paramount_importance.txt").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/experiment/${exp1Uid.get}/file_upload/meta",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }


  "now we can delete the uploaded file from the cloned experiment, that " should " work. " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(RmFile("of_paramount_importance.txt").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/experiment/${clonedExp.get}/file_upload/meta",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }


  "Retrieving one experiment " should " return detailed information of exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${exp1Uid.get}")).via(connectionFlow).runWith(Sink.head)


    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(experiment.description == exp1.description)
    }
  }


  "Retrieving experiment " should " return detailed information of cloned exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    assert(clonedExp.isDefined)

    println(clonedExp)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${clonedExp.get}")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.description == "experiment cloned")
    }
  }


  "Delete an experiment " should " move the experiment to the deleted folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/experiment/${exp1Uid.get}",
      entity = HttpEntity(MediaTypes.`application/json`, ""))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }


  "Delete cloned experiment " should " move the experiment to the deleted folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/experiment/${clonedExp.get}",
      entity = HttpEntity(MediaTypes.`application/json`, ""))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }
}


