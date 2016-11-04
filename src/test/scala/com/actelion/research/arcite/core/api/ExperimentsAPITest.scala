package com.actelion.research.arcite.core.api

import java.util.UUID

import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentalDesign}
import com.actelion.research.arcite.core.utils.Owner
import com.github.agourlay.cornichon.CornichonFeature
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.model.StatusCodes._

abstract class ApiTestingWithCornichon extends CornichonFeature {
  lazy val conf = ConfigFactory.load(System.getProperty("api.test.config.resource"))
    .withFallback(ConfigFactory.load("api.test.conf"))

  override lazy val baseUrl = s"""http://${conf.getString("http.host")}:${conf.getString("http.port")}"""

  val organization = "com.actelion.research.arcite"
  val person = "cornichon tester"
  val description = "cornichon testing"
  val owner = Owner(organization, person)
  val design1 = ExperimentalDesign("testing design")

}

/**
  * Created by deffabe1 on 4/28/16.
  *
  * Return one or many experiments
  *
  */
class FindExperiments extends ApiTestingWithCornichon {

  def feature = Feature("Find all or one experiment(s)") {

    Scenario("All experiments available ") {

      When I get("/experiments")

      Then assert status.is(200)

    }

    Scenario("Find many experiment") {

      When I get("/experiments").withParams(
        "search" -> "AMS",
        "maxHits" -> "20"
      )

      Then assert status.is(200)

      And assert body.path("totalResults").is(1)

      And assert body.path("experiments").asArray.hasSize(20)

    }

    Scenario("Find one experiment") {

      When I get("/experiments").withParams(
        "search" -> "AMS0094",
        "maxHits" -> "1"
      )

      Then assert status.is(200)

      And assert body.path("totalResults").is(1)

      And assert body.path("experiments").asArray.hasSize(1)

      And assert body.path("experiments[0].name").is("AMS0094")
    }

    //    Scenario("retrieve one experiment") {
    //
    //    }
    //    Scenario("search for some experiments ") {
    //
    //      When I post("/experiments", """{"search" : "bleomycyn"}""")
    //
    //      Then assert status.is(200)
    //
    //      And assert body.path("experiments.b33b369d48caa3d791cc7136e65c679db7c7419fc0fcda16542fcb4c96782ad5.name")
    //        .is("AMS0014_2")
    //    }
    //
    //    Scenario("insert new experiment") {
    //
    //      When I post("/experiment",
    //        """
    //          {
    //            "experiment" : {
    //              "name": "hello mars", "description": "Mars is great", "state": "new",
    //              "design": {
    //                "description": "design for Mars", "sampleConditions":[]},
    //                "owner": {
    //                  "organization" : "com.actelion.research", "person" : "B. Deffarges"
    //                },
    //                "properties": {}}}
    //        """)
    //
    //      Then assert status.is(200)

    //    }

  }
}


class AddDeleteExperiment extends ApiTestingWithCornichon {

  def feature = Feature("Add and delete experiment") {
    val name = s"test-experiment-${UUID.randomUUID()}"
    import spray.json._
    val exp1 = Experiment(name = name, description = description,
      owner = owner, design = design1)


    val expAsJson = exp1.toJson.prettyPrint

    Scenario("add an experiment") {

      When I post("/experiment").withBody(expAsJson)

      Then assert status.is(Created.intValue)
    }

    Scenario("add same experiment") {

      When I post("/experiment").withBody(expAsJson)

      Then assert status.is(Conflict.intValue)
    }

    Scenario("delete experiment") {
      When I delete(s"/experiment/${exp1.digest}")

      Then assert status.is(OK.intValue)
    }

    Scenario("delete experiment again") {
      When I delete(s"/experiment/${exp1.digest}")

      Then assert status.is(NotFound.intValue)
    }
  }

}
