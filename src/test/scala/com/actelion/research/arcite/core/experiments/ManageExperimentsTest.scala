package com.actelion.research.arcite.core.experiments

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.actelion.research.arcite.core.TestHelpers._
import com.actelion.research.arcite.core.api.ArciteService.AddedExperiment
import com.actelion.research.arcite.core.experiments.LocalExperiments.{ExperimentExisted, LoadExperiment, SaveExperiment, SaveExperimentSuccessful}
import com.actelion.research.arcite.core.experiments.ManageExperiments.{AddExperiment, GetExperiments, SnapshotTaken}
import com.actelion.research.arcite.core.utils.Env
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration.FiniteDuration


/**
  * Created by deffabe1 on 3/9/16.
  */
class ManageExperimentsTest extends TestKit(ActorSystem("Experiments"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  val pathToArciteHome = Env.getConf("arcite.home") + File.separator

  override def afterAll() {
    system.terminate()
  }

  "The manage experiment actor " must {
    " add a new Experiment to its list and create the corresponding folder if it does not yet exist " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(new ManageExperiments))

      actorRef ! SaveExperiment(experiment1)

      endProbe.expectMsgAnyOf(FiniteDuration(1, TimeUnit.SECONDS), SaveExperimentSuccessful, ExperimentExisted)
    }
  }

  "the manage experiment actor " should {
    "be able to save and read an experiment. " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(new ManageExperiments))

      actorRef ! SaveExperiment(experiment2)

      endProbe.expectMsgAnyOf(FiniteDuration(1, TimeUnit.SECONDS), SaveExperimentSuccessful, ExperimentExisted)

      actorRef ! LoadExperiment(s"${ExperimentFolderVisitor(experiment2).metaFolderPath}/experiment")

      endProbe.expectMsgAnyOf(FiniteDuration(10, TimeUnit.SECONDS), experiment2)
    }
  }

  "The snapshot actor" must {
    " save the state to the file system " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(new ManageExperiments))

      actorRef ! AddExperiment(experiment1)

      endProbe.expectMsg(FiniteDuration(2, TimeUnit.SECONDS), AddedExperiment)
      endProbe.expectMsg(FiniteDuration(2, TimeUnit.SECONDS), SnapshotTaken)

      actorRef ! GetExperiments

      endProbe.expectMsgClass(FiniteDuration(2, TimeUnit.SECONDS), classOf[Set[Experiment]])

    }

  }
}
