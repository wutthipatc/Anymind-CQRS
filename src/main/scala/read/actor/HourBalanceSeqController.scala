package read.actor

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import database.DatabaseUtils
import org.slf4j.LoggerFactory
import read.model._

import scala.concurrent.duration.DurationInt

object HourBalanceSeqController {
  private val logger = LoggerFactory.getLogger(classOf[HourBalanceSeqController.type])
  def apply(walletId: String): Behavior[HourBalanceSeqControlMsg] = Behaviors.setup { case ctx =>
    logger.info(s"HourBalanceSeqController::apply with wallet id: $walletId")
    import ctx.system
    registerShutdownHook(ctx.self)(walletId)
    receiveMessage(walletId)(0)
  }
  private def registerShutdownHook(self: ActorRef[HourBalanceSeqControlMsg])(walletId: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"HourBalanceSeqController::registerShutdownHook with wallet id: $walletId")
    import system.executionContext
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "HourBalancePersistLastSequence") { () =>
      implicit val askTimeout: Timeout = Timeout(5.seconds)
      implicit val scheduler: Scheduler = system.scheduler
      val db = DatabaseUtils.getConnection
      import slick.jdbc.MySQLProfile.api._
      for {
        sequence <- self ? AskSeqMsg
        _ <- db.run(sqlu"insert into reader_last_sequence_snapshot (persistence_id, sequence) values ('#$walletId', #${sequence.value + 1})")
        _ = db.close()
        _ = logger.info("Insert reader_last_sequence_snapshot complete")
      } yield akka.Done
    }
  }
  private def receiveMessage(walletId: String)(seq: Long): Behavior[HourBalanceSeqControlMsg] = Behaviors.receive { case (ctx, msg) =>
    msg match {
      case SeqControlMsg(replyTo, sequence) =>
        ctx.log.info(s"HourBalanceSequenceController received SeqControlMsg sequence: $sequence")
        replyTo ! AckMsg
        receiveMessage(walletId)(sequence)
      case InitMsg(replyTo, lastSeqOption) =>
        ctx.log.info("HourBalanceSequenceController received InitMsg")
        replyTo ! AckMsg
        // minus 1 in order to keep the same sequence if there is no SeqControlMsg call
        lastSeqOption.map(last => receiveMessage(walletId)(last - 1)).getOrElse(Behaviors.same)
      case CompleteMsg =>
        ctx.log.info("HourBalanceSequenceController received CompleteMsg")
        Behaviors.same
      case m @ FailureMsg(_) =>
        ctx.log.info(s"HourBalanceSequenceController received FailureMsg: $m")
        Behaviors.same
      case AskSeqMsg(replyTo) =>
        ctx.log.info("HourBalanceSequenceController received AskMsg")
        replyTo ! Sequence(seq)
        Behaviors.stopped
    }
  }
}
