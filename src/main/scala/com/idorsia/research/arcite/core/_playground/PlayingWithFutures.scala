package com.idorsia.research.arcite.core._playground

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

/**
  * arcite-core
  *
  * Copyright (C) 2016 Idorsia Ltd.
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
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  *
  * Created by Bernard Deffarges on 2016/09/14.
  *
  */
object PlayingWithFutures extends App {

  implicit val config = ConfigFactory.load("play-ground")
  implicit val actorSystem = ActorSystem("playground", config)
  implicit val ec = actorSystem.dispatcher

  import scala.concurrent.duration._

  class Actor1(timeout: Timeout) extends Actor {
    override def receive = {
      case s: String ⇒
        println(s"type [${getClass.getSimpleName}], actorRef: [${self}] on: [${Thread.currentThread().getName}] recieved: $s")
        Thread.sleep(500)
        sender() ! s.length
    }
  }

  object Actor1 {
    def props(implicit timeout: Timeout) = Props(classOf[Actor1], timeout)
  }

  class Actor3(timeout: Timeout) extends Actor {
    override def receive = {
      case i: Int ⇒
        println(s"type [${getClass.getSimpleName}], actorRef: [${self}] on: [${Thread.currentThread().getName}] recieved: $i")
        Thread.sleep(500)
        sender() ! i * i
    }
  }

  object Actor3 {
    def props(implicit timeout: Timeout) = Props(classOf[Actor3], timeout)
  }

  val actor1 = actorSystem.actorOf(Actor1.props(Timeout(1.second)), "actor1")
  val actor2 = actorSystem.actorOf(Actor1.props(Timeout(1.second)), "actor2")
  val actor3 = actorSystem.actorOf(Actor3.props(Timeout(1.second)), "actor3")

  import akka.pattern._
//  import scala.concurrent.{ExecutionContext, Future}

  def forComprehFuture1() = {
    implicit val timeout = Timeout(5.seconds)

    val f1 = ask(actor1, "Hello")
    val f2 = ask(actor2, "world")

      val f3 = for {
        a <- f1.mapTo[Int]
        b <- f2.mapTo[Int]
        c <- ask(actor3, a + b).mapTo[Integer]
      } yield c

      f3 foreach {
        f ⇒
          val v = f.asInstanceOf[Int]
          println(s"value: $v")
      }
  }

  while(true) {
    forComprehFuture1()
    Thread.sleep(1000)
  }
}
