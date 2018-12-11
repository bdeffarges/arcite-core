package com.idorsia.research.arcite.core.utils

/**
  * Created by Bernard Deffarges on 13/03/16.
  */
object MemoryUsage {

  def memInMB(): (Double, Double) = {
    val mb = Math.pow(2, 20)
    val tot = "%.2f".format(Runtime.getRuntime().totalMemory() / mb).toDouble
    val used = "%.2f".format((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / mb).toDouble

    (tot, used)
  }

  def meminMBAsString(): String = {
    val m = memInMB()
    s"Memory used ${m._2} / ${m._1} (MB)"
  }

  def printMemInMB() = println(meminMBAsString())
}


