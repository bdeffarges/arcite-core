package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkComplete
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.utils.FullName

class WorkExecUpperCase extends Actor with ActorLogging {

  import WorkExecUpperCase._

  def receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$defLight")
      require (t.transfDefName == defLight.fullName)
      log.info("starting work but will wait for fake...")
      Thread.sleep(10000)
      t.source match {
        case tfo: TransformSourceFromObject ⇒
          import spray.json.DefaultJsonProtocol._
          implicit val toUpperCaseJson = jsonFormat1(ToUpperCase)
          log.info("waited enough time, doing the work now...")
          val toBeTransformed = t.parameters.convertTo[ToUpperCase]
          sender() ! WorkComplete(s"in upperString=${toBeTransformed.stgToUpperCase.toUpperCase()}")
      }

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, defLight)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  val fullName = FullName("com.actelion.research.arcite.core", "to-uppercase")

  val defLight = TransformDefinitionIdentity(fullName, "to-uppercase",
    TransformDescription("to-uppercase", "text", "uppercase-text"))

  val definition = TransformDefinition(defLight, props)

  def props(): Props = Props(classOf[WorkExecUpperCase])

  case class ToUpperCase(stgToUpperCase: String)
}