package com.idorsia.research.arcite.core.eventinfo

import java.util.Date

import com.idorsia.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.idorsia.research.arcite.core.eventinfo.LogType.LogType
import com.idorsia.research.arcite.core.utils

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
  * Created by Bernard Deffarges on 2016/11/22.
  *
  */
object LogType extends scala.Enumeration {
  type LogType = Value
  val CREATED, UPDATED, TRANSFORM, TREE_OF_TRANSFORM, PUBLISHED, UNKNOWN = Value
}

object LogCategory extends scala.Enumeration {
  type LogCategory = Value
  val SUCCESS, WARNING, ERROR, INFO, UNKNOWN = Value
}

case class ExpLog(logType: LogType, logCat: LogCategory, message: String,
                  date: Date = new Date(), uid: Option[String] = None) {

  override def toString: String = {
    val uidStg = if (uid.isDefined) "\t$uid" else ""

    s"""${utils.getDateAsString(date.getTime)}
       |\t$logType\t$logCat\t$message$uidStg""".stripMargin
  }
}

object ExpLog {
  def apply(logType: LogType, logCat: LogCategory, message: String, uid: Option[String]) =
    new ExpLog(logType, logCat, message, new Date(), uid)
}

// todo could add keywords, domain, etc.
case class ArciteAppLog(category: LogCategory, message: String, date: Date = new Date())
