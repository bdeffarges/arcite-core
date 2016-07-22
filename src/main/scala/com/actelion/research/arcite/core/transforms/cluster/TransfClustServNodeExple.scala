package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import com.typesafe.config.ConfigFactory

/**
  * Created by deffabe1 on 7/19/16.
  */
class TransfClustServNodeExple extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  override def preStart() {
    cluster.subscribe(self, classOf[MemberUp])
  }

  override def postStop() {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case pt: ProcessText ⇒
      println(s"got message: $pt")
      sender ! ProcessedText(pt.text.toUpperCase)

    case MemberUp(member) ⇒
      println(s"member up [$member] with roles [${member.roles.mkString("-")}]")
      if (member.hasRole("transformers-ctrl")) {
        println(s"sending registration message...")
        context.actorSelection(RootActorPath(member.address) / "user" / "transform-cluster-ctrl") ! ServiceRegistration
      }
  }
}


object TransfClustServNodeExple {

  case class ProcessText(text: String)

  case class ProcessedText(text: String)

  def initiate(port: Int): ActorRef = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.load().getConfig("transform-cluster-example"))

    val system = ActorSystem("arcite-transformers-cluster", config)

    system.actorOf(Props[TransfClustServNodeExple])
  }
}

object TransfClustServNodeExpleMain extends App {

  val ct1 = TransformClusterCtrl.initiate(60001)
//  val ct2 = TransformClusterCtrl.initiate(60002)
//  val ct3 = TransformClusterCtrl.initiate(60003)

  TransfClustServNodeExple.initiate(60010)
  TransfClustServNodeExple.initiate(60011)
  TransfClustServNodeExple.initiate(60012)
  TransfClustServNodeExple.initiate(60013)

  Thread.sleep(10000)

  println("sending message...")
  ct1 ! ProcessText("hello world wwwww")


//  ct2 ! ProcessText("hello world ddd")
//  ct3 ! ProcessText("hello world vvv")
}