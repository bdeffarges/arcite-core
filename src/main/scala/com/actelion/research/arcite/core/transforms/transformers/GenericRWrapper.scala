package com.actelion.research.arcite.core.transforms.transformers

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.actelion.research.arcite.core.transforms.cluster.{GetTransformDefinition, TransformType}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.utils.{Env, FullName}

import scala.sys.process.ProcessLogger

/**
  * Created by deffabe1 on 5/20/16.
  */
class GenericRWrapper extends Actor with ActorLogging {


  val rScriptPath = Env.getConf("rscript")

  import GenericRWrapper._

  override def receive: Receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$defLight")
      require (t.transfDefName == defLight.fullName)
      log.info("starting work but will wait for fake...")
//      t.source match {
//        case tff: TransformSourceFiles ⇒
//          val workingDir =
//      }

    case rc: RunRCodeWithRequester ⇒
      val rrc = rc.rrc
      val wdir = new File(rrc.workingDir)
      if (!wdir.exists()) wdir.mkdirs()

      val output = new StringBuilder
      val error = new StringBuilder

      val rCmd = Seq(rScriptPath, rrc.rCodePath) ++ rrc.arguments

      val process = sys.process.Process(rCmd, wdir, ("ACT_R_PROD", rc.rrc.rCodeHome))

      log.debug(s"starting process: $process")

      val status = process.!(ProcessLogger(output append _, error append _))

      val result = Rreturn(rrc.transform, status, output.toString, error.toString, rc.requester)

      sender() ! result

    case GetTransformDefinition(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, definition)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object GenericRWrapper {
  def props(): Props = Props(classOf[GenericRWrapper])

  val fullName = FullName("com.actelion.research.arcite.core", "Simple-R-wrapper")
  val defLight = TransformDefinitionIdentity(fullName, "r-wrapper",
    TransformDescription("A simple wrapper to run a r process wrapped in an akka actor",
      "takes several arguments to start a R script",
      "returns a status code, output and error Strings, R output (PDF, dataframe) have to be returned somewhere else"))

  def definition() = TransformDefinition(defLight, props)

  case class RunRCode(transform: Transform, workingDir: String, rCodeHome: String, rCodePath: String, arguments: Seq[String])

  case class RunRCodeWithRequester(rrc: RunRCode, requester: ActorRef)

  case class Rreturn(origin: Transform, status: Int, output: String, error: String, requester: ActorRef)

}

