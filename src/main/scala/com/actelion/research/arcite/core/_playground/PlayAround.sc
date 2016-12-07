//import java.io.File
//import java.nio.file.{Path, Paths}
//
//import akka.http.javadsl.model.StatusCode
//import akka.http.scaladsl.marshalling.ToResponseMarshallable
//import akka.http.scaladsl.server.StandardRoute
import java.io.File

import com.typesafe.config.ConfigFactory

val config = ConfigFactory.parseFile(new File("/home/deffabe1/development/computbio/arc/arcite-core/src/main/resources/ubuntu_desktop.conf"))
  .getConfig("arcite")

import scala.collection.JavaConverters._
val l = config.getObjectList("mounts").asScala
val ess = l.flatMap(co ⇒ co.entrySet().asScala).map(es ⇒ (es.getKey, es.getValue.unwrapped.toString))
ess.foreach(println)


//import akka.http.scaladsl.server.StandardRoute
//import com.actelion.research.arcite.core.search.ArciteLuceneRamIndex.FoundExperiment

//import scala.sys.process.ProcessLogger
//import scala.xml.XML

//val rinput = Seq("/usr/bin/Rscript",
//  "/home/deffabe1/development/biostats/microarray_prod/prod/normalize_vsn_2ch.R",
//  "/media/deffabe1/DATA/application_test/arcite/home_dir_structure/com/actelion/research/microarray/AMS0090/transforms/6607b734-b2a3-481a-ba4d-b3ac0c53f553/file-list",
//"/media/deffabe1/DATA/application_test/arcite/home_dir_structure/com/actelion/research/microarray/AMS0090/transforms/3c4e79e9-f456-4896-ab57-8115870f5ca0/normalized-matrix]")
//
//val process = scala.sys.process.Process(rinput,
//  new File("/media/deffabe1/DATA/application_test/arcite/home_dir_structure/com/actelion/research/microarray/AMS0090/transforms/3c4e79e9-f456-4896-ab57-8115870f5ca0/"))
//
//val output = new StringBuilder
//val error = new StringBuilder


//val status = process.!(ProcessLogger(output append _, error append _))

//println(status)
//
//println(error)
//
//println(output)
//
//var a = Map("a" -> "hlll", "b" -> "hhhd", "c" -> "blkjdf")
//
//a += (("bd" , "dddw"))
//
//a += (("b", "sdafjlkdasfjlkdasf"))
//
////a -= "b"
////
////a -= "sdfa"
//
//println(a)
//
//
//java.lang.Long.numberOfLeadingZeros(0)
//java.lang.Long.numberOfLeadingZeros(1)
//java.lang.Long.numberOfLeadingZeros(2)
//java.lang.Long.numberOfLeadingZeros(3)
//java.lang.Long.numberOfLeadingZeros(4)
//java.lang.Long.numberOfLeadingZeros(1024)
//java.lang.Long.numberOfLeadingZeros(1025)
//java.lang.Long.numberOfLeadingZeros(Long.MaxValue)
//java.lang.Long.numberOfLeadingZeros(Long.MinValue)
//
//
//def sizeAsString(fileSize: Long): String = {
//  if (fileSize < 1024) return s"$fileSize B"
//  else {
//    val z = (63 - java.lang.Long.numberOfLeadingZeros(fileSize)) / 10
//    return s""" ${fileSize.toDouble / (1L << (z*10))} ${"KMGTPE"(z-1)}"""
//  }
//}
//
//sizeAsString(1023)
//sizeAsString(1024)
//sizeAsString(1025)
//sizeAsString(4096)
//sizeAsString(1024*1024)
//
//
//1L << 10
//
//10 >> 1L
//10 >> 1
//
//1.toBinaryString
//
//
//127.toBinaryString
//128.toBinaryString
//-128.toBinaryString
//-1.toBinaryString
//Int.MaxValue.toBinaryString
//-2.toBinaryString
//-3.toBinaryString
//Int.MinValue.toBinaryString
//


//val sl = List("hello", "world", "jupiter")
//val sl = List[String]()
//val sl = List[String]()

//val p = Paths.get("hello")
//
//val f = sl.foldLeft(p)((p, s) ⇒ p resolve s)

//val f1 = FoundExperiment("a", "www")
//val f2 = FoundExperiment("a", "hhh")
//val f22 = FoundExperiment("a", "hhdfh")
//val f3 = FoundExperiment("b", "iii")
//val f4 = FoundExperiment("c", "qer")
//
//val l = List(f1, f2, f22, f3, f4).groupBy(a ⇒ a.digest)
//  .map(b ⇒ FoundExperiment(b._1, b._2.map(c ⇒ c.where).mkString(" ")))

//val fileA = XML.load("/media/deffabe1/DATA/gobetween/Lf69f100f-c766-490f-9143-5e8e0c8cb7c9.xml")
//
//(fileA \\ "id").map { elt ⇒
//
//  println(elt)
//}


//def a(m: ⇒ ToResponseMarshallable): StandardRoute =  StandardRoute(_.complete(m))
//
//a("AAAA")


