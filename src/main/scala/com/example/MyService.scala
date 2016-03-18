package com.example

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util._
import spray.routing._
import spray.http._
import MediaTypes._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  val childRequestHandlerActor = actorRefFactory.actorOf(Props[ChildRequestHandlerActor], "child-actor")

  val childRoutes =
    path("fruit") {          // Passes the request context as part of the akka "tell"
      get { ctx =>
        childRequestHandlerActor ! GetFruit(ctx)
      }
    } ~
    path("animal") {          // Uses akka ask
      get { ctx =>
        implicit val timeout = Timeout(5 seconds)
        val futureReq = childRequestHandlerActor ? GetAnimal
        futureReq.onComplete(x => {
          x match {
            case Success(s) => ctx.complete(s"$s")
            case Failure(ex) => ctx.complete(StatusCodes.InternalServerError, s"$ex")
          }
        })
      }
    }


  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(childRoutes ~ myRoute)
}

case class GetFruit(ctx: RequestContext)
case object GetAnimal

class ChildRequestHandlerActor extends Actor {

  private val fruits = Array("apple", "banana", "pear", "strawberry", "orange", "peach")
  private val animals = Array("donkey", "cat", "dog", "pig", "elephant", "zebra", "giraffe")

  private val rndm = scala.util.Random

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = {
    case GetFruit(ctx) => ctx.complete(s"Have this fruit ${fruits(rndm.nextInt(fruits.length))} from child")
    case GetAnimal => sender ! s"Have this animal ${animals(rndm.nextInt(animals.length))} from child"
  }
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
              </body>
            </html>
          }
        }
      }
    }
}