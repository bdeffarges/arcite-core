package com.idorsia.research.arcite.core.utils

import scala.collection.immutable.Queue

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
  * Created by Bernard Deffarges on 2017/01/30.
  *
  */
class LoggingHelper(length: Int) {

  private var logs: Queue[LogEntry] = Queue.empty

  def addEntry(log: String) = logs = logs enqueue LogEntry(log) takeRight length

  def clear() = logs = Queue.empty

  override def toString: String = s"[${logs.mkString("\n")}]"
}

object LoggingHelper {
  def apply(length: Int = 20): LoggingHelper = new LoggingHelper(length)
}

case class LogEntry(log: String) {
  val time: String = getCurrentDateAsString

  override def toString: String = s"$time => $log"
}
