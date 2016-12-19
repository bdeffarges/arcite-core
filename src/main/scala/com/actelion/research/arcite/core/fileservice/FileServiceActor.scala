package com.actelion.research.arcite.core.fileservice

import java.io.File
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.fileservice.FileServiceActor.SourceInformation
import com.actelion.research.arcite.core.utils.{FileInformation, FileInformationWithSubFolder, FileVisitor}
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


    case GetExperimentFiles(fromExperiment, subFolder) ⇒

      fromExperiment match {
        case FromRawFolder(exp) ⇒
          sender() ! getFolderAndFiles(ExperimentFolderVisitor(exp).rawFolderPath, subFolder)
        case FromMetaFolder(exp) ⇒
          sender() ! getFolderAndFiles(ExperimentFolderVisitor(exp).metaFolderPath, subFolder)
      }


    case GetFilesFromSource(source, subFolder) ⇒
      val sourceF = sourceFolders.get(source)
      if (sourceF.isDefined) sender() ! getFolderAndFiles(sourceF.get.path, subFolder)
      else sender() ! FoundFoldersAndFiles(Set(), Set())


    case GetAllFilesWithRequester(fromExp, requester) ⇒

      val ev = ExperimentFolderVisitor(fromExp.fromExp.experiment)

      fromExp.fromExp match {
        case FromRawFolder(_) ⇒
          requester ! FolderFilesInformation(core.getFilesInformation(ev.userRawFolderPath))
        case FromMetaFolder(_) ⇒
          requester ! FolderFilesInformation(core.getFilesInformation(ev.userMetaFolderPath))
        case FromAllFolders(_) ⇒
          requester ! AllFilesInformation(rawFiles = core.getFilesInformation(ev.rawFolderPath),
            userRawFiles = core.getFilesInformation(ev.userRawFolderPath),
            metaFiles = core.getFilesInformation(ev.userMetaFolderPath))
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

  lazy val mounts: Option[Map[String, SourceInformation]] = {
    if (config.hasPath("arcite.mounts")) {
      Some(config.getConfigList("arcite.mounts").asScala
        .map(v ⇒ (v.getString("name"), SourceInformation(v.getString("name"), v.getString("description"),
          new File(v.getString("path")).toPath))).toMap)
    } else None
  }

  def props(): Props = Props(classOf[FileServiceActor], mounts)


  sealed trait FilesFromExperiment {
    def experiment: Experiment
  }

  case class FromRawFolder(experiment: Experiment) extends FilesFromExperiment

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

  case class GetFilesFromSource(sourceName: String, subFolder: List[String] = List())

  case object NothingFound

  case class GetExperimentFiles(source: FilesFromExperiment, subFolder: List[String] = List())

  case class FoundFoldersAndFiles(folders: Set[String], files: Set[FileInformation])

  case class GetAllFiles(fromExp: FilesFromExperiment)

  case class GetAllFilesWithRequester(getAllFiles: GetAllFiles, requester: ActorRef)

  case class FolderFilesInformation(files: Set[FileInformationWithSubFolder])

  case class AllFilesInformation(rawFiles: Set[FileInformationWithSubFolder] = Set(),
                                 userRawFiles: Set[FileInformationWithSubFolder] = Set(),
                                 metaFiles: Set[FileInformationWithSubFolder] = Set())


  private def getFolderAndFiles(sourceP: Path, subFolderPath: List[String]): FoundFoldersAndFiles = {

    val folder = subFolderPath.foldLeft(sourceP)((p, s) ⇒ p resolve s).toFile

    if (folder.exists()) {
      if (folder.isDirectory) {
        val subdirs = folder.listFiles.filter(f ⇒ f.isDirectory).map(_.getName).toSet

        val files = folder.listFiles.filter(f ⇒ f.isFile).map(FileVisitor(_).fileInformation).toSet
        FoundFoldersAndFiles(subdirs, files)
      } else {
        FoundFoldersAndFiles(Set(), Set(FileVisitor(folder).fileInformation))
      }
    } else {
      FoundFoldersAndFiles(Set(), Set())
    }
  }
}
