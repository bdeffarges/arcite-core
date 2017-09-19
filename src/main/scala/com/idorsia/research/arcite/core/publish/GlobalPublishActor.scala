package com.idorsia.research.arcite.core.publish

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props}
import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.api.ArciteService.DefaultSuccess
import com.idorsia.research.arcite.core.search.ArciteLuceneRamIndex.{luc_content, luc_description, luc_name, luc_uid}
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.Owner
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.ngram.NGramTokenizer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{FuzzyQuery, IndexSearcher, TermQuery}
import org.apache.lucene.store.RAMDirectory
import spray.json._

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
  * Created by Bernard Deffarges on 2017/09/15.
  *
  */
class GlobalPublishActor extends Actor with ActorLogging with ArciteJSONProtocol {

  import GlobalPublishActor._

  val directory = new RAMDirectory

  private lazy val executors = Executors.newCachedThreadPool

  private lazy val indexReader = DirectoryReader.open(directory)
  private lazy val indexSearcher = new IndexSearcher(indexReader, executors)

  private var globalPublished: Map[String, GlobalPublishedItem] = Map.empty

  private def readPublishFiles(): List[GlobalPublishedItem] = {
    import scala.collection.convert.wrapAsScala._

    val deleted = core.globalPublishPath.toFile.listFiles
      .filter(_.isFile).filter(_.getName.endsWith("_rm"))

    core.globalPublishPath.toFile.listFiles.
      filter(_.isFile).filterNot(deleted.contains)
      .map(f ⇒ Files.readAllLines(f.toPath, StandardCharsets.UTF_8).toList.mkString(" "))
      .map(st ⇒ parseToPublishedItem(st)).filter(_.isDefined).map(_.get).toSet[GlobalPublishedItem]
      .toList.sortBy(_.date)
  }

  private def parseToPublishedItem(json: String): Option[GlobalPublishedItem] = {
    var res: Option[GlobalPublishedItem] = None
    try {
      val r = json.parseJson.convertTo[GlobalPublishedItem]
      res = Some(r)
    } catch {
      case exc: Exception ⇒
        log.error(s"could not parse... $json")
    }
    res
  }

  override def preStart(): Unit = {
    val allPub = readPublishFiles()
    globalPublished = allPub.map(gpi ⇒ gpi.uid -> gpi).toMap
    allPub.foreach(p ⇒ self ! IndexItem(p))
  }


  override def receive: Receive = {

    case PublishGlobalItem(globalPublish) ⇒

      Files.write(core.globalPublishPath resolve globalPublish.uid,
        globalPublish.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      globalPublished += globalPublish.uid -> globalPublish

      self ! IndexItem(globalPublish)

      sender() ! GlobPubSuccess(globalPublish.uid)


    case GetGlobalPublishedItem(uid) ⇒
      log.info(s"asking for one precise published artifact.")
      val g = globalPublished.get(uid)
      if (g.isDefined) {
        sender() ! FoundGlobPubItem(g.get)
      } else {
        sender() ! DidNotFindGlobPubItem
      }


    case searchGl: SearchGlobalPublishedItems ⇒
      log.info(s"searching for published artifacts...search=[$searchGl]")
      sender() ! search(searchGl.search.toLowerCase.trim, searchGl.maxHits)


    case GetAllGlobPublishedItems(maxHits) ⇒
      sender() ! FoundGlobPubItems(globalPublished.take(maxHits).values.toSeq)


    case item: IndexItem ⇒
      val item.item = item.item
      val indexWriter = new IndexWriter(directory,
        new IndexWriterConfig(PublishedAnalyzerFactory.perfieldAnalyzerWrapper))

      val d = new Document
      d.add(new TextField(LUCENE_DESCRIPTION, item.item.globalPubInf.description, Field.Store.NO))
      d.add(new TextField(LUCENE_USER, item.item.globalPubInf.owner.person, Field.Store.NO))
      d.add(new TextField(LUCENE_ORGA, item.item.globalPubInf.owner.organization, Field.Store.NO))
      d.add(new StringField(LUCENE_UID, item.item.uid, Field.Store.YES))

      val content =
        s"""${item.item.globalPubInf.description} ${item.item.globalPubInf.owner}
           |${item.item.globalPubInf.items.mkString(" ")}""".toLowerCase

      d.add(new TextField(LUCENE_CONTENT, content, Field.Store.NO))
      indexWriter.addDocument(d)

      indexWriter.close()


    case rm: RmGloPubItem ⇒
      val gp = globalPublished.get(rm.uid)
      if (gp.isDefined) {
        Files.write(core.globalPublishPath resolve s"${gp.get.uid}_rm",
          "__MARKED_AS_DELETED__".getBytes(StandardCharsets.UTF_8), CREATE_NEW)

        val indexWriter = new IndexWriter(directory,
          new IndexWriterConfig(PublishedAnalyzerFactory.perfieldAnalyzerWrapper))
        indexWriter.deleteDocuments(new TermQuery(new Term(LUCENE_UID, rm.uid)))
        indexWriter.close()
        globalPublished -= rm.uid

        sender() ! GlobPubDeleted(s"published ${rm.uid} removed")
      } else {
        sender() ! GlobPubError(s"could not find ${rm.uid} to be deleted")
      }


    case msg: Any ⇒
      log.debug(s"I'm the global publish actor and I don't know what to do with this message [$msg]... ")
  }

  def search(input: String, maxHits: Int): FoundGlobPubItems = {
    val ngramSearch = if (input.length > maxNGrams) input.substring(0, maxNGrams) else input
    log.info(s"search for: $ngramSearch")

    var results: Seq[GlobalPublishedItem] = Seq.empty

    // search for description
    val descHit = indexSearcher.search(new TermQuery(new Term(LUCENE_DESCRIPTION, input.toLowerCase)), maxHits)
    if (descHit.totalHits > 0) {
      log.debug(s"found ${descHit.totalHits} in description ")
      results ++= descHit.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(LUCENE_UID).stringValue())
        .map(uuid ⇒ globalPublished.get(uuid)).filter(_.isDefined).map(_.get).toSeq

      if (results.size >= maxHits) return FoundGlobPubItems(results)
    }

    // fuzzy and ngrams searching
    val fuzzyHits = indexSearcher.search(new FuzzyQuery(new Term(luc_content, ngramSearch.toLowerCase())), maxHits)
    if (fuzzyHits.totalHits > 0) {
      log.debug(s"found ${fuzzyHits.totalHits} in fuzzy lowercase ngrams search")
      results ++= fuzzyHits.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_uid).stringValue())
        .map(uuid ⇒ globalPublished.get(uuid)).filter(_.isDefined).map(_.get).toSeq
    }

    log.debug(s"total returned results = ${results.size}")

    FoundGlobPubItems(results)
  }
}

