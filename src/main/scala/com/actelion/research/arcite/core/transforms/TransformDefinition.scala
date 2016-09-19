package com.actelion.research.arcite.core.transforms

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.Props
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.utils.{FullName, GetDigest}
import spray.json.JsValue

/**
  * Created by bernitu on 19/04/16.
  *
  */

/**
  * description of a transform, what its purpose is, what it consumes and what it produces
  *
  * @param summary
  * @param consumes
  * @param produces
  */
case class TransformDescription(summary: String, consumes: String, produces: String)

/**
  * Basic definition of a transform. What it does and its unique name.
  * todo should add a dependsOn to describe on what this transform depends on
  *
  * @param fullName
  * @param description
  */
case class TransformDefinitionIdentity(fullName: FullName, shortName: String, description: TransformDescription) {
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
                                               excludesRegex: Set[String] = Set()) extends TransformSource {

  def inputFiles(): Set[String] = {
    Set() // todo implement exclusion
  }
}

case class TransformSourceFromTransform(experiment: Experiment, transformUID: String)
  extends TransformSource {

  def inputFiles(): Set[String] = {
    Set()
  }
}

case class TransformSourceFromTransformWithExclusion(experiment: Experiment, transformUID: String,
                                                     excludes: Set[String] = Set(), excludesRegex: Set[String] = Set())
  extends TransformSource {

  def inputFiles(): Set[String] = {
    Set() // todo implement
  }
}


case class TransformSourceFromObject(experiment: Experiment) extends TransformSource


/**
  * the actual transform that contains all information for the instance of a transform.
  *
  * @param transfDefName
  * @param source
  * @param parameters , we keep it as a JsValue so the real transformer can decide at run time what to do with the parameters
  * @param uid
  */
case class Transform(transfDefName: FullName, source: TransformSource, parameters: JsValue,
                     uid: String = UUID.randomUUID().toString)


case class TransformHelper(transform: Transform) {
  def getTransformFolder(): Path = {
    Paths.get(ExperimentFolderVisitor(transform.source.experiment).transformFolderPath.toString, transform.uid)
  }
}


case class TransformResult(transform: Transform, result: Any)
