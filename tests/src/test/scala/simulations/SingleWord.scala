package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SingleWord extends Simulation {

  val httpConf = http
    .baseURL("http://localhost:4000")

  val scn = scenario("Get single word")
    .exec(http("request")
      .get("/content?q=game"))

  setUp(
    scn.inject(
      rampUsersPerSec(1) to 100 during (10 seconds)
    ).protocols(httpConf)
  )
}
