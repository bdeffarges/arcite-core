package com.actelion.research.arcite.core.transforms.cluster.workers

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkSuccessFull
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.utils.FullName

class WorkExecUpperCase extends Actor with ActorLogging {

  import WorkExecUpperCase._

  def receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
      log.info("starting work but will wait for fake...")
      Thread.sleep(1000)
      t.source match {
        case tfo: TransformSourceFromObject ⇒
          import spray.json.DefaultJsonProtocol._
          implicit val toUpperCaseJson = jsonFormat1(ToUpperCase)
          log.info("waited enough time, doing the work now...")
          val toBeTransformed = t.parameters.convertTo[ToUpperCase]
          val upperCased = toBeTransformed.stgToUpperCase.toUpperCase()
          val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "uppercase.txt")
          Files.write(p, upperCased.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          sender() ! WorkSuccessFull(Some(s"in upperString=$upperCased"))
      }

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, transfDefId)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  val fullName = FullName("com.actelion.research.arcite.core", "to-uppercase")

  val transfDefId = TransformDefinitionIdentity(fullName, "to-uppercase",
    TransformDescription("to-uppercase", "text", "uppercase-text"))

  val definition = TransformDefinition(transfDefId, props)

  def props(): Props = Props(classOf[WorkExecUpperCase])

  case class ToUpperCase(stgToUpperCase: String)

}