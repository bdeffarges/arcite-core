package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import com.typesafe.config.ConfigFactory

import scala.util.Random

/**
  * Created by deffabe1 on 7/19/16.
  */
class TransformClusterCtrl extends Actor with ActorLogging {

  var transfServices = IndexedSeq.empty[ActorRef]

  def receive = {
    case ProcessText if transfServices.isEmpty ⇒
      println("no services available to complete request. ")

    case pt: ProcessText ⇒
      println("ok, forwarding processText to service")
      transfServices(Random.nextInt(transfServices.size)) forward pt

    case ServiceRegistration if (!transfServices.contains(sender())) ⇒
      println(s"registering new service: ${sender()}")
      transfServices = transfServices :+ sender()
      context.watch(sender())

    case Terminated(a) ⇒
      transfServices = transfServices.filterNot(_ == a)

    case msg: Any ⇒
      println(s"unknown message: $msg")
  }
}

object TransformClusterCtrl {

  case object ServiceRegistration

  def initiate(port: Int): ActorRef = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.load().getConfig("transform-cluster-ctrl"))

    val system = ActorSystem("arcite-transformers-cluster", config)

    system.actorOf(Props[TransformClusterCtrl], "transform-cluster-ctrl")
  }
}



