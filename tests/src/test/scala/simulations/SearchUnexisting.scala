package simulations

import java.lang.Double

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SearchUnexisting extends Simulation {

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

  val scn = scenario("Get unexisting phrase")
    .exec(
      http(name)
        .get("/content?q=ThisIsNonExistingPhraseThatAreNotInDatabase")
        .check(regex( """(.+\n?)""").count.is(0)))

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
