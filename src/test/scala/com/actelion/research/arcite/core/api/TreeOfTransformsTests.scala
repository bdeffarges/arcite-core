package com.actelion.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.actelion.research.arcite.core.TestHelpers
import com.actelion.research.arcite.core.transftree.{DefaultTofT, ProceedWithTreeOfTransfOnRaw, TreeOfTransformInfo}
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
  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

  var transfDef1: Option[TreeOfTransformInfo] = None

  " returning all tree of transforms " should " return all TOfT names and descriptions " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/tree_of_transforms")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val treeOfTransformInfos = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TreeOfTransformInfo]]

      transfDef1 = treeOfTransformInfos.find(_.name == DefaultTofT.testTofT1.name.name)
      assert(treeOfTransformInfos.nonEmpty)
    }
  }

  " starting a simple tree of transform (upper, lower, ...) " should " produce the expected transform output " in {

    implicit val executionContext = system.dispatcher


    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val transf1 = ProceedWithTreeOfTransfOnRaw(exp1.uid, transfDef1.get.uid)

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = "/tree_of_transforms",
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
