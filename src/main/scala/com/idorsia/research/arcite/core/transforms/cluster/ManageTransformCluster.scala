package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, AddressFromURIString, PoisonPill, Props, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import com.idorsia.research.arcite.core.transforms.TransformDefinition
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest._
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory


/**
  * Created by deffabe1 on 7/22/16.
  */
object ManageTransformCluster {
  val logger = LoggerFactory.getLogger(ManageTransformCluster.getClass)

  val arcClusterSyst: String = "ArcTransfActClustSys"

  val arcWorkerActSys: String = "ArcWorkerActSys"

  val workTimeout = 24.hours // default max timeOut

  val config = ConfigFactory.load()

  val workConf = config.getConfig("transform_worker")
  logger.info(s"transform worker actor system config: ${workConf.toString}")

  // actor system only for the workers that are started in core, for now only test workers.
  private lazy val workSystem = ActorSystem(arcWorkerActSys, workConf)

  private val workInitialContacts = immutableSeq(workConf.getStringList("contact-points")).map {
    case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
  }.toSet

  logger.info(s"work initial contacts: $workInitialContacts")

  private var frontEnds = Seq[ActorRef]()

  def defaultTransformClusterStartFromConf(): Unit = {
    logger.info("starting Arcite workers cluster...")
    val bePorts = config.getIntList("transform_cluster.backends.ports")
    val fePorts = config.getInt("transform_cluster.frontends.numberOfports")
    import scala.collection.convert.wrapAsScala._

    defaultTransformClusterStart(bePorts.toIndexedSeq.map(_.intValue()), fePorts)
    startTestWorkers()
  }

  def defaultTransformClusterStart(backendPorts: Seq[Int], nbrOffrontEnds: Int): Unit = {
    logger.info("starting backend nodes... ... ... ")
    backendPorts.foreach(startBackend(_, "backend"))

    logger.info("starting frontend nodes... ... ... ")
    frontEnds = (1 to nbrOffrontEnds).map(_ ⇒ startFrontend(0))
  }

  def getNextFrontEnd(): ActorRef = {
    // todo random for now, instead it should pick-up those that are available
    //
    val i = java.util.concurrent.ThreadLocalRandom.current().nextInt(frontEnds.size)
    val chosenFE = frontEnds(i)
    logger.info(s"pickup id[$i] => $chosenFE}")

    chosenFE
  }

  def startBackend(port: Int, role: String) = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(config.getConfig("transform_cluster"))

    logger.info(s"starting a new backend with conf: ${conf.toString}")
    val system = ActorSystem(arcClusterSyst, conf) // we start a new actor system for each backend
    logger.info(s"actor system: ${system.toString}")

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole(role)), "master")
  }


  def startFrontend(port: Int): ActorRef = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(config.getConfig("transform_cluster"))

    logger.info(s"starting new frontend with conf ${conf.toString}")
    val system = ActorSystem(arcClusterSyst, conf)// we start a new actor system for each frontend as well
    logger.info(s"actor system: ${system.toString}")

    system.actorOf(Props[Frontend], "frontend")
  }


  def addWorker(td: TransformDefinition): Unit = {

    val name = s"${td.transDefIdent.fullName.asUID}-${UUID.randomUUID().toString}"

    val clusterClient = workSystem.actorOf(
      ClusterClient.props(
        ClusterClientSettings(workSystem).withInitialContacts(workInitialContacts)
      ), s"WorkerClusterClient-$name")

    workSystem.actorOf(TransformWorker.props(clusterClient, td), name)
  }


  def startTestWorkers(): Unit = {
    addWorker(WorkExecUpperCase.definition)
    addWorker(WorkExecUpperCase.definition)
    addWorker(WorkExecLowerCase.definition)
    addWorker(WorkExecLowerCase.definition)
    addWorker(WorkExecProd.definition)
    addWorker(WorkExecProd.definition)
    addWorker(WorkExecDuplicateText.definition)
    addWorker(WorkExecDuplicateText.definition)
    addWorker(WorkExecMergeText.definition)
    addWorker(WorkExecMergeText.definition)
  }
}
