package write.actor

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import database.DatabaseUtils
import org.slf4j.LoggerFactory
import write.kafka.config.KafkaProducerConfigs
import write.kafka.message.{AddAmountToWalletMsg, AskSequence, CreateWalletMsg, Done, KafkaCommandMsg, KafkaMsg, SendKafkaFailed, SendKafkaSuccess, Sequence}
import write.kafka.producer.KafkaProducer

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

case class MessageSequence(sequenceNumber: Long)
case class State(sequence: MessageSequence, isAck: Boolean)
object KafkaProducerActor {
  private val logger = LoggerFactory.getLogger(classOf[KafkaProducerActor.type])
  def apply(producerConfigs: KafkaProducerConfigs): Behavior[KafkaCommandMsg] = Behaviors.setup { case ctx =>
    logger.info("KafkaProducerActor::apply")
    import ctx.system
    val streamActor: ActorRef[KafkaMsg] = KafkaProducer.fromProducerConfigs(producerConfigs)(ctx.system).createActorFromStream(ctx.self)
    registerShutdownHook(ctx.self, streamActor)
    val sequenceOption = getInitialWriterSeq(ctx.self)
    receiveMessage(streamActor)(State(sequenceOption.map(MessageSequence).getOrElse(MessageSequence(1)), isAck = true))
  }
  private def registerShutdownHook(self: ActorRef[KafkaCommandMsg], streamActor: ActorRef[KafkaMsg])(implicit system: ActorSystem[_]): Unit = CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "WriterPersistLastSequence") { () =>
    logger.info("KafkaProducerActor::registerShutdownHook")
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    implicit val askTimeout: Timeout = Timeout(5.seconds)
    implicit val scheduler: Scheduler = system.scheduler
    val db = DatabaseUtils.getConnection
    for {
      sequenceResp <- self ? AskSequence
      // Persisted the sequence of event to be process after terminated and restart
      sequence = sequenceResp.sequence
      _ <- db.run(sqlu"insert into writer_last_sequence_snapshot (persistence_id, sequence) values ('#${self.path.name}', #$sequence)")
      _ = db.close()
      _ = logger.info("Insert writer_last_sequence_snapshot complete")
    } yield akka.Done
  }
  private def getInitialWriterSeq(self: ActorRef[KafkaCommandMsg])(implicit system: ActorSystem[_]) = {
    logger.info("KafkaProducerActor::getInitialWriterSeq")
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    val db = DatabaseUtils.getConnection
    Await.result(for {
      queryResult <- db.run(
        sql"""select sequence
             from writer_last_sequence_snapshot where persistence_id = '#${self.path.name}'
             order by id desc limit 1""".as[Long]
      )
      _ = db.close()
    } yield queryResult.headOption, 3000.milli)
  }

  private def receiveMessage(streamActor: ActorRef[KafkaMsg])(state: State): Behavior[KafkaCommandMsg] = Behaviors.receive[KafkaCommandMsg] { case (ctx, message) =>
    message match {
      case msg @ CreateWalletMsg(snapshotMsg, _) =>
        ctx.log.info(s"Receive message CreateWalletMsg: $msg, sequence: ${state.sequence}, isAck: ${state.isAck}")
        if (snapshotMsg.sequenceNumber == state.sequence.sequenceNumber) {
          if (state.isAck) {
            streamActor ! msg
            receiveMessage(streamActor)(state.copy(isAck = false))
          }
          else {
            ctx.self ! msg
            Behaviors.same
          }
        }
        else if (snapshotMsg.sequenceNumber > state.sequence.sequenceNumber) {
          ctx.self ! msg
          Behaviors.same
        }
        else Behaviors.same
      case msg @ AddAmountToWalletMsg(snapshotMsg, _) =>
        ctx.log.info(s"Receive message AddAmountToWalletMsg: $msg, sequence: ${state.sequence}, isAck: ${state.isAck}")
        if (snapshotMsg.sequenceNumber == state.sequence.sequenceNumber) {
          if (state.isAck) {
            streamActor ! msg
            receiveMessage(streamActor)(state.copy(isAck = false))
          }
          else {
            ctx.self ! msg
            Behaviors.same
          }
        }
        else if (snapshotMsg.sequenceNumber > state.sequence.sequenceNumber) {
          ctx.self ! msg
          Behaviors.same
        }
        else Behaviors.same
      case SendKafkaSuccess =>
        ctx.log.info("Send Kafka success")
        receiveMessage(streamActor)(state.copy(sequence = state.sequence.copy(sequenceNumber = state.sequence.sequenceNumber + 1), isAck = true))
      case SendKafkaFailed(_, t) =>
        ctx.log.error(s"Send Kafka failed with message: ${t.getMessage}")
        Behaviors.same
      case AskSequence(replyTo) =>
        ctx.log.info(s"Got AskSequence sequence: ${state.sequence}")
        replyTo ! Sequence(state.sequence.sequenceNumber)
        streamActor ! Done
        Behaviors.empty
    }
  }
}
