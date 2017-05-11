package com.idorsia.research.arcite.core.utils

import java.io.File

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}


/**
  * Created by deffabe1 on 3/8/16.
  */
class GetDigestTest extends FlatSpec with Matchers {

  val config = ConfigFactory.load()

  val pathToTest = config.getString("arcite.test.digest") + File.separator
  println(s"path to test files: $pathToTest")

  "get digest " should "get the same digest when called twice on the same file " in {

    val f1 = new File(s"${pathToTest}AMS0089/160217_br/160217_br_257236312090_S01_GE2_1105_Oct12_1_1.jpg")
    val f2 = new File(s"${pathToTest}AMS0089/160217_br/160217_br_257236312090_S01_GE2_1105_Oct12_1_3.jpg")

    val digest1 = GetDigest.getDigest(f1)
    val digest2 = GetDigest.getDigest(f1)
    val digest3 = GetDigest.getDigest(f2)

    println(s"digest1= $digest1")
    println(s"digest2= $digest2")
    println(s"digest3= $digest3")

    digest1 should equal(digest2)

    digest2 should not equal (digest3)
  }

  "get folder digest " should "get the same digest when called twice on the same folder " in {

    val folder1 = new File(s"${pathToTest}AMS0089/160217_br/")
    val folder2 = new File(s"${pathToTest}AMS0089/160219_br/")

    val digest1 = GetDigest.getFolderContentDigest(folder1)
    val digest2 = GetDigest.getFolderContentDigest(folder1)
    val digest3 = GetDigest.getFolderContentDigest(folder2)

    println(s"digest1= $digest1")
    println(s"digest2= $digest2")
    println(s"digest3= $digest3")

    digest1 should equal(digest2)

    digest2 should not equal (digest3)
  }
}

