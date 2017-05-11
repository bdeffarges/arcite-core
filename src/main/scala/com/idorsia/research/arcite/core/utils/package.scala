package com.idorsia.research.arcite.core

import java.text.SimpleDateFormat
import java.util.Date

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
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/10/11.
  *
  */
package object utils {

  val dateDefaultFormatter = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss")
  val dateDefaultFormatterMS = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss:SSS")

  val dateForFolderName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_S")


  def getCurrentDateAsString() = dateDefaultFormatter.format(new Date())


  def getDateAsString(time: Long) = dateDefaultFormatter.format(new Date(time))

  def getDateAsStringMS(time: Long) = dateDefaultFormatterMS.format(new Date(time))

  def getDateAsStrg(date: Date) = dateDefaultFormatter.format(date)

  def getAsDate(date: String) = dateDefaultFormatter.parse(date)

  def getAsDateMS(date: String) = dateDefaultFormatterMS.parse(date)


  def getDateForFolderName() = dateForFolderName.format(new Date())


  lazy val almostTenYearsAgo = new Date(System.currentTimeMillis() - (10 * 365 * 24 * 3600 * 1000L))

  lazy val almostTenYearsAgoAsString = dateDefaultFormatter.format(almostTenYearsAgo)
}
