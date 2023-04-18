package write.route

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import common.dto.BaseResponse
import org.slf4j.LoggerFactory
import write.dto.request.AddBitCoinRequest
import write.dto.response.WriteApiResponse
import write.service.AddBitCoinService

import scala.util.{Failure, Success}

object WriteRoute {
  private val logger = LoggerFactory.getLogger(classOf[WriteRoute.type])
  def apply(service: AddBitCoinService): Route = {
    path("add-bitcoin") {
      post {
        entity(as[AddBitCoinRequest]) { request =>
          validate(AddBitCoinRequest.isRequestValid(request), s"Invalid request body for $request") {
            onComplete(service.addBitCoin(request)) {
              case Success(value) => value.getCompleteRoute
              case Failure(t) => BaseResponse.getCompleteRouteFromThrowable[WriteApiResponse](t, logger)
            }
          }
        }
      }
    }
  }
}
