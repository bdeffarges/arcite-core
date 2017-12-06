package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorLogging, ActorRef, DeathPactException, OneForOneStrategy, Props, ReceiveTimeout, Terminated}
import akka.cluster.client.ClusterClient.SendToAll
import com.idorsia.research.arcite.core.experiments.ManageExperiments.Selectable
import com.idorsia.research.arcite.core.transforms.cluster.TransformWorker.{WorkerJobCompletion, WorkerJobProgress}
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

  private val workExecutor = context.watch(
    context.actorOf(transformDefinition.actorProps(), s"$workerId-exec"))

  private var currentTransform: Option[Transform] = None

  private var time: Long = 0L

  log.info(s"worker [$workerId] for work executor [$workExecutor] created.")

  def transform: Transform = currentTransform match {
    case Some(transf) => transf
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() { //todo introduce Arcite exception
    case _: ActorInitializationException =>
      log.error("Actor Initialization exception...")
      Stop

    case _: DeathPactException =>
      log.error("Death Pact Exception...")
      Stop

    case excp: Exception =>
      log.error(s"[@#£ç45%] that's bad... an exception probably occurred in the executor actor... ${excp.getMessage}")
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

      try {
        TransformHelper(t).getTransformFolder().toFile.mkdirs()
      } catch {
        case exc: Exception ⇒
          log.error(s"That's bad, an exception occurred while creating the transform folder... exception msg: ${exc.getMessage}")
          sendToMaster(WorkFailed(workerId, t))
      }

      currentTransform = Some(t)
      workExecutor ! t
      context.become(working)
      log.info("I'm now in working mode...")


    case gtd: GetTransfDefId ⇒
      log.debug(s"asked for my workerType ")
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


    case wp: WorkerJobProgress ⇒
      log.info(s"worker making progress... ${wp.progress}%...")
      sendToMaster(WorkerInProgress(workerId, transform, utils.getDateAsString(time), wp.progress))


    case _: Transform ⇒
      log.debug("Yikes. Master told me to do work, while I'm working.")


    case gtd: GetTransfDefId ⇒
      log.info(s"asked for my workerType ")
      workExecutor ! gtd


    case a: Any ⇒
      log.error(s"-[éàäè] does not know how to process message $a")
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
    * the worker job completed successfully and returned some artifacts and maybe some "selectables"
    *
    * @param feedback
    * @param artifacts a map title -> path of file
    * @param selectables
    */
  case class WorkerJobSuccessFul(feedback: String = "",
                                 artifacts: Map[String, String] = Map.empty,
                                 selectables: Set[Selectable] = Set.empty) extends WorkerJobCompletion

  /**
    * the actual has failed but it does not mean the worker itself has failed.
    *
    * @param feedback
    * @param errors
    */
  case class WorkerJobFailed(feedback: String = "", errors: String = "") extends WorkerJobCompletion

  /**
    * the worker has made some progress and informs the cluster about it.
    *
    * @param progress
    */
  case class WorkerJobProgress(progress: Int)


}

