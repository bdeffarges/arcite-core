package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging}
import com.actelion.research.arcite.core.transforms.cluster.{GetWorkerTypeFor, Worker, WorkerType}

class WorkExecUpperCase extends Actor with ActorLogging {
  import WorkExecUpperCase._

  def receive = {
    case ToUpperCase(stg) =>
      sender() ! Worker.WorkComplete(s"in upperString=${stg.toUpperCase()}")

    case GetWorkerTypeFor(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! WorkerType(wi, WorkExecUpperCase.jobType)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  val jobType = "ToUpperCase"

  case class ToUpperCase(stg: String)

}