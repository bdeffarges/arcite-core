package com.actelion.research.arcite.core.fileservice

import java.io.File
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.fileservice.FileServiceActor.SourceInformation
import com.actelion.research.arcite.core.utils.{FileInformation, FileInformationWithSubFolder, FileVisitor}
import com.typesafe.config.{Config, ConfigFactory}

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
  * Created by Bernard Deffarges on 2016/10/20.
  *
  * A user should be able to select files for different purposes:
  * -to select a meta files (e.g. annotations) that have been
  * uploaded to the meta folder
  * -to be able to select raw data files for the raw folder.
  * -to map a source mount as raw data source.
  *
  */
class FileServiceActor(mounts: Option[Map[String, SourceInformation]]) extends Actor with ActorLogging {

  import FileServiceActor._

  private var sourceFolders = Map[String, SourceInformation]() ++ mounts.getOrElse(Map())

  override def receive: Receive = {
    case sInfo: SourceInformation ⇒
      sourceFolders += ((sInfo.name, sInfo))

    case GetSourceFolders ⇒
      sender() ! SourceFoldersAsString(
        sourceFolders.values.map(si ⇒ (si.name, s"${si.description} (${si.path.toString})")).toMap)

    case GetFiles(rootFileLoc, subFolder) ⇒
      rootFileLoc match {
        case rfl: FromExperiment ⇒
          val ev = ExperimentFolderVisitor(rfl.experiment)
          rfl match {
            case r: FromRawFolder ⇒
              sender() ! getFolderAndFiles(ev.rawFolderPath, subFolder)
            case r: FromMetaFolder ⇒
              sender() ! getFolderAndFiles(ev.metaFolderPath, subFolder)
          }

        case rfl: FromSourceFolder ⇒
          val sourceF = sourceFolders.get(rfl.name)
          if (sourceF.isDefined) sender() ! getFolderAndFiles(sourceF.get.path, subFolder)
          else sender() ! FoundFiles(GetFiles(rootFileLoc, subFolder), Set(), Set())
      }

      def getFolderAndFiles(sourceP: Path, subFolderPath: List[String]): FoundFiles = {

        val folder = subFolderPath.foldLeft(sourceP)((p, s) ⇒ p resolve s).toFile

        if (folder.exists()) {
          if (folder.isDirectory) {
            val subdirs = folder.listFiles.filter(f ⇒ f.isDirectory).map(_.toString).toSet
            val files = folder.listFiles.filter(f ⇒ f.isFile).map(FileVisitor(_).fileInformation).toSet
            FoundFiles(GetFiles(rootFileLoc, subFolderPath), subdirs, files)
          } else {
            FoundFiles(GetFiles(rootFileLoc, subFolderPath), Set(), Set(FileVisitor(folder).fileInformation))
          }
        } else {
          FoundFiles(GetFiles(rootFileLoc, subFolderPath), Set(), Set())
        }
      }

    case GetAllFilesWithRequester(fromExp, requester) ⇒

      val ev = ExperimentFolderVisitor(fromExp.fromExp.experiment)

      val path = fromExp.fromExp match {
        case r: FromRawFolder ⇒
          ev.userRawFolderPath
        case r: FromMetaFolder ⇒
          ev.userMetaFolderPath
      }
      val result = core.getFilesInformation(path.toFile)
      requester ! FolderFilesInformation(result)


    case msg: Any ⇒
      log.error("Cannot process message. ")
  }
}

object FileServiceActor {
  private val config = ConfigFactory.load

  import scala.collection.JavaConverters._

  lazy val mounts: Option[Map[String, SourceInformation]] = {
    if (config.hasPath("arcite.mounts")) {
      Some(config.getConfigList("arcite.mounts").asScala
          .map(v ⇒ (v.getString("name"), SourceInformation(v.getString("name"), v.getString("description"),
          new File(v.getString("path")).toPath))).toMap)
    } else None
  }

//  val ess = l.map(v ⇒ SourceInformation(v.getString("name"), v.getString("description"),
//    new File(v.getString("path")).toPath))

  def props(): Props = Props(classOf[FileServiceActor], mounts)

  sealed trait RootFileLocations

  sealed trait FromExperiment extends RootFileLocations {
    def experiment: Experiment
  }

  case class FromRawFolder(experiment: Experiment) extends FromExperiment

  case class FromMetaFolder(experiment: Experiment) extends FromExperiment

  case class FromSourceFolder(name: String) extends RootFileLocations

  case class SetSourceFolder(sourceInformation: SourceInformation)

  case object GetSourceFolders

  case class SourceFoldersAsString(sourceFolders: Map[String, String])

  case class SourceInformation(name: String, description: String, path: Path) {
    override def toString: String = s"$name, $description (${path.toString})"
  }

  case class GetFiles(rootLocation: RootFileLocations, subFolder: List[String] = List())

  case class FoundFiles(getFilesList: GetFiles, folders: Set[String], files: Set[FileInformation])

  case class GetAllFiles(fromExp: FromExperiment)

  case class GetAllFilesWithRequester(getAllFiles: GetAllFiles, requester: ActorRef)

  case class FolderFilesInformation(files: Set[FileInformationWithSubFolder])

}
