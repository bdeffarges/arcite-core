package com.idorsia.research.arcite.core.experiments

import java.nio.file.{Files, Paths}

import com.idorsia.research.arcite.core.experiments.CombinedCondition.{NameTransform, Separator}
import com.idorsia.research.arcite.core.utils.SimpleMatrix


/**
  * Created by deffabe1 on 4/25/16.
  *
  * An experimental design is usually the description of the whole experiment and its conditions.
  * It associate experimental conditions to each sample in the experiment.
  *
  */
case class ExperimentalDesign(description: String = "", samples: Set[Sample] = Set())

/**
  * a condition that belongs to a category (e.g. 323223 in category barcode)
  *
  * @param name
  * @param description some added information about the condition
  * @param category
  */
case class Condition(name: String, description: String, category: String)

/**
  * each sample in an experiment can have multiple conditions (treatment, barcode, dose, etc.).
  * Usually the total number of conditions per sample should be the same for the whole experiment.
  *
  * One condition or a set of conditions should uniquely define each sample (e.g. in microarray studies the
  * slide barcode and the array id uniquely distinguish each array in the experiment).
  *
  * @param conditions
  */
case class Sample(conditions: Set[Condition])

/**
  * sometimes it's useful to combine some conditions to name samples in an up coming step.
  * Here we can define the list of categories of the conditions and produce the output for a sample.
  *
  * @param categories
  */
case class CombinedCondition(categories: String*) {

  def getCombined(sample: Sample)(implicit ordering: Ordering[String],
                                  separator: Separator, nameTransform: NameTransform): String = {

    sample.conditions
      .filter(c ⇒ categories.map(_.toLowerCase).contains(c.category.toLowerCase))
      .toList.sortBy(_.category)
      .map(_.name)
      .map(n ⇒ if (nameTransform.toUpper) n.toUpperCase else n)
      .map(n ⇒ nameTransform.len.fold(n)(len ⇒ if (n.length > len) n.substring(0, len) else n))
      .mkString(separator.sep)
  }
}

object CombinedCondition {

  case class NameTransform(len: Option[Int], toUpper: Boolean)

  case class Separator(sep: String)

  implicit val separator: Separator = Separator("_")
  implicit val ordering: Ordering[String] = Ordering[String]
  implicit val nameTransform: NameTransform = NameTransform(None, false)
}

/**
  * helpers to be able to deal easily with an experimental design.
  *
  */
object ExperimentalDesignHelpers {

  def conditionsByCategory(cat: String, expDesign: ExperimentalDesign): Set[Condition] = {
    expDesign.samples.flatMap(cs ⇒ cs.conditions.filter(c ⇒ c.category.equals(cat)))
  }

  def allCategories(expDesign: ExperimentalDesign): Set[String] = {
    expDesign.samples.flatMap(cs ⇒ cs.conditions.map(_.category))
  }

  def allValuesForCats(expDes: ExperimentalDesign, cat: String*): Set[Set[Condition]] = {
    val av4c = expDes.samples.map(cs ⇒ cs.conditions.filter(c ⇒ cat.contains(c.category)))
    //    println(s"cat=${cat} (${av4c.size}) => $av4c\n\n")
    av4c
  }

  def uniqueCombinedCats(expDes: ExperimentalDesign): List[List[String]] = {

    val size = expDes.samples.size
    val allCats = allCategories(expDes).toList.sorted
    val allDiffCats = allCats.toSet.subsets.map(_.toList).filter(_.nonEmpty)

    allDiffCats.filter(c ⇒
      allValuesForCats(expDes, c: _*)
        .map(sc ⇒ sc.toList.sortBy(_.category).mkString).size == size).toList
  }

  def uniqueCategories(expDes: ExperimentalDesign): List[String] =
    uniqueCombinedCats(expDes).filter(_.size == 1).flatten

  def importFromCSVFileWithHeader(path: String,
                                  description: String = "design from CSV file",
                                  separator: String = "\t"): ExperimentalDesign = {

    import scala.collection.convert.wrapAsScala._
    val designFile = Files.readAllLines(Paths.get(path)).toList

    val headers = designFile.head.split(separator)

    ExperimentalDesign(description,
      designFile.tail.map(l ⇒
        Sample(l.split(separator)
          .zipWithIndex.map(wi ⇒ Condition(wi._1, wi._1, headers(wi._2))).toSet)
      ).toSet)
  }

  /**
    * Sometimes it's useful to get all the categories of a set of condition names.
    * If some condition names can be found in multiple conditions in different categories then we
    * return all of them.
    *
    * @param exp
    * @param values
    * @return
    */
  def uniqueCategoriesForGivenValues(exp: ExperimentalDesign, values: String*): Set[String] = {
    exp.samples.flatMap(s ⇒ s.conditions)
      .filter(c ⇒ values.contains(c.name))
      .map(_.category)
  }

  /**
    *
    * @param expDesign
    * @param categories
    * @param separator
    * @return
    */
  @deprecated("should use the export from the SimpleMatrix. ")
  def exportToDelimitedWithHeader(expDesign: ExperimentalDesign,
                                 categories: List[String],
                                  separator: String = "\t"): String = {

    val sb = new StringBuilder

    sb append categories.mkString(separator) append "\n"

    expDesign.samples.foreach { s ⇒
      categories.zipWithIndex.foreach { c ⇒
        sb append s.conditions.find(_.category == c._1).fold(separator)(_.name)
        if (c._2 - 1 < categories.size) sb append separator
      }
      sb append "\n"
    }

    sb.toString()
  }


  def getSample(expDesign: ExperimentalDesign,
                cats2Values: Map[String, String]): Option[Sample] = {

    expDesign.samples
      .find(s ⇒ s.conditions.filter(s ⇒ cats2Values.contains(s.category))
        .forall(c ⇒ cats2Values.get(c.category)
          .fold(false)(v ⇒ v == c.name)))
  }


  def fromDesignToConditionMatrix(exp: ExperimentalDesign, addEndMissingValues: Boolean = true,
                                  headersSorted: Boolean = false): SimpleMatrix = {

    val allHeaders = exp.samples.flatMap(_.conditions).map(_.category).toList

    val lines: List[List[String]] = exp.samples.toList.map { s ⇒
      val conds = s.conditions
      allHeaders.map(h ⇒ conds.find(_.category == h)).map(c ⇒ if (c.isDefined) c.get.name else "")
    }

    SimpleMatrix(allHeaders, lines, ",", addEndMissingValues, headersSorted)
  }
}


