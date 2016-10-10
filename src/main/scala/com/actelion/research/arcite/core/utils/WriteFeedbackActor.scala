package com.actelion.research.arcite.core.utils

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption._

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.MasterWorkerProtocol.{WorkerFailed, WorkerIsDone, WorkerSuccess}
import com.actelion.research.arcite.core.utils.WriteFeedbackActor.{Feedback, FeedbackSource, WriteFeedback}

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
class WriteFeedbackActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case WriteFeedback(wid) ⇒
    log.info(s"writting feedback for [${wid.transf.uid}]")

      val transfFolder = TransformHelper(wid.transf).getTransformFolder()

      val kofs: (String, Option[Set[String]], Option[Set[String]]) = wid.transf.source match {
        case tsr: TransformSourceFromRaw ⇒
          ("raw", None, None)
        case tsr: TransformSourceFromRawWithExclusion ⇒
          ("raw", Some(tsr.excludes), Some(tsr.excludesRegex))
        case tst: TransformSourceFromTransform ⇒
          ("transform", None, None)
        case tst: TransformSourceFromTransformWithExclusion ⇒
          ("transform", Some(tst.excludes), Some(tst.excludesRegex))
      }

      val fs = FeedbackSource(kofs._1, kofs._2, kofs._3)

      val status: (String, String) = wid.result match {
        case ws: WorkerSuccess ⇒
          ("SUCCESS", "")
        case wf: WorkerFailed ⇒
          ("FAILED", wf.result.error)
      }

      val digest = GetDigest.getFolderContentDigest(transfFolder.toFile)

      val fb = Feedback(wid.transf.uid, wid.transf.transfDefName.toString, wid.transf.source.experiment.digest,
        fs, status._1, wid.result.feedback, wid.result.logging, status._2, digest)

      import spray.json._
      import DefaultJsonProtocol._
      implicit val feedbackSourceJsonFormat = jsonFormat3(FeedbackSource)
      implicit val feedbackJsonFormat = jsonFormat9(Feedback)

      Files.write(transfFolder, fb.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
  }
}

object WriteFeedbackActor {
  def props(): Props = Props(classOf[WriteFeedbackActor])

  case class WriteFeedback(wid: WorkerIsDone)

  case class Feedback(transform: String, transformDefinition: String, experiment: String,
                      source: FeedbackSource, status: String, feedback: String,
                      logging: String, errors: String, digest: String)

  case class FeedbackSource(kindOfSource: String, excludes: Option[Set[String]], excludesRegex: Option[Set[String]])

  // todo add info about origin transform or object, etc.
}
