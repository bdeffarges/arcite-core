package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.TransformDefinition

/**
  * Created by deffabe1 on 7/21/16.
  */
case class GetTransformDefinition(workerId: String)

//case class WorkerTransDefinition(transfDef: TransformDefinition)

case class TransformType(workerID: String, transDef: TransformDefinition)
