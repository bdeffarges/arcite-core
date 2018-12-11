package com.idorsia.research.arcite.core.shared

import com.idorsia.research.arcite.core.experiments.Sample
import com.idorsia.research.arcite.core.shared.Stars.Stars
import com.idorsia.research.arcite.core.utils.Owner

/**
  *
  * arcite-core
  *
  * Copyright (C) 2016 Karanar Software (B. Deffarges)
  * 38 rue Wilson, 68170 Rixheim, France
  *
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
  * Created by Bernard Deffarges on 2016/12/23.
  *
  */

sealed trait ShareExpEvent

case class ShareExperiment(uid: String, name: String, description: String = "", owner: Owner,
                           designDescription: String = "", sampleConditions: Set[Sample] = Set(),
                           properties: Map[String, String] = Map()) extends ShareExpEvent


case class SharedATransform(experiment: String, transform: String,
                            name: String, description: String) extends ShareExpEvent


case class SharedAPublished(experiment: String, published: String,
                            name: String, description: String) extends ShareExpEvent


case class RateExperiment(experiment: String, stars: Stars,
                          authors: Owner) extends ShareExpEvent


case class RateTransform(experiment: String, transform: String,
                         stars: Stars, authors: Owner) extends ShareExpEvent


case class RatePublished(experiment: String, published: String,
                         stars: Stars, authors: Owner) extends ShareExpEvent


case class NewShareEvent(shareExpEvent: ShareExpEvent, date: Long, uid: String)


case class BlockOfEvents(event: Set[NewShareEvent], previousBlock: String, previousPreviousBlock: String, digest: String)


object Stars extends scala.Enumeration {
  type Stars = Value
  val ONE, TWO, THREE, FOUR, FIVE = Value
}


