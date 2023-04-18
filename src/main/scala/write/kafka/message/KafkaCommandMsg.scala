package kafka.message

import `type`.{ObjectToString, StringToObject}
import akka.actor.typed.ActorRef
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsString, JsValue, JsonFormat, JsonReader, SerializationException, enrichAny}
import write.model.WalletEvent

import java.time.Instant

case class WalletSnapshotMsg(id: String, amount: BigDecimal, instant: Instant, sequenceNumber: Long)
object WalletSnapshotMsg extends DefaultJsonProtocol {
  implicit object instantJsonFormat extends JsonFormat[Instant] {
    override def write(obj: Instant): JsValue = JsNumber(obj.getEpochSecond)
    override def read(json: JsValue): Instant = Instant.ofEpochMilli(json match {
      case JsNumber(epoch) => epoch.toLong
      case _ => throw new RuntimeException("Error read Instant json value")
    })
  }
  implicit val jsonFormat: JsonFormat[WalletSnapshotMsg] = jsonFormat4(WalletSnapshotMsg.apply)
  def fromWalletEventAndSequence(walletEvent: WalletEvent, sequenceNumber: Long): WalletSnapshotMsg = {
    val WalletEvent(id, amount, instant) = walletEvent
    WalletSnapshotMsg(id, amount, instant, sequenceNumber)
  }
}
sealed trait WalletAction
object WalletAction {
  implicit object jsonFormat extends JsonFormat[WalletAction] {
    override def write(obj: WalletAction): JsValue = obj match {
      case Create => JsString("CREATE")
      case Add => JsString("ADD")
    }
    override def read(json: JsValue): WalletAction = json match {
      case JsString(s) => if (s == "CREATE") Create else Add
      case _ => throw DeserializationException("Invalid wallet action")
    }
  }
}
case object Create extends WalletAction
case object Add extends WalletAction

sealed trait KafkaCommandMsg
sealed trait KafkaMsg extends KafkaCommandMsg
sealed trait AckMessage extends KafkaCommandMsg
// To ask internal state
sealed trait AskMessage extends KafkaCommandMsg
object KafkaMsg extends DefaultJsonProtocol {
  implicit val toMsgStrFn: ObjectToString[KafkaMsg] = {
    case msg: CreateWalletMsg => msg.toJson.compactPrint
    case msg: AddAmountToWalletMsg => msg.toJson.compactPrint
    case _ => throw new SerializationException("Not a Kafka message")
  }
  implicit object jsonReader extends JsonReader[KafkaMsg] {
    override def read(json: JsValue): KafkaMsg = {
      val action: WalletAction = json.asJsObject.getFields("action") match {
        case Seq(js) => js.convertTo[WalletAction]
        case _ => throw DeserializationException("Invalid Kafka message")
      }
      action match {
        case Create => json.convertTo[CreateWalletMsg]
        case Add => json.convertTo[AddAmountToWalletMsg]
      }
    }
  }
  import spray.json._
  implicit val msgStrToKafkaMsg: StringToObject[KafkaMsg] = _.parseJson.convertTo[KafkaMsg]
}

case class CreateWalletMsg(snapshotMsg: WalletSnapshotMsg, action: WalletAction = Create) extends KafkaMsg
object CreateWalletMsg extends DefaultJsonProtocol {
  implicit val jsonFormat: JsonFormat[CreateWalletMsg] = jsonFormat2(CreateWalletMsg.apply)
}
case class AddAmountToWalletMsg(snapshotMsg: WalletSnapshotMsg, action: WalletAction = Add) extends KafkaMsg
object AddAmountToWalletMsg extends DefaultJsonProtocol {
  implicit val jsonFormat: JsonFormat[AddAmountToWalletMsg] = jsonFormat2(AddAmountToWalletMsg.apply)
}
case object Done extends KafkaMsg
case object SendKafkaSuccess extends AckMessage
case class SendKafkaFailed(msg: KafkaMsg, t: Throwable) extends AckMessage
case class AskSequence(replyTo: ActorRef[Sequence]) extends AskMessage

case class Sequence(sequence: Long)

