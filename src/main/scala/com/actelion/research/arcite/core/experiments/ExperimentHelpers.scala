package com.actelion.research.arcite.core.experiments

import java.nio.file.Files

import com.actelion.research.arcite.core
import com.actelion.research.arcite.core.transforms.TransformCompletionFeedback
import com.actelion.research.arcite.core.utils.WriteFeedbackActor

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
  * Created by Bernard Deffarges on 2017/04/12.
  *
  *
  */
class ExperimentHelpers(experiment: Experiment) {

  lazy val successfullTransforms: Set[TransformCompletionFeedback] = {
    import spray.json._

    import scala.collection.convert.wrapAsScala._

    val visitor = ExperimentFolderVisitor(experiment)
    val allTransforms = visitor.transformFolderPath.toFile
      .listFiles.filter(f ⇒ f.listFiles.exists(ff ⇒ ff.getName == core.successFile))
      .map(_.listFiles.find(f ⇒ f.getName == WriteFeedbackActor.FILE_NAME))
      .map(f ⇒ Files.readAllLines(f.get.toPath).mkString(" ").parseJson.convertTo[TransformCompletionFeedback]).toSet
  }

  def getParentTransform(transfUID: String): Option[TransformCompletionFeedback] = {
    val t = successfullTransforms.find(tf ⇒ tf.transform == transfUID)

    if (t.isDefined && t.get.source.fromTransform.isDefined)
      successfullTransforms.find(_.transform == t.get.source.fromTransform.get)
    else None
  }
}

