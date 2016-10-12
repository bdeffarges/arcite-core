package com.actelion.research.arcite.core.transforms.cluster.workers

import java.io.File

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.{WorkFailed, WorkSuccessFull}
import com.actelion.research.arcite.core.transforms.cluster.workers.RWrapperWorker.RunRCode
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition, TransformDefinitionIdentity, TransformDescription}
import com.actelion.research.arcite.core.utils.FullName
import com.typesafe.config.ConfigFactory

import scala.sys.process.ProcessLogger

/**
  * Created by deffabe1 on 5/20/16.
  *
  */
class RWrapperWorker extends Actor with ActorLogging {

  val config = ConfigFactory.load()

  val rScriptPath = config.getString("rscript")

  override def receive: Receive = {
    case rc: RunRCode ⇒
      log.info("preparing to run R code in detached process. ")
      val wdir = new File(rc.workingDir)
      if (!wdir.exists()) wdir.mkdirs()

      val output = new StringBuilder
      val error = new StringBuilder

      val rCmd = Seq(rScriptPath, rc.rCodePath) ++ rc.arguments

      val process = scala.sys.process.Process(rCmd, wdir)

      log.info(s"starting process: $process")

      val status = process.!(ProcessLogger(output append _, error append _))

      val rreturn = if (status == 0) WorkSuccessFull(Some(s"returned status: $status"), output.toString takeRight 500)
      else WorkFailed(output.toString takeRight 500, error.toString takeRight 1000)

      log.info(s"rscript result is: $rreturn")

      sender() ! rreturn


    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, RWrapperWorker.defIdent)


    case msg: Any ⇒
      val s = s"unable to deal with this message: $msg"
      log.error(s)
      sender() ! WorkFailed(s)
  }
}


object RWrapperWorker {

  val fullName = FullName("com.actelion.research.arcite.core", "Simple-R-wrapper")
  val defIdent = TransformDefinitionIdentity(fullName, "r-wrapper",
    TransformDescription("A simple wrapper to run a r process wrapped in an akka actor",
      "takes several arguments to start a R script",
      "returns a status code, output and error Strings, R output (PDF, dataframe) have to be returned somewhere else"))

  val definition = TransformDefinition(defIdent, props)

  def props(): Props = Props(classOf[RWrapperWorker])

  case class RunRCode(transform: Transform, workingDir: String, rCodePath: String, arguments: Seq[String])


}

