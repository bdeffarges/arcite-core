package com.actelion.research.arcite.core.transftree

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging}

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory


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
  * Created by Bernard Deffarges on 2016/12/27.
  *
  */
class IndexAndSearchTofT extends Actor with ActorLogging {

  private val directory = new RAMDirectory

  private lazy val executors = Executors.newCachedThreadPool

  private lazy val indexReader = DirectoryReader.open(directory)

  private lazy val indexSearcher = new IndexSearcher(indexReader,executors)

  import IndexAndSearchTofT._

  override def receive: Receive = {

    case IndexToT(tot) ⇒
      val indexW = new IndexWriter(directory, new IndexWriterConfig(ToTAnalyzer.perfieldAnalyzerWrapper))

      val d = new Document
      d.add(new TextField(LUC_NAME, tot.name.name, Field.Store.NO))
      d.add(new TextField(LUC_ORGA, tot.name.organization, Field.Store.NO))
      d.add(new TextField(LUC_DESC, tot.description, Field.Store.NO))

      //todo should also include the description of the transforms
//      val content = tot.allNodes.map(n ⇒ n.transfDefUID).mkString(" ")
//      d.add(new TextField(LUC_CONTENT, content, Field.Store.NO))

      indexW.addDocument(d)
      indexW.close()
  }

}

object IndexAndSearchTofT {
  val LUC_DIGEST = "uid"
  val LUC_NAME = "name"
  val LUC_ORGA = "organization"
  val LUC_DESC = "description"
  val LUC_CONTENT = "content"

  case class IndexToT(tot: TreeOfTransformDefinition)

  case class RemoveToT(tot: TreeOfTransformDefinition)

  case class SarchToT(search: String, maxResults: Int = 2)

  case class FoundToT(results: Set[TreeOfTransformDefinition])
}

object ToTAnalyzer {

  private val whiteSpaceAnalyzer = new WhitespaceAnalyzer
  private val standardAnalyzer = new StandardAnalyzer

  import IndexAndSearchTofT._

  private val perFieldAnalyzer: Map[String, Analyzer] =
    Map(LUC_DIGEST -> whiteSpaceAnalyzer,LUC_NAME -> standardAnalyzer, LUC_DESC -> standardAnalyzer,
      LUC_ORGA -> standardAnalyzer, LUC_CONTENT -> standardAnalyzer)

  // todo add all analyzer
  import scala.collection.JavaConverters._

  val perfieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(whiteSpaceAnalyzer, perFieldAnalyzer.asJava)
}
