package com.actelion.research.arcite.core.search

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.IndexExperiment
import com.actelion.research.arcite.core.experiments.{Condition, Experiment}
import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex._
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.ngram.NGramTokenizer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{FuzzyQuery, IndexSearcher, TermQuery}
import org.apache.lucene.store.RAMDirectory

/**
  * Created by bernitu on 13/03/16.
  */
class ArciteLuceneRamIndex(actorRef: ActorRef) extends Actor {
  val log = Logging(context.system, this)

  val directory = new RAMDirectory

  lazy val executors = Executors.newCachedThreadPool
  lazy val indexReader = DirectoryReader.open(directory)
  lazy val indexSearcher = new IndexSearcher(indexReader, executors) //todo is that compatible with the actor model??

  override def receive = {
    case IndexExperiment(exp) ⇒
      val config = new IndexWriterConfig(ArciteAnalyzerFactory.perfieldAnalyzerWrapper)

      // todo move config and index writer somewhere else
      val indexWriter = new IndexWriter(directory, config)

      import ArciteLuceneRamIndex._

      val d = new Document
      d.add(new TextField(luc_name, exp.name, Field.Store.NO))
      d.add(new TextField(luc_description, exp.description, Field.Store.NO))
      val content = s"${exp.name} ${exp.description}" //todo add design and properties to index
      d.add(new TextField(luc_content, content, Field.Store.NO))
      d.add(new StringField(luc_digest, exp.digest, Field.Store.YES))
      indexWriter.addDocument(d)

      indexWriter.close() //todo when to close the writer?

      actorRef ! IndexingCompletedForExp(exp)


    case RemoveFromIndex(exp) ⇒
      val config = new IndexWriterConfig(ArciteAnalyzerFactory.perfieldAnalyzerWrapper)

      // todo move config and index writer somewhere else
      val indexWriter = new IndexWriter(directory, config)
      indexWriter.deleteDocuments(new TermQuery(new Term(luc_digest, exp.digest)))
      indexWriter.close() //todo when to close the writer?


    case Search(text) ⇒
      val searchR = search(text, 10)
      actorRef ! SearchResult(searchR.experiments.size)
      actorRef ! searchR // todo who should be informed
    //      sender() ! searchResult // todo who should be informed


    case s: SearchForXResultsWithRequester ⇒
      val searchR = search(s.searchForXResults.text, s.searchForXResults.size)
      sender() ! FoundExperimentsWithRequester(searchR, s.requester)
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
    log.debug(s"search for: $ngramSearch")

    val results = scala.collection.mutable.MutableList[FoundExperiment]()

    def returnValue(res: scala.collection.mutable.MutableList[FoundExperiment]) = {
      val res = results.take(maxHits).toList.distinct
      FoundExperiments(res)
    }

    // search for name
    val nameHits1 = indexSearcher.search(new TermQuery(new Term(luc_name, input.toLowerCase)), 10)
    if (nameHits1.totalHits > 0) {
      log.debug(s"found ${nameHits1.totalHits} in name ")
      results ++= nameHits1.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_digest).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "name")).toList

      if (results.size >= maxHits) return returnValue(results)
    }

    // search for description
    val descHit = indexSearcher.search(new TermQuery(new Term(luc_description, input.toLowerCase)), 10)
    if (descHit.totalHits > 0) {
      log.debug(s"found ${descHit.totalHits} in description ")
      results ++= descHit.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_digest).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "description")).toList
      if (results.size >= maxHits) return returnValue(results)
    }


    val fuzzyHits = indexSearcher.search(new FuzzyQuery(new Term(luc_content, ngramSearch.toLowerCase())), 10)
    if (fuzzyHits.totalHits > 0) {
      log.debug(s"found ${fuzzyHits.totalHits} in fuzzy lowercase ngrams search")
      results ++= fuzzyHits.scoreDocs.map(d ⇒ indexSearcher.doc(d.doc).getField(luc_digest).stringValue())
        .map(uuid ⇒ FoundExperiment(uuid, "ngrams")).toList
    }

    val res = returnValue(results)

    log.debug(s"total returned results = ${res.experiments.size}")

    res
  }
}

object ArciteLuceneRamIndex {
  val luc_name = "name"
  val luc_description = "description"
  val luc_digest = "digest"
  val luc_content = "content"
  // todo val luc...=

  val minGram = 3
  val maxGram = 8


  case class Highlights(highlights: List[String])

  // what was found in the object, needs to be improved
  sealed trait TalkToLuceneRamDir

  case class IndexExperiment(experiment: Experiment) extends TalkToLuceneRamDir

  case class RemoveFromIndex(experiment: Experiment) extends TalkToLuceneRamDir

  case class AddCondition(condition: Condition) extends TalkToLuceneRamDir

  case class Search(text: String) extends TalkToLuceneRamDir

  case class SearchForXResults(text: String, size: Int) extends TalkToLuceneRamDir



  case class SearchForXResultsWithRequester(searchForXResults: SearchForXResults, requester: ActorRef)

  case class FoundExperiment(digest: String, where: String)//todo where means in which field it was found.... useful?

  case class ReturnExperiment(exp: Experiment) //todo for testing so far

  case class FoundExperiments(experiments: List[FoundExperiment])

  case class FoundExperimentsWithRequester(foundExperiments: FoundExperiments, requester: ActorRef)

  // sends back the number of results
  case class SearchResult(size: Int)

  // todo add matched string... especially for ngrams

  case class IndexingCompletedForExp(exp: Experiment) // todo should only return hascode-digest

  case object IndexCompletedForThisTime

}

class NGramExperimentAnalyzer extends Analyzer {
  override def createComponents(fieldName: String): TokenStreamComponents = {
    new Analyzer.TokenStreamComponents(
      new NGramTokenizer(ArciteLuceneRamIndex.minGram, ArciteLuceneRamIndex.maxGram))
  }
}


object ArciteAnalyzerFactory {

  val perFieldAnalyzer = scala.collection.mutable.Map[String, Analyzer]()

  val whiteSpaceAnalyzer = new WhitespaceAnalyzer
  val englishAnalyzer = new EnglishAnalyzer
  val standardAnalyzer = new StandardAnalyzer
  val nGramAnalyzer = new NGramExperimentAnalyzer

  import ArciteLuceneRamIndex._

  perFieldAnalyzer += ((luc_digest, whiteSpaceAnalyzer))
  perFieldAnalyzer += ((luc_name, standardAnalyzer))
  perFieldAnalyzer += ((luc_description, englishAnalyzer))
  perFieldAnalyzer += ((luc_content, nGramAnalyzer))

  // todo add all analyzer

  import scala.collection.JavaConverters._

  val perfieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(whiteSpaceAnalyzer, perFieldAnalyzer.toMap.asJava)

}