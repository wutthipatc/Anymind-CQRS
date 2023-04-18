package write.model

import `type`.ObjectToString
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, JsonFormat, enrichAny}

import java.time.Instant

case class WalletEvent(walletId: String, amount: BigDecimal, instant: Instant)
object WalletEvent extends DefaultJsonProtocol {
  implicit object instantJsonFormat extends JsonFormat[Instant] {
    override def write(obj: Instant): JsValue = JsNumber(obj.getEpochSecond)

    override def read(json: JsValue): Instant = Instant.ofEpochMilli(json match {
      case JsNumber(epoch) => epoch.toLong
      case _ => throw new RuntimeException("Error read Instant json value")
    })
  }
  implicit val jsonFormat: JsonFormat[WalletEvent] = jsonFormat3(WalletEvent.apply)
  def fromIdAndWalletSnapshot(id: String, snapshot: WalletSnapshot): WalletEvent =
    WalletEvent(id, snapshot.amount, snapshot.instant)
}

sealed trait Event
case class CreateWalletEvent(walletEvent: WalletEvent) extends Event
object CreateWalletEvent extends DefaultJsonProtocol {
  implicit val jsonFormat: JsonFormat[CreateWalletEvent] = jsonFormat1(CreateWalletEvent.apply)
  implicit val toStringFn: ObjectToString[CreateWalletEvent] = e => e.toJson.compactPrint
}
case class AddAmountToWalletEvent(walletEvent: WalletEvent) extends Event
object AddAmountToWalletEvent extends DefaultJsonProtocol {
  implicit val jsonFormat: JsonFormat[AddAmountToWalletEvent] = jsonFormat1(AddAmountToWalletEvent.apply)
  implicit val toStringFn: ObjectToString[AddAmountToWalletEvent] = e => e.toJson.compactPrint
}
object Event

