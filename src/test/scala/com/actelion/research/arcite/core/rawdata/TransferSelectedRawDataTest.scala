package com.actelion.research.arcite.core.rawdata

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.actelion.research.arcite.core.rawdata.TransferSelectedRawData.TransferFolder
import com.actelion.research.arcite.core.transforms.cluster.Frontend.Ok
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration.FiniteDuration


/**
  * Created by deffabe1 on 3/4/16.
  */
class TransferSelectedRawDataTest extends TestKit(ActorSystem("AgilentArraySystem"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll() {
    system.terminate()
  }

  "The Raw data transfer actor" must {
    " transfer raw data file in its raw data folder " in {

      val endProbe = TestProbe()

      val config = ConfigFactory.load()

      val folder = s"${config.getString("microarrays")}raw_data/AMS0089"
      val target = Paths.get(s"${config.getString("arcite.home")}AMS0089/raw_data")
      val actorRef = system.actorOf(Props(new TransferSelectedRawData(endProbe.ref, target)))

      actorRef ! TransferFolder(folder, """.*_(\d{10,15}+).*_(\d_\d)\.txt""".r, true)

      endProbe.expectMsg(FiniteDuration(10, TimeUnit.MINUTES), Ok)
    }
  }
}

