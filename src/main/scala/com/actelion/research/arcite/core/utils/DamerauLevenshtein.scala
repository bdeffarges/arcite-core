package com.actelion.research.arcite.core.utils

import com.typesafe.scalalogging.LazyLogging

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
  * Created by Bernard Deffarges on 2017/05/08.
  *
  */
object DamerauLevenshtein extends LazyLogging {

  def distance(source: String, target: String): Int = {
    logger.info("levenshtein distance from %s to %s".format(source, target))

    def dist(source: String, target: String): Int = {
      val sourceAr = source.toCharArray
      val targetAr = target.toCharArray

      val sourceL = sourceAr.length
      val targetL = targetAr.length

      val score = Array.ofDim[Int](sourceL + 2, targetL + 2)

      val max = sourceL + targetL // max edit length, used to prevent transpositions for first characters

      score(0)(0) = max

      (0 to sourceL).foreach(i => {
        score(i + 1)(0) = max
        score(i + 1)(1) = i
      })

      (0 to targetL).foreach(j => {
        score(0)(j + 1) = max
        score(1)(j + 1) = j
      })

      import scala.collection.mutable.Map

      val lastSourceCharPos = Map.empty ++ sourceAr.map(c => c -> 0) ++ targetAr.map(c => c -> 0)

      for (i <- 1 to sourceL) {
        var lastTargetCharMatch = 0

        for (j <- 1 to targetL) {
          val lastSourceCharMatch = lastSourceCharPos(targetAr(j - 1))

          if (sourceAr(i - 1) == targetAr(j - 1)) {
            score(i + 1)(j + 1) = score(i)(j)
          } else {
            score(i + 1)(j + 1) = math.min(score(i)(j), math.min(score(i + 1)(j), score(i)(j + 1))) + 1
          }

          val transpCost = (i - lastSourceCharMatch - 1) + 1 + (j - lastTargetCharMatch - 1) // assuming between transpose only del+ins

          score(i + 1)(j + 1) = math.min(score(i + 1)(j + 1), score(lastSourceCharMatch)(lastTargetCharMatch) + transpCost)

          if (sourceAr(i - 1) == targetAr(j - 1)) lastTargetCharMatch = j
        }

        lastSourceCharPos(sourceAr(i - 1)) = i
        //        logger.info("lastrowseen [%s]".format(lastSourceCharPos))
      }

      //      logger.info("score table:\n %s".format(scoreTableAsString(score, sourceAr, targetAr)))

      score(sourceL + 1)(targetL + 1)
    }

    if (source.isEmpty) {
      if (target.isEmpty) 0
      else target.length
    } else if (target.isEmpty) source.length
    else {
      dist(source, target)
    }
  }

  /**
    * is the damerau-lenvenshtein distance within the given distance
    *
    * @todo could be improved in breaking the loop once distance is reached
    * @param source
    * @param target
    * @param maxDistance
    * @return true if distance is below the distance
    */
  def distBelowThreshold(source: String, target: String, maxDistance: Int): Boolean = {
    val d = distance(source, target)
    //    logger.info("%s =d= %s = %s".format(source, target, d))

    d <= maxDistance
  }

  def acceptableDameLevenDist(source: String, target: String) = {
    val acceptable = math.min(source.length, target.length) / 7
    //    logger.info("acceptable damerau-levenshtein distance [%s] for [%s] and [%s]".format(acceptable, source, target))

    if (acceptable < 1) {
      source.equalsIgnoreCase(target)
    } else {
      val d = distance(source.toLowerCase, target.toLowerCase)
      d <= acceptable
    }
  }

  def scoreTableAsString(sct: Array[Array[Int]], src: Array[Char], tg: Array[Char]): String = {
    val sb = new StringBuilder

    sb ++= "\t\t"
    (0 until tg.length).foreach(i => {
      sb ++= "\t"
      sb += tg(i)
    })

    sb ++= "\n"

    (0 until sct.length).foreach(i => {
      if (i > 1) sb += src(i - 2)
      sb ++= "\t"

      (0 until sct(i).length).foreach(j => {
        sb ++= sct(i)(j).toString()
        sb ++= "\t"
      })
      sb ++= "\n"
    })

    sb.toString()
  }

}
