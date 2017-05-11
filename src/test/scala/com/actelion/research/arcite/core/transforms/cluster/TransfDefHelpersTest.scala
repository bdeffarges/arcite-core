package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.{TransformDefinitionIdentity, TransformDescription}
import com.actelion.research.arcite.core.utils.FullName
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
  * Created by Bernard Deffarges on 2017/04/18.
  *
  */
class TransfDefHelpersTest extends FlatSpec with Matchers {

  val transfDefs = TransformDefinitionIdentity(FullName("com.hello.world", "Brilliant data normalization", "brillNorm"),
    TransformDescription("very interesting z scoring", "some raw matrix", "a normalized matrix")) ::
    TransformDefinitionIdentity(FullName("com.hello.world", "Microarray QC", "MicQC"),
      TransformDescription("produces the default microarray qcs", "normalized matrix", "QC reports")) ::
    TransformDefinitionIdentity(FullName("com.hello.world", "Nanostring analysis", "NanostringA"),
      TransformDescription("default nanostring analysis", "raw nanostring files", "a working web application")) ::
    TransformDefinitionIdentity(FullName("org.bye.earth", "Deeplearning for NGS", "DeepLearnNGS"),
      TransformDescription("deeplearning network for NGS", "a training set", "a great prediction")) ::
    TransformDefinitionIdentity(FullName("org.bye.earth", "space shuttle launch", "spaceShuttleLaunch"),
      TransformDescription("space shuttle launch", "takes the space shuttle", "produces the launch of the shuttle")) ::
    TransformDefinitionIdentity(FullName("org.bye.earth", "fast-free-ride_to-mars", "fast-ride2mars"),
      TransformDescription("takes anybody who wants to mars", "somebody", "a nice ride to Mars")) ::
    TransformDefinitionIdentity(FullName("org.bye.earth", "free ride to mars", "ride2mars"),
      TransformDescription("takes anybody who wants to mars", "somebody", "a nice ride to Mars")) ::
    TransformDefinitionIdentity(FullName("ch.hello.world", "truth teller", "TrutH"),
      TransformDescription("will always tell you the truth", "start from anything, even not true", "returns ThE TrUtH")) ::
    TransformDefinitionIdentity(FullName("ch.hello.world", "Schrödinger cat", "SchrCAt"),
      TransformDescription("Is the cat alive or dead?", "will take any cat", "tells whether the cat is alive or dead")) :: Nil

  val transfDefHelpers = new TransfDefHelpers(transfDefs.toSet)

  "find the right transf def " should " return those that contain the passed string " in {

    assert(transfDefHelpers.findTransformers("Brilliant data", 3).head == transfDefs.head)
    assert(transfDefHelpers.findTransformers("Brilliant data", 3).size == 1)

    assert(transfDefHelpers.findTransformers("hello world brillNorm", 10).head == transfDefs.head)
    assert(transfDefHelpers.findTransformers("hello world brillNorm", 10).size == 5)
    assert(transfDefHelpers.findTransformers("hello world", 10).size == 5)
    assert(transfDefHelpers.findTransformers("fast-ride", 10).size == 2)
    assert(transfDefHelpers.findTransformers("fast-ride", 1).head.fullName.shortName == "fast-ride2mars")

    assert(transfDefHelpers.findTransformers("qcs", 10).size == 1)
    assert(transfDefHelpers.findTransformers("fast", 10).size == 1)
    assert(transfDefHelpers.findTransformers("qcs", 10).head == transfDefs.tail.head)
    assert(transfDefHelpers.findTransformers("hello.world@@schrcat", 1).head == transfDefs.last)
  }
}
