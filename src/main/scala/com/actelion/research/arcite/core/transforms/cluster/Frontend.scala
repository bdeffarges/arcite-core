package com.actelion.research.arcite.core.transforms.cluster

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import akka.util.Timeout
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}

object Frontend {

  // work has been accepted
  case class Ok(workId: String)

  case object NotOk


  case class QueryWorkStatus(workId: String)

  case class QueryJobInfo(workId: String)

  case object AllJobsStatus

  //todo should extend some trait
  //todo move out of frontend
  sealed trait JobFeedback
  case class JobIsRunning(percentDone: Int) extends JobFeedback
  case class JobIsCompleted(feedBack: String)  extends JobFeedback
  case object JobLost  extends JobFeedback
  case object JobQueued  extends JobFeedback//todo anything to add as param?
  case class JobTimedOut(time: Int) extends JobFeedback

  case class AllJobsFeedback(jobsQueued: Set[String], jobsRunning: Set[String],
                             jobsCompleted: Set[String])

  case class JobInfo(workId: String, jobType:String)
}

class Frontend extends Actor with ActorLogging {

  import Frontend._
  import context.dispatcher //todo change dispatcher/Executor

  val masterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"), name = "masterProxy")

  log.info(s"master proxy=$masterProxy")

  //todo change timeouts
  def receive = {

    case qw: QueryWorkStatus ⇒
      implicit  val timeout = Timeout(2.seconds)
      (masterProxy ? qw) pipeTo sender()

    case AllJobsStatus ⇒
      implicit  val timeout = Timeout(10.seconds)
      (masterProxy ? AllJobsStatus) pipeTo sender()

    case qji: QueryJobInfo ⇒
      implicit val timeout = Timeout(1.second)
      (masterProxy ? qji) pipeTo sender()

    case work ⇒
      log.info(s"got work message [$work]")
      implicit val timeout = Timeout(5.seconds)
      (masterProxy ? work) map {
        case Master.Ack(wid) => {
          log.info(s"work accepted, workid: $wid")
          Ok(wid)
        }
      } recover { case _ => NotOk } pipeTo sender()
  }
}