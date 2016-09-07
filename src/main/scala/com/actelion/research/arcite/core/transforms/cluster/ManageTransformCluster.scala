package com.actelion.research.arcite.core.transforms.cluster

import java.util.UUID

import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.{ActorIdentity, ActorPath, ActorRef, ActorSystem, AddressFromURIString, Identify, PoisonPill, Props, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.actelion.research.arcite.core.transforms.TransformDefinition
import com.actelion.research.arcite.core.transforms.cluster.workers.RWrapperWorker.RunRCode
import com.actelion.research.arcite.core.transforms.cluster.workers.WorkExecProd.CalcProd
import com.actelion.research.arcite.core.transforms.cluster.workers.WorkExecUpperCase.ToUpperCase
import com.actelion.research.arcite.core.transforms.cluster.workers.{RWrapperWorker, WorkExecProd, WorkExecUpperCase}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import org.slf4j.LoggerFactory

import scala.concurrent.forkjoin.ThreadLocalRandom

/**
  * Created by deffabe1 on 7/22/16.
  */
object ManageTransformCluster {
  val logger = LoggerFactory.getLogger(ManageTransformCluster.getClass)

  val arcTransfActClustSys = "ArcTransfActClustSys"

  val arcWorkerActSys = "ArcWorkerActSys"

  def workTimeout = 10.seconds

  val workConf = ConfigFactory.load("transform-worker")

  private val workSystem = ActorSystem(arcWorkerActSys, workConf)

  private val workInitialContacts = immutableSeq(workConf.getStringList("contact-points")).map {
    case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
  }.toSet

  logger.info(s"work initial contacts: $workInitialContacts")

  private var frontends = Seq[ActorRef]()

  def defaultTransformClusterStart(backendPorts: Seq[Int], frontEnds: Int): Unit = {
    backendPorts.foreach(startBackend(_, "backend"))

    frontends = (0 to frontEnds).map(_ ⇒ startFrontend(0))
  }

  def getNextFrontEnd(): ActorRef = {
    // todo random for now, instead it should pick-up those that are available
    //
    val i = ThreadLocalRandom.current().nextInt(frontends.size)
    val ar =    frontends(i)
    logger.info(s"pickup id[$i] => $ar}")

    ar
  }

  def startBackend(port: Int, role: String) = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load("transform-cluster"))

    val system = ActorSystem(arcTransfActClustSys, conf)

    //todo journal seed node port?
    startupSharedJournal(system, startStore = (port == 2551),
      path = ActorPath.fromString(s"akka.tcp://$arcTransfActClustSys@127.0.0.1:2551/user/store"))

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole(role)), "master")
  }

  def startFrontend(port: Int): ActorRef = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load("transform-cluster"))

    val system = ActorSystem(arcTransfActClustSys, conf)

//    system.actorOf(Props[WorkResultConsumer], "consumer")

    system.actorOf(Props[Frontend], "frontend")
  }

  def addWorker(td: TransformDefinition): Unit = {

    val name = s"${td.transDefIdent.shortName}-${td.transDefIdent.digestUID}"

    val clusterClient = workSystem.actorOf(
      ClusterClient.props(
        ClusterClientSettings(workSystem).withInitialContacts(workInitialContacts)
      ), s"WorkerClusterClient-$name")

    workSystem.actorOf(TransformWorker.props(clusterClient, td.actorProps()), name)
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // Start the shared journal on one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)

    val f = (system.actorSelection(path) ? Identify(None))

    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.terminate()
    }
    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.terminate()
    }
  }

  /**
    * to test the cluster
    *
    * @param args
    */
  def main(args: Array[String]): Unit = {

    Kamon.start()

    startSomeDefaultClusterForTesting()

    Thread.sleep(1000)

    val pwd = System.getProperty("user.dir")

    // todo fix lost jobs from worker not returning fast enough, look at "No ack from master, retrying"
    (0 to 40).foreach { i ⇒
      println(s"counter $i")
//      getNextFrontEnd() ! Work("R_helloWorld1"+UUID.randomUUID().toString, Job(RunRCode(s"$pwd/for_testing", s"$pwd/for_testing/sqrt1.r", Seq.empty), "r_code"))
//      Thread.sleep(100)
//      getNextFrontEnd() ! Work("uppercase1"+UUID.randomUUID().toString, Job(ToUpperCase("hello world, how are you doing"), "ToUpperCase"))
//      Thread.sleep(100)
//      getNextFrontEnd() ! Work("calcProduct1"+UUID.randomUUID().toString, Job(CalcProd(10), "product"))
//      Thread.sleep(100)
//      getNextFrontEnd() ! Work("uppercase2"+UUID.randomUUID().toString, Job(ToUpperCase("earth moon mars neptune jupiter "), "ToUpperCase"))
//      Thread.sleep(100)
//      getNextFrontEnd() ! Work("R_helloWorld2"+UUID.randomUUID().toString, Job(RunRCode(s"$pwd/for_testing", s"$pwd/for_testing/sqrt1.r", Seq.empty), "r_code"))
//      Thread.sleep(500)
//      getNextFrontEnd() ! Work("calcProduct1"+UUID.randomUUID().toString, Job(CalcProd(110), "product"))
//      getNextFrontEnd() ! Work("helloWorld3"+UUID.randomUUID().toString, Job(RunRCode(s"$pwd/for_testing", s"$pwd/for_testing/sqrt1.r", Seq.empty), "r_code"))
//      getNextFrontEnd() ! Work("helloWorld4"+UUID.randomUUID().toString, Job(RunRCode(s"$pwd/for_testing", s"$pwd/for_testing/sqrt1.r", Seq.empty), "r_code"))
      Thread.sleep(500)
    }
  }

  def startSomeDefaultClusterForTesting(): Unit = {

    defaultTransformClusterStart(Seq(2551, 2552, 2553, 2554, 2555, 2556, 2557, 2558), 30)

    ManageTransformCluster.addWorker(RWrapperWorker.definition)
    ManageTransformCluster.addWorker(RWrapperWorker.definition)
    ManageTransformCluster.addWorker(RWrapperWorker.definition)
    ManageTransformCluster.addWorker(RWrapperWorker.definition)

    ManageTransformCluster.addWorker(WorkExecProd.definition)
    ManageTransformCluster.addWorker(WorkExecProd.definition)
    ManageTransformCluster.addWorker(WorkExecProd.definition)
    ManageTransformCluster.addWorker(WorkExecProd.definition)

    ManageTransformCluster.addWorker(WorkExecUpperCase.definition)
    ManageTransformCluster.addWorker(WorkExecUpperCase.definition)
    ManageTransformCluster.addWorker(WorkExecUpperCase.definition)

 }
}
