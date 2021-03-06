package com.idorsia.research.arcite.core.utils

import java.io.File

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
  * Created by Bernard Deffarges on 2016/10/25.
  *
  */
class FileVisitTest extends FlatSpec with Matchers {

  val folder1 = new File("./for_testing/find_files/")

  "get file information " should "return information about all files in subfolder and children " in {

    val files = FileVisitor.getFilesInformation(folder1.toPath)

    println(files)
    
    files.size should equal (6)
  }


}
