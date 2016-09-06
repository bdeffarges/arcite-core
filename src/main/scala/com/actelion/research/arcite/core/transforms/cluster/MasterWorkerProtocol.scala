package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.Transform

object MasterWorkerProtocol {

  // Messages from Workers
  case class RegisterWorker(workerId: String)
  case class WorkerRequestsWork(workerId: String)
  case class WorkIsDone(workerId: String, transf: Transform, result: Any)
  case class WorkFailed(workerId: String, transf: Transform)

  // Messages to Workers
  case object WorkIsReady
  case class Ack(transform: Transform)
}