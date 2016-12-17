package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern._
import akka.util.Timeout
import com.actelion.research.arcite.core.transforms.TransfDefMsg.{FindTransfDefs, GetAllTransfDefs, GetTransfDef, GetTransfDefFromName}
import com.actelion.research.arcite.core.transforms.Transform

import scala.concurrent.duration._

object Frontend {

  sealed trait TransformJobReceived
  case class Ok(transfUID: String) extends TransformJobReceived
  case class NotOk(reason: String) extends TransformJobReceived

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

    case transform: Transform ⇒
      log.info(s"got work message [$transform]")
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? transform) map {
        case Master.Ack(transf) => {
          log.info(s"transform accepted: ${transf.uid}/${transf.transfDefName.name}")
          Ok(transf.uid)
        }
      } recover { case _ => NotOk } pipeTo sender()

    case GetAllTransfDefs ⇒
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? GetAllTransfDefs) pipeTo sender()

    case ft: FindTransfDefs ⇒
      implicit val timeout = Timeout(3.seconds)
      (masterProxy ? ft) pipeTo sender()

    case gtd: GetTransfDef ⇒
      implicit val timeout = Timeout(3.seconds)
      (masterProxy ? gtd) pipeTo sender()

    case gtd: GetTransfDefFromName ⇒
      implicit val timeout = Timeout(3.seconds)
      (masterProxy ? gtd) pipeTo sender()

    case m: Any ⇒ log.error(s"don't know what to do with message $m")
  }
}