package com.actelion.research.arcite.core.transforms

import java.nio.file.{Path, Paths}
import java.util.UUID

import akka.actor.{ActorRef, Props}
import com.actelion.research.arcite.core.experiments.{Experiment, ExperimentFolderVisitor}
import com.actelion.research.arcite.core.utils.{FullName, GetDigest}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}

/**
  * Created by bernitu on 19/04/16.
  *
  */

case class TransformDescription(summary: String, consumes: String, produces: String)

/**
  * Basic definition of a transform. What it does and its unique name.
  * todo should add a dependsOn to describe on what this transform depends on
  *
  * @param fullName
  * @param description
  */
case class TransformDefinitionLight(fullName: FullName, description: TransformDescription) {
  lazy val digest = GetDigest.getDigest(s"$fullName $description")
}

/**
  * Transforms are started from an actor, so here we add to the definition of the transform a props to be able
  * to get a new actor that weill do the actual transforé job.
  *
  * @param definitionLight
  * @param actorProps
  */
case class TransformDefinition(definitionLight: TransformDefinitionLight, actorProps: () ⇒ Props)

sealed trait TransformSource {
  def experiment: Experiment
}

/**
  * Where to find the source data for the transform
  *
  * @param experiment
  * @param sourceFoldersOrFiles
  */
case class TransformSourceFiles(experiment: Experiment, sourceFoldersOrFiles: Set[String]) extends TransformSource

case class TransformAsSource4Transform(experiment: Experiment, transformUID: String, sourceFoldersOrFiles: Set[String]) extends TransformSource

case class TransformSourceRegex(experiment: Experiment, folder: String, regex: String, withSubfolder: Boolean) extends TransformSource


/**
  * the actual Transform that contain all information for the instance of a transform.
  *
  * @param definition
  * @param source
  * @param parameters, we keep it as a JsValue so the real transformer can decide at run time what to do with the parameters
  * @param uid
  */
case class Transform(definition: TransformDefinition, source: TransformSource, parameters: JsValue,
                     uid: String = UUID.randomUUID().toString)


case class TransformWithRequester(transform: Transform, requester: ActorRef)

case class TransformHelper(transform: Transform) {
  def getTransformFolder(): Path = {
    Paths.get(ExperimentFolderVisitor(transform.source.experiment).transformFolderPath.toString, transform.uid)
  }
}


object TransformDefinionJson extends DefaultJsonProtocol {

  implicit object TransformDefinitionJsonFormat extends RootJsonFormat[TransformDefinitionLight] {

    def write(tdl: TransformDefinitionLight) = {
      JsObject(
        "organization" -> JsString(tdl.fullName.organization),
        "name" -> JsString(tdl.fullName.name),
        "description_summary" -> JsString(tdl.description.summary),
        "description_consumes" -> JsString(tdl.description.consumes),
        "description_produces" -> JsString(tdl.description.produces),
        "digest" -> JsString(tdl.digest)
      )
    }

    def read(value: JsValue) = {
      value.asJsObject.getFields("organization", "name", "description_summary",
        "description_consumes", "description_produces") match {
        case Seq(JsString(organization), JsString(name),
        JsString(descSummary), JsString(descConsumes), JsString(descProduces)) =>
          TransformDefinitionLight(FullName(organization, name), TransformDescription(descSummary, descConsumes, descProduces))

        case _ => throw new DeserializationException("Color expected")
      }
    }
  }

}