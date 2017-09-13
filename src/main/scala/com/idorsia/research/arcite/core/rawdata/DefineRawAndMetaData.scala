package com.idorsia.research.arcite.core.rawdata

import java.io.File
import java.nio.file.Files

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.idorsia.research.arcite.core.api.ArciteService.{ExperimentFound, ExperimentFoundFeedback, GetExperiment}
import com.idorsia.research.arcite.core.eventinfo.EventInfoLogging.AddLog
import com.idorsia.research.arcite.core.eventinfo.{ExpLog, LogCategory, LogType}
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.idorsia.research.arcite.core.rawdata.TransferSelectedRawData._
import com.idorsia.research.arcite.core.rawdata.TransferSelectedRawFile.TransferFiles
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
  * Created by deffabe1 on 5/12/16.
  */

class DefineRawAndMetaData(expManagerAct: ActorRef, eventInfo: ActorRef) extends Actor with ActorLogging {

  private val conf = ConfigFactory.load().getConfig("experiments-manager")
  private val actSys = conf.getString("akka.uri")

  import DefineRawAndMetaData._

  override def receive: Receive = {

    case rds: SetRawData ⇒
      val srdsa = context.actorOf(SetSrcRawDataAct.props(actSys, sender(), expManagerAct, eventInfo))
      srdsa ! rds


    case rrd: RemoveRaw ⇒
      val rrda = context.actorOf(RmRawDataAct.props(actSys, sender(), expManagerAct, eventInfo))
      rrda ! rrd


    case mds: LinkMetaData ⇒
      val srdsa = context.actorOf(SetSrcMetaDataAct.props(actSys, sender(), expManagerAct, eventInfo))
      srdsa ! mds


    case rmm: RemoveMetaData ⇒
      val rrda = context.actorOf(RmMetaDataAct.props(actSys, sender(), expManagerAct, eventInfo))
      rrda ! rmm


    case _: Any ⇒
      log.error("don't know what to do with passed message...")
  }
}

object DefineRawAndMetaData extends ArciteJSONProtocol with LazyLogging {

  def props(manageExpAct: ActorRef, eventInfo: ActorRef) = Props(classOf[DefineRawAndMetaData], manageExpAct, eventInfo)


  /**
    * a Source is given (microarray, ngs, ...) which usually is a mount to a drive
    * where the lab equipment stores its produced data, which can now be copied into the raw folder
    * of the experiment.
    *
    * @param experiment
    * @param files
    * @param symLink : should it be only symbolic links?
    */
  case class SetRawData(experiment: String, files: Set[String], symLink: Boolean = false)

  sealed trait RemoveRaw {
    def experiment: String
  }

  case class RemoveRawData(experiment: String, files: Set[String]) extends RemoveRaw

  case class RemoveAllRaw(experiment: String) extends RemoveRaw

  sealed trait RmRawDataResponse

  case object RmSuccess extends RmRawDataResponse

  case object RmCannot extends RmRawDataResponse

  case object RmFailed extends RmRawDataResponse


  sealed trait RawDataSetResponse

  case object RawDataSetAdded extends RawDataSetResponse

  case object RawDataSetInProgress extends RawDataSetResponse

  case class RawDataSetFailed(error: String) extends RawDataSetResponse


  case class LinkMetaData(experiment: String, files: Set[String])


  sealed trait MetaDataSetResponse

  case object MetaDataSetLinked extends MetaDataSetResponse

  case class MetaDataLinkFailed(error: String) extends MetaDataSetResponse


  case class RemoveMetaData(experiment: String, files: Set[String])


  sealed trait RmMetaDataResponse

  case object RmMetaSuccess extends RmMetaDataResponse

  case object RmMetaCannot extends RmMetaDataResponse

  case object RmMetaFailed extends RmMetaDataResponse

}

/**
  * a short living actor just to transfer some data from a source mount to the experiment
  */
private class SetSrcRawDataAct(actSys: String, requester: ActorRef, expManager: ActorRef,
                               eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import SetSrcRawDataAct._

  private var experiment: Option[Experiment] = None
  private var rawDataSet: Option[SetRawData] = None

  override def receive: Receive = {

    case srds: SetRawData ⇒
      log.debug(s"%4* transferring data from source... $srds")
      rawDataSet = Some(srds)
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          self ! StartDataTransfer

        case _: Any ⇒
          requester ! RawDataSetFailed("could not find experiment")
          self ! PoisonPill
      }


    case StartDataTransfer ⇒
      requester ! RawDataSetInProgress

      val target = ExperimentFolderVisitor(experiment.get).rawFolderPath

      val transferActor = context.actorOf(Props(classOf[TransferSelectedRawData], self, target))

      val files = rawDataSet.get.files.map(new File(_)).filter(_.exists())
      log.info(s"file size: ${files.size}")

      if (files.size < 1) {
        requester ! RawDataSetFailed(s"empty file set. ")
        self ! PoisonPill

      } else {
        transferActor ! TransferFiles(files, target, rawDataSet.get.symLink)
      }

    case FileTransferredSuccessfully ⇒

      requester ! FileTransferredSuccessfully

      log.debug("@#1 transfer completed successfully. ")

      eventInfoAct ! AddLog(experiment.get, ExpLog(LogType.UPDATED,
        LogCategory.SUCCESS, s"Raw data copied. [${rawDataSet}]"))

      self ! PoisonPill


    case f: FileTransferredFailed ⇒
      requester ! RawDataSetFailed(s"file transfer failed ${f.error}")

      self ! PoisonPill

  }
}

