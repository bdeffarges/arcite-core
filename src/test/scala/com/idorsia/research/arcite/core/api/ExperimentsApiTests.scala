package com.idorsia.research.arcite.core.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.idorsia.research.arcite.core.TestHelpers
import com.idorsia.research.arcite.core.experiments.ManageExperiments._
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentUID}
import com.idorsia.research.arcite.core.secure.WithToken
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
class ExperimentsApiTests extends ApiTests with ExpJsonProto {

  private val wrongExp = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment4)
  private var exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

  "Default get " should "return rest interface specification " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.NotFound)
    }
  }

  "All Experiments without argument " should "return many experiments... " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix/experiments")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiments = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[AllExperiments].experiments

      assert(experiments.size > 5)

      assert(experiments.exists(exp ⇒ exp.name.contains("Mars")))

    }
  }

  "searching for 'Mars' and paging through experiments " should "return at least a couple of experiments... " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix/experiments?page=0&max=200")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiments = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[AllExperiments].experiments

      assert(experiments.size > 5)

      assert(experiments.exists(exp ⇒ exp.name.contains("Mars")))

    }
  }


  "Create a new experiment " should " return the uid of the new experiment which we can then delete " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(AddExperiment(exp1).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      exp1 = exp1.copy(uid = Some(r.entity.asInstanceOf[HttpEntity.Strict]
        .data.decodeString("UTF-8").parseJson.convertTo[ExperimentUID].uid))
      assert(r.status == StatusCodes.Created)
    }
  }


  "Create a new experiment with a wrong organization " should " not work and raise an exception " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(AddExperiment(wrongExp).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Conflict)
    }
  }


  "adding properties" should " change the list of properties of the experiments " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(AddExpProps(Map(("hello", "mars"), ("bye", "jupiter"),
      ("eeew", "neptune"), ("asdfsda", "333ddd"), ("adad", "llkid"))).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/properties",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
    }
  }

  "removing  properties" should " reduce the list of properties of the experiments " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(RmExpProps(List("eeew", "asdfsda", "adad")).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/properties",
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
      Source.single(HttpRequest(uri =s"$urlPrefix/experiment/${exp1.uid.get}")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(experiment.description == exp1.description)
      assert(experiment.properties("hello") == "mars")
      assert(experiment.properties("bye") == "jupiter")
    }
  }


  "uploading new description " should " replace description of given experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(ChangeDescription("New description BLABLA ").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.PUT,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/description",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }


  "Retrieving an experiment after the description has changed " should
    " return detailed information with new description " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix/experiment/${exp1.uid.get}")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(experiment.description.contains("BLABLA"))
    }
  }


  "Hide experiment " should " change the hidden tag of the experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(WithToken().toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/hide",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      assert(r.entity.asInstanceOf[HttpEntity.Strict].
        data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage].message.contains("hidding"))
    }
  }


  "Hide experiment after it has already been done " should " complain " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(WithToken("blllllasdfkjlwkerj").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/hide",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.BadRequest)
      assert(r.entity.asInstanceOf[HttpEntity.Strict].
        data.decodeString("UTF-8").parseJson.convertTo[ErrorMessage].error.contains("in right state"))
    }
  }


  "Checking hidden properties of experiment " should " return true " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix/experiment/${exp1.uid.get}")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(experiment.hidden)
    }
  }


  "UnHide experiment " should " change the hidden tag of the experiment " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(WithToken("blllllasdfkjlwkerj").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/unhide",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
      assert(r.entity.asInstanceOf[HttpEntity.Strict].
        data.decodeString("UTF-8").parseJson.convertTo[SuccessMessage].message.contains("unhidding"))
    }
  }


  "Unhide experiment after it has already been done " should " complain " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)


    val jsonRequest = ByteString(WithToken("blllllasdfkjlwkerj").toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}/unhide",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.BadRequest)
      assert(r.entity.asInstanceOf[HttpEntity.Strict].
        data.decodeString("UTF-8").parseJson.convertTo[ErrorMessage].error.contains("in right state"))
    }
  }


  "Checking hidden properties of experiment after unhidding " should " return false " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri =s"$urlPrefix/experiment/${exp1.uid.get}")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == exp1.name)
      assert(!experiment.hidden)
    }
  }



  "Delete an experiment " should " move the experiment to the deleted folder " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri =s"$urlPrefix/experiment/${exp1.uid.get}",
      entity = HttpEntity(MediaTypes.`application/json`, ""))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }

}


