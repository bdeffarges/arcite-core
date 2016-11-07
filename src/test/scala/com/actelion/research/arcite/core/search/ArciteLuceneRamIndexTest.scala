package com.actelion.research.arcite.core.search

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}

import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.{IndexExperiment, IndexingCompletedForExp, Search, SearchResult}

import com.actelion.research.arcite.core.TestHelpers._

import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration.FiniteDuration


/**
  * Created by deffabe1 on 3/18/16.
  */
class ArciteLuceneRamIndexTest extends TestKit(ActorSystem("LuceneRamIndexSystem"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll() {
    system.terminate()
  }

  "The indexing actor" must {
    " accept experiments, index them and enable searching " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(classOf[ArciteLuceneRamIndex]))

      import com.actelion.research.arcite.core.TestHelpers._
      import scala.concurrent.duration._
      //
      //      within(4 seconds) {
      //        actorRef ! IndexExperiments(experiment1 :: experiment2 :: List())
      //        expectNoMsg
      //      }

      actorRef ! IndexExperiment(experiment1)
      actorRef ! IndexExperiment(experiment2)

      endProbe.expectMsg(FiniteDuration(3, TimeUnit.SECONDS), IndexingCompletedForExp(experiment1))

      endProbe.expectMsg(FiniteDuration(3, TimeUnit.SECONDS), IndexingCompletedForExp(experiment2))

      actorRef ! Search("neptune")
      endProbe.expectMsg(FiniteDuration(3, TimeUnit.SECONDS), SearchResult(1))

      //      actorRef ! Search("flying")
      //      endProbe.expectMsg(FiniteDuration(3, TimeUnit.SECONDS), SearchResult(4))

    }
  }
}