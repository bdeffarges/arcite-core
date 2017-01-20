package com.actelion.research.arcite.core.transforms.cluster.workers.fortest


import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.MasterWorkerProtocol.WorkerProgress
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.{WorkFailed, WorkSuccessFull}
import com.actelion.research.arcite.core.transforms.cluster.WorkState.WorkInProgress
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.utils.{FullName, WriteFeedbackActor}
import spray.json._

import scala.collection.convert.wrapAsScala._

/**
  *
  * arcite-core
  *
  * Copyright (C) 2016 Karanar Software (B. Deffarges)
  * 38 rue Wilson, 68170 Rixheim, France
  *
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/12/14.
  *
  */

class WorkExecLowerCase extends Actor with ActorLogging with ArciteJSONProtocol {

  import WorkExecLowerCase._

  def receive: Receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
      log.info("starting work but will wait for fake...")
      val end = java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 100)
      (1 to end).foreach { e ⇒
        Thread.sleep(5000)
        sender() ! WorkerProgress(e * 100/end)
      }
      log.info("waited enough time, doing the work now...")

      t.source match {
        case tfo: TransformSourceFromObject ⇒
          val toBeTransformed = ToLowerCase(t.parameters("ToLowerCase"))
          val lowerCased = toBeTransformed.stgToLowerCase.toLowerCase()
          val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "lowercase.txt")
          Files.write(p, lowerCased.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          sender() ! WorkSuccessFull("to lower case completed", p.getFileName.toString :: Nil)


        case tfFtf: TransformSourceFromTransform ⇒

          val transfFolder = ExperimentFolderVisitor(tfFtf.experiment).transformFolderPath
          val path = transfFolder resolve tfFtf.srcTransformID
          val feedbF = path resolve WriteFeedbackActor.FILE_NAME
          if (feedbF.toFile.exists()) {
            val tdi = Files.readAllLines(feedbF).toList.mkString("\n").parseJson.convertTo[TransformCompletionFeedback]
            var listFiles: List[String] = Nil
            tdi.artifacts.map { f ⇒
              val fileP = path resolve f
              if (fileP.toFile.exists) {
                val textLowerC = Files.readAllLines(fileP).mkString("\n").toLowerCase()
                listFiles = s"lowercase_$f" :: listFiles
                val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
                Files.write(p, textLowerC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
              }
            }
            sender() ! WorkSuccessFull("to lower case completed", listFiles)
          } else {
            sender() ! WorkFailed("to lower case failed ", "did not find previous transform output file.")
          }


        case tfr: TransformSourceFromRaw ⇒
          val expVisFolder = ExperimentFolderVisitor(tfr.experiment)
          var listFiles: List[String] = Nil

          (expVisFolder.userRawFolderPath.toFile.listFiles ++ expVisFolder.rawFolderPath.toFile.listFiles)
            .filterNot(fn ⇒ ExperimentFolderVisitor.isInternalFile(fn.getName)).map { f ⇒
            val textLowerC = Files.readAllLines(f.toPath).mkString("\n").toLowerCase()
            listFiles = s"lowercase_${f.getName}" :: listFiles
            val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
            Files.write(p, textLowerC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          }
          sender() ! WorkSuccessFull("to lower case completed", listFiles)
      }


    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, transfDefId)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecLowerCase {
  val fullName = FullName("com.actelion.research.arcite.core", "to-lowercase")

  val transfDefId = TransformDefinitionIdentity(fullName, "to-lowercase",
    TransformDescription("to-lowercase", "text", "lowercase-text"))

  val definition = TransformDefinition(transfDefId, props)

  def props(): Props = Props(classOf[WorkExecLowerCase])

  case class ToLowerCase(stgToLowerCase: String)

}