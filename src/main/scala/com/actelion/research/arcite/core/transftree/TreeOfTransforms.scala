package com.actelion.research.arcite.core.transftree

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
  * Created by Bernard Deffarges on 2016/12/13.
  *
  * The aim of this package is to enable executing multiple transforms one after
  * the other automatically. So, a user can start a tree of transform as a whole
  * data analysis process (e.g. normalization, QC, analysis...). As it's a tree
  * it can execute some transforms (on different branches)
  * in parallel on different workers.
  * Thus a tree of transforms is a set of transform chains.
  * It starts with a root transform which is the first transform to be processed.
  * Then comes the next transforms in the tree, it can be one or multiple on different
  * branhces.
  * The user can decide to start anywhere in the tree as long as the input for the
  * transform in the given node in the tree is provided (as it's not root anymore,
  * it will usually be the result of another transform or maybe another execution
  * of this or another tree of transform).
  * The code in the tree of transform package (transftree) is responsible for managing
  * the definition and the execution of tree of transforms as describe herein.
  *
  */
class TreeOfTransforms {

}
