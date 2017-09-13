package com.idorsia.research.arcite.core.transforms

import java.io.File
import java.nio.file.Path
import java.util.UUID

import akka.actor.Props
import com.idorsia.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.idorsia.research.arcite.core.transforms.ParameterType.ParameterType
import com.idorsia.research.arcite.core.transforms.TransformCompletionStatus.TransformCompletionStatus
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.{FullName, GetDigest}

/**
  * Created by Bernard Deffarges on 19/04/16.
  *
  */

// todo what about transforms that inherit from many transforms...
// todo transforms should have a stamp/digest to make sure they cannot be changed without changing their version after having being used at least once
// todo in the transform completion data we should also keep some digest from the produced artifact, to avoid changes

/**
  * description of a transform, its purpose, what it consumes and what it produces
  *
  * @param summary
  * @param consumes
  * @param produces
  */
case class TransformDescription(summary: String, consumes: String, produces: String,
                                transformParameters: Set[TransformParameter] = Set.empty)

/**
  * Basic definition of a transform. What it does and its unique name.
  * DependsOn can specify a previous transformation on which results this one might depend on
  * It just gives an indication for the user and developer of activity workers, however what counts
  * is the actual input (e.g. transformation result) available to the next transform.
  *
  * @param fullName
  * @param description
  * @param dependsOn
  */
case class TransformDefinitionIdentity(fullName: FullName, description: TransformDescription,
                                       dependsOn: Option[FullName] = None)

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

case class TransformSourceFromTransform(experiment: Experiment, srcTransformID: String) extends TransformSource

case class TransformSourceFromTransforms(experiment: Experiment, srcTransformIDs: Set[String]) extends TransformSource

case class TransformSourceFromObject(experiment: Experiment) extends TransformSource

case class TransformSourceFromTransformsAndRaw(experiment: Experiment,
                                               srcTransfomrIDs: Set[String]) extends TransformSource

//todo introduce transform from data structure. We could persist the transform results but at the same time use the in memory structure for the next transform.


/**
  * the actual transform that contains all information for the instance of a transform.
  *
  * @param transfDefName
  * @param source
  * @param parameters a map of parameters passed on to the transform worker
  * @param uid
  */
case class Transform(transfDefName: FullName, source: TransformSource,
                     parameters: Map[String, String] = Map.empty, uid: String = UUID.randomUUID().toString)


case class TransformHelper(transform: Transform) {
  lazy val experimentFolderVisitor = ExperimentFolderVisitor(transform.source.experiment)

  def getTransformFolder(): Path = experimentFolderVisitor.transformFolderPath resolve transform.uid

  def getRawUserFiles(): Set[Path] = experimentFolderVisitor.userRawFolderPath
    .toFile.listFiles().toSet[File].map(_.toPath)

  def getMetaUserFiles(): Set[Path] = experimentFolderVisitor.userMetaFolderPath
    .toFile.listFiles().toSet[File].map(_.toPath)

}


case class TransformDoneSource(experiment: String, kindOfSource: String, fromTransform: Option[String])

case class TransformCompletionFeedback(transform: String, transformDefinition: FullName, source: TransformDoneSource,
                                       parameters: Map[String, String], status: TransformCompletionStatus,
                                       artifacts: Map[String, String], feedback: String, errors: String,
                                       startTime: String, endTime: String = utils.getCurrentDateAsString())

case class RunningTransformFeedback(transform: String, transformDefinition: FullName, experiment: String,
                                    parameters: Map[String, String], progress: Int)

object TransformCompletionStatus extends scala.Enumeration {
  type TransformCompletionStatus = Value
  val SUCCESS, FAILED, COMPLETED_WITH_WARNINGS = Value
}

object ParameterType extends scala.Enumeration {
  type ParameterType = Value
  val PREDEFINED_VALUE, INT_NUMBER, FLOAT_NUMBER, FREE_TEXT = Value
}

sealed trait TransformParameter {
  def parameterID: String

  def parameterName: String

  def defaultValue: Option[Any]

  def parameterType: ParameterType
}

case class PredefinedValues(parameterID: String, parameterName: String, values: List[String],
                            defaultValue: Option[String] = None, allowsNew: Boolean = false,
                            parameterType: ParameterType = ParameterType.PREDEFINED_VALUE) extends TransformParameter {
}

case class IntNumber(parameterID: String, parameterName: String, defaultValue: Option[Long],
                     minBoundary: Option[Long] = None, maxBoundary: Option[Long] = None,
                     parameterType: ParameterType = ParameterType.INT_NUMBER) extends TransformParameter {
}

case class FloatNumber(parameterID: String, parameterName: String, defaultValue: Option[Double] = None,
                       minBoundary: Option[Double] = None, maxBoundary: Option[Double] = None,
                       parameterType: ParameterType = ParameterType.FLOAT_NUMBER) extends TransformParameter {
}

case class FreeText(parameterID: String, parameterName: String, defaultValue: Option[String] = None,
                    parameterType: ParameterType = ParameterType.FREE_TEXT) extends TransformParameter {
}

object TransformParameterHelper {

  /**
    * returns the params and the default values if provided for the expected params
    *
    * @param params
    * @param paramsType
    * @return
    */
  def getParamsWithDefaults(params: Map[String, String], paramsType: Set[TransformParameter]): Map[String, String] = {
    params ++ paramsType.filterNot(pt ⇒ params.isDefinedAt(pt.parameterName))
      .filter(_.defaultValue.isDefined)
      .map(pt ⇒ (pt.parameterName, pt.defaultValue.get.toString)).toMap
  }
}

