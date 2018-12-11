package com.idorsia.research.arcite.core.transforms.cluster

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.idorsia.research.arcite.core.transforms.TransfDefMsg._
import com.idorsia.research.arcite.core.transforms.cluster.Frontend._
import com.idorsia.research.arcite.core.transforms.cluster.MasterWorkerProtocol.{WorkIsReady, WorkerCompleted}
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.WorkerJobFailed
import com.idorsia.research.arcite.core.transforms.{Transform, TransformDefinitionIdentity}
import com.idorsia.research.arcite.core.utils
import com.idorsia.research.arcite.core.utils.WriteFeedbackActor.WriteFeedback

import scala.concurrent.duration.{Deadline, FiniteDuration}

object Master {

  def props(workTimeout: FiniteDuration): Props = Props(classOf[Master], workTimeout)

  case class Ack(transf: Transform)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(trans: Transform, deadline: Deadline) extends WorkerStatus

  private case class WorkerState(ref: ActorRef, status: WorkerStatus, transDef: Option[TransformDefinitionIdentity])

  private case object CleanupTick

}

class Master(workTimeout: FiniteDuration) extends Actor with ActorLogging {

  import Master._
  import WorkState._

  ClusterClientReceptionist(context.system).registerService(self)

  // workers state is not event sourced
  private var workers = Map[String, WorkerState]()
  private var transformDefs = Set[TransformDefinitionIdentity]()

  // workState is event sourced
  private var workState = WorkState.empty

  import context.dispatcher

  private val cleanupTask = context.system.scheduler.schedule(
    workTimeout / 2, workTimeout / 2, self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()


  override def receive: Receive = {
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
      if (workState.numberOfPendingJobs() > 0) {
        log.info(
          s"""total pending jobs = ${workState.numberOfPendingJobs()}
             |worker requesting work... do we have something to be done?""".
            stripMargin)
      }

      val td = workers(workerId).transDef
      if (td.isDefined && workState.hasWork(td.get.fullName)) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle, _)) =>
            if (s.transDef.isDefined) {
              val w = workState.nextWork(s.transDef.get.fullName)
              if (w.nonEmpty) {
                //todo maybe we don't need both checks
                val transf = w.get
                log.info(s"Giving worker [$workerId] something to do [${transf.transfDefName.toString}]")
                workState = workState.updated(WorkInProgress(transf))
                workers += (workerId -> s.copy(status = Busy(transf, Deadline.now + workTimeout)))
                sender() ! transf
              }
            }

          case m: Any =>
            log.info(s"worker state= ${m.toString}")
        }
      }


    case wid: MasterWorkerProtocol.WorkerCompleted ⇒
      // write transform feedback report
      log.info(s"received work completed ${wid.toString}")
      proxyToExpMgrActor ! WriteFeedback(wid)

      // idempotent
      if (workState.isDone(wid.transf.uid)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(wid.transf)
      } else if (!workState.isInProgress(wid.transf.uid)) {
        log.info(s"Work ${wid.transf} not in progress, reported as done by worker ${wid.workerId}")
      } else {
        log.info(s"Work ${wid.transf} is done by worker ${wid.workerId}")
        changeWorkerToIdle(wid.workerId, wid.transf)
        workState = workState.updated(WorkState.WorkCompleted(wid.transf))
        // Ack back to original sender
        log.info(s"sending ACK to ${sender().toString()}")
        sender() ! MasterWorkerProtocol.Ack(wid.transf)
      }


    case MasterWorkerProtocol.WorkFailed(workerId, transf) =>
      if (workState.isInProgress(workerId)) {
        log.info("Work {} failed by worker {}", transf, workerId)
        changeWorkerToIdle(workerId, transf)
        workState = workState.updated(WorkerFailed(transf))
        notifyWorkers()
      }


    case wp: MasterWorkerProtocol.WorkerInProgress ⇒
      log.info(s"got Work in Progress update: ${wp.percentCompleted} % of worker ${wp.workerId}")
      workState = workState.updated(WorkInProgress(wp.transf, wp.percentCompleted))


    case transf: Transform =>
      log.info(s"master received transform [${transf.uid}]")
      // idempotent
      if (workState.isAccepted(transf.uid)) {
        log.info(s"transform [${transf.uid}] is already accepted, Ack is returned.")
        sender() ! Master.Ack(transf)
      } else {
        log.info(s"transform [${transf.uid}] accepted.")
        sender() ! Master.Ack(transf)
        workState = workState.updated(WorkAccepted(transf))
        notifyWorkers()
      }


    case CleanupTick =>
      for ((workerId, s@WorkerState(_, Busy(workId, timeout), _)) ← workers) {
        if (timeout.isOverdue) {
          log.info(s"Work timed out: ${workId}")
          val ws = workers.get(workerId)
          if (ws.isDefined) {
            val wst = ws.get.status
            wst match {
              case Busy(t, _) ⇒
                val wc = WorkerCompleted(workerId, t,
                  WorkerJobFailed("time out", "time out, transform process seem to be lost"),
                  utils.getCurrentDateAsString())
                proxyToExpMgrActor ! WriteFeedback(wc)
            }
          }
          workers -= workerId
          workState = workState.updated(WorkerTimedOut(workId))
          notifyWorkers()
        }
      }


    case TransformType(wid, wt) ⇒
      workers += (wid -> workers(wid).copy(transDef = Some(wt)))
      transformDefs += wt
      log.info(s"[${transformDefs.size}] workers transforms def. types")
      //      log.info(s"workers transforms def. types: $transformDefs")
      if (workState.hasWork(wt.fullName)) {
        log.info(s"worker${wt.fullName} already busy with some work...")
        sender() ! WorkIsReady
      }


    case QueryWorkStatus(transfUID) ⇒
      sender() ! workState.jobState(transfUID)


    case GetAllJobsStatus ⇒
      sender() ! workState.workStateSummary()


    case GetRunningJobsStatus ⇒
      log.info("asking for Running job status...")
      sender() ! workState.runningJobsSummary()


    case GetAllTransfDefs ⇒
      sender() ! ManyTransfDefs(transformDefs.toList)


    case ft: FindTransfDefs ⇒
      sender() ! ManyTransfDefs(
        new TransfDefHelpers(transformDefs).findTransformers(ft.search, ft.maxHits))


    case GetTransfDef(d) ⇒
      transformDefs.find(_.fullName.asUID == d) match {
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


  def changeWorkerToIdle(workerId: String, transf: Transform): Unit =
    workers.get(workerId) match {
      case Some(s@WorkerState(_, Busy(`transf`, _), _)) ⇒
        workers += (workerId -> s.copy(status = Idle))
      case _ ⇒
      // ok, might happen after standby recovery, worker state is not persisted
    }


  private def proxyToExpMgrActor(): ActorRef = {
    val props =
      ClusterSingletonProxy.props(
        settings = ClusterSingletonProxySettings(context.system)
          .withRole("helper"),
        singletonManagerPath = s"/user/exp_actors_manager")
    context.system.actorOf(props)
  }
}