private object SetSrcRawDataAct {
  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[SetSrcRawDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartDataTransfer

}


private class RmRawDataAct(actSys: String, requester: ActorRef, expManager: ActorRef,
                           eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import RmRawDataAct._
  import DefineRawAndMetaData._

  private var experiment: Option[Experiment] = None
  private var filesToBeRemoved: Set[String] = Set.empty
  private var removeAll: Boolean = false

  override def receive: Receive = {

    case srds: RemoveRaw ⇒

      srds match {
        case rrd: RemoveRawData ⇒
          log.debug(s"%324a deleting source data... $rrd")
          filesToBeRemoved = rrd.files

        case ra: RemoveAllRaw ⇒
          log.debug(s"%324a deleting all data... ")
          removeAll = true
      }
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          if (ExperimentFolderVisitor(exp).isImmutableExperiment) {
            sender() ! RmCannot
          } else {
            self ! StartRemove
          }

        case _: Any ⇒
          requester ! RawDataSetFailed("could not find experiment")
          self ! PoisonPill
      }


    case StartRemove ⇒
      val rawFolder = ExperimentFolderVisitor(experiment.get).rawFolderPath

      try {
        if (removeAll) {
          rawFolder.toFile.listFiles.foreach(_.delete())
        } else {
          filesToBeRemoved.foreach(f ⇒ (rawFolder resolve f).toFile.delete())
        }
        requester ! RmSuccess

      } catch {
        case exc: Exception ⇒
          requester ! RmFailed
      } finally {
        self ! PoisonPill
      }
  }
}


private object RmRawDataAct {

  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[RmRawDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartRemove

}

private class SetSrcMetaDataAct(actSys: String, requester: ActorRef, expManager: ActorRef,
                                eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import SetSrcMetaDataAct._

  private var experiment: Option[Experiment] = None
  private var metaDataSet: Option[LinkMetaData] = None

  override def receive: Receive = {

    case srds: LinkMetaData ⇒
      log.debug(s"%4* transferring data from source... $srds")
      metaDataSet = Some(srds)
      expManager ! GetExperiment(srds.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          self ! StartDataTransfer

        case _: Any ⇒
          requester ! MetaDataLinkFailed("could not find experiment")
          self ! PoisonPill
      }


    case StartDataTransfer ⇒

      val target = ExperimentFolderVisitor(experiment.get).userMetaFolderPath

      val files = metaDataSet.get.files.map(new File(_)).filter(_.exists())
      log.info(s"file size: ${files.size}")

      files.map { f ⇒
        val link = target resolve f.getName
        if (!link.toFile.exists) {
          Files.createSymbolicLink(link, f.toPath)
        }
      }

      requester ! FileTransferredSuccessfully

      log.debug("@#1 transfer completed successfully. ")

      eventInfoAct ! AddLog(experiment.get, ExpLog(LogType.UPDATED,
        LogCategory.SUCCESS, s"Raw data copied. [${metaDataSet}]"))

      self ! PoisonPill

  }
}

private object SetSrcMetaDataAct {
  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[SetSrcMetaDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartDataTransfer

}


private class RmMetaDataAct(actSys: String, requester: ActorRef, expManager: ActorRef,
                            eventInfoAct: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._
  import RmMetaDataAct._

  private var experiment: Option[Experiment] = None
  private var filesToBeRemoved: Set[String] = Set.empty

  override def receive: Receive = {

    case rmd: RemoveMetaData ⇒
      log.debug(s"%324a deleting meta data... $rmd")
      filesToBeRemoved = rmd.files
      expManager ! GetExperiment(rmd.experiment)


    case eff: ExperimentFoundFeedback ⇒
      eff match {
        case ExperimentFound(exp) ⇒
          experiment = Some(exp)
          if (ExperimentFolderVisitor(exp).isImmutableExperiment) {
            sender() ! RmMetaCannot
          } else {
            self ! StartRemove
          }

        case _: Any ⇒
          requester ! MetaDataLinkFailed("could not find experiment where meta data should be linked. ")
          self ! PoisonPill
      }


    case StartRemove ⇒
      val metaFolder = ExperimentFolderVisitor(experiment.get).userMetaFolderPath

      try {
        filesToBeRemoved.foreach(f ⇒ (metaFolder resolve f).toFile.delete())
        requester ! RmMetaSuccess

      } catch {
        case exc: Exception ⇒
          requester ! RmMetaFailed
      } finally {
        self ! PoisonPill
      }
  }
}


private object RmMetaDataAct {

  def props(actSys: String, requester: ActorRef,
            expManager: ActorRef, eventInfoAct: ActorRef) =
    Props(classOf[RmMetaDataAct], actSys, requester, expManager, eventInfoAct)

  case object StartRemove

}

