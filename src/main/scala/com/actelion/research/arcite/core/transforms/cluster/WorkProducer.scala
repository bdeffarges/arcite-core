package com.actelion.research.arcite.core.transforms.cluster

import java.util.UUID

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import com.actelion.research.arcite.core.transforms.cluster.workers.{WorkExecProd, WorkExecUpperCase}
import com.actelion.research.arcite.core.transforms.cluster.workers.WorkExecProd.CalcProd
import com.actelion.research.arcite.core.transforms.cluster.workers.WorkExecUpperCase.ToUpperCase

object WorkProducer {

  case object Tick

}

class WorkProducer(frontend: ActorRef) extends Actor with ActorLogging {

  import WorkProducer._
  import context.dispatcher

  def scheduler = context.system.scheduler

  def rnd = ThreadLocalRandom.current

  def nextWorkId(): String = UUID.randomUUID().toString

  var n = 0

  override def preStart(): Unit =
    scheduler.scheduleOnce(5.seconds, self, Tick)

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  def receive = {
    case Tick =>
      n += 1
      log.info("Produced work: {}", n)
      val work = if (ThreadLocalRandom.current().nextBoolean()) {
        Work(nextWorkId(), Job(CalcProd(n), WorkExecProd.jobType))
      } else {
        Work(nextWorkId(), Job(ToUpperCase("Hello World"), WorkExecUpperCase.jobType))
      }
      frontend ! work
      context.become(waitAccepted(work), discardOld = false)
  }

  def waitAccepted(work: Work): Actor.Receive = {
    case Frontend.Ok(_) =>
      context.unbecome()
      scheduler.scheduleOnce(rnd.nextInt(3, 10).seconds, self, Tick)
    case Frontend.NotOk =>
      log.info("Work not accepted, retry after a while")
      scheduler.scheduleOnce(3.seconds, frontend, work)
  }
}