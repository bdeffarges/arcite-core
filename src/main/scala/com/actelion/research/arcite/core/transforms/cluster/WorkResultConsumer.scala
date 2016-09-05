package com.actelion.research.arcite.core.transforms.cluster

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator

  //todo do we need that class?
class WorkResultConsumer extends Actor with ActorLogging {

  val mediator = DistributedPubSub(context.system).mediator

  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  log.info(s"registered [$self] as work result consumer")

  def receive = {
    case _: DistributedPubSubMediator.SubscribeAck =>
    case WorkResult(workId, result) =>
      log.info("Consumed result: {}", result)
  }
}