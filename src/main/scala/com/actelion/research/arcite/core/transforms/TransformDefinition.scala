package com.actelion.research.arcite.core.transforms

import java.io.File
import java.nio.file.Path
import java.util.UUID

import akka.actor.Props
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.actelion.research.arcite.core.utils
import com.actelion.research.arcite.core.utils.{FullName, GetDigest}
import spray.json.JsValue

/**
  * Created by Bernard Deffarges on 19/04/16.
  *
  */

/**
  * description of a transform, its purpose, what it consumes and what it produces
  *
  * @param summary
  * @param consumes
  * @param produces
  */
case class TransformDescription(summary: String, consumes: String, produces: String)

/**
  * Basic definition of a transform. What it does and its unique name.
  * DependsOn can specify a previous transformation on which results this one might depend on
  * It just gives an indication for the user and developer of activity workers, however what counts
  * is the actual input (e.g. transformation result) available to the next transform.
  *
  * @param fullName
  * @param shortName
  * @param description
  * @param dependsOn
  */
case class TransformDefinitionIdentity(fullName: FullName, shortName: String, // todo version
                                       description: TransformDescription, dependsOn: Option[FullName] = None) {
  lazy val digestUID = GetDigest.getDigest(s"$fullName $description")
}

/**
  * Transforms are started from an actor, so here we add  a props to be able
  * to get a new actor that will do the actual transform job. This actor will be a worker actor in the cluster.
  *
  * @param transDefIdent
  * @param actorProps
  */
case class TransformDefinition(transDefIdent: TransformDefinitionIdentity, actorProps: () ⇒ Props)


/**
  * Where to find the source data for the transform
  *
  */
sealed trait TransformSource {
  def experiment: Experiment
}

case class TransformSourceFromRaw(experiment: Experiment) extends TransformSource

case class TransformSourceFromRawWithExclusion(experiment: Experiment, excludes: Set[String] = Set(),
                                               excludesRegex: Set[String] = Set()) extends TransformSource

case class TransformSourceFromTransform(experiment: Experiment, srcTransformID: String) extends TransformSource

case class TransformSourceFromTransformWithExclusion(experiment: Experiment, srcTransformUID: String,
                                                     excludes: Set[String] = Set(),
                                                     excludesRegex: Set[String] = Set()) extends TransformSource

case class TransformSourceFromObject(experiment: Experiment) extends TransformSource


/**
  * the actual transform that contains all information for the instance of a transform.
  *
  * @param transfDefName
  * @param source
  * @param parameters we keep it as a JsValue so the real transformer can decide at run time what to do with the parameters
  * @param uid
  */
case class Transform(transfDefName: FullName, source: TransformSource, parameters: Option[JsValue],
                     uid: String = UUID.randomUUID().toString)


case class TransformHelper(transform: Transform) {
  lazy val experimentFolderVisitor = ExperimentFolderVisitor(transform.source.experiment)

  def getTransformFolder(): Path = experimentFolderVisitor.transformFolderPath resolve transform.uid

  def getRawUserFiles(): Set[Path] = experimentFolderVisitor.userRawFolderPath
    .toFile.listFiles().toSet[File].map(_.toPath)

  def getMetaUserFiles(): Set[Path] = experimentFolderVisitor.userMetaFolderPath
    .toFile.listFiles().toSet[File].map(_.toPath)

}


case class TransformDoneSource(experiment: String, kindOfSource: String, fromTransform: Option[String],
                               excludes: Option[Set[String]], excludesRegex: Option[Set[String]])


case class TransformCompletionFeedback(transform: String, transformDefinition: FullName, source: TransformDoneSource,
                                       parameters: Option[JsValue], status: TransformCompletionStatus,
                                       artifacts: List[String], feedback: String, errors: String,
                                       startTime: String, endTime: String = utils.getCurrentDateAsString())

object TransformCompletionStatus extends scala.Enumeration {
  type TransformCompletionStatus = Value
  val SUCCESS, FAILED, COMPLETED_WITH_WARNINGS = Value
}