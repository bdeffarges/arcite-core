package com.actelion.research.arcite.core.transforms

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption._
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.experiments.{ExperimentFolderVisitor, ManageExperiments}
import com.actelion.research.arcite.core.transforms.GoTransformIt._
import com.actelion.research.arcite.core.utils.{FullName, Owner}
import org.slf4j.LoggerFactory
import spray.json.JsValue

/**
  * Created by deffabe1 on 5/19/16.
  */
class GoTransformIt extends Actor with ActorLogging {

  override def receive: Receive = {
    case gfat: TransformWithRequester ⇒
      val actor = context.actorOf(gfat.transform.definition.actorProps())
      actor ! gfat

    case rtr: RunTransformFromFilesWithRequester ⇒
      val t = Transformers.transformers(rtr.rt.transformDigest) //todo should we get those objects over the actors? also should return error if not found
    val exp = ManageExperiments.getExperimentFromDigest(rtr.rt.experimentDigest)
      val source = TransformSourceFiles(exp.get, rtr.rt.filesAndFolders)

      self ! TransformWithRequester(Transform(t, source, rtr.rt.parameters), rtr.requester)

    case rtr: RunTransformFromTransformWithRequester ⇒
      val t = Transformers.transformers(rtr.rt.transformDigest) //todo should we get those objects over the actors? also should return error if not found
    val exp = ManageExperiments.getExperimentFromDigest(rtr.rt.experimentDigest)
      val source = TransformAsSource4Transform(exp.get, rtr.rt.transformOrigin, rtr.rt.filesAndFolders)

      self ! TransformWithRequester(Transform(t, source, rtr.rt.parameters), rtr.requester)

    case rtr: RunTransformFromFolderAndRegexWithRequester ⇒
      log.debug(s"run a transform from defined folder [${rtr.rt.folder}] using regex [${rtr.rt.regex}]... ")
      val t = Transformers.transformers(rtr.rt.transformDigest) //todo should we get those objects over the actors?
    val exp = ManageExperiments.getExperimentFromDigest(rtr.rt.experimentDigest)
      // todo temp speedup work around
      val folder = if ("raw" == rtr.rt.folder) ExperimentFolderVisitor(exp.get).rawFolderPath.toString else rtr.rt.folder

      val source = TransformSourceRegex(exp.get, rtr.rt.folder, rtr.rt.regex, rtr.rt.withSubfolder)

      self ! TransformWithRequester(Transform(t, source, rtr.rt.parameters), rtr.requester)

    case tf: TransformFeedback ⇒
      writeFeedback(tf)

    case _ ⇒
      log.error(s"message not understood...")
  }

}

object GoTransformIt {
  def props: Props = Props(classOf[GoTransformIt])

  val logger = LoggerFactory.getLogger(getClass.getName)

  sealed trait TransformFeedback {
    def transform: Transform
  }

  def writeFeedback(transformFeedback: TransformFeedback): Unit = {

    val t = transformFeedback

    def transformSourceFiles(ts: TransformSource): String = ts match {
      case t: TransformSourceFiles ⇒
        s"""${
          t.sourceFoldersOrFiles.mkString(", ")
        }"""
      case t: TransformSourceRegex ⇒
        s"""folder=${
          t.folder
        }  regex="${
          t.regex
        }" withSubFolder=${
          t.withSubfolder
        }"""
      case t: TransformAsSource4Transform ⇒
        s"""transformOriginUID=${
          t.transformUID
        } filesAndFolders=[${
          t.sourceFoldersOrFiles.mkString(", ")
        }]"""
    }

    val stateOutPutError = transformFeedback match {
      case t: TransformSuccess ⇒
        ("SUCCEEDED", t.output, "")
      case t: TransformFailed ⇒
        ("FAILED", t.output, t.error)
    }

    val ts = TransformSourceDone(t.transform.source.experiment.name, t.transform.source.experiment.owner,
      t.transform.source.experiment.digest)

    val tdone = TransformDone(stateOutPutError._1,
      new SimpleDateFormat("yyyy:MM:dd-HH:mm:ss").format(new Date),
      t.transform.definition.definitionLight.fullName,
      t.transform.definition.definitionLight.description.summary,
      ts, transformSourceFiles(t.transform.source),
      core.getFirstAndLastLinesOfAVeryLongString(stateOutPutError._2, 100),
      core.getFirstAndLastLinesOfAVeryLongString(stateOutPutError._3, 100))

    import spray.json._
    import DefaultJsonProtocol._

    implicit val jsonTowner = jsonFormat2(Owner)
    implicit val jsonTFullName = jsonFormat2(FullName)
    implicit val jsonTdesc = jsonFormat3(TransformDescription)
    implicit val jsonTsd = jsonFormat3(TransformSourceDone)
    implicit val jsonTd = jsonFormat8(TransformDone)

    val strg = tdone.toJson.prettyPrint

    val fp = Paths.get(ExperimentFolderVisitor(t.transform.source.experiment).transformFolderPath.toString,
      t.transform.uid, "transformFeedback")

    logger.debug(tdone.toString)
    Files.write(fp, strg.getBytes(StandardCharsets.UTF_8), CREATE_NEW)

  }

  case class TransformSourceDone(experimentName: String, experimentOwner: Owner, transformDigest: String)

  case class TransformDone(state: String, date: String, transformDefinitionName: FullName, transformDefinitionSummary: String,
                           transformSource: TransformSourceDone, transformSourceFile: String,
                           output: String, error: String)

  case class TransformSuccess(transform: Transform, output: String) extends TransformFeedback

  case class TransformFailed(transform: Transform, output: String, error: String) extends TransformFeedback

  case class InTransform(transform: Transform) extends TransformFeedback


  sealed trait Message2GoTransform

  case class RunTransformFromFiles(experimentDigest: String, transformDigest: String, filesAndFolders: Set[String],
                                   parameters: JsValue) extends Message2GoTransform

  case class RunTransformFromTransform(experimentDigest: String, transformDigest: String, transformOrigin: String,
                                       filesAndFolders: Set[String], parameters: JsValue) extends Message2GoTransform

  case class RunTransformFromFolderAndRegex(experimentDigest: String, transformDigest: String, folder: String, regex: String,
                                            withSubfolder: Boolean, parameters: JsValue) extends Message2GoTransform

  case class RunTransformFromFilesWithRequester(rt: RunTransformFromFiles, requester: ActorRef) extends Message2GoTransform

  case class RunTransformFromTransformWithRequester(rt: RunTransformFromTransform, requester: ActorRef) extends Message2GoTransform

  case class RunTransformFromFolderAndRegexWithRequester(rt: RunTransformFromFolderAndRegex, requester: ActorRef) extends Message2GoTransform

}
