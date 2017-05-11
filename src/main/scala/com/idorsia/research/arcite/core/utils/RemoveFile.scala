package com.idorsia.research.arcite.core.utils

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
  * Created by Bernard Deffarges on 2016/12/13.
  *
  */

sealed trait RemoveFile {
  def exp: String
  def fileName: String
}

case class RemoveUploadedRawFile(exp: String, fileName: String) extends RemoveFile
case class RemoveUploadedMetaFile(exp: String, fileName: String) extends RemoveFile

case class RmFile(fileName: String)

sealed trait RemoveFileFeedback

case object RemoveFileSuccess extends RemoveFileFeedback

case class FailedRemovingFile(error: String) extends RemoveFileFeedback
