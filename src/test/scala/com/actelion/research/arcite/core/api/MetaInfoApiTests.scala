package com.actelion.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteService.AllExperiments
import com.actelion.research.arcite.core.meta.DesignCategories.{AllCategories, SimpleCondition}
import com.actelion.research.arcite.core.utils.FileInformationWithSubFolder
import org.scalatest.Failed

import scala.concurrent.Future
import spray.json._

import scala.util.Success

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
  * Created by Bernard Deffarges on 2017/02/03.
  *
  */
class MetaInfoApiTests extends ApiTests {

  "asking for categories " should " return the already used categories and their possible values " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"${core.urlPrefix}/meta_info/categories")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

//      import scala.concurrent.duration._
//      val f = r.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8"))
//
//      f onComplete {
//        case Success(stg) ⇒
//          val cats = stg.parseJson.convertTo[AllCategories]
//          assert(cats.categories.size > 100000000)
//          assert(cats.categories.contains("Cell_adLine"))
//          assert(cats.categories.contains("wash"))
//        //
//        case _ ⇒ Failed
//      }
//
//      assert(true)
    }
  }
}
