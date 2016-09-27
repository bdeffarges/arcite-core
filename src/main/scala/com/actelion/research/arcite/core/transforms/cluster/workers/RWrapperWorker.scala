package com.actelion.research.arcite.core.transforms.cluster.workers

import java.io.File

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.MasterWorkerProtocol.WorkFailed
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkComplete
import com.actelion.research.arcite.core.transforms.cluster.workers.RWrapperWorker.{Rreturn, RunRCode}
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition, TransformDefinitionIdentity, TransformDescription}
import com.actelion.research.arcite.core.utils.{Env, FullName}

import scala.sys.process.ProcessLogger

/**
  * Created by deffabe1 on 5/20/16.
  *
  */
class RWrapperWorker extends Actor with ActorLogging {

  val rScriptPath = Env.getConf("rscript")

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

      val result = Rreturn(rc.transform, status, output.toString, error.toString)
      log.info(s"rscript result is: $result")

      sender() ! WorkComplete(result)



    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, RWrapperWorker.defLight)



    case msg: Any ⇒
      val s = s"unable to deal with this message: $msg"
      log.error(s)
      sender() ! WorkFailed(s)
  }
}


object RWrapperWorker {

  val fullName = FullName("com.actelion.research.arcite.core", "Simple-R-wrapper")
  val defLight = TransformDefinitionIdentity(fullName, "r-wrapper",
    TransformDescription("A simple wrapper to run a r process wrapped in an akka actor",
      "takes several arguments to start a R script",
      "returns a status code, output and error Strings, R output (PDF, dataframe) have to be returned somewhere else"))

  val definition = TransformDefinition(defLight, props)

  def props(): Props = Props(classOf[RWrapperWorker])

  case class RunRCode(transform: Transform, workingDir: String, rCodePath: String, arguments: Seq[String])

  case class Rreturn(origin: Transform, status: Int, output: String, error: String)

}

