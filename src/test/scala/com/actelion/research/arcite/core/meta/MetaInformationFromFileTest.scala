package com.actelion.research.arcite.core.meta

import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory

/**
  * Created by deffabe1 on 7/12/16.
  */
class MetaInformationFromFileTest  extends FlatSpec with Matchers {

  val logger = LoggerFactory.getLogger(getClass.getName)

  val testFiles = "./for_testing/some_raw_data/072363_D_GeneID_DNABack_BCLeft_20150612.txt"

  logger.debug(s"path to test files: $testFiles")

  "meta information from file " should "should parse the file and being able to return lines based on keys values " in {

    val metaInfo = MetaInformationFromFile.getMetaInfo(testFiles, separator = "\t")

    val matchingLine = metaInfo.getMatchingLines(Map(("%Meta Row", "1"), ("Meta Column", "1"),("Row", "1"), ("Col", "38")))

    logger.debug(matchingLine.mkString("\n\t"))

    matchingLine.size should equal(1)

    matchingLine.head(metaInfo.headerReverseMap("ID")) should equal("A_22_P00008389")
    matchingLine.head(metaInfo.headerReverseMap("GeneSymbol")) should equal("C16orf82")
  }

}

