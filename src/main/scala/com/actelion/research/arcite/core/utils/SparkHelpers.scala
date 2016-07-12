package com.actelion.research.arcite.core.utils

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import org.apache.spark.sql.{Column, DataFrame}


/**
  * Created by deffabe1 on 6/28/16.
  */
class SparkHelpers {

}

object SparkHelpers {

  def saveDfToCsv(df: DataFrame, outputFile: Path, sep: String = ";", header: Boolean = true) = {
    val tmpParquetDir = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString).toString

    df.repartition(1).write
      .format("com.databricks.spark.csv")
      .option("header", header.toString)
      .option("delimiter", sep)
      .save(tmpParquetDir)

    Files.move(Paths.get(tmpParquetDir, "part-00000"), outputFile)

    val dir = new File(tmpParquetDir)
    dir.listFiles.foreach(f => f.delete)
    dir.delete
  }

  def dropMultipleCols(df: DataFrame, cols: Column*): DataFrame = {

    def dropCols(df: DataFrame, cs: List[Column]): DataFrame = cs match {
      case Nil ⇒ df
      case h :: l ⇒ dropCols(df.drop(h), l)
    }

    dropCols(df, cols.toList)
  }
}
