package com.actelion.research.arcite.core.rawdata

import java.io.File
import java.nio.file.{FileSystemException, Path, Paths}

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props}
import akka.event.Logging

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
class TransferSelectedRawData(caller: ActorRef, targetRawFolder: String) extends Actor {
  val log = Logging(context.system, this)

  import TransferSelectedRawData._
  import TransferSelectedRawFile._

  var counter = 0 //todo right?? depends who owns the actor...

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: FileSystemException     => Restart
      case _: Exception                => Escalate
    }

  override def receive: Receive = {

    case TransferFiles(files) ⇒
      counter = files.size
      files.keys.map(f ⇒ (f, context.actorOf(Props[TransferSelectedRawFile])))
        .foreach(x ⇒ x._2 ! TransferFile(x._1, files(x._1)))

    case TransferFolder(folder, regex, subfolder) ⇒

      var fileMap = mutable.Map[String, String]()

      def buildFileMap(folder: String, folderPrefix: String): Unit = {
        val files = new File(folder).listFiles.filter(_.isFile)
          .filter(f ⇒ regex.findFirstIn(f.getName).isDefined)

        fileMap ++= files.map(f ⇒ (f, s"$targetRawFolder${File.separator}$folderPrefix${f.getName}"))
          .map(a ⇒ (a._1.getPath, a._2))

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
        (f._1, Paths.get(target, f._2).toString, context.actorOf(Props[TransferSelectedRawFile])))
        .foreach(x ⇒ x._3 ! TransferFile(x._1.getAbsolutePath, x._2))

    case FileTransferred ⇒ //todo and?


  }
}

object TransferSelectedRawData {

  case class TransferFiles(files: Map[String, String])

  case class TransferFolder(folder: String, regex: Regex, includeSubFolder: Boolean)

  case class TransferFilesToFolder(files: Map[File, String], target: String)

}


class TransferSelectedRawFile extends Actor {
  val logger = Logging(context.system, this)

  import TransferSelectedRawFile._
  import java.nio.file.StandardCopyOption.REPLACE_EXISTING
  import java.nio.file.Files.copy
  import java.nio.file.Paths.get

  override def receive = {
    case TransferFile(source, target) ⇒
      logger.debug(s"transfering $source TO $target")

      implicit def toPath(filename: String): Path = get(filename)

      val parFolder = new File(target).getParentFile
      if (!parFolder.isDirectory) parFolder.mkdirs()

      copy(source, target, REPLACE_EXISTING)

      sender ! FileTransferred

      self ! PoisonPill
  }
}


object TransferSelectedRawFile {

  case class TransferFile(source: String, target: String) // todo file type instead of string

  case object FileTransferred

}
