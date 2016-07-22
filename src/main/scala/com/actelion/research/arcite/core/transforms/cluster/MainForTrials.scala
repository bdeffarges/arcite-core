package com.actelion.research.arcite.core.transforms.cluster

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RootActorPath
import akka.cluster.client.{ClusterClient, ClusterClientReceptionist, ClusterClientSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.japi.Util.immutableSeq
import akka.actor.AddressFromURIString
import akka.actor.ActorPath
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.Identify
import akka.actor.ActorIdentity
import com.actelion.research.arcite.core.transforms.cluster.workers.RWrapperWorker.RunRCode
import com.actelion.research.arcite.core.transforms.cluster.workers.{RWrapperWorker, WorkExecProd, WorkExecUpperCase}

object MainForTrials extends App {


  if (args.isEmpty) {
    startBackend(2551, "backend")
    Thread.sleep(5000)
    startBackend(2552, "backend")
    startWorker(0)
    Thread.sleep(5000)
    startFrontend(0)
  } else {
    val port = args(0).toInt
    if (2000 <= port && port <= 2999)
      startBackend(port, "backend")
    else if (3000 <= port && port <= 3999)
      startFrontend(port)
    else
      startWorker(port)
  }


  def workTimeout = 10.seconds

  def startBackend(port: Int, role: String): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).
      withFallback(ConfigFactory.load("transform-cluster"))
    val system = ActorSystem("ArciteTransClustSys", conf)

    startupSharedJournal(system, startStore = (port == 2551), path =
      ActorPath.fromString("akka.tcp://ArciteTransClustSys@127.0.0.1:2551/user/store"))

    system.actorOf(ClusterSingletonManager.props(
      Master.props(workTimeout),
      PoisonPill,
      ClusterSingletonManagerSettings(system).withRole(role)), "master")

  }

  def startFrontend(port: Int): Unit = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load("transform-cluster"))

    val system = ActorSystem("ArciteTransClustSys", conf)

    val frontend = system.actorOf(Props[Frontend], "frontend")

    system.actorOf(Props(classOf[WorkProducer], frontend), "producer")
    system.actorOf(Props[WorkResultConsumer], "consumer")
  }

  def startWorker(port: Int): Unit = {
    // load worker.conf
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load("transform-worker"))

    val system = ActorSystem("ArciteTransWorkSys", conf)

    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
    }.toSet

    val clusterClient = system.actorOf(
      ClusterClient.props(
        ClusterClientSettings(system)
          .withInitialContacts(initialContacts)),
      "clusterClient")

    // trying with examples....
    system.actorOf(Worker.props(clusterClient, Props[WorkExecProd]), "worker1")
    system.actorOf(Worker.props(clusterClient, Props[WorkExecUpperCase]), "worker2")
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

object TryingOutRWorker extends App {

  val frontEnds = StartTransformCluster.defaultTransformClusterStart()
  Thread.sleep(5000)
  StartTransformCluster.addWorker(RWrapperWorker.props())
  Thread.sleep(5000)
  val pwd = System.getProperty("user.dir")
  frontEnds.head ! Work("helloWorld", Job(RunRCode(s"$pwd/for_testing", s"$pwd/for_testing/sqrt1.r", Seq.empty), "r_code"))
}