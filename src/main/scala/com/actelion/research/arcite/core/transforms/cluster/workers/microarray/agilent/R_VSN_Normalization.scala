package com.actelion.research.arcite.core.transforms.cluster.workers.microarray.agilent

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, Props}
import com.actelion.research.arcite.core.transforms.cluster.workers.microarray.agilent.R_VSN_Normalization.StartRNormalization
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinitionIdentity, TransformDescription}
import com.actelion.research.arcite.core.transforms.transformers.GenericRWrapper
import com.actelion.research.arcite.core.transforms.transformers.GenericRWrapper.{RunRCode, RunRCodeWithRequester}
import com.actelion.research.arcite.core.utils.{Env, FullName}

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
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/09/16.
  *
  */
class R_VSN_Normalization {
  val rCodeFile = Env.getConf("r.vsn.normalize")
  val rCodeHome = Env.getConf("r.code.home")

//  override def receive: Receive = ???
  // {
//    case StartRNormalization(transform, wFolder, inputF, outputF, requester) ⇒
//      val ractor = context.actorOf(GenericRWrapper.props())
//
//      ractor ! RunRCodeWithRequester(RunRCode(transform, wFolder, rCodeHome, rCodeFile, Seq(inputF, outputF)), requester)
//
//        case t: TransformWithRequester ⇒
//          t.transform.source match {
//            case s: TransformAsSource4Transform ⇒
//              val visit = ExperimentFolderVisitor(t.transform.source.experiment)
//              val wfolder = Paths.get(visit.transformFolderPath.toString, t.transform.uid).toFile
//              wfolder.mkdirs()
//
//              val inputFile = Paths.get(visit.transformFolderPath.toString, s.transformUID, BuildAllArrayFiles.mainFileName).toString
//
//              import RunRNormalization._
//              val outputFile = Paths.get(wfolder.toString, outputFileName).toString
//
//              self ! StartRNormalization(t.transform, wfolder.toString, inputFile, outputFile, t.requester)
//          }
//
//        case rr: Rreturn ⇒
//          log.debug(rr.status.toString)
//          log.debug(rr.error)
//          log.debug(rr.output)
//
//          if (rr.status == 0) {
//            val success = TransformSuccess(rr.origin, rr.output)
//            rr.requester ! success
//            GoTransformIt.writeFeedback(success)
//          } else {
//            val failed = TransformFailed(rr.origin, rr.output, rr.error)
//            rr.requester ! failed
//            GoTransformIt.writeFeedback(failed)
//          }
//  }
}

object  R_VSN_Normalization {
  val outputFileName = "normalized-matrix"

  val fullName = FullName("com.actelion.research.arcite.microarray.agilent", "R_VSN_Normalization")

  val defLight = TransformDefinitionIdentity(fullName, "R VSN Normalization",
    TransformDescription("VSN Normalization for an agilent microarray data set",
      "takes array files containing row col gMeanSignal rMeanSignal gBGMedianSignal rBGMedianSignal gIsFound rIsFound",
      "produces one normalized matrix."))

  def props(): Props = Props(classOf[R_VSN_Normalization])

  /**
    * we keep the transform object even though we also get its content because we want to use it also for the feedback.
    *
    * @param transform
    * @param workingFolder
    * @param inputFile
    * @param outputFile
    * @param requester
    */
  case class StartRNormalization(transform: Transform, workingFolder: String, inputFile: String,
                                 outputFile: String, requester: ActorRef)
}
