package com.actelion.research.arcite.core.transforms.cluster.workers

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkComplete
import com.actelion.research.arcite.core.transforms.cluster.{GetTransformDefinition, TransformType}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.utils.FullName

class WorkExecUpperCase extends Actor with ActorLogging {

  import WorkExecUpperCase._

  def receive = {
    case t: Transform =>
      require (t.definition == definition)
      log.info("starting work but will wait for fake...")
      Thread.sleep(10000)
      t.source match {
        case tfo: TransformSourceFromObject ⇒
          log.info("waited enough time, doing the work now...")
          sender() ! WorkComplete(s"in upperString=${tfo.toString.toUpperCase()}")
      }

    case GetTransformDefinition(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, definition)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  def props(): Props = Props(classOf[WorkExecUpperCase])

  val fullName = FullName("com.actelion.research.arcite.core", "to-uppercase")
  val defLight = TransformDefinitionIdentity(fullName, "to-uppercase",
    TransformDescription("to-uppercase", "text", "uppercase-text"))

  val definition = TransformDefinition(defLight, props)

  case class ToUpperCase(stg: String)

}