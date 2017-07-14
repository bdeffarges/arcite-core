package com.idorsia.research.arcite.core.search

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.idorsia.research.arcite.core.api.ArciteService.{SearchExperiments, SearchExperimentsWithReq}
import com.idorsia.research.arcite.core.experiments.{Condition, Experiment}
import com.idorsia.research.arcite.core.search.ArciteLuceneRamIndex._
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

/**
  * Created by Bernard Deffarges on 13/03/16.
  */
class ArciteLuceneRamIndex extends Actor with ActorLogging {

  val directory = new RAMDirectory

  private lazy val executors = Executors.newCachedThreadPool

  private lazy val indexReader = DirectoryReader.open(directory)
  private lazy val indexSearcher = new IndexSearcher(indexReader, executors)

  override def receive: Receive = {
    case IndexExperiment(exp) ⇒
      val indexWriter = new IndexWriter(directory,
        new IndexWriterConfig(ArciteAnalyzerFactory.perfieldAnalyzerWrapper))

      val d = new Document
      d.add(new TextField(luc_name, exp.name, Field.Store.NO))
      d.add(new TextField(luc_description, exp.description, Field.Store.NO))

      val content =
        s"""${exp.name} ${exp.description} ${exp.design.description}
           |${exp.design.samples.mkString(" ")} ${exp.owner}""".toLowerCase

      d.add(new TextField(luc_content, content, Field.Store.NO))
      d.add(new StringField(luc_uid, exp.uid.get, Field.Store.YES))
      indexWriter.addDocument(d)

      indexWriter.close()


    case RemoveFromIndex(exp) ⇒

      val indexWriter = new IndexWriter(directory,
        new IndexWriterConfig(ArciteAnalyzerFactory.perfieldAnalyzerWrapper))

      indexWriter.deleteDocuments(new TermQuery(new Term(luc_uid, exp.uid.get)))
      indexWriter.close()


    case se: SearchExperimentsWithReq ⇒
      val searchR = search(se.search.search, se.search.maxHits)
      sender() ! FoundExperimentsWithReq(searchR, se.forWhom)
  }

  /**
    * search strategy:
    * do not return too many results, but if possible always return at least one
    * start with exact names, then description, tags, organization, etc.
    *
    */

  import ArciteLuceneRamIndex._

  def search(input: String, maxHits: Int): FoundExperiments = {
    val ngramSearch = if (input.length > maxGram) input.substring(0, maxGram) else input
    log.info(s"search for: $ngramSearch")

    val results = scala.collection.mutable.MutableList[FoundExperiment]()

    def returnValue(res: scala.collection.mutable.MutableList[FoundExperiment]): FoundExperiments = {
      val res = results.take(maxHits).groupBy(a ⇒ a.digest)
        .map(b ⇒ FoundExperiment(b._1, b._2.map(c ⇒ c.where).mkString(", "), b._2.head.order)).toList.sortBy(fe ⇒ fe.order)

      FoundExperiments(res)
    }

    // search for name
    val nameHits1 = indexSearcher.search(new TermQuery(new Term(luc_name, input.toLowerCase)), maxHits)
    if (nameHits1.totalHits > 0) {
      log.debug(s"found ${nameHits1.totalHits} in name ")
      results ++= nameHits1.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_uid).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "name", 1)).toList

      if (results.size >= maxHits) return returnValue(results)
    }

    // search for description
    val descHit = indexSearcher.search(new TermQuery(new Term(luc_description, input.toLowerCase)), maxHits)
    if (descHit.totalHits > 0) {
      log.debug(s"found ${descHit.totalHits} in description ")
      results ++= descHit.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_uid).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "description", 2)).toList

      if (results.size >= maxHits) return returnValue(results)
    }

    // fuzzy and ngrams searching
    val fuzzyHits = indexSearcher.search(new FuzzyQuery(new Term(luc_content, ngramSearch.toLowerCase())), maxHits)
    if (fuzzyHits.totalHits > 0) {
      log.debug(s"found ${fuzzyHits.totalHits} in fuzzy lowercase ngrams search")
      results ++= fuzzyHits.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_uid).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "ngrams", 3)).toList
    }

     val res = returnValue(results)

    log.debug(s"total returned results = ${res.experiments.size}")

    res
  }
}

object ArciteLuceneRamIndex {
  val luc_name = "name"
  val luc_description = "description"
  val luc_uid = "uid"
  val luc_content = "content"

  val minGram = 3
  val maxGram = 10


  case class Highlights(highlights: List[String])

  // what was found in the object, needs to be improved
  sealed trait TalkToLuceneRamDir

  case class IndexExperiment(experiment: Experiment) extends TalkToLuceneRamDir

  case class RemoveFromIndex(experiment: Experiment) extends TalkToLuceneRamDir

  case class AddCondition(condition: Condition) extends TalkToLuceneRamDir

  case class Search(text: String) extends TalkToLuceneRamDir

  case class FoundExperiment(digest: String, where: String, order: Int)

  //todo where means in which field it was found.... useful?

  case class ReturnExperiment(exp: Experiment)

  //todo only for testing?
  case class FoundExperiments(experiments: List[FoundExperiment])
  case class FoundExperimentsWithReq(foundExps: FoundExperiments, req: ActorRef)

  // sends back the number of results
  case class SearchResult(size: Int)

  // todo add matched string... especially for ngrams

  case class IndexingCompletedForExp(exp: Experiment)

  // todo should only return hascode-digest

  case object IndexCompletedForThisTime

}

class NGramExperimentAnalyzer extends Analyzer {
  override def createComponents(fieldName: String): TokenStreamComponents = {
    new Analyzer.TokenStreamComponents(
      new NGramTokenizer(ArciteLuceneRamIndex.minGram, ArciteLuceneRamIndex.maxGram))
  }
}

object ArciteAnalyzerFactory {

  private val perFieldAnalyzer = scala.collection.mutable.Map[String, Analyzer]()

  private val whiteSpaceAnalyzer = new WhitespaceAnalyzer
  private val standardAnalyzer = new StandardAnalyzer
  private val nGramAnalyzer = new NGramExperimentAnalyzer

  import ArciteLuceneRamIndex._

  perFieldAnalyzer += ((luc_uid, whiteSpaceAnalyzer))
  perFieldAnalyzer += ((luc_name, standardAnalyzer))
  perFieldAnalyzer += ((luc_description, standardAnalyzer))
  perFieldAnalyzer += ((luc_content, nGramAnalyzer))

  // todo add all analyzer
  import scala.collection.JavaConverters._

  val perfieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(whiteSpaceAnalyzer, perFieldAnalyzer.toMap.asJava)

}