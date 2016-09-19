package com.actelion.research.arcite.core.transforms

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.nio.file.StandardOpenOption._
import java.text.SimpleDateFormat
import java.util.Date

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.utils.{FullName, Owner}
import org.slf4j.LoggerFactory

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
  * Created by Bernard Deffarges on 2016/09/08.
  *
  */
sealed trait TransformFeedback {
  val logger = LoggerFactory.getLogger(getClass.getName)

  def transform: Transform

  def writeFeedback(transformFeedback: TransformFeedback): Unit = {

    val t = transformFeedback

    def transformSourceFiles(ts: TransformSource): String = ???
//    ts match {
//      case t: TransformSourceFromFiles ⇒
//        s"""${
//          t.sourceFoldersOrFiles.mkString(", ")
//        }"""
//      case t: TransformSourceRegex ⇒
//        s"""folder=${
//          t.folder
//        }  regex="${
//          t.regex
//        }" withSubFolder=${
//          t.withSubfolder
//        }"""
//      case t: TransformAsSource4Transform ⇒
//        s"""transformOriginUID=${
//          t.transformUID
//        } filesAndFolders=[${
//          t.sourceFoldersOrFiles.mkString(", ")
//        }]"""
//    }

    val stateOutPutError = transformFeedback match {
      case t: TransformSuccess ⇒
        ("SUCCEEDED", t.output, "")
      case t: TransformFailed ⇒
        ("FAILED", t.output, t.error)
    }

    val ts = TransformSourceDone(t.transform.source.experiment.name, t.transform.source.experiment.owner,
      t.transform.source.experiment.digest)

    val tdone = TransformDone(stateOutPutError._1,
      new SimpleDateFormat("yyyy:MM:dd-HH:mm:ss").format(new Date),
      t.transform.transfDefName,
      ts, transformSourceFiles(t.transform.source),
      core.getFirstAndLastLinesOfAVeryLongString(stateOutPutError._2, 100),
      core.getFirstAndLastLinesOfAVeryLongString(stateOutPutError._3, 100))

    import spray.json._
    import DefaultJsonProtocol._

    implicit val jsonTowner = jsonFormat2(Owner)
    implicit val jsonTFullName = jsonFormat2(FullName)
    implicit val jsonTdesc = jsonFormat3(TransformDescription)
    implicit val jsonTsd = jsonFormat3(TransformSourceDone)
    implicit val jsonTd = jsonFormat7(TransformDone)

    val strg = tdone.toJson.prettyPrint

    val fp = Paths.get(ExperimentFolderVisitor(t.transform.source.experiment).transformFolderPath.toString,
      t.transform.uid, "transformFeedback")

    logger.debug(tdone.toString)
    Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
  }

  case class TransformSourceDone(experimentName: String, experimentOwner: Owner, transformDigest: String)

  case class TransformDone(state: String, date: String, transformDefinitionName: FullName,
                           transformSource: TransformSourceDone, transformSourceFile: String,
                           output: String, error: String)

  case class TransformSuccess(transform: Transform, output: String) extends TransformFeedback

  case class TransformFailed(transform: Transform, output: String, error: String) extends TransformFeedback

  case class TransformInProgress(transform: Transform) extends TransformFeedback

}
