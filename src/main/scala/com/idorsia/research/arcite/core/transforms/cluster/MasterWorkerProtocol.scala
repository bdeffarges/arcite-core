package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.transforms.Transform
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkCompletionStatus, WorkFailed, WorkSuccessFull}
import com.idorsia.research.arcite.core.utils

object MasterWorkerProtocol {

  // Messages from Workers
  case class RegisterWorker(workerId: String)

  case class WorkerRequestsWork(workerId: String)

  case class WorkerProgress(progress: Int)

  sealed trait WorkerIsDone {
    def workerId: String

    def transf: Transform

    def result: WorkCompletionStatus

    def startTime: String

    def endTime: String
  }

  case class WorkerSuccess(workerId: String, transf: Transform,
                           result: WorkSuccessFull,
                           startTime: String,
                           endTime: String = utils.getCurrentDateAsString()) extends WorkerIsDone


  case class WorkerFailed(workerId: String, transf: Transform,
                          result: WorkFailed,
                          startTime: String,
                          endTime: String = utils.getCurrentDateAsString()) extends WorkerIsDone


  case class WorkerInProgress(workerId: String, transf: Transform, startTime: String, percentCompleted: Int)


  // Messages to Workers
  case object WorkIsReady

  case class Ack(transform: Transform)

}