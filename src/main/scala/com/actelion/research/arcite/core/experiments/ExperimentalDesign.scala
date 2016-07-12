package com.actelion.research.arcite.core.experiments

import spray.json.DefaultJsonProtocol

/**
  * Created by deffabe1 on 4/25/16.
  *
  * An experimental design is usually the description of the whole experiment and its conditions.
  * It associate experimental conditions to each sample in the experiment.
  *
  */
case class ExperimentalDesign(description: String = "", sampleConditions: Set[ConditionsForSample] = Set())

/**
  * a condition can be described and belongs to a category
  *
  * @param name
  * @param description
  * @param category
  */
case class Condition(name: String, description: String, category: String)

/**
  * one sample in an experiment can have multiple conditions (treatment, barcode, dose, etc.)
  * One condition should enable the mapping between the actual sample and the conditions that define it
  *
  * @param conditions
  */
case class ConditionsForSample(conditions: List[Condition]) //todo list to set?


trait ExpDesignJsonProtocol extends DefaultJsonProtocol {
  implicit val conditionJson = jsonFormat3(Condition)
  implicit val conditionForSampleJson = jsonFormat1(ConditionsForSample)
  implicit val expDesignJson = jsonFormat2(ExperimentalDesign)
}