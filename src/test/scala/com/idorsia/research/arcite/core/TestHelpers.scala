package com.idorsia.research.arcite.core

import java.util.UUID

import com.idorsia.research.arcite.core.experiments.{Condition, Sample, Experiment, ExperimentalDesign}
import com.idorsia.research.arcite.core.utils.Owner

/**
  * Created by deffabe1 on 3/18/16.
  */
object TestHelpers {

  val organization = "com.idorsia.research.mock"
  val organization2 = "com.idorsia.research.bioinfo"
  val organization3 = "com.fake.wrong"
  val person1 = "B. Deffarges"
  val person2 = "B. Renault"
  val person3 = "M. Best Scientist of planet earth"

  val owner1 = Owner(organization, person1)
  val owner2 = Owner(organization, person2)
  val owner3 = Owner(organization, person3)
  val owner4 = Owner(organization2, person3)
  val owner5 = Owner(organization3, person3)

  val cond1 = Condition("hw", "helloworld", "greetings")
  val cond2 = Condition("he", "helloearth", "greetings")
  val cond3 = Condition("hm", "hellomars", "greetings")
  val cond4 = Condition("hj", "hellojupiter", "greetings")
  val cond11 = Condition("1", "1", "sampleid")
  val cond22 = Condition("2", "2", "sampleid")
  val cond33 = Condition("3", "3", "sampleid")
  val cond44 = Condition("4", "4", "sampleid")

  val condFS1 = Sample((cond1 :: cond11 :: Nil).toSet)
  val condFS2 = Sample((cond2 :: cond22 :: Nil).toSet)
  val condFS3 = Sample((cond3 :: cond33 :: Nil).toSet)
  val condFS4 = Sample((cond4 :: cond44 :: Nil).toSet)

  val expDesign1 = ExperimentalDesign("hello", (condFS1 :: condFS2 :: condFS3 :: condFS4 :: Nil).toSet)
  val expDesign2 = ExperimentalDesign("hello", (condFS1 :: condFS2 :: condFS3 :: condFS4 :: Nil).toSet)

  val experiment1 = Experiment("flying to Mars", "Indeed, I will fly to Mars...", owner1, design = expDesign1)
  val experiment2 = Experiment("flying to Neptune", "Flying to Neptune is better...", owner2, design = expDesign2)

  val experiment3 = Experiment("flying to the next galaxy ", "Flying far away...", owner4, design = expDesign2)

  val experiment4 = Experiment("flying to the next galaxy ", "Flying far away...", owner5, design = expDesign2)

  def cloneForFakeExperiment(exp: Experiment): Experiment =
    exp.copy(name = s"${exp.name}--${UUID.randomUUID().toString}")

}
