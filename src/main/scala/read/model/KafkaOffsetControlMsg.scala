package read.model

import akka.actor.InvalidMessageException
import akka.actor.typed.ActorRef
import write.kafka.message.{AddAmountToWalletMsg, CreateWalletMsg, KafkaMsg}

import java.time.Instant

sealed trait KafkaOffsetControlMsg
case class OffsetControlMsg(replyTo: ActorRef[ConsumerAckMsg.type], offset: Offset, command: Command) extends KafkaOffsetControlMsg
case class ConsumerInitMsg(replyTo: ActorRef[ConsumerAckMsg.type]) extends KafkaOffsetControlMsg
case object ConsumerCompleteMsg extends KafkaOffsetControlMsg
case class ConsumerFailureMsg(t: Throwable) extends KafkaOffsetControlMsg
case class AskOffsetMsg(replyTo: ActorRef[Offset]) extends KafkaOffsetControlMsg

case class Offset(value: Long)

case object ConsumerAckMsg

case class WalletSnapshot(amount: BigDecimal, instant: Instant)

sealed trait Command
object Command {
  def fromKafkaMsg(replyTo: ActorRef[KafkaOffsetControlMsg])(msg: KafkaMsg): Command = msg match {
    case CreateWalletMsg(snapshot, _) => CreateWallet(WalletSnapshot(snapshot.amount, snapshot.instant), replyTo)
    case AddAmountToWalletMsg(snapshot, _) => AddAmountToWallet(WalletSnapshot(snapshot.amount, snapshot.instant), replyTo)
    case _ => throw InvalidMessageException("Invalid Kafka message")
  }
}
case class CreateWallet(walletSnapshot: WalletSnapshot, replyTo: ActorRef[KafkaOffsetControlMsg]) extends Command
case class AddAmountToWallet(walletSnapshot: WalletSnapshot, replyTo: ActorRef[KafkaOffsetControlMsg]) extends Command
