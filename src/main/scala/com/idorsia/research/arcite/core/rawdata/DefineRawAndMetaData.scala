package com.idorsia.research.arcite.core.rawdata

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.idorsia.research.arcite.core.api.ArciteJSONProtocol
import com.typesafe.scalalogging.LazyLogging

/**
  * Created by deffabe1 on 5/12/16.
  */

class DefineRawAndMetaData(expManagerAct: ActorRef, eventInfo: ActorRef) extends Actor with ActorLogging {

  import DefineRawAndMetaData._

  override def receive: Receive = {

    case rds: SetRawData ⇒
      val srdsa = context.actorOf(SetSrcRawDataAct.props(sender(), expManagerAct, eventInfo))
      srdsa ! rds


    case rrd: RemoveRaw ⇒
      val rrda = context.actorOf(RmRawDataAct.props(sender(), expManagerAct, eventInfo))
      rrda ! rrd


    case mds: DefineMetaData ⇒
      val srdsa = context.actorOf(DefineMetaAct.props(sender(), expManagerAct, eventInfo))
      srdsa ! mds


    case rmm: RemoveMetaData ⇒
      val rrda = context.actorOf(RmMetaDataAct.props(sender(), expManagerAct, eventInfo))
      rrda ! rmm


    case _: Any ⇒
      log.error("don't know what to do with passed message...")
  }
}

object DefineRawAndMetaData extends ArciteJSONProtocol with LazyLogging {

  def props(manageExpAct: ActorRef, eventInfo: ActorRef) = Props(classOf[DefineRawAndMetaData], manageExpAct, eventInfo)

  //an easy way to forward certain messages is inheritance
  trait RawAndMetaMsg

  /**
    * a Source is given (microarray, ngs, ...) which usually is a mount to a drive
    * where the lab equipment stores its produced data, which can now be copied into the raw folder
    * of the experiment.
    *
    * @param experiment
    * @param files
    * @param symLink : should it be only symbolic links?
    */
  case class SetRawData(experiment: String, files: Set[String], symLink: Boolean = false) extends RawAndMetaMsg


  sealed trait RemoveRaw extends RawAndMetaMsg {
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


  case class DefineMetaData(experiment: String, files: Set[String]) extends RawAndMetaMsg


  sealed trait MetaResponse

  case object MetaDataSetDefined extends MetaResponse

  case object MetaDataInProgress extends MetaResponse

  case class MetaDataFailed(error: String) extends MetaResponse


  case class RemoveMetaData(experiment: String, files: Set[String]) extends RawAndMetaMsg


  sealed trait RmMetaDataResponse

  case object RmMetaSuccess extends RmMetaDataResponse

  case object RmMetaCannot extends RmMetaDataResponse

  case object RmMetaFailed extends RmMetaDataResponse

}