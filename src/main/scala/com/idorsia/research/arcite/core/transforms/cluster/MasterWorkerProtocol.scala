package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.transforms.Transform
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkerJobCompletion, WorkerJobFailed, WorkerJobSuccessFul}
import com.idorsia.research.arcite.core.utils

object MasterWorkerProtocol {

  // Messages from Workers
  case class RegisterWorker(workerId: String)

  case class WorkerRequestsWork(workerId: String)

  case class WorkerProgress(progress: Int)

  case class WorkerCompleted(workerId: String, transf: Transform,
  result: WorkerJobCompletion,
  startTime: String,
  endTime: String = utils.getCurrentDateAsString())

  case class WorkerInProgress(workerId: String, transf: Transform, startTime: String, percentCompleted: Int)

  /**
    * in case the worker fails while processing its job.
    * It's not the worker itself failing.
    * @param workerId
    * @param transf
    */
  case class WorkFailed(workerId: String, transf: Transform)

  // Messages to Workers
  case object WorkIsReady

  case class Ack(transform: Transform)

}