package com.idorsia.research.arcite.core

import java.nio.file.{Files, Path, StandardOpenOption}
import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

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
package object utils extends LazyLogging {

  val dateDefaultFormatter = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss")
  val dateDefaultFormatterMS = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss:SSS")

  val dateForFolderName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_S")

  lazy val defaultDate = dateDefaultFormatter.parse("2018/01/01-00:00:00")

  def getCurrentDateAsString(): String = dateDefaultFormatter.format(new Date())

  def getDateAsString(time: Long): String = dateDefaultFormatter.format(new Date(time))

  def getDateAsStringMS(time: Long): String = dateDefaultFormatterMS.format(new Date(time))

  def getDateAsStrg(date: Date): String = dateDefaultFormatter.format(date)

  def getDateForFolderName(): String = dateForFolderName.format(new Date())


  def getAsDate(date: String): Date = Try(dateDefaultFormatter.parse(date))
    .getOrElse({
      logger.warn("$date could not be parsed to a date, returning default date. ")
      defaultDate
    })

  def getAsDateMS(date: String): Date = Try(dateDefaultFormatterMS.parse(date))
    .getOrElse({
      logger.warn("$date could not be parsed to a date, returning default date. ")
      defaultDate
    })

  lazy val almostTenYearsAgo = new Date(System.currentTimeMillis() - (10 * 365 * 24 * 3600 * 1000L))

  lazy val almostTenYearsAgoAsString = dateDefaultFormatter.format(almostTenYearsAgo)

  def concatFiles(target: Path, files: Seq[Path]): Unit = {

    val outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.APPEND)

    files.foreach(f ⇒ Files.copy(f, outputStream))

    outputStream.close()
  }


  def getEnoughButNotTooMuchForUserInfo(stg: String, maxLength: Int = 400): String = {
    if (stg.length <= maxLength) {
      stg
    } else {
      stg.substring(0, maxLength / 2) +
        "..." +
        stg.substring(Math.max(maxLength / 2, stg.length - maxLength / 2), stg.length)
    }
  }
}
