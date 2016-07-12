package com.actelion.research.arcite.core.api

import com.github.agourlay.cornichon.CornichonFeature

/**
  * Created by deffabe1 on 4/28/16.
  *
  * Test at the API level
  *
  */
class TestApi extends CornichonFeature {

  override lazy val baseUrl = s"http://CHALUW-DEV01:8084"

  lazy val feature = Feature("Experiments API") {

    Scenario("list of all experiments available ") {

      When I get("/experiments")

      Then assert status.is(200)

      And assert body.path("experiments[0].name").is("AMS0023")
    }

    Scenario("search for some experiments ") {

      When I post("/experiments", """{"search" : "bleomycyn"}""")

      Then assert status.is(200)

      And assert body.path("experiments.b33b369d48caa3d791cc7136e65c679db7c7419fc0fcda16542fcb4c96782ad5.name")
        .is("AMS0014_2")
    }

    Scenario("insert new experiment") {

      When I post("/experiment",
        """
          {
            "experiment" : {
              "name": "hello mars", "description": "Mars is great", "state": "new",
              "design": {
                "description": "design for Mars", "sampleConditions":[]},
                "owner": {
                  "organization" : "com.actelion.research", "person" : "B. Deffarges"
                },
                "properties": {}}}
        """)

      Then assert status.is(200)

    }

  }
}
