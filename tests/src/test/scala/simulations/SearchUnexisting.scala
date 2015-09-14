package simulations

import java.lang.Double

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class SearchUnexisting extends Simulation {

  val rampFrom = Double.valueOf(System.getProperty("rampFrom", "10"))
  val rampTo = Double.valueOf(System.getProperty("rampTo", "200"))
  val rampDuring = Integer.valueOf(System.getProperty("rampDuring", "30"))

  val httpConf = http
    .baseURL("http://localhost:4000")

  val scn = scenario("Get unexisting phrase")
    .exec(
      http("request")
        .get("/content?q=ThisIsNonExistingPhraseThatAreNotInDatabase")
        .check(regex( """(.+\n?)""").count.is(0)))

  setUp(
    scn.inject(
      // warmup
      rampUsersPerSec(1) to 50 during (5 seconds),
      // measure performance
      rampUsersPerSec(rampFrom) to rampTo during (rampDuring seconds)
    ).protocols(httpConf)
  )
}
