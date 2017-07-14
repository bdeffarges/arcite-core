package com.idorsia.research.arcite.core.experiments

import com.idorsia.research.arcite.core.TestHelpers
import org.scalatest.{FlatSpec, Matchers}

/**
  *
  * arcite-core
  *
  * Copyright (C) 2016 Karanar Software (B. Deffarges)
  * 38 rue Wilson, 68170 Rixheim, France
  *
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
  * Created by Bernard Deffarges on 2017/02/18.
  *
  */
class ExperimentTest extends FlatSpec with Matchers {

  " get URI for experiment " should " return a unique well formed uri " in {

    val exp1 = TestHelpers.cloneForFakeExperiment(TestHelpers.experiment1)

    val uriSlash = ExpUriHelper(exp1).expURIWithSlashArcite
    val uriSubDomain = ExpUriHelper(exp1).expURIWithSubdomain

    val expected1 = s"arcite.idorsia.com/com/idorsia/research/bioinfo/mock/${exp1.uid}"
    val expected2 = s"www.idorsia.com/arcite/com/idorsia/research/bioinfo/mock/${exp1.uid}"

    assert(uriSlash == expected2)
    assert(uriSubDomain == expected1)
  }
}
