package com.idorsia.research.arcite.core.utils

import java.util.Date

import com.idorsia.research.arcite.core.utils

/**
  * arcite-core
  *
  * Copyright (C) 2017 Idorsia Pharmaceuticals Ltd.
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
  * Created by Bernard Deffarges on 2017/06/20.
  *
  */

/**
  * While processing data in a transform we might need to save some log information to know
  * what has been done, what worked, what did not work to report all that to the calling part and
  * eventually to the user.
  * It might be useful in some cases to know how much time has been
  * spent on a given task.
  * Errors can also exist in successful activities, they just did not lead to the failure of the whole
  * activity. They should be considered as warnings from a user perspective.
  *
  */
trait ActivityLog {
  def name: String

  def log: String

  def error: String

  def started: Date

  def ended: Date

  def toStringShort: String
}

case class SuccessActivityLog(name: String = "", log: String = "completed successfully.",
                              error: String = "", started: Date = new Date(),
                              ended: Date = new Date()) extends ActivityLog {

  override def toString: String = {
    s"""* SUCCESS * $name,
       | started=${utils.getDateAsStrg(started)} / ended= ${utils.getDateAsStrg(ended)}
       | $log
       | $error
       | """.stripMargin
  }

  override def toStringShort: String = s"SUCCESS $name at ${utils.getDateAsStrg(ended)}"
}

case class FailedActivityLog(name: String = "", log: String = "failed.",
                             error: String = "failed.", started: Date = new Date(),
                             ended: Date = new Date()) extends ActivityLog {

  override def toString: String = {
    s"""* FAILED * $name,
       | started=${utils.getDateAsStrg(started)} / ended= ${utils.getDateAsStrg(ended)}
       | $log
       | $error
       | """.stripMargin
  }

  override def toStringShort: String = s"FAILED $name at ${utils.getDateAsStrg(ended)}"

}


