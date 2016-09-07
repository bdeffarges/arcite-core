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

case class TransformSourceFiles(experiment: Experiment, sourceFoldersOrFiles: Set[String]) extends TransformSource


case class TransformAsSource4Transform(experiment: Experiment, transformUID: String,
                                       sourceFoldersOrFiles: Set[String]) extends TransformSource


case class TransformSourceRegex(experiment: Experiment, folder: String, regex: String,
                                withSubfolder: Boolean) extends TransformSource

case class TransformFromObject(experiment: Experiment, source: Any) extends TransformSource


/**
  * the actual transform that contains all information for the instance of a transform.
  *
  * @param definition
  * @param source
  * @param parameters , we keep it as a JsValue so the real transformer can decide at run time what to do with the parameters
  * @param uid
  */
case class Transform(definition: TransformDefinition, source: TransformSource, parameters: JsValue,
                     uid: String = UUID.randomUUID().toString) {

  val light = TransformLight(definition.transDefIdent.fullName, uid)
}

/**
  * a light object describing a transform without all extra information
  *
  * @param transfDefinitionName
  * @param uid
  */
case class TransformLight(transfDefinitionName: FullName, uid: String)

case class TransformWithRequester(transform: Transform, requester: ActorRef) //todo remove

case class TransformHelper(transform: Transform) {
  def getTransformFolder(): Path = {
    Paths.get(ExperimentFolderVisitor(transform.source.experiment).transformFolderPath.toString, transform.uid)
  }
}


object TransformDefinionJson extends DefaultJsonProtocol {

  implicit object TransformDefinitionJsonFormat extends RootJsonFormat[TransformDefinitionIdentity] {

    def write(tdl: TransformDefinitionIdentity) = {
      JsObject(
        "organization" -> JsString(tdl.fullName.organization),
        "name" -> JsString(tdl.fullName.name),
        "short_name" -> JsString(tdl.shortName),
        "description_summary" -> JsString(tdl.description.summary),
        "description_consumes" -> JsString(tdl.description.consumes),
        "description_produces" -> JsString(tdl.description.produces),
        "digest" -> JsString(tdl.digestUID)
      )
    }

    def read(value: JsValue) = {
      value.asJsObject.getFields("organization", "name", "short_name", "description_summary",
        "description_consumes", "description_produces") match {
        case Seq(JsString(organization), JsString(name), JsString(shortName),
        JsString(descSummary), JsString(descConsumes), JsString(descProduces)) =>
          TransformDefinitionIdentity(FullName(organization, name), shortName,
            TransformDescription(descSummary, descConsumes, descProduces))

        case _ => throw new DeserializationException("could not deserialize.")
      }
    }
  }

}

case class TransformResult(transform: Transform, result: Any)
