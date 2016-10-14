package com.actelion.research.arcite.core.experiments


/**
  * Created by deffabe1 on 4/25/16.
  *
  * An experimental design is usually the description of the whole experiment and its conditions.
  * It associate experimental conditions to each sample in the experiment.
  *
  */
case class ExperimentalDesign(description: String = "", sampleConditions: Set[ConditionsForSample] = Set())

/**
  * a condition that belongs to a category (e.g. 323223 in category barcode)
  *
  * @param name
  * @param description some added information about the condition
  * @param category
  */
case class Condition(name: String, description: String, category: String)

/**
  * each sample in an experiment can have multiple conditions (treatment, barcode, dose, etc.). Usually the total
  * number of conditions per sample should be the same for the whole experiment.
  * One condition or a set of conditions should uniquely define each sample (e.g. in microarray studies the
  * slide barcode and the array id uniquely distinguish each array in the experiment).
  *
  * @param conditions
  */
case class ConditionsForSample(conditions: Set[Condition]) //todo list to set?

