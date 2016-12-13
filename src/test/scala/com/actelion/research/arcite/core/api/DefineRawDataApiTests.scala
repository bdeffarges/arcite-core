package com.actelion.research.arcite.core.api

import java.io.File
import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, RequestEntity, _}
import akka.stream.scaladsl._
import akka.util.ByteString
import com.actelion.research.arcite.core.TestHelpers
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExperiment, CloneExperimentNewProps}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentUID}
import com.actelion.research.arcite.core.rawdata.DefineRawData.SourceRawDataSet
import com.actelion.research.arcite.core.utils.FileInformationWithSubFolder
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
  * Created by Bernard Deffarges on 2016/11/10.
  *
  */
class DefineRawDataApiTests extends ApiTests {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)
  var clonedExp: Option[String] = None


  "Create a new experiment " should " return the uid of the new experiment " in {

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


  "defining raw data from data source " should " copy the given files to the raw data folder of the experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(SourceRawDataSet(exp1.uid, "microarray1",
      filesAndFolders = List("AMS0090/160309_br")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"/raw_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))


    val res: Future[HttpResponse] = Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    res.map { r ⇒
      assert(r.status == StatusCodes.Created || r.status == StatusCodes.OK)
    }
  }


  "Clone an experiment " should " return the uid of the new experiment " in {

    implicit val executionContext = system.dispatcher


    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(CloneExperimentNewProps(s"cloned-${UUID.randomUUID().toString}",
      "com.actelion.research.test.cloned", TestHelpers.owner3).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"/experiment/${exp1.uid}/clone",
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

}


