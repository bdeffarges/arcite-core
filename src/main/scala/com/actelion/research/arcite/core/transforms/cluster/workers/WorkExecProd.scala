package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkComplete
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.transforms.{TransformDefinition, TransformDefinitionIdentity, TransformDescription}
import com.actelion.research.arcite.core.utils.FullName

class WorkExecProd extends Actor with ActorLogging {

  import WorkExecProd._

  def receive = {
    case CalcProd(n) =>
      val n2 = n * n
      val result = s"workexecutor= $n * $n = $n2"
      sender() ! WorkComplete(result)

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, defLight)

    case msg: Any ⇒ log.error(s"unable to deal with message $msg")
  }

}

object WorkExecProd {
  def props(): Props = Props(classOf[WorkExecProd])

  val fullName = FullName("com.actelion.research.arcite.core", "product1")
  val defLight = TransformDefinitionIdentity(fullName, "product1",
    TransformDescription("product1", "number", "number"))

  val definition = TransformDefinition(defLight, props)

  case class CalcProd(n: Int)
}