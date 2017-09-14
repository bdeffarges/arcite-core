package com.idorsia.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.experiments.ExperimentUID
import com.idorsia.research.arcite.core.experiments.ManageExperiments.AddExperiment
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.GetFilesFromSource
import com.idorsia.research.arcite.core.rawdata.DefineRawAndMetaData.{LinkMetaData, RemoveAllRaw, RemoveRawData, SetRawData}
import com.idorsia.research.arcite.core.utils.FilesInformation

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
  * Created by Bernard Deffarges on 2017/07/25.
  *
  */
class SourceFilesApiTests extends ApiTests {

  var exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

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

      exp1 = exp1.copy(uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid))

      assert(exp1.uid.isDefined)
    }
  }

  "get data sources details " should " return the list of folders and files for the given source..." in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(GetFilesFromSource("microarray", List("AMS0100")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/data_sources",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val dataSources = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[FilesInformation]
      assert(dataSources.files.nonEmpty)
    }
  }

  "post to copy data from source to raw " should " copy the data files to the raw folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(SetRawData(exp1.uid.get,
      Set("/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_4.txt")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/raw_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("transfer started"))
    }
  }

  "retrieve data from source to raw twice " should " copy the data files with new names to avoid collision " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(SetRawData(exp1.uid.get,
      Set("/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_4.txt")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/raw_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("transfer started"))
    }
  }


  "post a link data from source to meta " should " create symlinks to the files or folder in source " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(LinkMetaData(exp1.uid.get,
      Set("/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/meta_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK || r.status == StatusCodes.Created)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("almost linked"))
    }
  }


  " once transferred total raw files for experiment " should " eventually be 16 " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._
    getAllFilesForExperiment(exp1.uid.get)
    eventually(timeout(4 minutes), interval(10 seconds)) {
      getAllFilesForExperiment(exp1.uid.get)
//      println(filesInfo)
      val rawFileSize = filesInfo.fold(0)(_.rawFiles.size)
      println(s"number of copied files... $rawFileSize")
      assert(rawFileSize == 16)
    }
  }

  "check the right files have been copied and renamed " should
    " confirm that some files have been renamed after being copied " in {

    val rawFileNames = filesInfo.get.rawFiles.map(_.name)
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_2.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_3.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_4.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_1__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_2__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_3__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_4__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_1__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_2__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_3__1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_4__1.txt"))
  }

  "posting delete raw files " should " remove given file from raw folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(RemoveRawData(exp1.uid.get,
      Set("161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt",
        "161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt",
        "161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt",
        "161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/raw_data/rm",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("raw data removed"))
    }
  }

  " once some files have been deleted total raw files for experiment " should " eventually be only 8 anymore " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._
    getAllFilesForExperiment(exp1.uid.get)
    eventually(timeout(2 minutes), interval(10 seconds)) {
      getAllFilesForExperiment(exp1.uid.get)
      println(s"checking the number of copied files... ${filesInfo.fold(0)(_.rawFiles.size)}")
      assert(filesInfo.fold(0)(_.rawFiles.size) == 12)
    }
  }


  "now we ask to delete everything in the raw folder " should " empty the raw folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(RemoveAllRaw(exp1.uid.get).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"$urlPrefix/raw_data/rm_all",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("raw data removed"))
    }
  }


  "an rm * has been posted which " should " have emptied the raw folder " in {

    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._
    getAllFilesForExperiment(exp1.uid.get)
    eventually(timeout(2 minutes), interval(10 seconds)) {
      getAllFilesForExperiment(exp1.uid.get)
      println(s"checking the number of copied files... ${filesInfo.fold(0)(_.rawFiles.size)}")
      assert(filesInfo.fold(0)(_.rawFiles.size) == 0)
    }
  }

  "post a symlinking of files from source to raw " should " create symlinks for all given files " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(SetRawData(exp1.uid.get,
      Set("/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_1.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_2.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_3.txt",
        "/arcite/raw_data/microarrays/AMS0100/161125_br_257236312183_S01_GE2_1105_Oct12_2_4.txt"),
      symLink = true).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/raw_data/from_source",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val message = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[SuccessMessage].message
      assert(message.contains("transfer started"))
    }
  }

  " once linking completed, total raw files symlinks for experiment " should " eventually be 8 " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._
    getAllFilesForExperiment(exp1.uid.get)
    eventually(timeout(2 minutes), interval(10 seconds)) {
      getAllFilesForExperiment(exp1.uid.get)
      println(s"checking the number of copied files... ${filesInfo.fold(0)(_.rawFiles.size)}")
      assert(filesInfo.fold(0)(_.rawFiles.size) == 8)
    }
  }

  "check the right symlinks have been created " should
    " confirm the 8 files which have been linked. " in {

    val rawFileNames = filesInfo.get.rawFiles.map(_.name)
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_2.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_3.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_1_4.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_1.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_2.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_3.txt"))
    assert(rawFileNames.contains("161125_br_257236312183_S01_GE2_1105_Oct12_2_4.txt"))
  }

  //todo test case where the experiment is immutable should not delete
}
