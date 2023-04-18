package read.model

import akka.actor.typed.ActorRef

sealed trait HourBalanceSeqControlMsg
case class SeqControlMsg(replyTo: ActorRef[AckMsg.type], sequence: Long) extends HourBalanceSeqControlMsg
case class InitMsg(replyTo: ActorRef[AckMsg.type], lastSeqOption: Option[Long]) extends HourBalanceSeqControlMsg
case object CompleteMsg extends HourBalanceSeqControlMsg
case class FailureMsg(t: Throwable) extends HourBalanceSeqControlMsg
case class AskSeqMsg(replyTo: ActorRef[Sequence]) extends HourBalanceSeqControlMsg

case class Sequence(value: Long)

case object AckMsg