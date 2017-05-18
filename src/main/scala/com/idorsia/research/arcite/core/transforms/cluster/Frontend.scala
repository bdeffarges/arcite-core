package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern._
import akka.util.Timeout
import com.idorsia.research.arcite.core.transforms.TransfDefMsg.{FindTransfDefs, GetAllTransfDefs, GetTransfDef, GetTransfDefFromName}
import com.idorsia.research.arcite.core.transforms.Transform

import scala.concurrent.duration._

object Frontend {

  sealed trait TransformJobReceived
  case class OkTransfReceived(transfUID: String) extends TransformJobReceived
  case class TransfNotReceived(reason: String) extends TransformJobReceived

  case class QueryWorkStatus(uid: String)

  case object GetAllJobsStatus

  case object GetRunningJobsStatus

}

class Frontend extends Actor with ActorLogging {

  import Frontend._

  //todo change dispatcher/Executor
  import context.dispatcher

  private val masterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"), name = "masterProxy")

  log.info(s"master proxy=$masterProxy")

  //todo change timeouts
  def receive:Receive = {

    case qw: QueryWorkStatus ⇒
      implicit val timeout = Timeout(2 seconds)
      (masterProxy ? qw) pipeTo sender()


    case GetAllJobsStatus ⇒
      implicit val timeout = Timeout(10 seconds)
      (masterProxy ? GetAllJobsStatus) pipeTo sender()


    case GetRunningJobsStatus ⇒
      implicit val timeout = Timeout(10 seconds)
      (masterProxy ? GetRunningJobsStatus) pipeTo sender()


    case transform: Transform ⇒
      log.info(s"got work message [$transform]")
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? transform) map {
        case Master.Ack(transf) => {
          log.info(s"transform accepted: ${transf.uid}/${transf.transfDefName.name}")
          OkTransfReceived(transf.uid)
        }
      } recover { case _ => TransfNotReceived } pipeTo sender()


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