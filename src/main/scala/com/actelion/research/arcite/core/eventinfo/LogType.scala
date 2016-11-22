package com.actelion.research.arcite.core.eventinfo

import java.util.Date

import com.actelion.research.arcite.core.eventinfo.LogCategory.LogCategory
import com.actelion.research.arcite.core.eventinfo.LogType.LogType
import com.actelion.research.arcite.core.utils

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
  * Created by Bernard Deffarges on 2016/11/22.
  *
  */
object LogType extends scala.Enumeration {
  type LogType = Value
  val CREATED, UPDATED, TRANSFORM, UNKNOWN = Value
}

object LogCategory extends scala.Enumeration {
  type LogCategory = Value
  val SUCCESS, ERROR = Value
}

trait LogInfo

case class ExpLog(logType: LogType, logCategory: LogCategory, message: String,
                  uid: Option[String] = None, date: Date = new Date()) extends LogInfo {

  override def toString: String = {
    val uidStg = if (uid.isDefined) "\t$uid" else ""

    s"""${utils.getDateAsString(date.getTime)}
       |\t$logType\t$logCategory\t$message$uidStg""".stripMargin
  }
}

