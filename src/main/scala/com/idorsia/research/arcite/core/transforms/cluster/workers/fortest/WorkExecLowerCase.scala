package com.idorsia.research.arcite.core.transforms.cluster.workers.fortest


import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core.api.TransfJsonProto
import com.idorsia.research.arcite.core.experiments.ExperimentFolderVisitor
import com.idorsia.research.arcite.core.transforms._
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkerJobFailed, WorkerJobSuccessFul}
import com.idorsia.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.idorsia.research.arcite.core.utils.{FullName, WriteFeedbackActor}
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

class WorkExecLowerCase extends Actor with ActorLogging with TransfJsonProto {

  import WorkExecLowerCase._

  def receive: Receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
//      log.info("starting work but will wait for fake...")
//      val end = java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 20)
//      val increment = 100 / end
//      0 to end foreach { _ ⇒
//        Thread.sleep(1000)
//        sender() ! WorkerProgress(increment)
//      }
//      log.info("waited enough time, doing the work now...")

      t.source match {
        case tfo: TransformSourceFromObject ⇒
          val toBeTransformed = ToLowerCase(t.parameters("ToLowerCase"))
          if (toBeTransformed.stgToLowerCase.contains("fail!")) {
            sender() ! WorkerJobFailed("failing on purpose for tests...")
          } else {
            val lowerCased = toBeTransformed.stgToLowerCase.toLowerCase()
            val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "lowercase.txt")
            Files.write(p, lowerCased.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
            sender() ! WorkerJobSuccessFul("to lower case completed", Map("fileName" -> p.getFileName.toString))
          }

        case tfFtf: TransformSourceFromTransform ⇒

          val transfFolder = ExperimentFolderVisitor(tfFtf.experiment).transformFolderPath
          val path = transfFolder resolve tfFtf.srcTransformID
          val feedbF = path resolve WriteFeedbackActor.FILE_NAME
          if (feedbF.toFile.exists()) {
            val tdi = Files.readAllLines(feedbF).toList.mkString("\n").parseJson.convertTo[TransformCompletionFeedback]
            var listFiles: List[String] = Nil
            tdi.artifacts.values.map { f ⇒
              val fileP = path resolve f
              if (fileP.toFile.exists && fileP.toFile.isFile) {
                val textLowerC = Files.readAllLines(fileP).mkString("\n").toLowerCase()
                listFiles = s"lowercase_$f" :: listFiles
                val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
                Files.write(p, textLowerC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
              }
            }
            sender() ! WorkerJobSuccessFul("to lower case completed", Map("fileList" -> listFiles.mkString("\n")))
          } else {
            sender() ! WorkerJobFailed("to lower case failed ", "did not find previous transform output file.")
          }


        case tfr: TransformSourceFromRaw ⇒
          val expVisFolder = ExperimentFolderVisitor(tfr.experiment)
          var listFiles: List[String] = Nil

          val toBeTransformed = ToLowerCase(t.parameters("ToLowerCase"))
          if (toBeTransformed.stgToLowerCase.contains("fail!")) {
            log.debug("got a fail message, failing on purpose to test failure behavior...")
            sender() ! WorkerJobFailed("failing on purpose for tests...")
          } else {
            expVisFolder.getAllRawFiles.map { f ⇒
              val textLowerC = Files.readAllLines(f.toPath).mkString("\n").toLowerCase()
              listFiles = s"lowercase_${f.getName}" :: listFiles
              val p = Paths.get(TransformHelper(t).getTransformFolder().toString, listFiles.head)
              Files.write(p, textLowerC.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
            }
            sender() ! WorkerJobSuccessFul("to lower case completed", Map("fileList" -> listFiles.mkString("\n")))
          }
      }

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, transfDefId)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}

object WorkExecLowerCase {
  val fullName = FullName("com.idorsia.research.arcite.core", "to-lowercase", "to-lowercase")

  val transfDefId = TransformDefinitionIdentity(fullName,
    TransformDescription("to-lowercase", "text", "lowercase-text",
      transformParameters = Set(FreeText("ToLowerCase", "ToLowerCase", Some("TO LOWER CASE")))))

  val definition = TransformDefinition(transfDefId, props)

  def props(): Props = Props(classOf[WorkExecLowerCase])

  case class ToLowerCase(stgToLowerCase: String)

}