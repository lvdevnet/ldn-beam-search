package simulations

import java.lang.Double

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SearchPhrase extends Simulation {

  val rampFrom = Double.valueOf(System.getProperty("rampFrom", "10"))
  val rampTo = Double.valueOf(System.getProperty("rampTo", "200"))
  val rampDuring = Integer.valueOf(System.getProperty("rampDuring", "30"))

  val httpConf = http
    .baseURL("http://localhost:4000")

  val data = csv("data.csv").random
  val scn = scenario("Get phrase")
    .feed(data)
    .exec(
      http("request")
        .get("/content?q=${phrase}")
        .check(regex( """(.+\n?)""").count.is(session => session("lineCount").as[String].toInt))
        .check(substring("${expectedPhrase}")))

  setUp(
    scn.inject(
      // warmup
      rampUsersPerSec(1) to 50 during (5 seconds),
      // measure performance
      rampUsersPerSec(rampFrom) to rampTo during (rampDuring seconds)
    ).protocols(httpConf)
  )
}
