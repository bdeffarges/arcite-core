package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.PersistentActor
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition, TransformResult}
import com.actelion.research.arcite.core.transforms.cluster.Frontend.{AllJobsFeedback, _}

import scala.concurrent.duration.{Deadline, FiniteDuration}

//todo because of event sourcing, it will also include saved jobs...
object Master {

  val ResultsTopic = "results" //todo remove? originally, it was intended for subscribe/publish

  def props(workTimeout: FiniteDuration): Props = Props(classOf[Master], workTimeout)

  case class Ack(transf: Transform)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(trans: Transform, deadline: Deadline) extends WorkerStatus

  private case class WorkerState(ref: ActorRef, status: WorkerStatus, transDef: Option[TransformDefinition])

  private case object CleanupTick

}

//todo timeout should be defined by worker or job type
class Master(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {

  import Master._
  import WorkState._

  //todo do we need the mediator for anything else than Pub/sub??
  val mediator = DistributedPubSub(context.system).mediator
  log.info(s"Pub/Sub mediator= $mediator")

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
        sender() ! GetTransformDefinition
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle, None))
        sender() ! GetTransformDefinition
      }

    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
      log.info(s"total pending jobs = ${workState.pendingJobs()} worker requesting work... do we have something for him?")
      val td = workers(workerId).transDef
      if (td.isDefined && workState.hasWork(td.get)) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle, _)) =>
            if (s.transDef.isDefined) {
              val w = workState.nextWork(s.transDef.get)
              if (w.nonEmpty) { //todo maybe we don't need both checks
                val transf = w.get
                persist(WorkStarted(transf)) { event =>
                  log.info(s"Giving worker [$workerId] something to do [${transf}]")
                  workState = workState.updated(event)
                  workers += (workerId -> s.copy(status = Busy(transf, Deadline.now + workTimeout)))
                  sender() ! transf
                }
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
          mediator ! DistributedPubSubMediator.Publish(ResultsTopic, TransformResult(workId, result))

          // Ack back to original sender
          sender() ! MasterWorkerProtocol.Ack(workId)
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

    case work: Transform =>
      log.info("master received work...")
      // idempotent
      if (workState.isAccepted(work)) {
        log.info("work is accepted...")
        sender() ! Master.Ack(work)
      } else {
        log.info("Accepted work: {}", work)
        persist(WorkAccepted(work)) { event ⇒
          // Ack back to original sender
          sender() ! Master.Ack(work)
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
      workers += (wid -> workers(wid).copy(transDef = wt))
      //      log.info(s"workers list with new types: $workers")
      log.info(s"workers types list: ${workers.map(w ⇒ w._2.transDef)}")

    case QueryWorkStatus(workId) ⇒
      if (workState.isDone(workId)) {
        sender() ! JobIsCompleted(s"job [$workId] successfully completed ")
      } else if (workState.isInProgress(workId)) {
        sender() ! JobIsRunning(0) //todo need to get the percentage completion from somewhere
      } else if (workState.isAccepted(workId)) {
        sender() ! JobQueued //todo does not say enough
      } else {
        sender() ! JobLost //todo is it really like that?
      }

    case AllJobsStatus ⇒
      sender() ! AllJobsFeedback(workState.pendingTransforms.toSet, workState.jobsInProgress.values.toSet, workState.jobsDone)

    case QueryJobInfo(qji) ⇒
      sender() ! workState.jobInfo(qji) //todo implement
  }

  def notifyWorkers(): Unit = if (workState.hasWorkLeft) {
    log.info(s"workstate=${workState.workstateSummary()} ,has some work left ?")
    // could pick a few random instead of all
    workers.foreach {
      case (_, WorkerState(ref, Idle, _)) => ref ! MasterWorkerProtocol.WorkIsReady
      case _ => // worker is busy
    }
  }

  def changeWorkerToIdle(workerId: String, transf: Transform): Unit =
    workers.get(workerId) match {
      case Some(s@WorkerState(_, Busy(`transf`, _), _)) ⇒
        workers += (workerId -> s.copy(status = Idle))
      case _ ⇒
      // ok, might happen after standby recovery, worker state is not persisted
    }

  // TODO cleanup old workers
  // TODO cleanup old workIds, doneWorkIds

}