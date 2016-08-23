package com.actelion.research.arcite.core.transforms.cluster

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import akka.util.Timeout
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}

object Frontend {

  case object Ok

  case object NotOk

}

class Frontend extends Actor with ActorLogging {

  import Frontend._
  import context.dispatcher

  val masterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"), name = "masterProxy")

  def receive = {
    case work =>
      log.debug(s"got message $work")
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work) map {
        case Master.Ack(_) => Ok
      } recover { case _ => NotOk } pipeTo sender()
  }
}