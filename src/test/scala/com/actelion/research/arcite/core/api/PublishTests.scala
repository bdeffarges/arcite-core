package com.actelion.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.ManageExperiments.ManyTransforms
import com.actelion.research.arcite.core.publish.PublishActor.{PublishInfo, PublishedInfo}
import com.actelion.research.arcite.core.transforms.{TransformCompletionFeedback, TransformCompletionStatus}
import spray.json._

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
  * Created by Bernard Deffarges on 2017/02/21.
  *
  */
class PublishTests extends ApiTests {

  private var transf1: Option[TransformCompletionFeedback] = None

  private var totPublished = 0

  private var publishedUID : Option[UniqueID] = None

  "All transforms without argument " should "return many transforms... " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"${core.urlPrefix}/all_transforms")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transforms = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformCompletionFeedback]]
        .filter(t ⇒ t.status == TransformCompletionStatus.SUCCESS)

      transf1 = Some(transforms.toList(java.util.concurrent.ThreadLocalRandom.current().nextInt(transforms.size)))

      assert(transforms.size > 1)
    }
  }


  "Call to publish an artifact to a experiment/transform " should " add the published info " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishInfo(transf1.get.source.experiment, transf1.get.transform,
      "test publishing", List("test Artifact. ")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/publish",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
    }
  }

  "ask for all published artifacts for an experiment " should "return all published items " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/published"))
        .via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transforms = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[List[PublishedInfo]]

      totPublished = transforms.size

      assert(transforms.nonEmpty)
    }
  }


  "Call to publish a second artifact to a experiment/transform " should " add the published info " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishInfo(transf1.get.source.experiment, transf1.get.transform,
      "test publishing 2", List("test Artifact 2 ")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/publish",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)

      publishedUID = Some(r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[UniqueID])

      assert(publishedUID.isDefined && publishedUID.get.uid.length > 5)
    }
  }


  "ask for all published for an experiment after adding a second one " should "return all published items " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/published"))
        .via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transforms = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[List[PublishedInfo]]

      assert(transforms.size == totPublished + 1)
    }
  }


  "Call to remove a published artifact from an experiment " should " hide the published artifact " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(PublishInfo(transf1.get.source.experiment, transf1.get.transform,
      "test publishing 2", List("test Artifact 2 ")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/published/${publishedUID.get.uid}",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }


  "ask for all published for an experiment once one has been deleted " should "return all published items " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"${core.urlPrefix}/experiment/${transf1.get.source.experiment}/published"))
        .via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transforms = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[List[PublishedInfo]]

      assert(transforms.size == totPublished)
    }
  }
}
