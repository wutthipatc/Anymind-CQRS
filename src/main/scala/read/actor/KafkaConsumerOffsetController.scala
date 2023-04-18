package read.actor

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import database.DatabaseUtils
import org.slf4j.LoggerFactory
import read.model._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object KafkaConsumerOffsetController {
  private val logger = LoggerFactory.getLogger(classOf[KafkaConsumerOffsetController.type])
  def apply()(implicit system: ActorSystem[_]): Behavior[KafkaOffsetControlMsg] = Behaviors.setup { case ctx =>
    registerShutdownHook(ctx.self)
    val initialOffsetOption = getInitialOffset(ctx.self)
    receiveMessage(initialOffsetOption.getOrElse(Offset(0)))
  }
  private def registerShutdownHook(self: ActorRef[KafkaOffsetControlMsg])(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"KafkaConsumerOffsetController::registerShutdownHook")
    import system.executionContext
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "KafkaPersistLastOffset") { () =>
      implicit val askTimeout: Timeout = Timeout(5.seconds)
      implicit val scheduler: Scheduler = system.scheduler
      val db = DatabaseUtils.getConnection
      import slick.jdbc.MySQLProfile.api._
      for {
        offset <- self ? AskOffsetMsg
        _ <- db.run(sqlu"insert into consumer_last_offset_snapshot (persistence_id, offset) values ('#${self.path.name}', #${offset.value})")
        _ = db.close()
        _ = logger.info("Insert consumer_last_offset_snapshot complete")
      } yield akka.Done
    }
  }
  private def getInitialOffset(self: ActorRef[KafkaOffsetControlMsg])(implicit system: ActorSystem[_]): Option[Offset] = {
    logger.info("KafkaConsumerOffsetController::getInitialOffset")
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    val db = DatabaseUtils.getConnection
    Await.result(for {
      queryResult <- db.run(
        sql"""select offset
             from consumer_last_offset_snapshot where persistence_id = '#${self.path.name}'
             order by id desc limit 1""".as[Long]
      )
      _ = db.close()
    } yield queryResult.headOption.map(Offset), 3000.milli)
  }
  private def receiveMessage(offset: Offset): Behavior[KafkaOffsetControlMsg] = Behaviors.receive[KafkaOffsetControlMsg] { case (ctx, message) =>
    message match {
      case msg @ OffsetControlMsg(replyTo, msgOffset, command) =>
        ctx.log.info(s"KafkaConsumerOffsetController receive message OffsetControlMsg: $msg, stateOffset: $offset")
        if (msgOffset.value == offset.value) {
          // tell persistent actor to persist an event
          ctx.log.info(s"KafkaConsumerOffsetController call persistent actor with command: $command")
          replyTo ! ConsumerAckMsg
          receiveMessage(offset.copy(offset.value + 1))
        }
        else if (msgOffset.value > offset.value) {
          ctx.log.error(s"KafkaConsumerOffsetController invalid message order (message could be lost) received message OffsetControlMsg: $msg, stateOffset: $offset")
          replyTo ! ConsumerAckMsg
          receiveMessage(msgOffset)
        }
        else {
          ctx.log.info(s"KafkaConsumerOffsetController receive already processed message OffsetControlMsg: $msg, stateOffset: $offset")
          replyTo ! ConsumerAckMsg
          Behaviors.same
        }
      case ConsumerInitMsg(replyTo) =>
        ctx.log.info("KafkaConsumerOffsetController receive message ConsumerInitMsg")
        replyTo ! ConsumerAckMsg
        Behaviors.same
      case ConsumerCompleteMsg =>
        ctx.log.info("KafkaConsumerOffsetController receive message ConsumerCompleteMsg")
        Behaviors.same
      case m @ ConsumerFailureMsg(_) =>
        ctx.log.info(s"KafkaConsumerOffsetController receive message ConsumerFailureMsg: $m")
        Behaviors.same
      case AskOffsetMsg(replyTo) =>
        ctx.log.info(s"KafkaConsumerOffsetController receive message AskOffsetMsg offset: $offset")
        replyTo ! offset
        Behaviors.empty
    }
  }
}
