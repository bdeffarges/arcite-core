package com.idorsia.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentUID}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{AddExperiment, BunchOfSelectables}
import com.idorsia.research.arcite.core.transforms.RunTransform.{RunTransformOnObject, RunTransformOnTransform}
import com.idorsia.research.arcite.core.transforms.TransformDefinitionIdentity
import com.idorsia.research.arcite.core.transforms.cluster.Frontend.OkTransfReceived
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.WorkExecDuplicateText

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
class TransformApiTests extends ApiTests {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)
  var exp1Uid:Option[String] = None // because the uid is created on the server.

  private var transfDef1: Option[TransformDefinitionIdentity] = None
  private var transfDef2: Option[TransformDefinitionIdentity] = None
  private var transfDefDuplicate: Option[TransformDefinitionIdentity] = None

  private var lowerCaseJobID: Option[String] = None
  private var upperCaseJobID: Option[String] = None
  private var dupliJobID: Option[String] = None


  "Get all transform definitions " should "return all possible transforms " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform_definitions")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transfDefs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformDefinitionIdentity]]

      assert(transfDefs.size > 1)
    }
  }

  "Get upper case transform definitions " should " return one transform definition " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform_definitions?search=uppercase")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transfDefs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformDefinitionIdentity]]

      assert(transfDefs.size == 1)

      transfDef1 = Some(transfDefs.toSeq.head)

      assert(transfDef1.get.fullName.shortName == "to-uppercase")
    }
  }

  "Get lower case transform definitions " should " return one transform definition " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform_definitions?search=lowercase")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transfDefs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformDefinitionIdentity]]

      assert(transfDefs.size == 1)

      transfDef2 = Some(transfDefs.toSeq.head)

      assert(transfDef2.get.fullName.shortName == "to-lowercase")
    }
  }

  "Get duplicate transform definition " should " return one transform definition " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/transform_definitions?search=duplicate")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val transfDefs = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Set[TransformDefinitionIdentity]]

      assert(transfDefs.size == 1)

      transfDefDuplicate = Some(transfDefs.toSeq.head)

      assert(transfDefDuplicate.get.fullName == WorkExecDuplicateText.fullName)
    }
  }

  "Create a new experiment " should " return the uid of the new experiment which we can then delete " in {

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

      exp1Uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid)

      assert(exp1Uid.isDefined)
    }
  }

  "Retrieving one experiment " should " return detailed information of exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${exp1Uid.get}")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(experiment.description == exp1.description)
    }
  }

  "start upper case transform on experiment " should " return the transform job uid " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    import spray.json._
    val transf1 = RunTransformOnObject(exp1Uid.get, transfDef1.get.fullName.asUID,
      Map("ToUpperCase" -> "transform me to upper case"))

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/run_transform",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8").parseJson.convertTo[OkTransfReceived]

      upperCaseJobID = Some(result.transfUID)
      assert(result.transfUID.length > 5)
    }
  }

  "start lower case transform on experiment " should " return the transform job uid " in {

    implicit val executionContext = system.dispatcher


    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    import spray.json._
    val transf1 = RunTransformOnObject(exp1Uid.get, transfDef2.get.fullName.asUID,
      Map("ToLowerCase" -> "transform me to lower case"))

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/run_transform",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8").parseJson.convertTo[OkTransfReceived]

      lowerCaseJobID = Some(result.transfUID)

      assert(result.transfUID.length > 5)
    }
  }

  "start duplicate transform on experiment " should " return the transform job uid " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    import spray.json._
    val transf1 = RunTransformOnTransform(exp1Uid.get, transfDefDuplicate.get.fullName.asUID, upperCaseJobID.get)
    println(transf1.toString)

    val jsonRequest = ByteString(transf1.toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"$urlPrefix/run_transform/on_transform",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))


    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      println(r.toString())
      assert(r.status == StatusCodes.OK)
      val result = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8").parseJson.convertTo[OkTransfReceived]

      dupliJobID = Some(result.transfUID)

      println(s"duplicJob=${dupliJobID.get}")

      assert(result.transfUID.length > 5)
    }
  }

  "Delete an experiment " should " NOT move the experiment to the deleted folder because it's immutable now!" in {

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
      assert(r.status == StatusCodes.Locked) // the experiment cannot be deleted anymore
    }
  }

  " uppercase transform " should " eventually complete and produce an upper cased text " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._

    transStatus += (upperCaseJobID.get -> false)

    eventually(timeout(10 minutes), interval(30 seconds)) {
      println("checking whether upper case job is completed...")

      checkTransformStatus(upperCaseJobID.get)

      transStatus(upperCaseJobID.get) should be(true)
    }
  }

  " lowercase transform " should " eventually complete and produce an lower cased text " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._

    transStatus += (lowerCaseJobID.get -> false)

    eventually(timeout(10 minutes), interval(30 seconds)) {
      println("checking whether lower case job  is completed...")

      checkTransformStatus(lowerCaseJobID.get)

      transStatus(lowerCaseJobID.get) should be(true)
    }
  }

  " duplicate transform " should " eventually complete and produce duplicated text and selectable " in {
    implicit val executionContext = system.dispatcher
    import scala.concurrent.duration._

    transStatus += (dupliJobID.get -> false)

    eventually(timeout(10 minutes), interval(30 seconds)) {
      println("checking whether duplicate is completed...")

      checkTransformStatus(dupliJobID.get)

      transStatus(dupliJobID.get) should be(true)
    }
  }

  "Retrieving selectable for one experiment/transform " should " return all selectables " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"$urlPrefix/experiment/${exp1Uid.get}/transform/${dupliJobID.get}/selectable"))
        .via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val selectables = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[BunchOfSelectables]

      println(selectables)

      assert(selectables.selectables.nonEmpty)
    }
  }
}


