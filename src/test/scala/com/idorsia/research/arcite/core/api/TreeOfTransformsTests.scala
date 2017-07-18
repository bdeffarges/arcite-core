package com.idorsia.research.arcite.core.api

import java.io.File

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.experiments.ExperimentUID
import com.idorsia.research.arcite.core.experiments.ManageExperiments.AddExperiment
import com.idorsia.research.arcite.core.transftree.{DefaultTofT, ProceedWithTreeOfTransf, TreeOfTransformInfo}
import spray.json._

import scala.concurrent.Future

/**
  *
  * arcite-core
  *
  * Copyright (C) 2016 Karanar Software (B. Deffarges)
  * 38 rue Wilson, 68170 Rixheim, France
  *
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
  * Created by Bernard Deffarges on 2016/12/14.
  *
  */
class TreeOfTransformsTests extends ApiTests {

  private var exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)
  private var transfDef1: Option[TreeOfTransformInfo] = None
  private var transfDef2: Option[TreeOfTransformInfo] = None

  "Create a new experiment " should " that can then be used to test the tree of transform " in {

    implicit val executionContext = system.dispatcher
    import spray.json._

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(AddExperiment(exp1).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      exp1 = exp1.copy(uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid))
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

    val req = createRequest(s"$urlPrefix/experiment/${exp1.uid}/file_upload/raw",
      new File("./for_testing/for_unit_testing/of_paramount_importance.txt"))

    val res: Future[HttpResponse] = Source.single(req).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created)
    }
  }


  " returning all tree of transforms " should " return all TOfT names and descriptions " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/tree_of_transforms")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val treeOfTransformInfos = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TreeOfTransformInfo]]

      transfDef1 = treeOfTransformInfos.find(_.name == DefaultTofT.testTofT1.name.name)
      transfDef2 = treeOfTransformInfos.find(_.name == DefaultTofT.testTofT2.name.name)
      assert(treeOfTransformInfos.nonEmpty)
    }
  }


  " starting the most simple tree of transform (upper and lower case) " should " return the tree Of transform UID " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val transf1 = ProceedWithTreeOfTransf(exp1.uid.get, transfDef2.get.uid)

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/tree_of_transforms",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)

      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
      assert(result.nonEmpty)
    }
  }

  " starting a simple tree of transform (upper, lower, ...) " should " return the tree Of transform UID " in {

    implicit val executionContext = system.dispatcher


    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val transf1 = ProceedWithTreeOfTransf(exp1.uid.get, transfDef1.get.uid)

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/tree_of_transforms",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)

      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
      assert(result.nonEmpty)
    }
  }
}
