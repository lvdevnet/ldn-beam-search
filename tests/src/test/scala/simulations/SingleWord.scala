package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SingleWord extends Simulation {

  val httpConf = http
    .baseURL("http://localhost:4000")

  val data = csv("data.csv").random

  val scn = scenario("Get single word")
    .feed(data)
    .exec(http("request")
      .get("/content?q=${word}")
      .check(regex("""(.+\n?)""").count.is(session => session("lineCount").as[String].toInt))
      .check(substring("${expectedPhrase}"))
    )

  setUp(
    scn.inject(
      rampUsersPerSec(1) to 100 during (10 seconds)
    ).protocols(httpConf)
  )
}
