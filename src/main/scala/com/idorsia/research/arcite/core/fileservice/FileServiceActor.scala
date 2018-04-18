package com.idorsia.research.arcite.core.fileservice

import java.io.File
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.idorsia.research.arcite.core.fileservice.FileServiceActor.SourceInformation
import com.idorsia.research.arcite.core.publish.GlobalPublishActor.{GetAllGlobPublishedItems, GlobalPublishedItem, SearchGlobPublishedAsFInfo}
import com.idorsia.research.arcite.core.utils.{FileInformation, FileVisitor, FilesInformation}
import com.typesafe.config.ConfigFactory

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
  * Created by Bernard Deffarges on 2016/10/20.
  *
  * A user should be able to select files for different purposes:
  * -to select a meta files (e.g. annotations) that have been
  * uploaded to the meta folder
  * -to be able to select raw data files for the raw folder.
  * -to map a source mount as raw data source.
  *
  */
class FileServiceActor(mounts: Map[String, SourceInformation]) extends Actor with ActorLogging {

  import FileServiceActor._

  private var sourceFolders: Map[String, SourceInformation] = mounts + (GlobPublishedSource.name -> GlobPublishedSource)

  private val actSys = ConfigFactory.load().getConfig("experiments-manager").getString("akka.uri")

  private val gloPubSelect = s"${actSys}/user/arcite-services/global_publish" //todo global publish to be removed
  private val gloPubAct = context.actorSelection(ActorPath.fromString(gloPubSelect))
  log.info(s"****** connect event info actor [$gloPubSelect] actor: $gloPubAct")


  override def receive: Receive = {

    case sInfo: SourceInformation ⇒
      sourceFolders += ((sInfo.name, sInfo))


    case GetSourceFolders ⇒
      sender() ! SourceFoldersAsString(
        sourceFolders.values.map(si ⇒ (si.name, s"${si.description} (${si.path.toString})")).toMap)


    case GetFilesFromSource(source, subFolder) ⇒
      val sourceF = sourceFolders.get(source)
      if (sourceF.isDefined) {
        if (sourceF.get == GlobPublishedSource) {
          log.debug(s"{{#%} asking glob. publish actor ")
          gloPubAct forward SearchGlobPublishedAsFInfo(subFolder, 100)
        } else {
          val sinf = FilesInformation(FileVisitor
            .getFilesInformationOneLevel(sourceF.get.path, subFolder: _*).toSeq.sortBy(_.name))

          sender() ! sinf
        }

      } else sender() ! FilesInformation()


    case GetAllFiles(fromExp) ⇒
      log.debug(s"asking for files for experiment [${fromExp.experiment.uid}]")
      val ev = ExperimentFolderVisitor(fromExp.experiment)

      fromExp match {
        case FromUserRawFolder(_) ⇒
          val rawFilesInfo = FilesInformation(
            ev.userRawFolderPath.toFile.listFiles.map(f ⇒ FileVisitor(f).fileInformation))
          log.info(s"found ${rawFilesInfo.files.size} files...")
          sender() ! rawFilesInfo

        case FromRawFolder(_) ⇒
          val rawFilesInfo = FilesInformation(
            ev.rawFolderPath.toFile.listFiles.map(f ⇒ FileVisitor(f).fileInformation))
          log.info(s"found ${rawFilesInfo.files.size} files...")
          sender() ! rawFilesInfo

        case FromMetaFolder(_) ⇒
          val metaF = FileVisitor.getFilesInformation3(ev.userMetaFolderPath)
          log.info(s"found ${metaF.files.size} files...")
          sender() ! metaF

        case FromAllFolders(_) ⇒
          val allFiles = AllFilesInformation(rawFiles = FileVisitor.getFilesInformation(ev.rawFolderPath, false),
            userRawFiles = FileVisitor.getFilesInformation(ev.userRawFolderPath, false),
            metaFiles = FileVisitor.getFilesInformation(ev.userMetaFolderPath, false))

          val totFiles = allFiles.metaFiles.size + allFiles.rawFiles.size + allFiles.userRawFiles.size
          log.info(s"found $totFiles files...")

          sender() ! allFiles
      }


    case GetSourceFolder(source) ⇒
      val s = sourceFolders.get(source)
      if (s.isDefined) sender() ! s.get else sender() ! NothingFound


    case msg: Any ⇒
      log.error("Cannot process message. ")
  }
}


object FileServiceActor {
  private val config = ConfigFactory.load

  import scala.collection.JavaConverters._

  lazy val mounts: Map[String, SourceInformation] = {
    if (config.hasPath("arcite.mounts")) {
      config.getConfigList("arcite.mounts").asScala
        .map(v ⇒ (v.getString("name"), SourceInformation(v.getString("name"), v.getString("description"),
          new File(v.getString("path")).toPath))).toMap
    } else Map.empty
  }

  def props(): Props = Props(classOf[FileServiceActor], mounts)


  sealed trait FilesFromExperiment {
    def experiment: Experiment
  }

  case class FromRawFolder(experiment: Experiment) extends FilesFromExperiment

  case class FromUserRawFolder(experiment: Experiment) extends FilesFromExperiment

  case class FromMetaFolder(experiment: Experiment) extends FilesFromExperiment

  case class FromAllFolders(experiment: Experiment) extends FilesFromExperiment


  case class FromSourceFolder(name: String)

  case class SetSourceFolder(sourceInformation: SourceInformation)

  case object GetSourceFolders

  //todo maybe should only return those for which the mount is active and working

  case class SourceFoldersAsString(sourceFolders: Map[String, String])

  case class GetSourceFolder(name: String)

  case class SourceInformation(name: String, description: String, path: Path) {
    override def toString: String = s"$name, $description (${path.toString})"
  }

  object GlobPublishedSource extends SourceInformation("arcite published", "published with arcite", core.globalPublishPath)

  case class GetFilesFromSource(sourceName: String, subFolder: Seq[String] = Seq.empty)

  case object NothingFound

  case class GetAllFiles(fromExp: FilesFromExperiment)

  case class AllFilesInformation(rawFiles: Set[FileInformation] = Set.empty,
                                 userRawFiles: Set[FileInformation] = Set.empty,
                                 metaFiles: Set[FileInformation] = Set.empty)

}
