package com.actelion.research.arcite.core.api

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.actelion.research.arcite.core.TestHelpers
import com.actelion.research.arcite.core.api.ArciteService.{AddedExperiment, AllExperiments, ExperimentFound}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentSummary}
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExpProps, AddExperiment}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{AsyncFlatSpec, Matchers}

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
class ExperimentsApiTests extends ApiTests {

  val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

  "Default get " should "return rest interface specification " in {

    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/")).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)
      assert(r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8") == refApi)
    }
  }

  "All Experiments without argument " should "return many experiments... " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/experiments")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiments = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[AllExperiments].experiments

      assert(experiments.size > 10)

      assert(experiments.exists(exp ⇒ exp.name.contains("AMS")))

    }
  }

  "Paging through experiments " should "return exact number of experiments... " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = "/experiments?page=0&max=50")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiments = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[AllExperiments].experiments

      assert(experiments.size > 30)

      assert(experiments.exists(exp ⇒ exp.name.contains("AMS")))

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
      uri = "/experiment",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
    }
  }


  "adding properties" should " change the list of properties of the experiments " in {

    implicit val executionContext = system.dispatcher
    import spray.json._

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val jsonRequest = ByteString(AddExpProps(Map(("hello", "mars"), ("bye", "jupiter"))).toJson.prettyPrint)

    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = s"/experiment/${exp1.uid}/properties",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.Created)
    }
  }


  "Retrieving one experiment " should " return detailed information of exp " in {
    implicit val executionContext = system.dispatcher

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val responseFuture: Future[HttpResponse] =
      Source.single(HttpRequest(uri = s"/experiment/${exp1.uid}")).via(connectionFlow).runWith(Sink.head)

    import spray.json._
    responseFuture.map { r ⇒
      assert(r.status == StatusCodes.OK)

      val experiment = r.entity.asInstanceOf[HttpEntity.Strict].data.decodeString("UTF-8")
        .parseJson.convertTo[Experiment]

      assert(experiment.name == experiment.name)
      assert(experiment.description == experiment.description)
      assert(experiment.properties("hello") == "mars")
      assert(experiment.properties("bye") == "jupiter")
    }
  }


  "Delete an experiment " should " move the experiment to the deleted folder " in {

    implicit val executionContext = system.dispatcher
    import spray.json._

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    val postRequest = HttpRequest(
      HttpMethods.DELETE,
      uri = s"/experiment/${exp1.uid}",
      entity = HttpEntity(MediaTypes.`application/json`, ""))

    val responseFuture: Future[HttpResponse] =
      Source.single(postRequest).via(connectionFlow).runWith(Sink.head)

    responseFuture.map { r ⇒
      logger.info(r.toString())
      assert(r.status == StatusCodes.OK)
    }
  }
}


