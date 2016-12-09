package com.actelion.research.arcite.core.rawdata

import java.io.File
import java.nio.file.{FileSystemException, Path, Paths}

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props}
import akka.event.Logging
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.util.matching.Regex
import scala.concurrent.duration._

/**
  * Created by deffabe1 on 3/4/16.
  *
  * Usually a laboratory experiments produces a lot of raw data files that are stored in some location that can be
  * written by the laboratory equipment. Very often it's some PC to which the equipment is connected.
  * The raw data files can be of different formats: xml, csv, txt, images (jpeg, png, ...). Some of these files contain
  * the actual measurements, others are quality checks, etc. Often the data is duplicated (e.g. in text and in xml as well).
  *
  * The first task that arcite will complete is to copy the selected raw data files (those that you are interested
  * to process and to keep in arcite) into the arcite home folder under the unique study name. From there on, arcite will
  * not care anymore about the original raw data folder, it will do all its work with raw data starting from its
  * own raw data folder.
  *
  */
class TransferSelectedRawData(caller: ActorRef, targetRawFolder: Path) extends Actor with ActorLogging {

  import TransferSelectedRawData._
  import TransferSelectedRawFile._

  var counter = 0

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Escalate
    }

  override def receive: Receive = {

    case TransferFiles(files) ⇒
      counter = files.size
      files.keys.map(f ⇒ (f, context.actorOf(Props[TransferSelectedRawFile])))
        .foreach(x ⇒ x._2 ! TransferFile(x._1, files(x._1)))


    case TransferFolder(folder, regex, subfolder) ⇒

      var fileMap = mutable.Map[Path, Path]()

      def buildFileMap(folder: String, folderPrefix: String): Unit = {
        val files = new File(folder).listFiles.filter(_.isFile)
          .filter(f ⇒ regex.findFirstIn(f.getName).isDefined)

        fileMap ++= files.map(f ⇒ (f, targetRawFolder resolve folderPrefix resolve f.getName))
          .map(a ⇒ (a._1.toPath, a._2))

        if (subfolder) {
          new File(folder).listFiles().filter(_.isDirectory)
            .foreach(fo ⇒ buildFileMap(fo.getPath, folderPrefix + fo.getName + File.separator))
        }
      }

      buildFileMap(folder, "")

      //      log.debug(s"fileMap: \n $fileMap")

      self ! TransferFiles(fileMap.toMap)


    case TransferFilesToFolder(files, target) ⇒
      files.map(f ⇒
        (f._1, target resolve f._2, context.actorOf(Props[TransferSelectedRawFile])))
        .foreach(x ⇒ x._3 ! TransferFile(x._1.toPath, x._2))


    case FileTransferred ⇒
      counter -= 1
      if (counter == 0) caller ! FileTransferredSuccessfully


    case TransferFilesFromSourceToFolder(source, files, regex) ⇒
      log.debug("transferring data from a (usually mounted) source")

      var fileMap = Map[Path, Path]()

      def buildFileMap(file: Path, folderPrefix: Path): Unit = {


        val f = source resolve folderPrefix resolve file
        val fi = f.toFile
        log.debug(s"folderPrefix=[$folderPrefix] fileFolder=[$file] full path=[$f]")
        if (fi.isFile && regex.findFirstIn(fi.getName).isDefined) {
          fileMap += ((f, targetRawFolder resolve folderPrefix resolve file.getFileName))
        } else if (fi.isDirectory) {
          fi.listFiles.foreach(ff ⇒ buildFileMap(ff.toPath.getFileName, folderPrefix resolve file))
        }
      }

      files.foreach(f ⇒ buildFileMap(Paths.get(f), Paths.get("")))

      log.debug(s"${fileMap.size} files will be transferred. ")

      self ! TransferFiles(fileMap)


    case _: Any ⇒
      log.error("did not know what to do with recieved message...")
  }
}

object TransferSelectedRawData extends LazyLogging {

  case class TransferFiles(files: Map[Path, Path])

  case class TransferFolder(folder: String, regex: Regex, includeSubFolder: Boolean)

  case class TransferFilesToFolder(files: Map[File, String], target: Path)

  case class TransferFilesFromSourceToFolder(source: Path, files: List[String], regex: Regex)

  trait FileTransferFeedback

  case object FileTransferredSuccessfully extends FileTransferFeedback

  case object FileTransferInProgress extends FileTransferFeedback

  case class FileTransferredFailed(error: String) extends FileTransferFeedback


  def buildFileTransferMap(source: Path, files: List[Path], target: Path, regex: Regex) = {
//
//    var fileMap = Map[Path, Path]()
//
//    def buildFileMap(file: Path, folderPrefix: List[String]): Map[Path, List[String]] = {
//
//      val f = source resolve folderPrefix.mkString(File.separator) resolve file
//      val fi = f.toFile
//      logger.debug(s"folderPrefix=[$folderPrefix] fileFolder=[$file] full path=[$f]")
//
//      if (fi.isFile && regex.findFirstIn(fi.getName).isDefined) {
//        (f -> folderPrefix)
//      } else if (fi.isDirectory) {
//        fi.listFiles.foreach(ff ⇒ buildFileMap(ff.toPath.getFileName, folderPrefix resolve file))
//      }
//    }
//
//    files.foreach(f ⇒ buildFileMap(Paths.get(f), Paths.get("")))
//
//    logger.debug(s"${fileMap.size} files will be transferred. ")
  }

  def getCommonPath(files: List[Path]): Path = {
    var tree = HashTree[String]()
    files.map(p ⇒ p.)
  }
}


class TransferSelectedRawFile extends Actor {
  val logger = Logging(context.system, this)

  import TransferSelectedRawFile._
  import java.nio.file.StandardCopyOption.REPLACE_EXISTING
  import java.nio.file.Files.copy
  import java.nio.file.Paths.get

  override def receive = {
    case TransferFile(source, target) ⇒
      logger.debug(s"&3% transferring $source TO $target")

      val parFolder = target.toFile.getParentFile
      if (!parFolder.isDirectory) parFolder.mkdirs()

      copy(source, target, REPLACE_EXISTING)

      sender ! FileTransferred

      self ! PoisonPill
  }
}


object TransferSelectedRawFile {

  case class TransferFile(source: Path, target: Path)

  case object FileTransferred

}
