package common.route

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object AllRoute {
  def apply(writeRoute: Route, readRoute: Route): Route =
    RejectionHandler() {
      pathPrefix("api" / "wallet") {
        writeRoute ~ readRoute
      }
    }
}
