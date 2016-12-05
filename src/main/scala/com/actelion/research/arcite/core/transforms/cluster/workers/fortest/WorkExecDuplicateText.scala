package com.actelion.research.arcite.core.transforms.cluster.workers.fortest

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.experiments.ExperimentFolderVisitor
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkSuccessFull
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.utils.FullName

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
  * Created by Bernard Deffarges on 2016/10/12.
  *
  */
class WorkExecDuplicateText extends Actor with ActorLogging {

  import WorkExecDuplicateText._

  def receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} transfDef=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
      log.info("starting work but will wait for fake...")
      Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(30000))
      t.source match {
        case tfo: TransformSourceFromTransform ⇒
          log.info("waited enough time, doing the work now...")
          val visit = ExperimentFolderVisitor(tfo.experiment)
          val inputFile = Paths.get(visit.transformFolderPath.toString,
            tfo.srcTransformID, "uppercase.txt")

          import scala.collection.convert.wrapAsScala._
          val lines = Files.readAllLines(inputFile).toList.mkString("\n")
          val dup = lines + lines + lines + lines

          val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "duplicated.txt")

          Files.write(p, dup.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

          sender() ! WorkSuccessFull(s"text has been duplicated" :: Nil)
      }

    case GetTransfDefId(wi) ⇒
      log.debug(s"asking worker type for $wi")
      sender() ! TransformType(wi, transfDefId)

    case msg: Any ⇒ log.error(s"unable to deal with message: $msg")
  }
}


object WorkExecDuplicateText {
  val fullName = FullName("com.actelion.research.arcite.core", "duplicate-text")

  val transfDefId = TransformDefinitionIdentity(fullName, "dupText",
    TransformDescription("duplicate-text", "upper-cased-text", "duplicated"),
    dependsOn = Some(FullName("com.actelion.research.arcite.core", "to-uppercase")))

  val definition = TransformDefinition(transfDefId, props)

  def props(): Props = Props(classOf[WorkExecDuplicateText])

}
