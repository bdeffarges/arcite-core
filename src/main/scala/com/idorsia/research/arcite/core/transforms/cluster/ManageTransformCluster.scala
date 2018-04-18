package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{ActorSystem, AddressFromURIString, PoisonPill, RootActorPath}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._

import com.typesafe.config.ConfigFactory

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

  val workConf = config.getConfig("transform-worker")
  logger.info(s"transform worker actor system config: ${workConf.toString}")

  private val workInitialContacts = immutableSeq(workConf.getStringList("contact-points")).map {
    case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
  }.toSet

  logger.info(s"work initial contacts: $workInitialContacts")


  def startBackend(port: Int) = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[backend]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(config.getConfig("transform-cluster"))

    logger.info(s"starting a new backend with conf: ${conf.toString}")
    val system = ActorSystem(arcClusterSyst, conf) // we start a new actor system for each backend
    logger.info(s"actor system: ${system.toString}")

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole("backend")), "master")
  }
}
