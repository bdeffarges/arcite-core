package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.TransformDefinitionIdentity
import com.actelion.research.arcite.core.utils.DamerauLevenshtein

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
  * Created by Bernard Deffarges on 2017/04/18.
  *
  */
class TransfDefHelpers(transformDefs: Set[TransformDefinitionIdentity]) {

  import DamerauLevenshtein._

  def findTransformers(search: String, maxResults: Int): List[TransformDefinitionIdentity] = {

    (transformDefs.find(_.fullName.shortName == search).toList ++
      transformDefs.find(_.fullName.name == search).toList ++
      transformDefs.find(_.fullName.shortName.toLowerCase == search.toLowerCase).toList ++
      transformDefs.find(_.fullName.name.toLowerCase == search.toLowerCase).toList ++
      transformDefs.filter(td ⇒ distBelowThreshold(td.fullName.shortName, search, 1)).toList ++
      transformDefs.filter(td ⇒ distBelowThreshold(td.fullName.name, search, 3)).toList ++
      transformDefs.filter(td ⇒ comp2String(td.fullName.shortName, search) > 0).toList ++
      transformDefs.filter(td ⇒ comp2String(td.fullName.name, search) > 0).toList ++
      transformDefs.filter(td ⇒ comp2String(td.fullName.organization, search) > 0).toList ++
      transformDefs.filter(td ⇒ comp2String(td.description.summary, search) > 0).toList ++
      transformDefs.filter(td ⇒ comp2String(td.description.consumes, search) > 0).toList ++
      transformDefs.filter(td ⇒ comp2String(td.description.produces, search) > 0).toList
      ).distinct.take(maxResults)
  }

  def comp2String(strg1: String, strg2: String): Int = {
    comp2StringList(strg1.toLowerCase() +: strg1.toLowerCase().split("(\\s|\\.|\\-|\\_)").toList,
      strg2.toLowerCase() +: strg2.toLowerCase().split("(\\s|\\.|\\-|\\_)").toList)
  }

  def comp2StringList(list1: List[String], list2: List[String]): Int = {
    val l2 = list2.map(_.toLowerCase)
    list1.map(s ⇒ if (l2.contains(s.toLowerCase)) 1 else 0).sum
  }
}
