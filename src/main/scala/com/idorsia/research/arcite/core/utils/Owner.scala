package com.idorsia.research.arcite.core.utils

import java.io.File

import com.idorsia.research.arcite.core.experiments.Experiment

/**
  * Created by deffabe1 on 3/8/16.
  * An owner is the combination of an organization (e.g. com.idorsia.research.microarray,
  * so the microarray within the research group of Actelion),
  * a name (project or study name) and a person who is the main contributor
  * (has done the experimental work or is taking care of the experimental data).
  */

case class Owner(organization: String, person: String) {

  override def toString: String = s"$organization:$person"

  lazy val asFileStructure: String = organization.replace(".", File.separator)

}

object DefaultOwner {
  val systemOwner = Owner("system", "arcite")
}

/**
  *
  * @param organization
  * @param name
  * @param shortName should not contain spaces (if it's the case, they will be replaced with '_')
  * @param version
  */
case class FullName(organization: String, name: String, shortName: String, version: String = "1.0.0") {
  private lazy val sName = shortName.replaceAll("\\s", "_")
  lazy val asUID: String = s"$organization@@$sName@@$version".toLowerCase
}



