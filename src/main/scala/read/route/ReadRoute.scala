package read.route

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import common.dto.BaseResponse
import org.slf4j.LoggerFactory
import read.dto.request.GetHourBalanceRequest
import read.dto.response.GetHourBalanceResponse
import read.service.GetHourBalanceService

import scala.util.{Failure, Success}


object ReadRoute {
  private val logger = LoggerFactory.getLogger(classOf[ReadRoute.type])
  def apply(service: GetHourBalanceService): Route =
    path("hour-balance") {
      post {
        entity(as[GetHourBalanceRequest]) { request =>
          onComplete(service.getHourBalance("TestWallet", request.startDateTime, request.endDateTime)) {
            case Success(value) => value.getCompleteRoute
            case Failure(t) => BaseResponse.getCompleteRouteFromThrowable[GetHourBalanceResponse](t, logger)
          }
        }
      }
    }
}