object GlobalPublishActor {

  val maxNGrams = 12
  val minNGrams = 4

  val LUCENE_DESCRIPTION = "description"
  val LUCENE_USER = "user"
  val LUCENE_ORGA = "organization"
  val LUCENE_UID = "uid"
  val LUCENE_CONTENT = "content"

  case class GlobalPublishedItemLight(description: String, owner: Owner, items: Seq[String])

  case class GlobalPublishedItem(globalPubInf: GlobalPublishedItemLight,
                                 uid: String = UUID.randomUUID().toString,
                                 date: String = utils.getCurrentDateAsString())

  case class IndexItem(item: GlobalPublishedItem)


  sealed trait GlobalPublishApi

  case class PublishGlobalItem(globalPublish: GlobalPublishedItem) extends GlobalPublishApi

  case class GetGlobalPublishedItem(uid: String) extends GlobalPublishApi

  case class GetAllGlobPublishedItems(maxHits: Int = 100) extends GlobalPublishApi

  case class SearchGlobalPublishedItems(search: String, maxHits: Int = 10) extends GlobalPublishApi

  //todo should check the permissions to delete
  case class RmGloPubItem(uid: String) extends GlobalPublishApi


  sealed trait PublishResponse

  case class FoundGlobPubItem(published: GlobalPublishedItem) extends PublishResponse

  case class FoundGlobPubItems(published: Seq[GlobalPublishedItem]) extends PublishResponse

  case class GlobPubSuccess(uid: String) extends PublishResponse

  case class GlobPubDeleted(uid: String) extends PublishResponse

  case class GlobPubError(error: String) extends PublishResponse

  case object DidNotFindGlobPubItem extends PublishResponse


  def props: Props = Props(classOf[GlobalPublishActor])

  class NGramPublishAnalyzer extends Analyzer {
    override def createComponents(fieldName: String): TokenStreamComponents = {
      new Analyzer.TokenStreamComponents(
        new NGramTokenizer(minNGrams, maxNGrams))
    }
  }

  object PublishedAnalyzerFactory {

    private val perFieldAnalyzer = scala.collection.mutable.Map[String, Analyzer]()

    private val whiteSpaceAnalyzer = new WhitespaceAnalyzer
    private val standardAnalyzer = new StandardAnalyzer
    private val nGramAnalyzer = new NGramPublishAnalyzer

    perFieldAnalyzer += ((LUCENE_UID, whiteSpaceAnalyzer))
    perFieldAnalyzer += ((LUCENE_DESCRIPTION, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_ORGA, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_USER, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_CONTENT, nGramAnalyzer))

    import scala.collection.JavaConverters._

    val perfieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(whiteSpaceAnalyzer, perFieldAnalyzer.toMap.asJava)
  }

}