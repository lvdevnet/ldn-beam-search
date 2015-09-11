import akka.actor.{Props, ActorSystem}
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {

  val akka = ActorSystem()
  //val myActor = system.actorOf(Props[MyActor])

  override def init(context: ServletContext) {
    context.mount(new search.Redis(akka), "/*")
    //context.mount(new MyActorApp(system, myActor), "/actors/*")
  }

  override def destroy(context: ServletContext) {
    akka.shutdown()
  }
}
