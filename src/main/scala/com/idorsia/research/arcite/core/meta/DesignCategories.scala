package com.idorsia.research.arcite.core.meta

import java.nio.file.{Files, Path}

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.{ArciteJSONProtocol, ExpJsonProto}
import com.idorsia.research.arcite.core.experiments.Experiment
import com.idorsia.research.arcite.core.meta.DesignCategories.{AllCategories, GetCategories, RebuildDesignCategories, SimpleCondition}
import com.typesafe.scalalogging.LazyLogging

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
  * Created by Bernard Deffarges on 2017/02/03.
  *
  */
class DesignCategories extends Actor with ExpJsonProto with ActorLogging {

  import scala.concurrent.duration._

  private var categories: Map[String, Set[SimpleCondition]] = Map.empty

  import context.dispatcher

  context.system.scheduler.schedule(10 seconds, 1 minutes) {
    context.actorOf(Props(classOf[CategoriesRebuilder])) ! RebuildDesignCategories
  }

  override def receive: Receive = {

    case GetCategories ⇒
      sender() ! AllCategories(categories)

    case AllCategories(allCats) ⇒
      categories = allCats
  }
}

class CategoriesRebuilder extends Actor with ExpJsonProto with ActorLogging {

  import spray.json._
  import scala.collection.convert.wrapAsScala._
  import DesignCategories._

  override def receive: Receive = {

    case RebuildDesignCategories ⇒
      val byCategories = getExpFiles(core.dataPath)
        .map(f ⇒ Files.readAllLines(f).toList.mkString("\n").parseJson.convertTo[Experiment])
        .map(_.design).flatMap(_.samples).flatMap(_.conditions).groupBy(_.category)
        .map(c ⇒ (c._1, c._2.map(co ⇒ SimpleCondition(co.name, co.description))))

      sender() ! AllCategories(byCategories)

  }

}

object DesignCategories extends LazyLogging {

  case object RebuildDesignCategories

  case object GetCategories

  case class SimpleCondition(name: String, description: String)

  case class AllCategories(categories: Map[String, Set[SimpleCondition]])

  def props(): Props = Props(classOf[DesignCategories])

  def getExpFiles(folder: Path): Set[Path] = {
//    logger.error(s"looking into $folder")
    val f = folder.toFile
    // todo that means that the exp. folder cannot be called raw, etc. needs therefore to be improved
    if (f.exists() && f.getName !="raw" && f.getName !="user_raw" && f.getName !="transforms" ) {
      val listFiles = folder.toFile.listFiles
      val files = listFiles.filter(_.isFile)
        .filter(_.getParent.endsWith("meta"))
        .filter(_.getName == "experiment").map(_.toPath).toSet

      val folders = listFiles.filter(_.isDirectory)

      files ++ folders.flatMap(f ⇒ getExpFiles(f.toPath))
    } else Set.empty
  }
}


