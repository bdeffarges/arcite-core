package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorLogging, ActorRef, DeathPactException, OneForOneStrategy, Props, ReceiveTimeout, Terminated}
import akka.cluster.client.ClusterClient.SendToAll
import com.idorsia.research.arcite.core.experiments.ManageExperiments.Selectable
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.WorkerJobCompletion
import com.idorsia.research.arcite.core.transforms.{Transform, TransformDefinition, TransformHelper}
import com.idorsia.research.arcite.core.utils

import scala.concurrent.duration.{Duration, FiniteDuration, _}

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
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

  private val workerId = UUID.randomUUID().toString

  //todo change dispatcher?
  import context.dispatcher

  private val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
    SendToAll("/user/master/singleton", RegisterWorker(workerId)))

  private val workTransformExec = context.actorOf(transformDefinition.actorProps(), s"$workerId-exec")

  private val workExecutor = context.watch(workTransformExec)

  private var currentTransform: Option[Transform] = None

  private var time: Long = 0L

  log.info(s"worker [$workerId] for work executor [$workExecutor] created.")

  def transform: Transform = currentTransform match {
    case Some(transf) => transf
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() { //todo introduce Arcite exception
    case _: ActorInitializationException => Stop

    case _: DeathPactException => Stop

    case excp: Exception =>
      currentTransform foreach { transf ⇒
        sendToMaster(WorkFailed(workerId, transf))
      }
      context.become(idle)
      Restart
  }

  override def postStop(): Unit = registerTask.cancel()

  def receive: Receive = idle

  def idle: Receive = {
    case WorkIsReady =>
      log.info("received [WorkIsReady]")
      sendToMaster(WorkerRequestsWork(workerId))


    case t: Transform =>
      time = System.currentTimeMillis()

      log.info(
        s"""Got a transform: ${t.transfDefName} / ${t.uid}
           |/ ${t.source.experiment.name} / ${t.source.getClass.getSimpleName}""".stripMargin)

      TransformHelper(t).getTransformFolder().toFile.mkdirs()

      currentTransform = Some(t)
      workExecutor ! t
      context.become(working)


    case gtd: GetTransfDefId ⇒
      log.info(s"asked for my workerType ")
      workExecutor ! gtd


    case wt: TransformType ⇒
      sendToMaster(wt)
  }

  def working: Receive = {
    case wc: WorkerJobCompletion ⇒
        log.info(s"Work is completed. feedback: ${wc.feedback}")
        sendToMaster(WorkerCompleted(workerId, transform, wc, utils.getDateAsString(time)))
        context.setReceiveTimeout(10.seconds)
        context.become(waitForWorkIsDoneAck(wc))


    case wp: WorkerProgress ⇒
      log.info(s"worker making progress... ${wp.progress}%...")
      sendToMaster(WorkerInProgress(workerId, transform, utils.getDateAsString(time), wp.progress))


    case _: Transform ⇒
      log.info("Yikes. Master told me to do work, while I'm working.")


    case gtd: GetTransfDefId ⇒
      log.info(s"asked for my workerType ")
      workExecutor ! gtd


    case a: Any ⇒
      log.error(s"does not know how to process message $a")
  }

  def waitForWorkIsDoneAck(result: WorkerJobCompletion): Receive = {

    case Ack(trans) if trans == transform =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)

    case ReceiveTimeout =>
      log.info(s"received timeout from ${sender().toString()}")
      log.info(s"result= $result")
      sendToMaster(WorkerCompleted(workerId, transform, result, utils.getDateAsString(time)))
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
  //todo define timing
  def props(clusterClient: ActorRef, transfDef: TransformDefinition,
            registerInterval: FiniteDuration = 1 minute): Props =
    Props(classOf[TransformWorker], clusterClient, transfDef, registerInterval)


  /**
    * what comes back from the actual Worker: whether the work has been successful
    * This does not cover the case when the worker itself fails.
    */
  sealed trait
  WorkerJobCompletion {
    def feedback: String
  }

  /**
    * the work was successful,
    * @param feedback
    * @param artifacts
    * @param selectable
    */
  case class WorkerJobSuccessFul(feedback: String = "",
                                 artifacts: Map[String, String] = Map.empty,
                                 selectable: Set[Selectable] = Set.empty) extends WorkerJobCompletion

  case class WorkerJobFailed(feedback: String = "", errors: String = "") extends WorkerJobCompletion
}

