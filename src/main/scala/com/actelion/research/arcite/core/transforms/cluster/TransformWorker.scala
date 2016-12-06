package com.actelion.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorLogging, ActorRef, DeathPactException, OneForOneStrategy, Props, ReceiveTimeout, Terminated}
import akka.cluster.client.ClusterClient.SendToAll
import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.{WorkCompletionStatus, WorkFailed, WorkSuccessFull}
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition, TransformHelper}
import com.actelion.research.arcite.core.utils
import tachyon.client.WorkerFileSystemMasterClient

import scala.concurrent.duration.{Duration, FiniteDuration, _}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Actelion Pharmaceuticals Ltd.
  * Gewerbestrasse 16
  * CH-4123 Allschwil, Switzerland.
  *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program. If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/09/05.
  */
class TransformWorker(clusterClient: ActorRef, transformDefinition: TransformDefinition,
                      registerInterval: FiniteDuration) extends Actor with ActorLogging {

  import MasterWorkerProtocol._

  val workerId = UUID.randomUUID().toString

  //todo change dispatcher?
  import context.dispatcher

  val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
    SendToAll("/user/master/singleton", RegisterWorker(workerId)))

  val workTransformExec = context.actorOf(transformDefinition.actorProps(), s"$workerId-exec")

  val workExecutor = context.watch(workTransformExec)

  var currentTransform: Option[Transform] = None

  var time: Long = 0L

  log.info(s"worker [$workerId] for work executor [$workExecutor] created.")

  def transform: Transform = currentTransform match {
    case Some(transf) => transf
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop

    case _: DeathPactException => Stop

    case excp: Exception =>
      currentTransform foreach { transf ⇒
        val wf = WorkFailed("worker failed", excp.toString)
        sendToMaster(WorkerFailed(workerId, transf, wf, utils.getDateAsString(time)))
      }
      context.become(idle)
      Restart
  }

  override def postStop(): Unit = registerTask.cancel()

  def receive = idle

  def idle: Receive = {
    case WorkIsReady =>
      log.info("received [WorkIsReady]")
      sendToMaster(WorkerRequestsWork(workerId))

    case t: Transform =>
      time = System.currentTimeMillis()

      log.info(s"Got a transform: ${t.transfDefName} / ${t.uid} / ${t.source.experiment.name} / ${t.source.getClass.getSimpleName}")
      TransformHelper(t).getTransformFolder().toFile.mkdirs()

      currentTransform = Some(t)
      workExecutor ! t
      context.become(working)

    case gtd: GetTransfDefId ⇒
      log.info(s"asked for my [$self] workerType ")
      workExecutor ! gtd

    case wt: TransformType ⇒
      sendToMaster(wt)
  }

  def working: Receive = {
    case wc: WorkCompletionStatus ⇒ wc match {
      case ws: WorkSuccessFull ⇒
        log.info(s"Work is completed. feedback: ${ws.feedback}")
        sendToMaster(WorkerSuccess(workerId, transform, ws, utils.getDateAsString(time)))
        context.setReceiveTimeout(5.seconds)
        context.become(waitForWorkIsDoneAck(ws))

      case wf: WorkFailed ⇒
        log.info(s"Work failed. feedback: ${wf.feedback}")
        sendToMaster(WorkerFailed(workerId, transform, wf, utils.getDateAsString(time)))
        context.setReceiveTimeout(5.seconds)
        context.become(waitForWorkIsDoneAck(wf))
    }

    case _: Transform =>
      log.info("Yikes. Master told me to do work, while I'm working.")
  }

  def waitForWorkIsDoneAck(result: WorkCompletionStatus): Receive = {

    case Ack(trans) if trans == transform =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)

    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      result match {
        case ws: WorkSuccessFull ⇒
          sendToMaster(WorkerSuccess(workerId, transform, ws, utils.getDateAsString(time)))
        case wf: WorkFailed ⇒
          sendToMaster(WorkerFailed(workerId, transform, wf, utils.getDateAsString(time)))
      }
  }

  override def unhandled(message: Any): Unit = message match {
    case Terminated(`workExecutor`) => context.stop(self)
    case WorkIsReady =>
    case _ => super.unhandled(message)
  }

  def sendToMaster(msg: Any): Unit = {
    clusterClient ! SendToAll("/user/master/singleton", msg)
  }
}

object TransformWorker {
  def props(clusterClient: ActorRef, transfDef: TransformDefinition,
            registerInterval: FiniteDuration = 10.seconds): Props =
    Props(classOf[TransformWorker], clusterClient, transfDef, registerInterval)


  sealed trait WorkCompletionStatus {
    def feedback:  String
  }

  case class WorkSuccessFull(feedback: String = "", artifacts: List[String] = Nil) extends WorkCompletionStatus

  case class WorkFailed(feedback: String = "", errors:  String = "") extends WorkCompletionStatus

}

