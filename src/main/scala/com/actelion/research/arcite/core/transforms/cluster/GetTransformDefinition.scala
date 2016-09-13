package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.TransformDefinition

/**
  * Created by deffabe1 on 7/21/16.
  */

/**
  * return transformer definition for given worker
  * @param workerId
  */
case class GetTransformDefinition(workerId: String)

case class GetTransformDefinitionFromDigest(digest: String)

/**
  * return transform type for worker and transf def.
  * @param workerID
  * @param transDef
  */
case class TransformType(workerID: String, transDef: TransformDefinition)


