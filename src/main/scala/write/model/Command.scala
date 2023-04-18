package write.model

import akka.actor.typed.ActorRef
import write.actor.WalletState
import write.dto.response.WriteApiResponse

import java.time.Instant

case class WalletSnapshot(amount: BigDecimal, instant: Instant)

sealed trait Command
case class CreateWallet(walletSnapshot: WalletSnapshot, replyTo: ActorRef[WriteApiResponse]) extends Command
case class AddAmountToWallet(walletSnapshot: WalletSnapshot, replyTo: ActorRef[WriteApiResponse]) extends Command
case class AskState(replyTo: ActorRef[WalletState]) extends Command
