package com.actelion.research.arcite.core.transforms.cluster

import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.{ActorIdentity, ActorPath, ActorRef, ActorSystem, AddressFromURIString, Identify, PoisonPill, Props, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.actelion.research.arcite.core.transforms.cluster.workers.{WorkExecProd, WorkExecUpperCase}
import com.typesafe.config.ConfigFactory

/**
  * Created by deffabe1 on 7/22/16.
  */
object StartTransformCluster {

  def workTimeout = 10.seconds

  private val workerActorSystem = startWorkerActorSystem()
  private val system = workerActorSystem._1
  private val initialContacts = workerActorSystem._2

  def defaultTransformClusterStart(): Set[ActorRef] = {
    startBackend(2551, "backend")
    Thread.sleep(5000)
    startBackend(2552, "backend")
    Thread.sleep(5000)
    startBackend(2553, "backend")
    Thread.sleep(5000)
    val frontEnd1 = startFrontend(0)
    Thread.sleep(5000)
    val frontEnd2 = startFrontend(0)

    Set(frontEnd1, frontEnd2)
  }

  def startBackend(port: Int, role: String) = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load("transform-cluster"))

    val system = ActorSystem("ArciteTransClustSys", conf)

    startupSharedJournal(system, startStore = (port == 2551),
      path = ActorPath.fromString("akka.tcp://ArciteTransClustSys@127.0.0.1:2551/user/store"))

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole(role)), "master")
  }

  def startFrontend(port: Int): ActorRef = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load("transform-cluster"))

    val system = ActorSystem("ArciteTransClustSys", conf)

    system.actorOf(Props[Frontend], "frontend")
  }

  def startWorkerActorSystem(): (ActorSystem, Set[ActorPath]) = {
    val conf = ConfigFactory.load("transform-worker")

    val system = ActorSystem("ArciteTransWorkSys", conf)

    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
    }.toSet

    (system, initialContacts)
  }

  def addWorker(props: Props): Unit = {

    val clusterClient = system.actorOf(
      ClusterClient.props(ClusterClientSettings(system).withInitialContacts(initialContacts)), "WorkerClusterClient")

    system.actorOf(Worker.props(clusterClient, props), "worker")
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
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
}
