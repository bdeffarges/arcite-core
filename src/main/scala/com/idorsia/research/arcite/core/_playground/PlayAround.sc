import java.nio.charset.StandardCharsets
//import scala.sys.process.ProcessLogger
//import java.io.File
//import java.nio.file.{CopyOption, Files, Path}
//import scala.sys.process.Process
//import scala.sys.process._

List("hello", "blab", ".arcite").filterNot(_.startsWith("."))
//"hello world. hello mars ".trim.replaceAll("\\s", "_").replaceAll("\\.", "_")


//sealed trait HW
//
//case class HelloWorld(hw: String) extends HW
//
//case object HelloMars extends HW
//
//val set: Set[HW] = Set(HelloWorld("hhla"), HelloWorld("dfsaf"), HelloMars)
//
//set.count { case _: HelloWorld ⇒ true
//case _ ⇒ false
//}
//set.count(_ == HelloMars)

//val set = Set("sadf", "32424", "8435ll", "wwwwwww", "qeroiqwre", "llllll")
//
//set.withFilter(_.contains("ll"))




//val p1 = Process("find /home/deffabe1/development/computbio/arc/arcite-core/src -name *.scala -exec grep null {} ;") #| Process("xargs test -z") #&& Process("echo null-free") #|| Process("echo null detected")
//val p1 = Process("for tp in /tmp -name *.tmp") #&& Process("do echo $tp") #&& Process("done")
//val p1 = "find /var/log  -name *.log" #| "xargs grep 'CPU'"
//var a = List()
//Process("find /var/log -name *.log")  #| Process("xargs more")!!
//Process("""find /var/log -name *.log -exec more {} ;""") !!

//Process("find /home/deffabe1/development/computbio/arc/arcite-core/src -name *.scala -exec more {} ;") !!
//"find /var/log -name \\*.log" #| "xargs grep 'CPU'" ! ProcessLogger(st => println(st), st => println(st))

//Process(Seq("find","/var/log","-name", "\\*\\.log")) !!
//p1 ! ProcessLogger(st => println(st), st => println(st))
//println(a)


//import spray.json.{DefaultJsonProtocol, RootJsonFormat}
//val s = "Undetermined_S0_L001_R1_001.fastq.gz"
//val rgx = ".{3,50}\\_L\\d{1,5}\\_.{2,40}\\.fastq\\.gz".r
//rgx.findFirstIn(s)
//s.replaceAll("L001", "L_ALL")

//val s = "aaaaaa"
//println(s.substring(0, 300))

//import scala.collection.immutable.Queue
//def sizeToString(fileSize: Long): String = {
//  if (fileSize < 1024) s"$fileSize B"
//  else {
//    val z = (63 - java.lang.Long.numberOfLeadingZeros(fileSize)) / 10
//    val res = (fileSize.toDouble / (1L << (z * 10))).toInt
//    val uni = "KMGTPE" (z - 1)
//    s"""$res ${uni}B"""
//  }
//}
//
//println(sizeToString(1023))
//println(sizeToString(1024))
//println(sizeToString(1025))
//println(sizeToString(1000000000L))
//val s = "hello,world,earth,"
//println(s.split(',').length)
//val m = Map("a" -> "b", "c" -> "d", "e" -> "f", "g" -> "h")
//val l = List("c", "g")
//
//println(m - "a")
//println(m -- l)

//println(m  l)
//"\\S+(257236312158|257236312159)\\S+txt".r.findFirstIn("161125_br_257236312158_S01_GE2_1105_Oct12_1_3_MAGEML.txt")

//val m1 = Map("a" -> "aa", "b" -> "bb", "c" -> "cc", "d" -> "dd")
//val m2 = Map("a" -> "aaa", "b" -> "bbb", "c" -> "ccc", "d" -> "ddd")
//

//var q = Queue[String]()
//
//q = q enqueue "hello1" takeRight 2
//q = q enqueue "hello2" takeRight 2
//q = q enqueue "hello3" takeRight 2
//q = q enqueue "hello4" takeRight 2

//val s = "sdfsdaf"
//
//val t = `s`
//val f = s
//
//println(t == f)
//println(t eq f)

//val reg = "(.)+_published.json".r
//reg.findFirstIn("adsfjk234234_published.json")

//val reg = "(C|c)ombine(.{0,3})(C|c)ondition(.{0,2})".r
//reg.findFirstIn("CombinedConditions")
//reg.findFirstIn("Combined conditions")
//reg.findFirstIn("Combine condition")
//reg.findFirstIn("combine condition")
//reg.findFirstIn("combine conditions")
//reg.findFirstIn("combined conditions")
//reg.findFirstIn("aacombined_Conditionsddc")

//
//m1.zip(m2).map(a ⇒ ((a._1._2, a._2._2)))

//m1.map(a ⇒ (a._1 -> (a._2, m2.get(a._1))))

//"aa/bb/cc/DD/at.txt".split("aa/bb/cc/DD")(1)

