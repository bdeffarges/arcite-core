package com.idorsia.research.arcite.core.transforms.cluster

import com.idorsia.research.arcite.core.transforms.Transform
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.WorkerJobCompletion
import com.idorsia.research.arcite.core.utils

object MasterWorkerProtocol {

  /**
    * messages from workers
    */

  /**
    * A worker can register itself into the cluster, the worker is the parent actor and the watcher
    * of an actor that will actually do the work.
    *
    * @param workerId
    */
  case class RegisterWorker(workerId: String)

  /**
    * a worker is ready to do some work, therefore it informs the cluster.
    *
    * @param workerId
    */
  case class WorkerRequestsWork(workerId: String)

  /**
    * the worker is completed (means its belonging actor which did the work informed about
    * the completion of the work and whether it was successful or not)
    * @param workerId
    * @param transf
    * @param result
    * @param startTime
    * @param endTime
    */
  case class WorkerCompleted(workerId: String, transf: Transform, result: WorkerJobCompletion,
                             startTime: String, endTime: String = utils.getCurrentDateAsString())

  /**
    * a message send from the Worker informing the cluster about the progress of a transform.
    * @param workerId
    * @param transf
    * @param startTime
    * @param percentCompleted
    */
  case class WorkerInProgress(workerId: String, transf: Transform, startTime: String, percentCompleted: Int)

  /**
    * in case the worker fails while processing its job.
    * It's not the worker itself which is failing but the actor that does the "work" reported some problems
    * and therefore fails.
    *
    * @param workerId
    * @param transf
    */
  case class WorkFailed(workerId: String, transf: Transform)

  // Messages to Workers
  case object WorkIsReady

  case class Ack(transform: Transform)

}