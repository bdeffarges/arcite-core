package com.idorsia.research.arcite.core.meta

import java.io.File

import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.LoggerFactory

/**
  * Created by deffabe1 on 7/12/16.
  */
class MetaInformationFromFileTest  extends FlatSpec with Matchers {

  val logger = LoggerFactory.getLogger(getClass.getName)

  val testFiles = "./for_testing/some_raw_data/072363_D_GeneID_DNABack_BCLeft_20150612.txt"

  logger.debug(s"path to test files: $testFiles")

  "meta information from file " should " parse the file and being able to return lines based on keys values " in {

    val metaInfo = MetaInformationFromFile.getMetaInfo(testFiles, separator = "\t")

    val matchingLine = metaInfo.getMatchingLines(Map(("%Meta Row", "1"), ("Meta Column", "1"),("Row", "1"), ("Col", "38")))

    logger.debug(matchingLine.mkString("\n\t"))

    matchingLine.size should equal(1)

    matchingLine.head(metaInfo.headerReverseMap("ID")) should equal("A_22_P00008389")
    matchingLine.head(metaInfo.headerReverseMap("GeneSymbol")) should equal("C16orf82")
  }

  "finding experiment files in a path " should " return the list of experiment files in a meta folder " in {
    val f1 = DesignCategories.getExpFiles(new File("./for_testing/find_meta_experiment").toPath)
    assert(f1.exists(_.endsWith("a/b/c/d/e/meta/experiment")))

    val f2 = DesignCategories.getExpFiles(new File("./for_testing/find_meta_experiment/empty").toPath)
    assert(f2.isEmpty)

    val f3 = DesignCategories.getExpFiles(new File("./for_testing/find_meta_experiment/fake").toPath)
    assert(f3.isEmpty)
  }
}


