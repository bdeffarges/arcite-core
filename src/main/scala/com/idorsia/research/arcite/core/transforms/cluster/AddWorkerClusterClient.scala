package com.idorsia.research.arcite.core.transforms.cluster

import java.util.UUID

import akka.actor.{ActorPath, ActorSystem}

import akka.cluster.client.{ClusterClient, ClusterClientSettings}

import akka.discovery.marathon.MarathonApiSimpleServiceDiscovery

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}

import akka.management.cluster.{ClusterMember, ClusterMembers, ClusterUnreachableMember}

import akka.stream.ActorMaterializer

import com.idorsia.research.arcite.core.transforms.TransformDefinition

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{Failure, Success}

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
  * Created by Bernard Deffarges on 2018/05/09.
  *
  */
class AddWorkerClusterClient(actorSystemName: String, conf: Config) extends LazyLogging {

  implicit val system = ActorSystem(actorSystemName, conf)
  logger.debug(s"actor system: ${system.toString}")

  implicit val context = system.dispatcher

  implicit val materializer = ActorMaterializer()

  import scala.concurrent.duration._

  def addWorkers(td: TransformDefinition, nbOfWorker: Int) = {

    val discovery = new MarathonApiSimpleServiceDiscovery(system)

    val discoverCluster = discovery.lookup("arcite-cluster-engine", 5 seconds)

    discoverCluster.onComplete { r ⇒ //returns the host:port of the akka management service
      // now we need to find the receptionists
      logger.debug(s"discovery= ${r.get}")
      val clusterMgmt = r.get.addresses.find(_.port.isDefined)

      if (clusterMgmt.isDefined) {
        val uri = s"http://${clusterMgmt.get.host}:${clusterMgmt.get.port.get}/cluster/members"
        logger.debug(s"cluster management uri: ${clusterMgmt.get}")
        val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = uri))
        responseFuture.onComplete {
          case Success(res) => {
            import spray.json._
            import spray.json.DefaultJsonProtocol._
            implicit val clusterUnreachableMemberFormat = jsonFormat2(ClusterUnreachableMember)
            implicit val clusterMemberFormat = jsonFormat4(ClusterMember)
            implicit val clusterMembersFormat = jsonFormat5(ClusterMembers)

            val asClusterMembers = res.entity.asInstanceOf[HttpEntity.Strict]
              .data.decodeString("UTF-8").parseJson.convertTo[ClusterMembers]

            logger.debug(s"cluster members: ${asClusterMembers}")

            //for now we take the first one
            val contactPoints = Set(ActorPath.fromString(s"${asClusterMembers.leader.get}/system/receptionist"))

            logger.debug(s"contact point actors: ${contactPoints.mkString(" ; ")}")

            (0 to nbOfWorker).foreach { i ⇒
              addWorker(td, contactPoints)
            }
          }

          case Failure(_) =>
            sys.error("something went wrong")
            System.exit(1)
        }
      }
    }
  }

  private def addWorker(td: TransformDefinition, workinitContacts: Set[ActorPath]): Unit = {

    val name = s"${td.transDefIdent.fullName.asUID}-${UUID.randomUUID().toString}"

    val clustCliSettings = ClusterClientSettings(system).withInitialContacts(workinitContacts)

    val clusterClient = system.actorOf(ClusterClient.props(clustCliSettings),
      s"$name--WorkClustCli")

    logger.debug(s"cluster client: ${clusterClient.toString}")

    val workerName = s"$name--Worker"

    system.actorOf(TransformWorker.props(clusterClient, td), workerName)
  }

}
