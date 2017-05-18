package com.idorsia.research.arcite.core.transforms.cluster.workers.fortest

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.experiments.ExperimentFolderVisitor
import com.idorsia.research.arcite.core.transforms._
import com.idorsia.research.arcite.core.transforms.cluster.MasterWorkerProtocol.WorkerProgress
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkerJobFailed, WorkerJobSuccessFul}
import com.idorsia.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.idorsia.research.arcite.core.utils.{FullName, WriteFeedbackActor}
import spray.json._

import scala.collection.convert.wrapAsScala._

class WorkExecUpperCase extends Actor with ActorLogging with ArciteJSONProtocol {

  import WorkExecUpperCase._

  def receive: Receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
      log.info("starting work but will wait for fake...")
      val end = java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 300)
      val increment = 100 / end
      0 to end foreach { _ ⇒
        Thread.sleep(500)
        sender() ! WorkerProgress(increment)
      }
      log.info("waited enough time, doing the work now...")

      t.source match {
        case tfo: TransformSourceFromObject ⇒
          val toBeTransformed = ToUpperCase(t.parameters("ToUpperCase"))
          val upperCased = toBeTransformed.stgToUpperCase.toUpperCase()
          val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "uppercase.txt")
          Files.write(p, upperCased.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          sender() ! WorkerJobSuccessFul("to upper case completed", Map("fileName" -> p.getFileName.toString))


        case tfFtf: TransformSourceFromTransform ⇒
          val transfFolder = ExperimentFolderVisitor(tfFtf.experiment).transformFolderPath
          val path = transfFolder resolve tfFtf.srcTransformID
          val feedbF = path resolve WriteFeedbackActor.FILE_NAME
          if (feedbF.toFile.exists()) {
            val tdi = Files.readAllLines(feedbF).toList.mkString("\n").parseJson.convertTo[TransformCompletionFeedback]
            var listFiles: List[String] = Nil
            tdi.artifacts.values.map { f ⇒
              val fileP = path resolve f
              if (fileP.toFile.exists) {
                val textUpperC = Files.readAllLines(fileP).mkString("\n").toUpperCase()
                listFiles = s"Uppercase_$f" :: listFiles
                val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
                Files.write(p, textUpperC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
              }
            }
            sender() ! WorkerJobSuccessFul("to Upper case completed", Map("fileList" -> listFiles.mkString("\n")))
          } else {
            sender() ! WorkerJobFailed("to Upper case failed ", "did not find previous transform output file.")
          }


        case tfr: TransformSourceFromRaw ⇒
          val expVisFolder = ExperimentFolderVisitor(tfr.experiment)
          var listFiles: List[String] = Nil

          (expVisFolder.userRawFolderPath.toFile.listFiles ++ expVisFolder.rawFolderPath.toFile.listFiles)
            .filterNot(fn ⇒ ExperimentFolderVisitor.isInternalFile(fn.getName)).map { f ⇒
            val textUpperC = Files.readAllLines(f.toPath).mkString("\n").toUpperCase()
            listFiles = s"Uppercase_${f.getName}" :: listFiles
            val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
            Files.write(p, textUpperC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          }
          sender() ! WorkerJobSuccessFul("to Upper case completed", Map("fileList" -> listFiles.mkString("\n")))
      }

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, transfDefId)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecUpperCase {
  val fullName = FullName("com.idorsia.research.arcite.core", "to-uppercase", "to-uppercase")

  val transfDefId = TransformDefinitionIdentity(fullName,
    TransformDescription("to-uppercase", "text", "uppercase-text"))

  val definition = TransformDefinition(transfDefId, props)

  def props(): Props = Props(classOf[WorkExecUpperCase])

  case class ToUpperCase(stgToUpperCase: String)

}