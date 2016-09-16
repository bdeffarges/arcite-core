package com.actelion.research.arcite.core.transforms.cluster

import java.util.UUID

import scala.concurrent.duration._
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorLogging, ActorRef, DeathPactException, OneForOneStrategy, Props, ReceiveTimeout, Terminated}
import akka.cluster.client.ClusterClient.SendToAll

import com.actelion.research.arcite.core.transforms.cluster.TransformWorker.WorkComplete
import com.actelion.research.arcite.core.transforms.{Transform, TransformDefinition}

import scala.concurrent.duration.{Duration, FiniteDuration}

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

  log.info(s"worker [$workerId] for work executor [$workExecutor] created.")

  def transform: Transform = currentTransform match {
    case Some(transf) => transf
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop

    case _: DeathPactException => Stop

    case _: Exception =>
      currentTransform foreach { transf ⇒ //todo reimplement
        //        sendToMaster(WorkFailed(workerId, transf))
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
      log.info(s"Got a transform: $t")
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
    case WorkComplete(result) =>
      log.info("Work is complete. Result {}.", result)
      sendToMaster(WorkIsDone(workerId, transform, result))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck(result))

    case _: Transform =>
      log.info("Yikes. Master told me to do work, while I'm working.")
  }

  def waitForWorkIsDoneAck(result: Any): Receive = {

    case Ack(trans) if trans == transform =>
      sendToMaster(WorkerRequestsWork(workerId))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)

    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(WorkIsDone(workerId, transform, result))
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

  case class WorkComplete(result: Any)

}


//todo fix the ClassNotFoundException problem because transfExecutor is an actorRef on an existing class which might
//not be known by core
class TransformWorkerWithRemoteExec(clusterClient: ActorRef, transfExecutor: ActorRef,
                                    workerName: String, registerInterval: FiniteDuration)
                                    extends Actor with ActorLogging {

  import MasterWorkerProtocol._

  //todo change dispatcher?
  import context.dispatcher

  val registerTask = context.system.scheduler.schedule(0.seconds, registerInterval, clusterClient,
    SendToAll("/user/master/singleton", RegisterWorker(workerName)))

  val workExecutor = context.watch(transfExecutor)

  var currentTransform: Option[Transform] = None

  log.info(s"worker [$workerName] for work executor [$workExecutor] created.")

  def transform: Transform = currentTransform match {
    case Some(transf) => transf
    case None => throw new IllegalStateException("Not working")
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop

    case _: DeathPactException => Stop

    case _: Exception =>
      currentTransform foreach { transf ⇒
        //        sendToMaster(WorkFailed(workerId, transf))
      }
      context.become(idle)
      Restart
  }

  override def postStop(): Unit = registerTask.cancel()

  def receive = idle

  def idle: Receive = {
    case WorkIsReady =>
      log.info("received [WorkIsReady]")
      sendToMaster(WorkerRequestsWork(workerName))

    case t: Transform =>
      log.info(s"Got a transform: $t")
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
    case WorkComplete(result) =>
      log.info("Work is complete. Result {}.", result)
      sendToMaster(WorkIsDone(workerName, transform, result))
      context.setReceiveTimeout(5.seconds)
      context.become(waitForWorkIsDoneAck(result))

    case _: Transform =>
      log.info("Yikes. Master told me to do work, while I'm working.")
  }

  def waitForWorkIsDoneAck(result: Any): Receive = {

    case Ack(trans) if trans == transform =>
      sendToMaster(WorkerRequestsWork(workerName))
      context.setReceiveTimeout(Duration.Undefined)
      context.become(idle)

    case ReceiveTimeout =>
      log.info("No ack from master, retrying")
      sendToMaster(WorkIsDone(workerName, transform, result))
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

object TransformWorkerWithRemoteExec {
  def props(clusterClient: ActorRef, transfExecutor: ActorRef, workerName: String,
            registerInterval: FiniteDuration = 10.seconds): Props =
    Props(classOf[TransformWorkerWithRemoteExec], clusterClient, transfExecutor, workerName, registerInterval)

  case class WorkComplete(result: Any)

}

