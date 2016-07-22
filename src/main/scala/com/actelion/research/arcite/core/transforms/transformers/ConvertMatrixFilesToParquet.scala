package com.actelion.research.arcite.core.transforms.transformers

import java.io.File
import java.nio.file.Paths

import akka.actor.{Actor, ActorLogging}
import org.apache.spark.{SparkConf, SparkContext}
import org.slf4j.LoggerFactory

/**
  * Created by deffabe1 on 5/10/16.
  */
class ConvertMatrixFilesToParquet extends Actor with ActorLogging {

  import ConvertMatrixFilesToParquet._

  override def receive = {

    case cfs: ConvertFileSet ⇒
      val result = cfs.files.map(f ⇒ csvToParquet(f, Paths.get(cfs.targetFolder, new File(f).getName).toString, cfs.separator))

  }
}

object ConvertMatrixFilesToParquet {

  val logger = LoggerFactory.getLogger(getClass.getName)

  case class ConvertFileSet(files: Set[String], targetFolder: String, separator: String = ";")

  sealed trait ConversionOutcome

  case object ConversionSuccess extends ConversionOutcome

  case class ConversionFailed(reason: String) extends ConversionOutcome

  lazy val conf = {
    new SparkConf(false)
      .setMaster("local[*]")
      .setAppName("spark-convert-1")
      .set("spark.logConf", "true")
  }

  lazy val sc = SparkContext.getOrCreate(conf)

  val sqlContext = new org.apache.spark.sql.SQLContext(sc)

  /**
    * transform a csv file to a parquet structure, taking everything it finds in the file
    *
    * @param file
    * @param target
    * @return
    */
  def csvToParquet(file: String, target: String, separator: String = ";"): ConversionOutcome = {

    val df = sqlContext.read
      .format("com.databricks.spark.csv")
      .option("header", "true") // Use first line of all files as header
      .option("inferSchema", "true") // Automatically infer data types
      .option("delimiter", separator)
      .load(file)

    try {
      df.write.save(target) //todo  any exception raised??
    } catch {
      case ex: Exception ⇒
        val cause = s"problem while writting parquet file, exception was: $ex"
        logger.error(cause)
        return ConversionFailed(cause)
    }

    ConversionSuccess
  }

  def readParquetFile(file: String, groupBy: String) = {
    val df = sqlContext.read.parquet(file)
    val groupedByDate = df.groupBy(groupBy)
    df.show()
  }

  def main(args: Array[String]) {
//        csvToParquet("/media/deffabe1/DATA/catwalk/original/ALS007_results_corrected2.csv",
//          "/media/deffabe1/DATA/catwalk/processed/ALS007_results_corrected.parquet")
//        readParquetFile("/media/deffabe1/DATA/catwalk/processed/ALS007_results_corrected.parquet", "Date")

//        csvToParquet("/media/deffabe1/DATA/application_test/arcite/home_dir_structure/com/actelion/research/microarray/AMS0090/transforms/cbfc0cc1-76c6-415c-813c-db64af4986dd/normalized-matrix",
//          "/tmp/parquet-test", "\t")
//
    readParquetFile("/tmp/parquet-test", "Col")
  }

}

