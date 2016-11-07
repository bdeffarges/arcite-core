package com.actelion.research.arcite.core.utils

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.MasterWorkerProtocol.{WorkerFailed, WorkerIsDone, WorkerSuccess}
import com.actelion.research.arcite.core.utils.WriteFeedbackActor.WriteFeedback

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
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
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/10/10.
  *
  */
class WriteFeedbackActor extends Actor with ActorLogging with ArciteJSONProtocol {

  import WriteFeedbackActor._

  override def receive: Receive = {
    case WriteFeedback(wid) ⇒
    log.info(s"writing feedback for [${wid.transf.uid}]")

      val transfFolder = TransformHelper(wid.transf).getTransformFolder()

      val exp = wid.transf.source.experiment.uid

      val fs: TransformDoneSource = wid.transf.source match {
        case tsr: TransformSourceFromRaw ⇒
          TransformDoneSource(exp,RAW, None, None, None)
        case tsr: TransformSourceFromRawWithExclusion ⇒
          TransformDoneSource(exp,RAW,None,  Some(tsr.excludes), Some(tsr.excludesRegex))
        case tst: TransformSourceFromTransform ⇒
          TransformDoneSource(exp,TRANSFORM, Some(tst.srcTransformID), None, None)
        case tst: TransformSourceFromTransformWithExclusion ⇒
          TransformDoneSource(exp,TRANSFORM, Some(tst.srcTransformUID), Some(tst.excludes), Some(tst.excludesRegex))
        case tob: TransformSourceFromObject ⇒
          TransformDoneSource(exp,JSON, None, None, None)
      }

      val status: (String, String) = wid match {
        case ws: WorkerSuccess ⇒
          (SUCCESS, "")
        case wf: WorkerFailed ⇒
          (FAILED, wf.result.error)
      }

      val digest = GetDigest.getFolderContentDigest(transfFolder.toFile)

      val params = Option(wid.transf.parameters)
      val fb = TransformDoneInfo(wid.transf.uid, wid.transf.transfDefName, fs, params,
                                 status._1, wid.result.feedback, Option(status._2), wid.startTime)

      import spray.json._
      import DefaultJsonProtocol._

      Files.write(Paths.get(transfFolder.toString, FILE_NAME), fb.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
  }
}

object WriteFeedbackActor {
  val FILE_NAME = "transform_output.json"
  val SUCCESS = "SUCCESS"
  val FAILED = "FAILED"
  val RAW = "RAW"
  val TRANSFORM = "TRANSFORM"
  val JSON = "JSON"


  def props(): Props = Props(classOf[WriteFeedbackActor])

  case class WriteFeedback(wid: WorkerIsDone)

}
