package com.actelion.research.arcite.core.fileservice

import java.nio.file.Path

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.experiments.Experiment
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
class FileServiceActor(config: Config) extends Actor with ActorLogging {


  override def receive = {

  }


}

object FileServiceActor {
  val config = ConfigFactory.load

  def props(): Props = Props(classOf[FileServiceActor], config)

  sealed trait RootFileLocations
  case class RawFolder(experiment: Experiment) extends RootFileLocations
  case class MetaFolder(experiment: Experiment) extends RootFileLocations
  case class SourceFolder(name: String, fullPath: Path) extends RootFileLocations

  case class FileInformation(name: String, )
  case class GetFilesList(rootLocation: RootFileLocations, subFolder: List[String] = List())

  case class FoundFiles(getFilesList: GetFilesList, folders: Set[String], files: Set[String])
}
