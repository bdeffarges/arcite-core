package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.{WorkerTransDefinition, Worker, WorkerType}

class WorkExecProd extends Actor with ActorLogging {

  import WorkExecProd._

  def receive = {
    case CalcProd(n) =>
      val n2 = n * n
      val result = s"workexecutor= $n * $n = $n2"
      sender() ! Worker.WorkComplete(result)

    case WorkerTransDefinition(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! WorkerType(wi, WorkExecProd.jobType)

    case msg: Any ⇒ log.error(s"unable to deal with message $msg")
  }

}

object WorkExecProd {
  val jobType = "product"

  def props(): Props = Props(classOf[WorkExecProd])

  case class CalcProd(n: Int)
}