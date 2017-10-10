package com.idorsia.research.arcite.core.publish

import com.idorsia.research.arcite.core
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils._
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.ngram.NGramTokenizer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, TextField}
import org.apache.lucene.index._
import org.apache.lucene.search._
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

  private lazy val directory = new RAMDirectory

  private lazy val executors = Executors.newCachedThreadPool

  private var globalPublished: Map[String, GlobalPublishedItem] = Map.empty

  private lazy val indexWriter = new IndexWriter(directory,
    new IndexWriterConfig(PublishedAnalyzerFactory.perfieldAnalyzerWrapper))


  private def readPublishFiles(): List[GlobalPublishedItem] = {
    import scala.collection.convert.wrapAsScala._

    val deleted = core.globalPublishPath.toFile.listFiles
      .filter(_.isFile).map(_.getName).filter(_.startsWith("rm_"))

    log.debug(s"deleted= ${deleted.mkString(",")}")

    core.globalPublishPath.toFile.listFiles.
      filter(_.isFile).filterNot(f ⇒ deleted.contains(f.getName) || deleted.contains(s"rm_${f.getName}"))
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
        log.error(s"could not parse... [$json] exception was [${exc.getMessage}]")
    }
    res
  }


  override def preStart(): Unit = {
    val allPub = readPublishFiles()
    globalPublished = allPub.map(gpi ⇒ gpi.uid -> gpi).toMap
    indexGlobPub(allPub: _*)
    log.info(s"there is a total of ${globalPublished.size} published global items.")
  }


  override def receive: Receive = {

    case PublishGlobalItem(gpL) ⇒
      val globalPublish = GlobalPublishedItem(gpL)

      Files.write(core.globalPublishPath resolve globalPublish.uid,
        globalPublish.toJson.prettyPrint.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

      globalPublished += globalPublish.uid -> globalPublish
      log.debug(s"adding a global publish ${globalPublish}, total published ${globalPublished.size}")

      indexGlobPub(globalPublish)

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
      sender() ! FoundGlobPubItems(search(searchGl.search.toLowerCase.trim, searchGl.maxHits))


    case GetAllGlobPublishedItems(maxHits) ⇒
      sender() ! FoundGlobPubItems(globalPublished.values.toList.sortBy(_.date).reverse.take(maxHits))


    case searchGl: SearchGlobPublishedAsFInfo ⇒
      log.debug(s"{33#ç} searching for published artifacts...search=[$searchGl]")
      val searchR = if (searchGl.search.nonEmpty) {
        search(searchGl.search.mkString(" ").toLowerCase, searchGl.maxHits)
          .map(_.globalPubInf)
      } else {
        globalPublished.values.toList.sortBy(_.date).reverse.take(searchGl.maxHits).map(_.globalPubInf)
      }

      val filesInfosFromSearch = searchR
        .flatMap(item ⇒ item.items.map(iz ⇒ (item.description, iz))).groupBy(a ⇒ a._1)
        .flatMap(b ⇒ b._2.zipWithIndex.map(c ⇒ (s"${b._1}${c._2}", c._1._2)))
        .map(item ⇒ FileInformation(item._2, item._1, FileVisitor.sizeOfFileIfItExists(item._2))).toSeq

      sender() ! FilesInformation(filesInfosFromSearch)


    case rm: RmGloPubItem ⇒
      val gp = globalPublished.get(rm.uid)
      if (gp.isDefined) {
        val uid = gp.get.uid
        log.debug(s"removing glob. pub. item [${uid}]")
        Files.write(core.globalPublishPath resolve s"rm_$uid",
          "__MARKED_AS_DELETED__".getBytes(StandardCharsets.UTF_8), CREATE_NEW)

        try {
          val indexWriter = new IndexWriter(directory,
            new IndexWriterConfig(PublishedAnalyzerFactory.perfieldAnalyzerWrapper))
          indexWriter.deleteDocuments(new Term(LUCENE_UID, uid))
          indexWriter.commit()
        } catch {
          case exc: Exception ⇒
            log.error(exc.getMessage)
        }

        globalPublished -= uid
        log.debug(s"removed a pub. glob. item, current total: ${globalPublished.size}")

        sender() ! GlobPubDeleted(s"published ${uid} removed")
      } else {
        sender() ! GlobPubError(s"could not find ${rm.uid} to be deleted")
      }

    case msg: Any ⇒
      log.debug(s"I'm the global publish actor and I don't know what to do with this message [$msg]... ")
  }

  def indexGlobPub(globPubItems: GlobalPublishedItem*): Unit = {
    try {

      globPubItems.foreach { glPub ⇒
        val glPL = glPub.globalPubInf
        val d = new Document
        d.add(new StringField(LUCENE_UID, glPub.uid, Field.Store.YES))
        d.add(new TextField(LUCENE_DESCRIPTION, glPL.description, Field.Store.NO))
        d.add(new TextField(LUCENE_USER, glPL.owner.person, Field.Store.NO))
        d.add(new TextField(LUCENE_ORGA, glPL.owner.organization, Field.Store.NO))

        val content =
          s"""${glPL.description} ${glPL.owner.organization} ${glPL.owner.person}
             |${glPL.items.mkString(" ")}""".toLowerCase

        d.add(new TextField(LUCENE_CONTENT, content, Field.Store.NO))
        indexWriter.addDocument(d)
        //        log.debug(s"indexed ${d}")
        //        log.debug(s"current number of docs in index: ${indexWriter.numDocs()}")
      }
      indexWriter.commit()

    } catch {
      case exc: Exception ⇒
        log.error(exc.getMessage)
    }
  }

  private def search(input: String, maxHits: Int): List[GlobalPublishedItem] = {
    val indexReader = DirectoryReader.open(directory)
    val indexSearcher = new IndexSearcher(indexReader, executors)

    //    log.debug(
    //      s"""searching for [$input], current published:
    //         |[${
    //        globalPublished.values
    //          .map(gp ⇒ s"[${gp.uid}/${gp.globalPubInf.description}]").mkString(",")
    //      }]""".stripMargin)

    var results: List[GlobalPublishedItem] = List.empty

    // search for description
    try {//todo remove?
      val descHit = indexSearcher.search(new TermQuery(new Term(LUCENE_DESCRIPTION, input.toLowerCase)), maxHits)
      if (descHit.totalHits > 0) {
        //        log.debug(s"found ${descHit.totalHits} in field description in RAM index")
        val found: List[GlobalPublishedItem] = descHit.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(LUCENE_UID).stringValue())
          .map(uuid ⇒ globalPublished.get(uuid)).filter(_.isDefined).map(_.get).toList
        log.debug(s"found in (description) index= ${found.mkString(",")}")
        results ++= found

        results = results.distinct
        if (results.size >= maxHits) {
          log.debug(s"total returned results = ${results.size}")
          return results.sortBy(_.date).reverse
        }
      }

      val ngramSearch = if (input.length > maxNGrams) input.substring(0, maxNGrams) else input
      log.info(s"ngrams, searching for: $ngramSearch")

      // fuzzy and ngrams searching
      val fuzzyHits = indexSearcher.search(new FuzzyQuery(new Term(LUCENE_CONTENT, ngramSearch.toLowerCase())), maxHits)
      if (fuzzyHits.totalHits > 0) {
        //        log.debug(s"found ${fuzzyHits.totalHits} in fuzzy lowercase ngrams search in content in RAM index")
        val foundUIDs = fuzzyHits.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(LUCENE_UID).stringValue())
        log.debug(s"foundUIDs: ${foundUIDs.mkString(",")}")
        val found: List[GlobalPublishedItem] = foundUIDs.map(uuid ⇒ globalPublished.get(uuid))
          .filter(_.isDefined).map(_.get).toList

        log.debug(s"found in (fuzzy) index= ${found.mkString(",")}")

        results ++= found
      }

      results = results.distinct
      log.debug(s"total returned results = ${results.size}")
    } catch {
      case exc: Exception ⇒
        log.error(s"error while searching... ${exc.getMessage}")
    }
    results.sortBy(_.date).reverse
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

  case class GlobalPublishedItemLight(description: String, items: Seq[String],
                                      owner: Owner = DefaultOwner.anonymous)


  case class GlobalPublishedItem(globalPubInf: GlobalPublishedItemLight,
                                 uid: String = UUID.randomUUID().toString,
                                 date: String = utils.getCurrentDateAsString())


  sealed trait GlobalPublishApi

  case class PublishGlobalItem(globalPublish: GlobalPublishedItemLight) extends GlobalPublishApi

  case class GetGlobalPublishedItem(uid: String) extends GlobalPublishApi

  case class GetAllGlobPublishedItems(maxHits: Int = 100) extends GlobalPublishApi

  case class SearchGlobalPublishedItems(search: String, maxHits: Int = 10) extends GlobalPublishApi

  //todo should check the permissions to delete
  case class RmGloPubItem(uid: String) extends GlobalPublishApi


  sealed trait PublishResponse

  case class FoundGlobPubItem(published: GlobalPublishedItem) extends PublishResponse

  case class FoundGlobPubItems(published: List[GlobalPublishedItem]) extends PublishResponse

  case class GlobPubSuccess(uid: String) extends PublishResponse

  case class GlobPubDeleted(uid: String) extends PublishResponse

  case class GlobPubError(error: String) extends PublishResponse

  case object DidNotFindGlobPubItem extends PublishResponse


  case class SearchGlobPublishedAsFInfo(search: Seq[String], maxHits: Int = 100)


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

    perFieldAnalyzer += ((LUCENE_DESCRIPTION, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_ORGA, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_USER, standardAnalyzer))
    perFieldAnalyzer += ((LUCENE_CONTENT, nGramAnalyzer))

    import scala.collection.JavaConverters._

    val perfieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(whiteSpaceAnalyzer, perFieldAnalyzer.toMap.asJava)
  }

}