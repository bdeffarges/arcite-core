package com.actelion.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.InfoLogs
import com.actelion.research.arcite.core.experiments.ManageExperiments.ManyTransforms
import com.actelion.research.arcite.core.fileservice.FileServiceActor.SourceFoldersAsString
import com.actelion.research.arcite.core.transforms.TransformCompletionFeedback

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
  * Created by Bernard Deffarges on 2016/12/07.
  *
  */
class GetInfoTests extends ApiTests {

  "get recent logs " should " return at least a couple of logs..." in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/recent_logs")).via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val recentLogs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[InfoLogs]

      assert(recentLogs.logs.size > 3)

    }
  }

  "get recent transforms " should " return a least a couple of transform logs..." in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/all_transforms")).via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transformInfos = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformCompletionFeedback]]

      assert(transformInfos.size > 1)
    }
  }

  "get data sources " should " return the different data sources (mounted drives)..." in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/data_sources")).via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val dataSources = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SourceFoldersAsString]

      assert(dataSources.sourceFolders.size > 1)
    }
  }

  "get data sources details " should " return the list of folders and files for the given source..." in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/data_sources")).via(connectionFlow).runWith(Sink.head)

    import spray.json._

    responseFuture.map { r ⇒
        fail()
    }
  }
}
