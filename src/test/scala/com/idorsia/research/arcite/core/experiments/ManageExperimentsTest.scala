package com.idorsia.research.arcite.core.experiments

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.idorsia.research.arcite.core.TestHelpers._
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging
import com.idorsia.research.arcite.core.experiments.LocalExperiments.{LoadExperiment, SaveExperimentSuccessful}
import com.idorsia.research.arcite.core.experiments.ManageExperiments.{AddExperiment, AddedExperiment}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration.FiniteDuration


/**
  * Created by deffabe1 on 3/9/16.
  */
class ManageExperimentsTest extends TestKit(ActorSystem("Experiments"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  val config = ConfigFactory.load()

  val pathToArciteHome = config.getString("arcite.home") + File.separator

  val eventInfoLoggingAct = system.actorOf(Props(classOf[EventInfoLogging]), "event_logging_info")


  override def afterAll() {
    system.terminate()
  }

  "The manage experiment actor " must {
    " add a new Experiment to its list and create the corresponding folder if it does not yet exist " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(classOf[ManageExperiments], eventInfoLoggingAct))

      actorRef ! AddExperiment(experiment1)

      endProbe.expectMsgAnyOf(FiniteDuration(1, TimeUnit.SECONDS), SaveExperimentSuccessful)
    }
  }

  "the manage experiment actor " should {
    "be able to save and read an experiment. " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(classOf[ManageExperiments], eventInfoLoggingAct))

      actorRef ! AddExperiment(experiment2)

      endProbe.expectMsgAnyOf(FiniteDuration(1, TimeUnit.SECONDS), SaveExperimentSuccessful)

      actorRef ! LoadExperiment(s"${ExperimentFolderVisitor(experiment2).metaFolderPath}/experiment")

      endProbe.expectMsgAnyOf(FiniteDuration(10, TimeUnit.SECONDS), Some(experiment2))
    }
  }

  "The snapshot actor" must {
    " save the state to the file system " in {

      val endProbe = TestProbe()

      val actorRef = system.actorOf(Props(classOf[ManageExperiments], eventInfoLoggingAct))

      actorRef ! AddExperiment(experiment1)

      endProbe.expectMsg(FiniteDuration(2, TimeUnit.SECONDS), AddedExperiment)

//      actorRef ! GetAllExperimentsWithRequester(endProbe)

      endProbe.expectMsgClass(FiniteDuration(2, TimeUnit.SECONDS), classOf[Set[Experiment]])

    }

  }
}
