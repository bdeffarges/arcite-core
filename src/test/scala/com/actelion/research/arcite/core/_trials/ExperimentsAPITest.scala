package com.actelion.research.arcite.core._trials

import akka.http.scaladsl.model.StatusCodes._
import com.actelion.research.arcite.core.experiments.ExperimentalDesign
import com.actelion.research.arcite.core.utils.Owner
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.regular.assertStep.{AssertStep, CustomMessageAssertion}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

abstract class ApiTestingWithCornichon extends CornichonFeature {
  lazy val conf = ConfigFactory.load(System.getProperty("api.test.config.resource"))
    .withFallback(ConfigFactory.load("api.test.conf"))

  override lazy val baseUrl = s"""http://${conf.getString("http.host")}:${conf.getString("http.port")}"""

  val organization = "com.actelion.research.arcite"
  val person = "cornichon tester"
  val description = "cornichon testing"
  val owner = Owner(organization, person)
  val design1 = ExperimentalDesign("testing design")

  val logger = LoggerFactory.getLogger(this.getClass)
}

case class experimentsSize(source: String) {

  private def szError(v: Int, ms: Int): Boolean ⇒ String = b ⇒ s"$v is not bigger than $ms"

  def isBigger(minSize: Int) =
    AssertStep(
      title = s"size of json array $source is bigger than $minSize",
      action = s ⇒ {
        val v = s.get(source).toInt
        CustomMessageAssertion(true, v > minSize, szError(v, minSize))
      }
    )
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
        "search" -> "replicate",
        "maxHits" -> "10")

      Then assert status.is(200)

      And assert body.path("experiments").asArray.hasSize(10)
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
  }
}


class AddDeleteExperiment extends ApiTestingWithCornichon {

  var expAsJson: String = _

  lazy val feature = Feature("Add and delete experiment") {

    Scenario("add an experiment") {

      When I post("/experiment").withBody(expAsJson)

      Then assert status.is(Created.intValue)
    }


    Scenario("add same experiment") {

      When I post("/experiment").withBody(expAsJson)

      Then assert status.is(Conflict.intValue)
    }


//    Scenario("delete experiment") {
//
//      When I delete(s"/experiment/${exp1.digest}")
//
//      Then assert status.is(OK.intValue)
//    }
//
//
//    Scenario("delete experiment again") {
//      When I delete(s"/experiment/${exp1.digest}")
//
//      Then assert status.is(NotFound.intValue)
//    }
  }


//  beforeFeature {
//    val name = s"test-experiment-${UUID.randomUUID()}"
//
//    val exp1 = Experiment(name = name, description = description,
//      owner = owner, design = design1)
//
//    val digest = exp1.uid
//
//    expAsJson = ExperimentSummary(exp1.name, exp1.description, exp1.owner, exp1.uid)
//
//    logger.info(s"add delete test with exp= $exp1, digest=${exp1.uid}")
//    logger.info(s"add delete test with json exp= $expAsJson")
//  }

}
