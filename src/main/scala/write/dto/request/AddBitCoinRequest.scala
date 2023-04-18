package write.dto.request

import `type`.Predicate
import akka.actor.typed.ActorRef
import spray.json.{DeserializationException, JsNumber, JsString, JsValue, RootJsonReader}
import write.dto.response.WriteApiResponse
import write.model.{AddAmountToWallet, WalletSnapshot}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.{Failure, Success, Try}

case class AddBitCoinRequest(amount: BigDecimal, dateTime: Instant)
object AddBitCoinRequest {
  implicit object RootJsonReader extends RootJsonReader[AddBitCoinRequest] {
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    override def read(json: JsValue): AddBitCoinRequest = {
      val zoneOffsetFromStringFn: String => ZoneOffset = s => {
        val isStringContainPlus: Predicate[String] = _.contains("+")
        val isStringContainMinus: Predicate[String] = _.contains("-")
        if (isStringContainPlus(s)) ZoneOffset.of(s.substring(s.indexOf("+")))
        else if (isStringContainMinus(s)) ZoneOffset.of(s.substring(s.indexOf("-")))
        else ZoneOffset.of("Z")
      }
      json.asJsObject.getFields("amount", "dateTime") match {
        case Seq(JsNumber(amount), JsString(dateTimeStr)) =>
          Try(LocalDateTime.parse(dateTimeStr, dateTimeFormatter).toInstant(zoneOffsetFromStringFn(dateTimeStr)))
            .map(instant => AddBitCoinRequest(amount, instant)) match {
            case Success(value) => value
            case Failure(ex) =>
              ex.printStackTrace()
              throw DeserializationException("Invalid dateTime value")
          }
        case _ => throw DeserializationException("Invalid add-bitcoin request body")
      }
    }
  }
  def toActorCommand(request: AddBitCoinRequest): ActorRef[WriteApiResponse] => AddAmountToWallet =
    AddAmountToWallet.curried(WalletSnapshot(request.amount, request.dateTime))
  def isRequestValid(request: AddBitCoinRequest): Boolean = request.amount > 0
}
