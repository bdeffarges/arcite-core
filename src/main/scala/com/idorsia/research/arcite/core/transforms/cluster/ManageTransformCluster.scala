package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, AddressFromURIString, PoisonPill, Props, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import com.idorsia.research.arcite.core.transforms.TransformDefinition
import com.idorsia.research.arcite.core.transforms.cluster.workers.fortest.{WorkExecDuplicateText, WorkExecLowerCase, WorkExecProd, WorkExecUpperCase}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory


/**
  * Created by deffabe1 on 7/22/16.
  */
object ManageTransformCluster {
  val logger = LoggerFactory.getLogger(ManageTransformCluster.getClass)

  val arcTransfActClustSys = "ArcTransfActClustSys"

  val arcWorkerActSys = "ArcWorkerActSys"

  val workTimeout = 36.hours // default max timeOut

  val config = ConfigFactory.load()

  val workConf = config.getConfig("transform_worker")

  private val workSystem = ActorSystem(arcWorkerActSys, workConf)

  private val workInitialContacts = immutableSeq(workConf.getStringList("contact-points")).map {
    case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
  }.toSet

  logger.info(s"work initial contacts: $workInitialContacts")

  private var frontEnds = Seq[ActorRef]()

  def defaultTransformClusterStartFromConf(): Unit = {
    logger.info("starting cluster...")
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

    val actorStoreLoc = s"akka.tcp://$arcTransfActClustSys@${conf.getString("store")}"
    logger.info(s"actor store location: $actorStoreLoc")

    val system = ActorSystem(arcTransfActClustSys, conf) // we start a new actor system for each backend

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole(role)), "master")
  }


  def startFrontend(port: Int): ActorRef = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(config.getConfig("transform_cluster"))

    val system = ActorSystem(arcTransfActClustSys, conf)// we start a new actor system for each frontend as well

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


  /**
    * to test the cluster
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {
    defaultTransformClusterStart(Seq(2551, 2552, 2553, 2554, 2555, 2556, 2557, 2558), 10)
  }

  def startSomeDefaultClusterForTesting(): Unit = {
    defaultTransformClusterStart(Seq(2551, 2552, 2553, 2554, 2555, 2556, 2557, 2558), 30)
    startTestWorkers()
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
  }
}
