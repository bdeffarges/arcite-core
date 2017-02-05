package com.actelion.research.arcite.core.utils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/12/01.
  *
  */
class FoldersHelersTest extends FlatSpec with Matchers with LazyLogging {

  "linking folder " should " link all files in the origin folder from the target folder " in {

    val of = new File("./for_testing/gobetween/origin")
    of.mkdirs()
    val tf = new File("./for_testing/gobetween/target")
    tf.mkdir()

    val p1 = of.toPath resolve "a" resolve "b" resolve "c"
    val p2 = of.toPath resolve "a" resolve "b" resolve "e"
    val p3 = of.toPath resolve "a" resolve "d" resolve "f"
    val p4 = of.toPath resolve "a" resolve "d" resolve "w"
    val p5 = of.toPath resolve "a"
    val p6 = of.toPath resolve "a" resolve "b"
    val p7 = of.toPath resolve "c" resolve "d"
    val p8 = of.toPath resolve "b"

    p1.toFile.mkdirs
    p2.toFile.mkdirs
    p3.toFile.mkdirs
    p4.toFile.mkdirs
    p5.toFile.mkdirs
    p6.toFile.mkdirs
    p7.toFile.mkdirs
    p8.toFile.mkdirs

    val txt = "helloworld"
    val asBytes = txt.getBytes(StandardCharsets.UTF_8)
    Files.write(p1 resolve "a.txt", asBytes, CREATE_NEW)
    Files.write(p2 resolve "dd.txt", asBytes, CREATE_NEW)
    Files.write(p3 resolve "ac.txt", asBytes, CREATE_NEW)
    Files.write(p4 resolve "db.txt", asBytes, CREATE_NEW)
    Files.write(p5 resolve "adf.txt", asBytes, CREATE_NEW)
    Files.write(p6 resolve "dewd.txt", asBytes, CREATE_NEW)
    Files.write(p7 resolve "aqqc.txt", asBytes, CREATE_NEW)
    Files.write(p8 resolve "dgb.txt", asBytes, CREATE_NEW)

    FoldersHelpers.deepLinking(of, tf)

    val pt1 = tf.toPath resolve "a" resolve "b" resolve "c"
    val pt2 = tf.toPath resolve "a" resolve "b" resolve "e"
    val pt3 = tf.toPath resolve "a" resolve "d" resolve "f"
    val pt4 = tf.toPath resolve "a" resolve "d" resolve "w"
    val pt5 = of.toPath resolve "a"
    val pt6 = of.toPath resolve "a" resolve "b"
    val pt7 = of.toPath resolve "c" resolve "d"
    val pt8 = of.toPath resolve "b"

    assert(Files.readAllLines(pt1 resolve "a.txt").get(0) == txt)
    assert(Files.readAllLines(pt2 resolve "dd.txt").get(0) == txt)
    assert(Files.readAllLines(pt3 resolve "ac.txt").get(0) == txt)
    assert(Files.readAllLines(pt4 resolve "db.txt").get(0) == txt)
    assert(Files.readAllLines(pt5 resolve "adf.txt").get(0) == txt)
    assert(Files.readAllLines(pt6 resolve "dewd.txt").get(0) == txt)
    assert(Files.readAllLines(pt7 resolve "aqqc.txt").get(0) == txt)
    assert(Files.readAllLines(pt8 resolve "dgb.txt").get(0) == txt)

    FoldersHelpers.deleteRecursively(tf)
    FoldersHelpers.deleteRecursively(of)
  }
}
