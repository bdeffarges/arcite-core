package com.actelion.research.arcite.core.transforms.cluster

/**
  * Created by deffabe1 on 7/21/16.
  */
case object GetWorkerType

case class GetWorkerTypeFor(workerID: String)

case class WorkerType(workerID: String, wt: String)
