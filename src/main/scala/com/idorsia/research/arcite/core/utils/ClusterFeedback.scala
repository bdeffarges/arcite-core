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
  * Created by Bernard Deffarges on 2017/04/12.
  *
  */
trait ClusterFeedback

case class SuccessFeedback(shortMessage: String = "Success",
                           detailedMessage: String = "") extends ClusterFeedback

case class ErrorFeedback(shortMessage: String = "Error",
                         detailedMessage: String = "", errorDetailedMessage: String = "") extends ClusterFeedback

sealed trait UnimportantMsg
case class AreYouThere(msg: String = "actor, are you there??") extends UnimportantMsg
case class ImThere(msg: String = "I'm listening.") extends UnimportantMsg