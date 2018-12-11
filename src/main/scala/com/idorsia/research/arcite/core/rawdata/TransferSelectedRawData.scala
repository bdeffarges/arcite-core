package com.idorsia.research.arcite.core.rawdata

import java.io.File
import java.nio.file.{FileSystemException, Files, Path, Paths}

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props}
import akka.event.Logging
import com.idorsia.research.arcite.core.utils.FoldersHelpers
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

/**
  * Created by deffabe1 on 3/4/16.
  *
  * an experiment in the laboratory produces raw data files that are stored on a file share.
  * It's usually either some PC to which the equipment is directly connected or a share on the network.
  * The raw data files can be of different formats: xml, csv, txt, images (jpeg, png, ...). Some of these files contain
  * the actual measurements, others are quality checks, design or status information, etc.
  * It's not usual that data are duplicated (e.g. in different formats like text, xml, csv or with maybe different meta
  * information).
  *
  * The first task that Arcite must complete is to get access to the raw data.
  * An option is for the user to upload it through the web interface.
  * Another one is to transfer data from a mounted drive to the raw folder of an experiment.
  * The raw data can be linked (linux symbolic links) or transferred.
  *
  */
class TransferSelectedRawData(caller: ActorRef, targetFolder: Path) extends Actor with ActorLogging {

  import TransferSelectedRawData._
  import TransferSelectedRawFile._

  var counter = 0 // todo should be moved higher up

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 30 seconds) {
      case _: FileSystemException ⇒ Restart
      case _: Exception ⇒ Escalate
    }

  override def receive: Receive = {

    case TransferFiles(files, target, symLink) ⇒
      counter = files.size

      files.map(f ⇒ (context.actorOf(Props[TransferSelectedRawFile]), f))
        .foreach(x ⇒ x._1 ! TransferFile(x._2, target, symLink))


    case FileTransferred ⇒
      counter -= 1
      if (counter == 0) caller ! FileTransferredSuccessfully


    case _: Any ⇒
      log.error("#&w I don't know what to do with received message...")
    // todo inform caller...
  }
}

object TransferSelectedRawData extends LazyLogging {

  def props(caller: ActorRef, targetPath: Path) = Props(classOf[TransferSelectedRawData], caller, targetPath)

  case class FromSourceToRaw(sourceFiles: Set[String], targetFolder: Path, symLink: Boolean = false)


  trait FileTransferFeedback

  case object FileTransferredSuccessfully extends FileTransferFeedback

  case object FileTransferInProgress extends FileTransferFeedback

  case class FileTransferredFailed(error: String) extends FileTransferFeedback

}


class TransferSelectedRawFile extends Actor {
  val logger = Logging(context.system, this)

  import TransferSelectedRawFile._

  override def receive = {
    case TransferFile(srcFile, target, symLink) ⇒

      if (symLink) {

        val link = target resolve srcFile.getName
        logger.debug(s"&3% linking $srcFile from $target")
        if (!link.toFile.exists())
          Files.createSymbolicLink(link, srcFile.toPath)
      } else {
        logger.debug(s"&3% transferring $srcFile TO $target")
        Files.copy(srcFile.toPath, target resolve FoldersHelpers.nextFileName(target, srcFile.getName))
      }

      sender ! FileTransferred
      self ! PoisonPill
  }
}


object TransferSelectedRawFile {

  case object FileTransferred

  case class TransferFile(file: File, targetFolder: Path, symLink: Boolean = false)

  case class TransferFiles(files: Set[File], targetFolder: Path, symLink: Boolean = false)

}
