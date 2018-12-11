package com.idorsia.research.arcite.core.publish

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.Files
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.idorsia.research.arcite.core.CommonMsg.DefaultSuccess
import com.idorsia.research.arcite.core.api.TransfJsonProto
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.idorsia.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.idorsia.research.arcite.core.utils
import spray.json._

import scala.collection.convert.wrapAsScala._

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
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
  * Created by Bernard Deffarges on 2017/02/20.
  *
  */
class PublishActor(eventInfoAct: ActorRef) extends Actor with ActorLogging with TransfJsonProto {

  import PublishActor._

  override def receive: Receive = {

    case GetPublished4Exp(experiment) ⇒
      log.info(s"asking for published artifacts for experiment: ${experiment.name}")
      val visit = ExperimentFolderVisitor(experiment)
      val allFiles = visit.publishedFolderPath.toFile.listFiles()

      val removed = allFiles.filter(_.getName.endsWith(ExperimentFolderVisitor.publishedRemovedFileExtension))
        .map(f ⇒ f.getName.substring(0, f.getName.length - ExperimentFolderVisitor.publishedRemovedFileExtension.length))

      val published = allFiles.filter(_.getName.endsWith(ExperimentFolderVisitor.publishedFileExtension))
        .filterNot(f ⇒ removed.exists(s ⇒ f.getName.contains(s)))
        .map(f ⇒ Files.readAllLines(f.toPath).mkString(" ").parseJson.convertTo[PublishedInfo])
        .toList.sortBy(pi ⇒ utils.getAsDate(pi.date).getTime)(Ordering.Long.reverse)

      sender() ! Published(published)


    case pi: PublishInfo4Exp ⇒
      log.info(s"publishing artifact for experiment ${pi.exp.name}")
      val pubInfo = PublishedInfo(pi.publish)

      val file = ExperimentFolderVisitor(pi.exp).publishedFolderPath
        .resolve(s"${pubInfo.uid}${ExperimentFolderVisitor.publishedFileExtension}")

      Files.write(file, pubInfo.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      sender() ! ArtifactPublished(pubInfo.uid)

      eventInfoAct ! AddLog(pi.exp, ExpLog(LogType.PUBLISHED, LogCategory.SUCCESS, s"published a transform result: ${pubInfo}"))


    case rmPub: RemovePublished4Exp ⇒
      log.info(s"removing published message ${rmPub.uid} from experiment ${rmPub.exp.name}")
      val file = ExperimentFolderVisitor(rmPub.exp).publishedFolderPath
        .resolve(s"${rmPub.uid}${ExperimentFolderVisitor.publishedRemovedFileExtension}")

      if (!file.toFile.exists()) {
        Files.write(file, "removed".getBytes(StandardCharsets.UTF_8), CREATE_NEW)
        eventInfoAct ! AddLog(rmPub.exp, ExpLog(LogType.PUBLISHED, LogCategory.SUCCESS, s"removed published: ${rmPub.uid}"))
      }

      sender() ! DefaultSuccess(s"published ${rmPub.uid} removed")


    case msg: Any ⇒
      log.debug(s"I'm the publish actor and I don't know what to do with this message [$msg]... ")
  }
}

object PublishActor {

  case class PublishInfoLight(transform: String, description: String, artifacts: List[String])


  sealed trait PublishApi {
    def exp: String
  }

  case class PublishInfo(exp: String, transform: String, description: String, artifacts: List[String]) extends PublishApi

  case class GetPublished(exp: String) extends PublishApi

  case class RemovePublished(exp: String, uid: String) extends PublishApi


  case class Published(published: List[PublishedInfo])

  case class PublishedInfo(pubInfo: PublishInfo,
                           uid: String = UUID.randomUUID().toString,
                           date: String = utils.getCurrentDateAsString())


  sealed trait PublishFeedback

  case class ArtifactPublished(uid: String) extends PublishFeedback

  case class ArtifactPublishedFailed(reason: String) extends PublishFeedback



  sealed trait PublishActorApi {
    def exp: Experiment
  }

  case class GetPublished4Exp(exp: Experiment) extends PublishActorApi

  case class PublishInfo4Exp(exp: Experiment, publish: PublishInfo) extends PublishActorApi

  case class RemovePublished4Exp(exp: Experiment, uid: String,
                                 date: String = utils.getCurrentDateAsString()) extends PublishActorApi

}


