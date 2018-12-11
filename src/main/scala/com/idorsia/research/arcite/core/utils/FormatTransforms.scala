package com.idorsia.research.arcite.core.utils

/**
  * Created by deffabe1 on 6/30/16.
  */
class FormatTransforms {

}


import java.io.FileWriter

import scala.io.Source

/**
  * Created by deffabe1 on 6/30/16.
  *
  * An ultra simple matrix to json transformer:
  * builds an array of json objects from all rows, using header line element as variable names
  * Writes the result to a file.
  */
object MatrixToJson {

  def toJson(matrixFile: String, targetFile: String, sep: String = ";") = {
    var cheaders: List[String] = null

    var firstLine = true
    var counter = 0

    var counterWrong = 0

    val fw = new FileWriter(targetFile)
    //    fw.write("{\"results\" : [")
    fw.write("[")

    for (l <- Source.fromFile(matrixFile).getLines()) {
      val line = l.split(sep).toList

      if (firstLine) {
        cheaders = line
        firstLine = false
      } else {
        if (line.size != cheaders.size) counterWrong += 1
        else {
          val stg = cheaders.zip(line).map(a => s""" "${a._1}" : "${a._2}" """).mkString(",")
          if (counter > 0) fw.write(",")
          fw.write(s"{ $stg }")
          counter += 1
        }
      }
    }

    fw.write("]")
    //    fw.write("]}")
    fw.close()
  }
}


object ToJson {

  def matrixToJson(objectName: String, headers: List[String], rows: List[List[String]]): String = {

    //    import org.json4s.JsonDSL._
    //    import org.json4s.jackson.JsonMethods._
    //
    //    val json = objectName -> rows.map { r ⇒
    //      headers.zip(r).map(a ⇒ a._1 -> a._2).reduceLeft(_~_) todo this piece does not work as I expected...
    //    }
    //
    //    pretty(render(json))

    val rws = rows.map { r ⇒
      val l = headers.zip(r).map(a => s""" "${a._1}" : "${a._2}" """).mkString(", ")
      s"{ $l }"
    }.mkString(",\n")
    val json = s"""{$objectName: [$rws]}"""

    json
  }

  def matrixFileToJsonFile(file: String, target: String, sep: String): Unit = {
    val f = Source.fromFile(file, "UTF-8").getLines().toList
    val hs = f.head.split(sep).toList
    val lines = f.tail.map(l ⇒ l.split(sep).toList)

    val json = matrixToJson(hs, lines)

    val fw = new FileWriter(target)

    fw.write(json)
    fw.close()
  }

  def matrixToJson(headers: List[String], rows: List[List[String]]): String = {

    val rws = rows.map { r ⇒
      val l = headers.zip(r).map(a => s""" "${a._1}" : "${a._2}" """).mkString(", ")
      s"{ $l }"
    }.mkString(",\n")
    val json = s"""[$rws]"""

    json
  }

  def main(args: Array[String]) {
    println(matrixToJson("hello", List("a", "b", "c"), List(List("zzz", "yyy", "www"), List("hhh", "uuu", "ttt"))))
    println(matrixToJson(List("a", "b", "c"), List(List("zzz", "yyy", "www"), List("hhh", "uuu", "ttt"))))
  }
}