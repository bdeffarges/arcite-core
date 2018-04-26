package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.ActorSystem
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


/**
  *
  * Created by deffabe1 on 7/22/16.
  */
object ManageTransformCluster {
  val logger = LoggerFactory.getLogger(ManageTransformCluster.getClass)

  val workTimeout = 24.hours // default max timeOut

  val config = ConfigFactory.load()

  val arcClusterSyst: String = config.getString("arcite.cluster.name")

  def startBackend(): Unit = {
    val system = ActorSystem(arcClusterSyst, configWithRole("back-end"))
    logger.info(s"starting actor system (cluster backend): ${system.toString}")

    logger.info("starting akka management...")
    AkkaManagement(system).start()

    logger.info("starting cluster bootstrap... ")
    ClusterBootstrap(system).start()

    MasterSingleton.startSingleton(system)
  }

}
