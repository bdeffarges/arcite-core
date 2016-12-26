package com.actelion.research.arcite.core.publish

import com.actelion.research.arcite.core.publish.ArtifactType.ArtifactType

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
  * Created by Bernard Deffarges on 2016/12/22.
  *
  */

/**
  * describes what to find in a published area of an experiment.
  * It could in principle link to material from different transforms.
  *
  */
case class PublishedInfo(uid: String, name: String, description: String,
                         artifacts: Set[PublishedSelection])

case class PublishedSelection(transform: String, artifacts: Set[ArtifactItem])

case class ArtifactItem(name: String, artifactType: ArtifactType)


/**
  * the type of artifact that is produced by the published transform
  */
object ArtifactType extends scala.Enumeration {
  type ArtifactType = Value
  val PDF, WEB_APP, MATRIX = Value
}

