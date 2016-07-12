package com.actelion.research.arcite.core.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
  * Created by deffabe1 on 3/8/16.
  * 3 ways of defining environment:
  * 1. in the applicaton.conf file
  * 2. from the ENV variable at startup
  * 3. programmatically
  *
  * programmatically defined overrides both others anytime
  * if ENV is set at start time then it overrides application.conf value
  *
  */
object Env {

  val logger = Logger(LoggerFactory.getLogger("Env.logger"))

  val conf = ConfigFactory.load

  val defaultEnv = conf.getString("env")
  logger.debug(s"default env from application.conf: $defaultEnv")

  //  logger.debug(sys.props.mkString("\n"))
  //  logger.debug(sys.env.mkString("\n"))

  var environment: Option[String] = Option(sys.env.getOrElse("ENV", null))
  logger.debug(s"environment from ENV variable: $environment")

  def setEnv(env: String) = {
    environment = Some(env)
    logger.debug(s"environment has been set programmatically to $environment")
  }

  def getConf(key: String) = {
    conf.getString(s"$getEnv.$key")
  }

  def getEnv(): String = {
    if (environment.isDefined) environment.get else defaultEnv
  }
}
