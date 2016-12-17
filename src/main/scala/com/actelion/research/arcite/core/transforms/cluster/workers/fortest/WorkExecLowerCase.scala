package com.actelion.research.arcite.core.transforms.cluster.workers.fortest


import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms._
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkSuccessFull
import com.actelion.research.arcite.core.transforms.cluster.{GetTransfDefId, TransformType}
import com.actelion.research.arcite.core.utils.FullName

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

class WorkExecLowerCase extends Actor with ActorLogging {

  import WorkExecLowerCase._

  def receive: Receive = {
    case t: Transform =>
      log.info(s"transformDef: ${t.transfDefName} defLight=$transfDefId")
      require(t.transfDefName == transfDefId.fullName)
      log.info("starting work but will wait for fake...")
      Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(100000))
      t.source match {
        case tfo: TransformSourceFromObject ⇒
          import spray.json.DefaultJsonProtocol._
          implicit val toLowerCaseJson = jsonFormat1(ToLowerCase)
          log.info("waited enough time, doing the work now...")
          val toBeTransformed = t.parameters.get.convertTo[ToLowerCase]
          val lowerCased = toBeTransformed.stgToLowerCase.toLowerCase()
          val p = Paths.get(TransformHelper(t).getTransformFolder().toString, "lowercase.txt")
          Files.write(p, lowerCased.getBytes(StandardCharsets.UTF_8), CREATE_NEW)
          sender() ! WorkSuccessFull("to lower case completed", p.getFileName.toString :: Nil)
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