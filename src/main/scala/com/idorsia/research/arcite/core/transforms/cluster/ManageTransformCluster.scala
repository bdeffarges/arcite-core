package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
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

  def startBackend(): Unit = {
    val system = ActorSystem(arcClusterSyst, configWithRole("back-end"))
    logger.info(s"starting actor system (cluster backend): ${system.toString}")

    logger.info("starting akka management...")
    AkkaManagement(system).start()

    logger.info("starting cluster bootstrap... ")
    ClusterBootstrap(system).start()

    MasterSingleton.startSingleton(system)

    system.actorOf(Props(classOf[SimpleClusterListener]))
  }
}


class SimpleClusterListener extends Actor with ActorLogging {

  private val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) ⇒
      log.info("--->>> Member is Up: {}", member.address)
    case UnreachableMember(member) ⇒
      log.info("--->>> Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) ⇒
      log.info(
        "--->>> Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent ⇒ // ignore
  }
}