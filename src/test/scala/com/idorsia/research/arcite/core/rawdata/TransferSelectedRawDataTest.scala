package com.idorsia.research.arcite.core.rawdata

import java.nio.file.Paths

import com.idorsia.research.arcite.core.utils.FoldersHelpers
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
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
  * Created by Bernard Deffarges on 2016/12/14.
  *
  */
class TransferSelectedRawDataTest extends FlatSpec with Matchers with LazyLogging {

  "transfer folder from test " should " prepare map for  copy files to the gobetween folder in the same file structure " in {

    val source = Paths.get("./for_testing", "file_transfer")

    assert(source.toFile.exists())

    val targetParent = Paths.get("./for_testing", "gobetween")

    assert(targetParent.toFile.exists())

    val target = targetParent resolve "mock_project"

    targetParent.toFile.mkdir()

    val fileList = List("a", "b")

    val map1 = FoldersHelpers.buildTransferFromSourceFileMap(source, fileList, ".*33\\.txt".r, target)

    assert(map1.size == 2)

    val map2 = FoldersHelpers.buildTransferFromSourceFileMap(source, fileList, ".*33|44\\.txt".r, target)

    assert(map2.size == 4)
  }

}

