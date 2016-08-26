package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.PersistentActor
import scala.concurrent.duration.{Deadline, FiniteDuration}


object Master {

  val ResultsTopic = "results"

  def props(workTimeout: FiniteDuration): Props =
    Props(classOf[Master], workTimeout)

  case class Ack(workId: String)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(workId: String, deadline: Deadline) extends WorkerStatus

  private case class WorkerState(ref: ActorRef, status: WorkerStatus, workType: String = "?")

  private case object CleanupTick

}

class Master(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {

  import Master._
  import WorkState._

  val mediator = DistributedPubSub(context.system).mediator

  ClusterClientReceptionist(context.system).registerService(self)

  // persistenceId must include cluster role to support multiple masters
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) ⇒ role + "-master"
    case None ⇒ "master"
  }

  // workers state is not event sourced
  private var workers = Map[String, WorkerState]()

  // workState is event sourced
  private var workState = WorkState.empty

  import context.dispatcher

  val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveRecover: Receive = {
    case event: WorkDomainEvent =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)
  }

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      log.info(s"received RegisterWorker for $workerId")
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender()))
        sender() ! GetWorkerType
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle))
        sender() ! GetWorkerType
        //        if (workState.hasWorkLeft)
        //          sender() ! MasterWorkerProtocol.WorkIsReady
      }

    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
      log.info("worker requesting work... do we have something for him?")
      if (workState.hasWork(workers(workerId).workType)) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle, _)) =>
            val w = workState.nextWork(s.workType)
            if (w.nonEmpty) {
              val work = w.get
              persist(WorkStarted(work.workId)) { event =>
                log.info(s"Giving worker [$workerId] something to do [${work.workId}]")
                workState = workState.updated(event)
                workers += (workerId -> s.copy(status = Busy(work.workId, Deadline.now + workTimeout)))
                sender() ! work
              }
            }
          case _ =>
        }
      }

    case MasterWorkerProtocol.WorkIsDone(workerId, workId, result) =>
      // idempotent
      if (workState.isDone(workId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(workId)
      } else if (!workState.isInProgress(workId)) {
        log.info("Work {} not in progress, reported as done by worker {}", workId, workerId)
      } else {
        log.info("Work {} is done by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkCompleted(workId, result)) { event ⇒
          workState = workState.updated(event)
          mediator ! DistributedPubSubMediator.Publish(ResultsTopic, WorkResult(workId, result))
          // Ack back to original sender
          sender ! MasterWorkerProtocol.Ack(workId)
        }
      }

    case MasterWorkerProtocol.WorkFailed(workerId, workId) =>
      if (workState.isInProgress(workId)) {
        log.info("Work {} failed by worker {}", workId, workerId)
        changeWorkerToIdle(workerId, workId)
        persist(WorkerFailed(workId)) { event ⇒
          workState = workState.updated(event)
          notifyWorkers()
        }
      }

    case work: Work =>
      log.info("master received work...")
      // idempotent
      if (workState.isAccepted(work.workId)) {
        log.info("work is accepted...")
        sender() ! Master.Ack(work.workId)
      } else {
        log.info("Accepted work: {}", work.workId)
        persist(WorkAccepted(work)) { event ⇒
          // Ack back to original sender
          sender() ! Master.Ack(work.workId)
          workState = workState.updated(event)
          notifyWorkers()
        }
      }

    case CleanupTick =>
      for ((workerId, s@WorkerState(_, Busy(workId, timeout), _)) ← workers) {
        if (timeout.isOverdue) {
          log.info("Work timed out: {}", workId)
          workers -= workerId
          persist(WorkerTimedOut(workId)) { event ⇒
            workState = workState.updated(event)
            notifyWorkers()
          }
        }
      }

    case WorkerType(wid, wt) ⇒
      workers += (wid -> workers(wid).copy(workType = wt))
//      log.info(s"workers list with new types: $workers")
      log.info(s"workers types list: ${workers.map(w ⇒ w._2.workType)}")

  }

  def notifyWorkers(): Unit = if (workState.hasWorkLeft) {
    log.info("has work left")
    // could pick a few random instead of all
    workers.foreach {
      case (_, WorkerState(ref, Idle, _)) => ref ! MasterWorkerProtocol.WorkIsReady
      case _ => // worker is busy
    }
  }

  def changeWorkerToIdle(workerId: String, workId: String): Unit =
    workers.get(workerId) match {
      case Some(s@WorkerState(_, Busy(`workId`, _), _)) ⇒
        workers += (workerId -> s.copy(status = Idle))
      case _ ⇒
      // ok, might happen after standby recovery, worker state is not persisted
    }

  // TODO cleanup old workers
  // TODO cleanup old workIds, doneWorkIds

}