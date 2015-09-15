package simulations

import java.lang.Double

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SearchPhrase extends Simulation {

  val url = System.getProperty("url", "localhost:4000")

  val warmRampFrom = Double.valueOf(System.getProperty("warmRampFrom", "1"))
  val warmRampTo = Double.valueOf(System.getProperty("warmRampTo", "100"))
  val warmRampDuring = Integer.valueOf(System.getProperty("warmRampDuring", "10"))

  val rampFrom = Double.valueOf(System.getProperty("rampFrom", "10"))
  val rampTo = Double.valueOf(System.getProperty("rampTo", "200"))
  val rampDuring = Integer.valueOf(System.getProperty("rampDuring", "30"))

  val name = s"request $url"

  val httpConf = http
    .baseURL(s"http://$url")
    .connection("keep-alive")

  val data = csv("data.csv").random
  val scn = scenario("Get phrase")
    .feed(data)
    .exec(
      http(name)
        .get("/content?q=${phrase}")
        .check(regex( """(.+\n?)""").count.is(session => session("lineCount").as[String].toInt))
        .check(substring("${expectedPhrase}")))

  setUp(
    scn.inject(
      // warmup
      rampUsersPerSec(warmRampFrom) to warmRampTo during (warmRampDuring seconds),
      // wait
      nothingFor(2 seconds),
      // measure performance
      rampUsersPerSec(rampFrom) to rampTo during (rampDuring seconds)
    ).protocols(httpConf)
  )
}
