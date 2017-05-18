package com.idorsia.research.arcite.core.transforms.cluster.workers.fortest

import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core.transforms.cluster.MasterWorkerProtocol.WorkerProgress
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.WorkerJobSuccessFul
import com.idorsia.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.idorsia.research.arcite.core.transforms.{TransformDefinition, TransformDefinitionIdentity, TransformDescription}
import com.idorsia.research.arcite.core.utils.FullName

class WorkExecProd extends Actor with ActorLogging {

  import WorkExecProd._

  def receive: Receive = {
    case CalcProd(n) =>
      val n2 = n * n
      val result = s"workexecutor= $n * $n = $n2"
      val end = java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 25)
      val increment = 100 / end
      0 to end foreach { _ ⇒
        Thread.sleep(5000)
        sender() ! WorkerProgress(increment)
      }
      log.info("waited enough time, doing the work now...")

      sender() ! WorkerJobSuccessFul(result)

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, defIdent)

    case msg: Any ⇒ log.error(s"unable to deal with message $msg")
  }

}

object WorkExecProd {
  def props(): Props = Props(classOf[WorkExecProd])

  val fullName = FullName("com.idorsia.research.arcite.core", "product1", "product1")
  val defIdent = TransformDefinitionIdentity(fullName,
    TransformDescription("product1", "number", "number"))

  val definition = TransformDefinition(defIdent, props)

  case class CalcProd(n: Int)

}