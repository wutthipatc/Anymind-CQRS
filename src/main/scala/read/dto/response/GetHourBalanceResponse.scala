package read.dto.response

import spray.json.{DefaultJsonProtocol, JsArray, JsNumber, JsObject, JsString, JsonWriter}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

case class HourBalanceResponse(dateTime: Instant, amount: BigDecimal)
object HourBalanceResponse extends DefaultJsonProtocol {
  implicit val jsonWriter: JsonWriter[HourBalanceResponse] = JsonWriter.func2Writer { case resp =>
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    JsObject(
      "dateTime" -> JsString(formatter.format(resp.dateTime.atZone(ZoneId.of("UTC")))),
      "amount" -> JsNumber(resp.amount)
    )
  }
}
case class GetHourBalanceResponse(list: List[HourBalanceResponse])
object GetHourBalanceResponse {
  implicit val jsonWriter: JsonWriter[GetHourBalanceResponse] = JsonWriter.func2Writer( resp =>
    JsArray(resp.list.map(HourBalanceResponse.jsonWriter.write).toVector)
  )
}
