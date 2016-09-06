package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.{WorkerTransDefinition, Worker, WorkerType}

class WorkExecUpperCase extends Actor with ActorLogging {

  import WorkExecUpperCase._

  def receive = {
    case ToUpperCase(stg) =>
      log.info("starting work but will wait for fake...")
      Thread.sleep(10000)
      log.info("waited enough time, doing the work now...")
      sender() ! Worker.WorkComplete(s"in upperString=${stg.toUpperCase()}")

    case WorkerTransDefinition(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! WorkerType(wi, WorkExecUpperCase.jobType)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  val jobType = "ToUpperCase"

  def props(): Props = Props(classOf[WorkExecUpperCase])

  case class ToUpperCase(stg: String)

}