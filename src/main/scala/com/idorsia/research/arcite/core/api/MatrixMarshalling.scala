package com.idorsia.research.arcite.core.api

/**
  * Created by deffabe1 on 2/29/16.
  */
//todo move somewhere else

import spray.json._

case class RawDataFolder(folder: String, target: String)

case class Error(message: String)

trait MatrixMarshalling  extends DefaultJsonProtocol {

implicit val rawDataFolderFormat = jsonFormat2(RawDataFolder)

}
