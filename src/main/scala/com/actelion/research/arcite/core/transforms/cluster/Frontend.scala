package com.actelion.research.arcite.core.transforms.cluster

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import akka.util.Timeout
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.actelion.research.arcite.core.transforms.{Transform, TransformLight}

object Frontend {

  // work has been accepted
  case class Ok(transf: Transform)

  case object NotOk

  case class QueryWorkStatus(uid: String)

  case class QueryJobInfo(transf: Transform)

  case object AllJobsStatus

}

class Frontend extends Actor with ActorLogging {

  import Frontend._
  import context.dispatcher

  //todo change dispatcher/Executor

  val masterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"), name = "masterProxy")

  log.info(s"master proxy=$masterProxy")

  //todo change timeouts
  def receive = {

    case qw: QueryWorkStatus ⇒
      implicit val timeout = Timeout(2.seconds)
      (masterProxy ? qw) pipeTo sender()

    case AllJobsStatus ⇒
      implicit val timeout = Timeout(10.seconds)
      (masterProxy ? AllJobsStatus) pipeTo sender()

    case qji: QueryJobInfo ⇒
      implicit val timeout = Timeout(1.second)
      (masterProxy ? qji) pipeTo sender()

    case transf: Transform ⇒
      log.info(s"got work message [$transf]")
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? transf) map {
        case Master.Ack(wid) => {
          log.info(s"work accepted, workid: $wid")
          Ok(wid)
        }
      } recover { case _ => NotOk } pipeTo sender()

    case m: Any ⇒ log.info(s"don't know what to do with message $m")
  }
}