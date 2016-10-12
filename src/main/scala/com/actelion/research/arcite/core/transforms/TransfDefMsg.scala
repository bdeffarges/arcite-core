package com.actelion.research.arcite.core.transforms

import com.actelion.research.arcite.core.utils.FullName

/**
  * Created by deffabe1 on 5/19/16.
  *
  * Messages for the transform definitions
  *
  */

object TransfDefMsg {

  sealed trait Msg2TransfDefsManager

  case object GetAllTransfDefs extends Msg2TransfDefsManager

  case class FindTransfDefs(search: String) extends Msg2TransfDefsManager

  case class GetTransfDef(digest: String) extends Msg2TransfDefsManager

  case class GetTransfDefFromName(fullName: FullName) extends Msg2TransfDefsManager


  sealed trait MsgFromTransfDefsManager

  case class ManyTransfDefs(transfDefIds: Set[TransformDefinitionIdentity]) extends MsgFromTransfDefsManager

  case class OneTransfDef(transfDefId: TransformDefinitionIdentity) extends MsgFromTransfDefsManager

  case object NoTransfDefFound extends MsgFromTransfDefsManager
}