//var v = Vector("aa")
//
//v = v :+ "bb"
//
//v = v :+ "aa"
//
//v = "cc" +: v
//
//var l: List[String] = List("aa")
//
//l = l :+ "bb"
//
//l = l :+ "aa"
//
//l = "cc" +: l
//
//l += "kk"
//
//l ++= List("aakk", "bbdd")

//l --= List("kk")
//
//println(l)


//".*[58|59].*\\.txt".r.findFirstIn("af2315923jkdd.txt")

//import scala.collection.immutable.Queue
//import java.io.File
//import java.nio.file.{Path, Paths}
//
//import akka.http.javadsl.model.StatusCode
//import akka.http.scaladsl.marshalling.ToResponseMarshallable
//import akka.http.scaladsl.server.StandardRoute

//".*".r.findFirstIn("hello")

//val a = Queue("a", "b", "c", "d", "e", "g", "h")
//val b = a enqueue "f"
//println(b dequeue)
//println(b drop 3)
//println(b dropRight  2)
//println(a splitAt 3)
//println(a take 6)
//println(a takeRight 6)


//import java.io.File
//
//import com.idorsia.research.arcite.core.fileservice.FileServiceActor.SourceInformation
//import com.typesafe.config.ConfigFactory
//
//val config = ConfigFactory.parseFile(new File("/home/deffabe1/development/computbio/arc/arcite-core/src/main/resources/ubuntu_desktop.conf"))
//  .getConfig("arcite")
//
//import scala.collection.JavaConverters._
//val l = config.getConfigList("mounts").asScala
//val ess = l.map(v ⇒ SourceInformation(v.getString("name"), v.getString("description"),
//    new File(v.getString("path")).toPath))
//
//ess.foreach(println)


//import akka.http.scaladsl.server.StandardRoute
//import com.idorsia.research.arcite.core.search.ArciteLuceneRamIndex.FoundExperiment

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

//val s: Seq[String] = "-d adf +dd".split("\\s")
//
//val s1 = ("multiqc" +: s).toList
//
//s1 ++ (Seq("elle", "adfa", "sadf") :+ "--outdir" :+ "Blalb")
//"   ".trim.split("\\s").map(_.trim).filter(_.nonEmpty).length

//"\\d+".r.pattern.matcher("333").matches()
//
//val m : Map[String, String] = Map("helle" -> "34")
//
//"\\d+".r.pattern.matcher(m.getOrElse("hellea", "")).matches()

//val rgx = ".{3,50}\\_L\\d{1,5}\\_.{2,40}\\.fastq\\.gz".r
//
//rgx.findFirstIn("Undetermined_S0_L004_R1_001.fastq.gz").fold("undefined.fastq.gz")(_.replaceAll("L(\\d{3,4})", "L_ALL"))
//rgx.findFirstIn("Undetermined_S0_L001_R1_001.fastq.gz").fold("undefined.fastq.gz")(_.replaceAll("L(\\d{3,4})", "L_ALL"))
//rgx.findFirstIn("Undetermined_S0_L0002_R1_001.fastq.gz").fold("undefined.fastq.gz")(_.replaceAll("L(\\d{3,4})", "L_ALL"))
//rgx.findFirstIn("Undetermined_S0_L00043_R1_001.fastq.gz").fold("undefined.fastq.gz")(_.replaceAll("L(\\d{3,4})", "L_ALL"))


//println(System.getProperty())

//val a = Option(null)
//println(a)
//println(a.isDefined)
//
//val xs = Seq(Seq("a", "b","c"),Seq("d","e","f"), Seq("g","h"),Seq("i","j","k"))
//
//val ys = for (Seq(x,y,z) <- xs) yield x+y+z
//
////val zs = xs map{case Seq(x,y,z) => x+y+z}
//
////val zz = xs withFilter {case Seq(x,y,z) ⇒ true ; case _ ⇒ false} map {case Seq(x,y,z) ⇒ x+y+z }
//val zz = xs withFilter {case Seq(x,y,z) ⇒ true ; case _ ⇒ false}
//
//val s = Option(System.getProperty("HOST")) getOrElse "hello"
//println(s)

//case class Person(name: String)
//
//object Person {
//  def getPerson(firstName: String, lastName: String): Person = new Person(firstName + " " + lastName)
//
//  def apply(name: String): Person = new Person(name)
//
//  def unapply(arg: Person): Option[String] = Some(arg.name)
//}
//
//class Tojson extends DefaultJsonProtocol {
//  implicit val pjson: RootJsonFormat[Person] = jsonFormat1(Person.apply)
//}
//
//def cTest() = {
//
//  def copyRecursively(source: Path, destination: Path): Unit = {
//    val srcF = source.toFile
//    val destF = destination.toFile
//    if (srcF.isFile) {
//      Files.copy(source, destination)
//    } else {
//      if (!destF.exists()) destF.mkdirs()
//      source.toFile.listFiles
//        .foreach(s ⇒ copyRecursively(s.toPath, destination resolve s.getName))
//    }
//  }
//
//  copyRecursively(new File("/home/deffabe1/development/computbio/arc/arcite-core/for_testing/find_files").toPath,
//    new File ("/home/deffabe1/development/computbio/arc/arcite-core/for_testing/find_files1").toPath)
//}

//cTest()

