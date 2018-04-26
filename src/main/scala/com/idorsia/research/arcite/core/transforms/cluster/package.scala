package com.idorsia.research.arcite.core.transforms

import akka.actor.ActorSystem
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.{Config, ConfigFactory}

/**
  * arcite-core
  *
  * Copyright (C) 2018 Idorsia Pharmaceuticals Ltd.
  * Hegenheimermattweg 91 
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
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2018/04/26.
  *
  */
package object cluster {

  private val config = ConfigFactory.load

  def configWithRole(role: String): Config =
    ConfigFactory.parseString(
      s"""
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())

  def proxyProps4SingletonWithRoleAndName(system: ActorSystem, singletonRole: String,
                                          singletonName: String) = {

    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(system).withRole(singletonRole),
      singletonManagerPath = s"/user/$singletonName")
  }

  def clusterActorSystemWithRole(role: String): ActorSystem = {
    val arcClusterSystName: String = config.getString("arcite.cluster.name")

    val actSystem = ActorSystem(arcClusterSystName, configWithRole(role))

    actSystem
  }
}
