package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.persistence.PersistentActor
import com.actelion.research.arcite.core.transforms.TransfDefMsg._
import com.actelion.research.arcite.core.transforms.cluster.Frontend._
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinitionIdentity}
import com.actelion.research.arcite.core.utils.WriteFeedbackActor
import com.actelion.research.arcite.core.utils.WriteFeedbackActor.WriteFeedback

import scala.concurrent.duration.{Deadline, FiniteDuration}

//todo because of event sourcing, it will also include saved jobs...
object Master {

  def props(workTimeout: FiniteDuration): Props = Props(classOf[Master], workTimeout)

  case class Ack(transf: Transform)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(trans: Transform, deadline: Deadline) extends WorkerStatus

  private case class WorkerState(ref: ActorRef, status: WorkerStatus, transDef: Option[TransformDefinitionIdentity])

  private case object CleanupTick

}

//todo timeout should be defined by worker or job type
class Master(workTimeout: FiniteDuration) extends PersistentActor with ActorLogging {

  import Master._
  import WorkState._

  ClusterClientReceptionist(context.system).registerService(self)

  private val feedbackActor = context.actorOf(WriteFeedbackActor.props())

  // persistenceId must include cluster role to support multiple masters
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) ⇒ role + "-master"
    case None ⇒ "master"
  }

  // workers state is not event sourced
  private var workers = Map[String, WorkerState]()
  private var transformDefs = Set[TransformDefinitionIdentity]()


  // workState is event sourced
  private var workState = WorkState.empty

  import context.dispatcher

  private val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveRecover: Receive = {
    case event: WorkStatus =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.info(s"Replayed ${event.getClass.getSimpleName}")
  }

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) ⇒
      log.info(s"received RegisterWorker for $workerId")
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender()))
        sender() ! GetTransfDefId(workerId)
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle, None))
        sender() ! GetTransfDefId(workerId)
      }


    case MasterWorkerProtocol.WorkerRequestsWork(workerId) ⇒
      log.info(s"total pending jobs = ${workState.numberOfPendingJobs()} worker requesting work... do we have something to be done?")
      val td = workers(workerId).transDef
      if (td.isDefined && workState.hasWork(td.get.fullName)) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle, _)) =>
            if (s.transDef.isDefined) {
              val w = workState.nextWork(s.transDef.get.fullName)
              if (w.nonEmpty) {
                //todo maybe we don't need both checks
                val transf = w.get
                persist(WorkInProgress(transf, 0.0)) { event =>
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


    case wid: MasterWorkerProtocol.WorkerSuccess ⇒
      // write transform feedback report
      feedbackActor ! WriteFeedback(wid)

      // idempotent
      if (workState.isDone(wid.transf.uid)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(wid.transf)
      } else if (!workState.isInProgress(wid.transf.uid)) {
        log.info(s"Work ${wid.transf} not in progress, reported as done by worker ${wid.workerId}")
      } else {
        log.info(s"Work ${wid.transf} is done by worker ${wid.workerId}")
        changeWorkerToIdle(wid.workerId, wid.transf)
        persist(WorkState.WorkCompleted(wid.transf)) { event ⇒
          workState = workState.updated(event)

          // Ack back to original sender
          sender() ! MasterWorkerProtocol.Ack(wid.transf)
        }
      }


    case wf: MasterWorkerProtocol.WorkerFailed ⇒
      // write transform feedback report
      feedbackActor ! WriteFeedback(wf)

      if (workState.isInProgress(wf.transf.uid)) {
        log.info(s"Work ${wf.transf.uid} failed in worker ${wf.workerId}")
        changeWorkerToIdle(wf.workerId, wf.transf)
        persist(WorkState.WorkerFailed(wf.transf)) { event ⇒
          workState = workState.updated(event)
          notifyWorkers()
        }
      }


    case wp : MasterWorkerProtocol.WorkerInProgress ⇒
      log.info(s"got Work in Progress update: ${wp.percentCompleted} % of worker ${wp.workerId}")
      workState = workState.updated(WorkState.WorkInProgress(wp.transf, wp.percentCompleted))


    case transf: Transform =>
      log.info(s"master received transform [${transf.uid}]")
      // idempotent
      if (workState.isAccepted(transf.uid)) {
        log.info(s"transform [${transf.uid}] is accepted, Ack is returned.")
        sender() ! Master.Ack(transf)
      } else {
        log.info(s"transform [${transf.uid}] accepted.")
        persist(WorkAccepted(transf)) { event ⇒
          // Ack back to original sender
          sender() ! Master.Ack(transf)
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


    case TransformType(wid, wt) ⇒
      workers += (wid -> workers(wid).copy(transDef = Some(wt)))
      transformDefs += wt
      log.info(s"[${transformDefs.size}] workers transforms def. types")
    //      log.info(s"workers transforms def. types: $transformDefs")


    case QueryWorkStatus(transfUID) ⇒
      sender() ! workState.jobState(transfUID)


    case GetAllJobsStatus ⇒
      sender() ! workState.workStateSummary()


    case GetRunningJobsStatus ⇒
      sender() ! workState.runningJobsSummary()

    case GetAllTransfDefs ⇒
      sender() ! ManyTransfDefs(transformDefs)


    case ft: FindTransfDefs ⇒
      sender() ! ManyTransfDefs(findTransformers(ft.search))


    case GetTransfDef(d) ⇒
      transformDefs.find(_.digestUID == d) match {
        case Some(x) ⇒ sender() ! OneTransfDef(x)
        case _ ⇒ sender() ! NoTransfDefFound
      }

    case GetTransfDefFromName(fn) ⇒
      transformDefs.find(_.fullName == fn) match {
        case Some(x) ⇒ sender() ! OneTransfDef(x)
        case _ ⇒ sender() ! NoTransfDefFound
      }
  }


  def notifyWorkers(): Unit = if (workState.hasWorkLeft) {
    log.info(s"workstate=${workState.workStateSizeSummary()}, has some work left ?")

    // could pick a few random instead of all
    workers.foreach {
      case (_, WorkerState(ref, Idle, _)) => ref ! MasterWorkerProtocol.WorkIsReady
      case _ => log.info("worker is busy. ")
    }
  }


  def findTransformers(search: String): Set[TransformDefinitionIdentity] = {

    transformDefs.filter(td ⇒ td.fullName.name.toLowerCase.contains(search)).take(10) ++
      transformDefs.filter(td ⇒ td.fullName.organization.toLowerCase.contains(search)).take(5) ++
      transformDefs.filter(td ⇒ td.description.summary.toLowerCase.contains(search)) ++
      transformDefs.filter(td ⇒ td.description.consumes.toLowerCase.contains(search)) ++
      transformDefs.filter(td ⇒ td.description.produces.toLowerCase.contains(search))
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
