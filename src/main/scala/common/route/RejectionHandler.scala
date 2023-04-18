package common.route

import akka.http.scaladsl.server.Directives.handleRejections
import akka.http.scaladsl.server.{Directive0, ValidationRejection}
import common.dto.BaseResponse
import spray.json.DefaultJsonProtocol._

object RejectionHandler {
  def apply(): Directive0 =
    handleRejections {
      case seq if seq.exists(_.isInstanceOf[ValidationRejection]) =>
        Some(BaseResponse.getValidationErrorCompleteRoute[String](seq.find(_.isInstanceOf[ValidationRejection]).map {
          case ValidationRejection(msg, _) => msg
        }.getOrElse("Request validation error")))
      case seq => Some(BaseResponse.getDefaultRejectionCompleteRoute[String](seq))
    }
}
