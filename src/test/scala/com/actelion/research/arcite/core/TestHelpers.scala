package com.actelion.research.arcite.core

import com.actelion.research.arcite.core.experiments.{Condition, ConditionsForSample, Experiment, ExperimentalDesign}
import com.actelion.research.arcite.core.utils.Owner

/**
  * Created by deffabe1 on 3/18/16.
  */
object TestHelpers {

  val organization = "com.actelion.research.microarray"
  val person1 = "B. Deffarges"
  val person2 = "B. Renault"

  val owner1 = Owner(organization, person1)
  val owner2 = Owner(organization, person2)

  val cond1 = Condition("hw", "helloworld", "greetings")
  val cond2 = Condition("he", "helloearth", "greetings")
  val cond3 = Condition("hm", "hellomars", "greetings")
  val cond4 = Condition("hj", "hellojupiter", "greetings")
  val cond11 = Condition("1", "1", "sampleid")
  val cond22 = Condition("2", "2", "sampleid")
  val cond33 = Condition("3", "3", "sampleid")
  val cond44 = Condition("4", "4", "sampleid")

  val condFS1 = ConditionsForSample(cond1 :: cond11 :: Nil)
  val condFS2 = ConditionsForSample(cond2 :: cond22 :: Nil)
  val condFS3 = ConditionsForSample(cond3 :: cond33 :: Nil)
  val condFS4 = ConditionsForSample(cond4 :: cond44 :: Nil)

  val expDesign1 = ExperimentalDesign("hello", (condFS1 :: condFS2 :: condFS3 :: condFS4 :: Nil).toSet)
  val expDesign2 = ExperimentalDesign("hello", (condFS1 :: condFS2 :: condFS3 :: condFS4 :: Nil).toSet)

  val experiment1 = Experiment("flying to Mars", "Indeed, I will fly to Mars...", owner1, design = expDesign1)
  val experiment2 = Experiment("flying to Neptune", "Flying to Neptune is better...", owner1, design = expDesign2)

}
