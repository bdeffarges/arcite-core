package com.actelion.research.arcite.core.utils

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorPath, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.api.ArciteJSONProtocol
import com.actelion.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.actelion.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.actelion.research.arcite.core.experiments.ManageExperiments.{BunchOfSelectable, SaveSelectable}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.MasterWorkerProtocol.{WorkerFailed, WorkerIsDone, WorkerSuccess}
import com.typesafe.config.ConfigFactory

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

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")
  private val eventInfoSelect = s"${actSys}/user/exp_actors_manager/event_logging_info"
  private val eventInfoAct = context.actorSelection(ActorPath.fromString(eventInfoSelect))
  private val expManager =
    context.actorSelection(ActorPath.fromString(s"${actSys}/user/exp_actors_manager/experiments_manager"))

  override def receive: Receive = {
    case WriteFeedback(wid) ⇒
      log.info(s"writing feedback for [${wid.transf.uid}]")

      val transfFolder = TransformHelper(wid.transf).getTransformFolder()

      val immutableF = transfFolder resolve core.immutableFile
      if (immutableF.toFile.exists()) {
        sender() ! GeneralMessages.ImmutablePath(transfFolder.toString)
      } else {
        Files.write(immutableF, "IMMUTABLE".getBytes(StandardCharsets.UTF_8), CREATE_NEW)

        val exp = wid.transf.source.experiment.uid

        val fs: TransformDoneSource = wid.transf.source match {
          case tsr: TransformSourceFromRaw ⇒
            TransformDoneSource(exp, RAW, None, None, None)
          case tsr: TransformSourceFromRawWithExclusion ⇒
            TransformDoneSource(exp, RAW, None, Some(tsr.excludes), Some(tsr.excludesRegex))
          case tst: TransformSourceFromTransform ⇒
            TransformDoneSource(exp, TRANSFORM, Some(tst.srcTransformID), None, None)
          case tst: TransformSourceFromTransformWithExclusion ⇒
            TransformDoneSource(exp, TRANSFORM, Some(tst.srcTransformUID), Some(tst.excludes), Some(tst.excludesRegex))
          case tob: TransformSourceFromObject ⇒
            TransformDoneSource(exp, JSON, None, None, None)
        }

        val digest = GetDigest.getFolderContentDigest(transfFolder.toFile)
        val params = wid.transf.parameters

        import spray.json._

        wid match {
          case ws: WorkerSuccess ⇒
            val fb = TransformCompletionFeedback(wid.transf.uid, wid.transf.transfDefName, fs, params,
              TransformCompletionStatus.SUCCESS, ws.result.artifacts,
              ws.result.feedback, "", wid.startTime)

            Files.write(Paths.get(transfFolder.toString, FILE_NAME),
              fb.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

            Files.write(transfFolder resolve core.successFile, "SUCCESS".getBytes(StandardCharsets.UTF_8), CREATE_NEW)

            if (ws.result.selectable.nonEmpty) {
              expManager ! SaveSelectable(wid.transf.source.experiment.uid, wid.transf.uid,
              BunchOfSelectable(ws.result.selectable))
            }

            eventInfoAct ! AddLog(wid.transf.source.experiment,
              ExpLog(LogType.TRANSFORM, LogCategory.SUCCESS,
                s"transform [${wid.transf.transfDefName.name}] successfully completed", Some(wid.transf.uid)))


          case wf: WorkerFailed ⇒
            val fb = TransformCompletionFeedback(wid.transf.uid, wid.transf.transfDefName, fs, params,
              TransformCompletionStatus.FAILED, Map.empty,
              wf.result.feedback, wf.result.errors, wid.startTime)

            Files.write(Paths.get(transfFolder.toString, FILE_NAME),
              fb.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

            Files.write(transfFolder resolve core.failedFile, "FAILED".getBytes(StandardCharsets.UTF_8), CREATE_NEW)

            eventInfoAct ! AddLog(wid.transf.source.experiment,
              ExpLog(LogType.TRANSFORM, LogCategory.ERROR,
                s"transform [${wid.transf.transfDefName.name}] failed", Some(wid.transf.uid)))
        }
      }
  }
}

object WriteFeedbackActor {
  val FILE_NAME = s"${core.arciteFilePrefix}transform_output.json"
  val SUCCESS = "SUCCESS"
  val FAILED = "FAILED"
  val RAW = "RAW"
  val TRANSFORM = "TRANSFORM"
  val JSON = "JSON"


  def props(): Props = Props(classOf[WriteFeedbackActor])

  case class WriteFeedback(wid: WorkerIsDone)

}
