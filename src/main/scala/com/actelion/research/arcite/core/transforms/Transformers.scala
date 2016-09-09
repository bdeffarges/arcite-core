package com.actelion.research.arcite.core.transforms

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  * Created by deffabe1 on 5/19/16.
  *
  * todo should come from the cluster
  */
class Transformers extends Actor with ActorLogging {

  import Transformers._

  override def receive = {

    case AddTransformerWithReq(transformDefinition, requester) ⇒
        transformers += ((transformDefinition.transDefIdent.digestUID, transformDefinition))
        requester ! TransformerAdded

//    case GetAllTransformersWithReq(requester) ⇒
//      requester ! ManyTransformers(transformers.values.map(d ⇒ d.definitionLight).toSet)

    case FindTransformerWithReq(search, requester) ⇒
      requester ! ManyTransformers(findTransformers(search))

    case GetTransformerWithReq(digest, requester) ⇒
      val td = Option(transformers(digest))
      if (td.isDefined) requester ! OneTransformer(td.get.transDefIdent)
      else requester ! NoTransformerFound
  }
}

object Transformers {
  def props: Props = Props(classOf[Transformers])

  sealed trait MessageToTransformers

  case class AddTransformer(transformer: TransformDefinition) extends MessageToTransformers
  case class AddTransformerWithReq(transformer: TransformDefinition, requester: ActorRef) extends MessageToTransformers

  case object GetAllTransformers extends MessageToTransformers
  case class GetAllTransformersWithReq(requester: ActorRef) extends MessageToTransformers

  case class GetTransformer(digest: String) extends MessageToTransformers
  case class GetTransformerWithReq(digest: String, requester: ActorRef) extends MessageToTransformers

  case class FindTransformer(search: String) extends MessageToTransformers
  case class FindTransformerWithReq(search: String, requester: ActorRef) extends MessageToTransformers


  sealed trait MessageFromTransformers

  case class ManyTransformers(transformers: Set[TransformDefinitionIdentity]) extends MessageFromTransformers

  case class OneTransformer(transformer: TransformDefinitionIdentity) extends MessageFromTransformers

  case object TransformerAdded extends MessageFromTransformers

  case object NoTransformerFound extends MessageFromTransformers


  var transformers: Map[String, TransformDefinition] = Map( //todo find a place to plug in the transformers.
//    (Proxy2Raw2ArrayFiles.transformDefinition.definitionLight.digest, Proxy2Raw2ArrayFiles.transformDefinition),
//    (RunRNormalization.transformDefinition.definitionLight.digest, RunRNormalization.transformDefinition)
  )

  def findTransformers(search: String): Set[TransformDefinitionIdentity] = {
    val transfs = transformers.values

    (transfs.map(t ⇒ t.transDefIdent).filter(td ⇒ td.fullName.name.toLowerCase.contains(search)).take(10) ++
    transfs.map(t ⇒ t.transDefIdent).filter(td ⇒ td.fullName.organization.toLowerCase.contains(search)).take(5) ++
    transfs.map(t ⇒ t.transDefIdent).filter(td ⇒ td.description.summary.toLowerCase.contains(search)) ++
    transfs.map(t ⇒ t.transDefIdent).filter(td ⇒ td.description.consumes.toLowerCase.contains(search)) ++
    transfs.map(t ⇒ t.transDefIdent).filter(td ⇒ td.description.produces.toLowerCase.contains(search))).toSet
  }
}
