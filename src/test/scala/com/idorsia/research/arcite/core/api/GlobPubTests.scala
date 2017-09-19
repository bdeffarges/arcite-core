package com.idorsia.research.arcite.core.api

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.publish.GlobalPublishActor.{GlobalPublishedItem, GlobalPublishedItemLight, PublishGlobalItem}

import scala.concurrent.Future
import spray.json._

/**
  * arcite-core
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2017/09/18.
  *
  */
class GlobPubTests extends ApiTests {

  val random = UUID.randomUUID().toString
  val description1 = s"test data $random"
  val description2 = s"hello world $random"
  val description3 = s"hello Neptune $random"

  var selected1: Option[GlobalPublishedItem] = None
  var selected2: Option[GlobalPublishedItem] = None
  var selected3: Option[GlobalPublishedItem] = None

  var currentResultLength = 0

  "Publish some test data globally " should " create an entry in the publish global place " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishGlobalItem(GlobalPublishedItem(
      GlobalPublishedItemLight(description1, TestHelpers.owner1,
        Seq("/arcite/test/data")))).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/publish",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }

  "Publish some test data globally (world)" should " create an entry in the publish global place " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishGlobalItem(GlobalPublishedItem(
      GlobalPublishedItemLight(description2, TestHelpers.owner1,
        Seq("/arcite/hello/world")))).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/publish",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }


  "Publish some data globally (neptune)" should " create an entry in the publish global place " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishGlobalItem(GlobalPublishedItem(
      GlobalPublishedItemLight(description3, TestHelpers.owner1,
        Seq("/arcite/hello/neptune")))).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/publish",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }


  "Retrieving all published items " should " return list of all published items " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.nonEmpty)

      selected1 = publishedItems.find(pi ⇒ pi.globalPubInf.description == description1)

      assert(selected1.isDefined)

    }
  }


  "Searching for data " should " return at least one published item " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish?search=data&maxHits=100")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.nonEmpty)

      println(publishedItems)

      selected1 = publishedItems.find(pi ⇒ pi.globalPubInf.description == description1)

      assert(selected1.isDefined)
    }
  }

  "Searching for neptune " should " return at least one published item " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish?search=Neptune&maxHits=100")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.nonEmpty)

      selected3 = publishedItems.find(pi ⇒ pi.globalPubInf.description == description3)

      assert(selected3.isDefined)
    }
  }


  "Searching for world " should " return at least one published item " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish?search=world&maxHits=20")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.nonEmpty)

      selected2 = publishedItems.find(pi ⇒ pi.globalPubInf.description == description2)

      assert(selected2.isDefined)
    }
  }

  "Retrieving all published items after new additions " should " return list of all published items " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      currentResultLength = publishedItems.size

      assert(publishedItems.size > 2)
    }
  }

  "Deleting selection1 entry " should " mark the item as being deleted. " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val deleteRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/publish/${selected1.get.uid}")

    val responseFuture: Future[HttpResponse] =
      Source.single(deleteRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }

  "Retrieving all published items after 1st delete " should " return list of all published items -1" in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.size == currentResultLength - 1)
    }
  }

  "Deleting selection2 entry " should " mark the item as being deleted. " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val deleteRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/publish/${selected2.get.uid}")

    val responseFuture: Future[HttpResponse] =
      Source.single(deleteRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }

  "Retrieving all published items after 2snd delete " should " return list of all published items -1" in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.size == currentResultLength - 2)
    }
  }

  "Deleting selection3 entry " should " mark the item as being deleted. " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val deleteRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/publish/${selected3.get.uid}")

    val responseFuture: Future[HttpResponse] =
      Source.single(deleteRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
      val sucMsg = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage])
      assert(sucMsg.nonEmpty)
    }
  }

  "Retrieving all published items after 3rd delete " should " return list of all published items -1" in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(publishedItems.size == currentResultLength - 3)
    }
  }
  "Searching for neptune after delete " should " return empty results" in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish?search=Neptune&maxHits=1")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(!publishedItems.exists(pi ⇒ pi.globalPubInf.description == description3))

    }
  }


  "Searching for world after delete " should " return empty results" in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/publish?search=world&maxHits=1")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val publishedItems = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Seq[GlobalPublishedItem]]

      assert(!publishedItems.exists(pi ⇒ pi.globalPubInf.description == description2))

    }
  }
}
