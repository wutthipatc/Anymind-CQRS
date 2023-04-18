package common.dto

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Rejection, StandardRoute}
import org.slf4j.Logger
import spray.json.{DefaultJsonProtocol, JsNull, JsObject, JsString, JsonWriter, RootJsonWriter}

case class BaseResponse[T: JsonWriter](dataOption: Option[T], errorMessageOption: Option[String], statusCodeOption: Option[StatusCode] = None) {
  private def isSuccess: Boolean = dataOption.isDefined && errorMessageOption.isEmpty
  def getCompleteRoute: StandardRoute =
    if (isSuccess) statusCodeOption.map(complete(_, this)).getOrElse(complete(StatusCode.int2StatusCode(200), this))
      // Business exception
    else statusCodeOption.map(complete(_, this)).getOrElse(complete(StatusCode.int2StatusCode(200), this))
}

object BaseResponse extends DefaultJsonProtocol {
  def getSuccessResponse[T: JsonWriter](data: T, statusCodeOption: Option[StatusCode] = None): BaseResponse[T] = BaseResponse(Some(data), None, statusCodeOption)
  def getErrorResponse[T: JsonWriter](errorMessage: String, statusCodeOption: Option[StatusCode] = None): BaseResponse[T] = BaseResponse(None, Some(errorMessage), statusCodeOption)
  implicit def writer[T: JsonWriter]: RootJsonWriter[BaseResponse[T]] = (obj: BaseResponse[T]) => JsObject(
    "data" -> obj.dataOption.map(implicitly[JsonWriter[T]].write(_)).getOrElse(JsNull),
    "errorMessage" -> obj.errorMessageOption.map(JsString(_)).getOrElse(JsNull)
  )
  def getCompleteRouteFromThrowable[T: JsonWriter](t: Throwable, logger: Logger): StandardRoute = {
    logger.error(s"Something went wrong with error message: ${t.getMessage}", t)
    complete(
      StatusCode.int2StatusCode(500),
      BaseResponse.getErrorResponse[T](s"Something went wrong with error message: ${t.getMessage}")
    )
  }
  def getValidationErrorCompleteRoute[T: JsonWriter](errMsg: String): StandardRoute =
    getErrorResponse[T](errMsg, Some(StatusCode.int2StatusCode(400))).getCompleteRoute
  def getDefaultRejectionCompleteRoute[T: JsonWriter](seq: Seq[Rejection]): StandardRoute =
    getErrorResponse[T](seq.mkString, Some(StatusCode.int2StatusCode(400))).getCompleteRoute
}