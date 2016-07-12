package com.actelion.research.arcite.core.experiments

import java.nio.file.Paths

import com.actelion.research.arcite.core.utils.{Env, GetDigest, Owner, OwnerJsonProtocol}
import spray.json.{DefaultJsonProtocol, JsArray, JsString, JsValue, RootJsonFormat}


/**
  * Created by bernitu on 06/03/16.
  *
  * The main entity is an experiment which is defined by a set of raw data files,
  * design information (col, row header names, etc.) and some general information like name,
  * description, tag, producer, organization...
  *
  *
  */
case class Experiment(name: String, description: String, owner: Owner, state: ExperimentState = New,
                      design: ExperimentalDesign = ExperimentalDesign(), properties: Map[String, String] = Map()) {

  def digest = GetDigest.getDigest(s"${owner.organization}$name")
}

case class ExperimentFolderVisitor(exp: Experiment) {

  val defaultMetaFileName = "meta.json" // the default file that describes the content of a folder

  val arciteHome = Env.getConf("arcite.home")

  // relative paths
  val folderName = name.replaceAll("\\s", "_")
  val relFolderPath = Paths.get(owner.asFileStructure, folderName)
  val relMetaFolderPath = Paths.get(owner.asFileStructure, folderName, "meta")
  val relRawFolderPath = Paths.get(owner.asFileStructure, folderName, "raw")
  val relTransformFolderPath = Paths.get(owner.asFileStructure, folderName, "transforms")
  val relPublishedFolderPath = Paths.get(owner.asFileStructure, folderName, "published")

  def name = exp.name

  def description = exp.description

  def owner = exp.owner

  def properties = exp.properties

  def expFolderPath = Paths.get(arciteHome, relFolderPath.toString)

  def rawFolderPath = Paths.get(arciteHome, relRawFolderPath.toString)

  def metaFolderPath = Paths.get(arciteHome, relMetaFolderPath.toString)

  def transformFolderPath = Paths.get(arciteHome, relTransformFolderPath.toString)

  def publishedFolderPath = Paths.get(arciteHome, relPublishedFolderPath.toString)

  def experimentFilePath = Paths.get(arciteHome, relMetaFolderPath.toString, LocalExperiments.EXPERIMENT_FILE_NAME)

}

trait ExperimentJsonProtocol extends OwnerJsonProtocol with ExpDesignJsonProtocol {

  import spray.json._

  implicit object ExpStateJsonFormat extends RootJsonFormat[ExperimentState] {
    def write(c: ExperimentState) = JsString(c.name)

    def read(value: JsValue) = value match {
      case JsString("new") ⇒ New
      case JsString("saved") ⇒ Saved
      case JsString("processed") ⇒ Processed
      case JsString("published") ⇒ Published
      case JsString("global") ⇒ Global

      case _ ⇒ deserializationError("Experiment state expected")
    }
  }

  implicit val experimentJson = jsonFormat6(Experiment)
}

/**
  * in which state an experiment can be:
  * New: the experiment is new, only local and virtual, nothing is setup on disk
  * Saved: the experiment folder structure has been created but nothing has been saved yet
  * Processed: something has been done with this experiment, data has been transformed, etc.
  * Published: the local experiment has been published globally
  * Global: it's a global experiment that can be queried and retrieved from local
  */
sealed trait ExperimentState {
  def name: String
}

case object New extends ExperimentState {
  val name = "new"
}

case object Saved extends ExperimentState {
  val name = "saved"
}

case object Processed extends ExperimentState {
  val name = "processed"
}

case object Published extends ExperimentState {
  val name = "published"
}

case object Global extends ExperimentState {
  val name = "global"
}

