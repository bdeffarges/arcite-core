package com.idorsia.research.arcite.core.meta

import com.typesafe.scalalogging.LazyLogging

import scala.io.Source

/**
  * Created by deffabe1 on 6/6/16.
  */
class MetaInformationFromFile(filePath: String, parsedFile: ParsedFile) extends LazyLogging {

  val headers: List[String] = parsedFile.header
  val lines: List[List[String]] = parsedFile.lines

  val headerMap: Map[Int,String] = headers.map(h ⇒ (headers.indexOf(h), h)).toMap
  val headerReverseMap: Map[String, Int] = headers.map(h ⇒ (h, headers.indexOf(h))).toMap

  logger.debug(s"headers=$headerReverseMap")
  logger.debug(s"line size=${lines.size}")

  /**
    * for a given list of keys (values in a line) give the lines where these keys/values appear.
    * @param keys
    * @return
    */
  def getMatchingLines(keys: Map[String, String]): List[List[String]] = {

    for {
      l <- lines
      if keys.keySet.forall(k ⇒ l(headerReverseMap(k)) == keys(k))
    } yield l
  }

}

object MetaInformationFromFile {

  def parseFile(filePath: String, separator: String): ParsedFile = {
    val source = Source.fromFile(filePath, "UTF-8").getLines().toList

    val header = source.head.split(separator).toList.map(_.trim)

    val lines = source.tail.map(l => l.split(separator).toList.map(_.trim))

    ParsedFile(header, lines)
  }

  def getMetaInfo(filePath: String, separator: String = ";"): MetaInformationFromFile = {

    val parsedFile = parseFile(filePath, separator)

    new MetaInformationFromFile(filePath, parsedFile)
  }
}

case class ParsedFile(header: List[String], lines: List[List[String]])



