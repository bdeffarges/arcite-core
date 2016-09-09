package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.{ActorLogging, ActorPath, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.persistence.PersistentActor
import com.actelion.research.arcite.core.transforms.RunTransform.{ProceedWithTransform, RunTransformOnObject}
import com.actelion.research.arcite.core.transforms.Transformers.{GetAllTransformers, ManyTransformers}
import com.actelion.research.arcite.core.transforms.cluster.Frontend._
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition, TransformLight, TransformResult}

import scala.concurrent.duration.{Deadline, FiniteDuration}

//todo because of event sourcing, it will also include saved jobs...
object Master {

  val ResultsTopic = "results" //todo remove? originally, it was intended for subscribe/publish

  def props(workTimeout: FiniteDuration): Props = Props(classOf[Master], workTimeout)

  case class Ack(transf: Transform)

  private sealed trait WorkerStatus

  private case object Idle extends WorkerStatus

  private case class Busy(trans: TransformLight, deadline: Deadline) extends WorkerStatus

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

  //get access to the actor that manages experiments, todo replace with config
//  val expsActorPath = ActorPath.fromString(s"akka.tcp://$arcTransfActClustSys@127.0.0.1:2551/user/store"))


  // persistenceId must include cluster role to support multiple masters
  override def persistenceId: String = Cluster(context.system).selfRoles.find(_.startsWith("backend-")) match {
    case Some(role) ⇒ role + "-master"
    case None ⇒ "master"
  }

  // workers state is not event sourced
  private var workers = Map[String, WorkerState]()
  private var transformDefs = Set[TransformDefinition]()

  // workState is event sourced
  private var workState = WorkState.empty

  import context.dispatcher

  val cleanupTask = context.system.scheduler.schedule(workTimeout / 2, workTimeout / 2,
    self, CleanupTick)

  override def postStop(): Unit = cleanupTask.cancel()

  override def receiveRecover: Receive = {
    case event: WorkStatus =>
      // only update current state by applying the event, no side effects
      workState = workState.updated(event)
      log.info("Replayed {}", event.getClass.getSimpleName)
  }

  override def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      log.info(s"received RegisterWorker for $workerId")
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender()))
        sender() ! GetTransformDefinition(workerId)
      } else {
        log.info("Worker registered: {}", workerId)
        workers += (workerId -> WorkerState(sender(), status = Idle, None))
        sender() ! GetTransformDefinition(workerId)
      }

    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
      log.info(s"total pending jobs = ${workState.numberOfPendingJobs()} worker requesting work... do we have something for him?")
      val td = workers(workerId).transDef
      if (td.isDefined && workState.hasWork(td.get.transDefIdent.fullName)) {
        workers.get(workerId) match {
          case Some(s@WorkerState(_, Idle, _)) =>
            if (s.transDef.isDefined) {
              val w = workState.nextWork(s.transDef.get.transDefIdent.fullName)
              if (w.nonEmpty) { //todo maybe we don't need both checks
                val transf = w.get
                persist(WorkInProgress(transf, 0)) { event =>
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

    case MasterWorkerProtocol.WorkIsDone(workerId, transf, result) =>
      // idempotent
      if (workState.isDone(transf.uid)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(transf)
      } else if (!workState.isInProgress(transf.uid)) {
        log.info("Work {} not in progress, reported as done by worker {}", transf, workerId)
      } else {
        log.info("Work {} is done by worker {}", transf, workerId)
        changeWorkerToIdle(workerId, transf)
        persist(WorkCompleted(transf.light, result)) { event ⇒
          workState = workState.updated(event)
          mediator ! DistributedPubSubMediator.Publish(ResultsTopic, TransformResult(transf, result))

          // Ack back to original sender
          sender() ! MasterWorkerProtocol.Ack(transf)
        }
      }

//    case MasterWorkerProtocol.WorkFailed(workerId, transf) =>
//      if (workState.isInProgress(transf.uid)) {
//        log.info("Work {} failed by worker {}", transf, workerId)
//        changeWorkerToIdle(workerId, transf)
//        persist(WorkerFailed(transf.light, "")) { event ⇒
//          workState = workState.updated(event)
//          notifyWorkers()
//        }
//      }

    case pwt: ProceedWithTransform ⇒
      log.info("master received a request to proceed with a transform which first needs to be build. ")
       pwt match {
         case rto: RunTransformOnObject ⇒

       }

    case transf: Transform =>
      log.info("master received work...")
      // idempotent
      if (workState.isAccepted(transf.uid)) {
        log.info("work is accepted...")
        sender() ! Master.Ack(transf)
      } else {
        log.info("Accepted work: {}", transf)
        persist(WorkAccepted(transf.light)) { event ⇒
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
      log.info(s"workers transforms def. types: $transformDefs")

    case QueryWorkStatus(transfUID) ⇒
      sender () ! workState.jobsInProgress(transfUID)

    case AllJobsStatus ⇒
      sender() ! workState.workStateSummary()

    case QueryJobInfo(qji) ⇒
      sender() ! "hello world" //todo implement


    case GetAllTransformers ⇒
      sender() ! ManyTransformers(transformDefs.map(_.transDefIdent))
  }

  def notifyWorkers(): Unit = if (workState.hasWorkLeft) {
    log.info(s"workstate=${workState.workStateSizeSummary()} ,has some work left ?")
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