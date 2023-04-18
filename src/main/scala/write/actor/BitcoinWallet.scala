package write.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import write.dto.response.{AddToWalletSuccessResp, CreateWalletFailedResp, CreateWalletSuccessResp}
import write.kafka.config.KafkaProducerConfigs
import write.kafka.message.{AddAmountToWalletMsg, CreateWalletMsg, KafkaCommandMsg, WalletSnapshotMsg}
import write.model._

import java.time.Instant

case class WalletState(balance: BigDecimal, sequence: Long, isWalletCreated: Boolean, lastDateTime: Instant)
object BitcoinWallet {
  private val KAFKA_CHILD_PREFIX = "Kafka-"
  private def getChildName(walletId: String) = KAFKA_CHILD_PREFIX + walletId
  private def commandHandler(walletId: String): (WalletState, Command) => Effect[Event, WalletState] = (state, command) =>
    command match {
      // Use when need to receive CreateWallet command from api
      case CreateWallet(snapshot, replyTo) =>
        if (state.isWalletCreated) Effect.reply(replyTo)(CreateWalletFailedResp(s"Failed to create wallet with id: $walletId, wallet already existed"))
        else
          Effect.persist(CreateWalletEvent(WalletEvent.fromIdAndWalletSnapshot(walletId, snapshot)))
            .thenReply(replyTo)(_ => CreateWalletSuccessResp(s"Create wallet with info: $snapshot"))
      case AddAmountToWallet(snapshot, replyTo) =>
        if (state.isWalletCreated)
          Effect.persist(AddAmountToWalletEvent(WalletEvent.fromIdAndWalletSnapshot(walletId, snapshot)))
            .thenReply(replyTo)(_ => AddToWalletSuccessResp(s"Add wallet with amount: ${snapshot.amount}, time: ${snapshot.instant}"))
          // Use when need to receive CreateWallet command from api
//        else Effect.reply(replyTo)(AddToWalletFailedResp(s"Wallet with wallet id: $walletId was not created"))
        // As the requirement to initial the value to be 1000 BTC
        else Effect.persist(Seq(
          CreateWalletEvent(WalletEvent.fromIdAndWalletSnapshot(walletId, WalletSnapshot(1000, snapshot.instant))),
          AddAmountToWalletEvent(WalletEvent.fromIdAndWalletSnapshot(walletId, snapshot))
        )).thenReply(replyTo)(_ => AddToWalletSuccessResp(
          s"Create wallet with id: $walletId, initial amount: 1000 and Add amount: ${snapshot.amount}, time: ${snapshot.instant}"
        ))
      case AskState(replyTo) =>
        Effect.reply(replyTo)(state)
    }
  private def eventHandler(actorRef: ActorRef[KafkaCommandMsg]): (WalletState, Event) => WalletState = (state, event) =>
    event match {
      case CreateWalletEvent(walletEvent) =>
        val msg = CreateWalletMsg(WalletSnapshotMsg.fromWalletEventAndSequence(walletEvent, state.sequence))
        actorRef ! msg
        val newState = state.copy(balance = state.balance + walletEvent.amount, sequence = state.sequence + 1, isWalletCreated = true, lastDateTime = walletEvent.instant)
        newState
      case AddAmountToWalletEvent(walletEvent) =>
        val msg = AddAmountToWalletMsg(WalletSnapshotMsg.fromWalletEventAndSequence(walletEvent, state.sequence))
        actorRef ! msg
        val newState = state.copy(balance = state.balance + walletEvent.amount, sequence = state.sequence + 1, lastDateTime = walletEvent.instant)
        newState
    }
  def apply(walletId: String)(producerConfigs: KafkaProducerConfigs): Behavior[Command] =
    Behaviors.setup { case ctx =>
      ctx.log.info(s"BitcoinWallet::apply setup a persistent actor for wallet id: $walletId")
      val actorRef: ActorRef[KafkaCommandMsg] = ctx.spawn(KafkaProducerActor(producerConfigs), getChildName(walletId))
      EventSourcedBehavior[Command, Event, WalletState](
        persistenceId = PersistenceId.ofUniqueId(walletId),
        emptyState = WalletState(BigDecimal(0), 1, isWalletCreated = false, Instant.ofEpochMilli(0)),
        commandHandler = commandHandler(walletId),
        eventHandler = eventHandler(actorRef)
      )
    }
}
