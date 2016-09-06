package com.actelion.research.arcite.core.transforms.cluster.workers

import java.io.File

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.cluster.{WorkerTransDefinition, Worker, WorkerType}
import com.actelion.research.arcite.core.transforms.cluster.workers.RWrapperWorker.{Rreturn, RunRCode}
import com.actelion.research.arcite.core.utils.Env

import scala.sys.process.ProcessLogger

/**
  * Created by deffabe1 on 5/20/16.
  */
class RWrapperWorker extends Actor with ActorLogging {


  val rScriptPath = Env.getConf("rscript")

  override def receive: Receive = {
    case rc: RunRCode ⇒
      val wdir = new File(rc.workingDir)
      if (!wdir.exists()) wdir.mkdirs()

      val output = new StringBuilder
      val error = new StringBuilder

      val rCmd = Seq(rScriptPath, rc.rCodePath) ++ rc.arguments

      val process = sys.process.Process(rCmd, wdir)

      log.debug(s"starting process: $process")

      val status = process.!(ProcessLogger(output append _, error append _))

      val result = Rreturn(status, output.toString, error.toString)
      log.debug(s"rscript result is: $result")

      sender() ! Worker.WorkComplete(result)


    case WorkerTransDefinition(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! WorkerType(wi, RWrapperWorker.jobType)


    case msg: Any ⇒ log.error(s"unable to deal with message $msg")

  }
}


object RWrapperWorker {

  val jobType = "r_code"

  def props(): Props = Props(classOf[RWrapperWorker])

  case class RunRCode(workingDir: String, rCodePath: String, arguments: Seq[String])

  case class Rreturn(status: Int, output: String, error: String)

}

