package com.actelion.research.arcite.core.transforms.cluster

import com.actelion.research.arcite.core.transforms.Transform
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.{WorkCompletionStatus, WorkFailed, WorkSuccessFull}

object MasterWorkerProtocol {

  // Messages from Workers
  case class RegisterWorker(workerId: String)
  case class WorkerRequestsWork(workerId: String)

  sealed trait WorkerIsDone {
    def workerId: String
    def transf: Transform
    def result: WorkCompletionStatus
  }
  case class WorkerSuccess(workerId: String, transf: Transform, result: WorkSuccessFull) extends WorkerIsDone
  case class WorkerFailed(workerId: String, transf: Transform, result: WorkFailed) extends WorkerIsDone

  // Messages to Workers
  case object WorkIsReady
  case class Ack(transform: Transform)
}