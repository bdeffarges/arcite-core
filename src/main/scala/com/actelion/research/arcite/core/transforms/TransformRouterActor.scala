package com.actelion.research.arcite.core.transforms

import akka.actor.{Actor, ActorLogging, Props}
import com.actelion.research.arcite.core.transforms.GoTransformIt.Message2GoTransform
import com.actelion.research.arcite.core.transforms.Transformers.MessageToTransformers

/**
  * Created by deffabe1 on 5/19/16.
  */
class TransformRouterActor extends Actor with ActorLogging {

  val transformersActor = context.system.actorOf(Props(classOf[Transformers]))

  val transformActor = context.system.actorOf(GoTransformIt.props)


  override def receive = {

    case t: MessageToTransformers ⇒
      transformersActor forward  t //todo send OR forward it? What is better in this case??

    case mgt: Message2GoTransform ⇒
      transformActor forward  mgt
  }
}

object TransformRouterActor {

}