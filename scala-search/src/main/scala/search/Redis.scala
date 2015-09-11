package search

import akka.actor.ActorSystem
import org.scalatra._
import redis.RedisClient
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
//import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConversions._

class Redis(_akka: ActorSystem) extends ScalatraServlet with FutureSupport {

  implicit def akka = _akka
  implicit def executor = akka.dispatcher
  implicit val timeout = 5.seconds

  val redis = RedisClient()

  val _token   = "t:"
  val _content = "c:"

  get("/content") {
    params.get("q").map { q =>
      val tokens = q.split(' ').map(_token + _)
      redis.sinter[String](tokens(0), tokens:_* /*rediscala wtf!*/).flatMap { ids =>
        Future.sequence {
          ids.map { id =>
            redis.get[String](_content + id).map(id + "," + _.getOrElse("(none)"))
          }
        } .map(_.mkString("\n"))
      }

    } .getOrElse(Future.successful("Usage: /content?q=ask+me"))
  }
}
