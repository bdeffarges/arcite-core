package com.actelion.research.arcite.core.utils

import java.io.File

/**
  * Created by deffabe1 on 3/8/16.
  * An owner is the combination of an organization (e.g. com.actelion.research.microarray,
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

case class FullName(organization: String, name: String, version: String = "1.0.0")

