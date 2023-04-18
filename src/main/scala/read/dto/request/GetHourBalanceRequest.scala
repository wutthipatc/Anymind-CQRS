package read.dto.request

import `type`.Predicate
import spray.json.{DeserializationException, JsString, JsValue, RootJsonReader}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.{Failure, Success, Try}

case class GetHourBalanceRequest(startDateTime: Instant, endDateTime: Instant)
object GetHourBalanceRequest {
  implicit object RootJsonReader extends RootJsonReader[GetHourBalanceRequest] {
    override def read(json: JsValue): GetHourBalanceRequest = {
      val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
      val zoneOffsetFromStringFn: String => ZoneOffset = s => {
        val isStringContainPlus: Predicate[String] = _.contains("+")
        val isStringContainMinus: Predicate[String] = _.contains("-")
        if (isStringContainPlus(s)) ZoneOffset.of(s.substring(s.indexOf("+")))
        else if (isStringContainMinus(s)) ZoneOffset.of(s.substring(s.indexOf("-")))
        else ZoneOffset.of("Z")
      }
      json.asJsObject.getFields("startDateTime", "endDateTime") match {
        case Seq(JsString(startDateTimeStr), JsString(endDateTimeStr)) =>
          (for {
            startDateTime <- Try(LocalDateTime.parse(startDateTimeStr, dateTimeFormatter).toInstant(zoneOffsetFromStringFn(startDateTimeStr)))
            endDateTime <- Try(LocalDateTime.parse(endDateTimeStr, dateTimeFormatter).toInstant(zoneOffsetFromStringFn(endDateTimeStr)))
          } yield GetHourBalanceRequest(startDateTime, endDateTime)) match {
            case Success(value) => value
            case Failure(ex) =>
              ex.printStackTrace()
              throw DeserializationException("Invalid dateTime value")
          }
        case _ => throw DeserializationException("Invalid get-hour-balance request body")
      }
    }
  }
}
