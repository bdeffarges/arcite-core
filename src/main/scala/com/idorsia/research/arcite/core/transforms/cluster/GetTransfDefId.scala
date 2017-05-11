package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.transforms.TransformDefinitionIdentity


/**
  * Created by deffabe1 on 7/21/16.
  */

/**
  * return transformer definition for given worker
  * @param workerId
  */
case class GetTransfDefId(workerId: String)

/**
  * return transform type for worker and transf def.
  * @param workerID
  * @param transDef
  */
case class TransformType(workerID: String, transDef: TransformDefinitionIdentity)


