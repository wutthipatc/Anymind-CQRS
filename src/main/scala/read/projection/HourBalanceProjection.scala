package read.projection

import akka.NotUsed
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, UniqueKillSwitch}
import database.DatabaseUtils
import org.slf4j.LoggerFactory
import read.model._
import write.model.{AddAmountToWalletEvent, CreateWalletEvent, WalletEvent}

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}
import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object HourBalanceProjection {
  private val PROJECTION_DURATION_HOUR = 1
  private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH")
  private val logger = LoggerFactory.getLogger(classOf[HourBalanceProjection.type])
  @tailrec
  private def generateHourRecurse(accList: List[ZonedDateTime], startExclusive: ZonedDateTime, endExclusive: ZonedDateTime): List[ZonedDateTime] =
    if (endExclusive.compareTo(startExclusive.plusHours(PROJECTION_DURATION_HOUR)) <= 0) accList.reverse
    else
      generateHourRecurse(startExclusive.plusHours(PROJECTION_DURATION_HOUR) :: accList, startExclusive.plusHours(PROJECTION_DURATION_HOUR), endExclusive)

  private def generateMissingHourStrDBValues(walletId: String, startBalance: BigDecimal, startExclusive: ZonedDateTime, endExclusive: ZonedDateTime): List[String] =
    generateHourRecurse(List(), startExclusive, endExclusive)
      .map(zonedDateTime => s"('$walletId', $startBalance, '${fullDateTimeFormatter.format(zonedDateTime)}')")

  private def queryAndStore(event: WalletEvent)(implicit system: ActorSystem[_]): Future[Unit] = {
    logger.info(s"HourBalanceProjection::queryAndStore with waller event: $event")
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    val db = DatabaseUtils.getConnection
    val WalletEvent(walletId, amount, instant) = event
    val zonedDateTime: ZonedDateTime = instant.atZone(ZoneId.of("UTC"))
    val (minute, second, milliSec) = (zonedDateTime.getMinute, zonedDateTime.getSecond, zonedDateTime.get(ChronoField.MILLI_OF_SECOND))
    val isAbsoluteHour = minute == 0 && second == 0 && milliSec == 0

    val endDateTimeInclusive = if (isAbsoluteHour) zonedDateTime else zonedDateTime.plusHours(PROJECTION_DURATION_HOUR)
    //2018-10-08 15:19:50 MySql format
    val dateConditionStr = s"${dateTimeFormatter.format(endDateTimeInclusive)}:00:00"
    val effectiveEndDateTime = ZonedDateTime.of(LocalDateTime.parse(dateConditionStr, fullDateTimeFormatter), ZoneId.of("UTC"))
    (for {
      queryResult <- db.run(
        sql"""select id, balance
             from wallet_hour_projection
             where wallet_id = '#$walletId' and date_time_projection = '#$dateConditionStr' limit 1""".as[(Long, BigDecimal)]
      )
      _ <- queryResult.headOption
        .fold{
          // if not found date_time_projection record from event end-of-hour time
          for {
            selectResultTuple <- db.run(
              sql"""select balance, date_time_projection
                   from wallet_hour_projection
                   where wallet_id = '#$walletId' and date_time_projection < '#$dateConditionStr' order by id DESC limit 1""".as[(BigDecimal, Timestamp)]
            )
            strValues = selectResultTuple.headOption.map{ case(startBalance, timestamp) =>
              generateMissingHourStrDBValues(
                walletId,
                startBalance,
                Instant.ofEpochMilli(timestamp.getTime).atZone(ZoneId.of("UTC")),
                effectiveEndDateTime
              )
            }
            accumulatedBalance = selectResultTuple.headOption.map(_._1).map(_ + amount).getOrElse(amount)
            insertResult <- db.run(
              sqlu"""insert into wallet_hour_projection (wallet_id, balance, date_time_projection)
                    values #${strValues.map(list => if (list.nonEmpty) list.mkString("", ", ", ",") else "").getOrElse("")}
                    ('#$walletId', #$accumulatedBalance, '#$dateConditionStr')"""
            )
          } yield insertResult
          // if the event end-of-hour time record from wallet_hour_projection existed, update the balance
        }(idBalanceTuple => db.run(sqlu"update wallet_hour_projection set balance = #${idBalanceTuple._2 + amount} where id = #${idBalanceTuple._1}" ))
      _ = db.close()
      _ = logger.info("Upsert into wallet_hour_projection successfully")
    } yield ()).recover{case t => t.printStackTrace()}
  }
  private def getInitialReaderSequence(walletId: String)(implicit system: ActorSystem[_]): Option[Long] = {
    logger.info(s"HourBalanceProjection::getInitialReaderSequence with wallet id: $walletId")
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    val db = DatabaseUtils.getConnection
    Await.result(for {
      result <- db.run(sql"select sequence from reader_last_sequence_snapshot where persistence_id = '#$walletId' order by id desc limit 1".as[Long])
      _ = db.close()
    } yield result.headOption, 3000.milli)
  }
  private def registerShutdownHook(killStream: UniqueKillSwitch)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"HourBalanceProjection::registerShutdownHook")
    import system.executionContext
    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "StopReadEventJournal") {
        () =>
          for {
            _ <- Future(killStream.shutdown())
            _ = logger.info("StopReadEventJournal successfully")
          } yield akka.Done
      }
  }
  def apply(walletId: String, controlSeqActor: ActorRef[HourBalanceSeqControlMsg])(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"HourBalanceProjection::apply with wallet id: $walletId")
    import system.executionContext
    val lastSeqOption = getInitialReaderSequence(walletId)
    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    val eventSource: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId(walletId, lastSeqOption.getOrElse(0), Long.MaxValue)
    val killStream: UniqueKillSwitch = eventSource
      .viaMat(KillSwitches.single)(Keep.right)
      .map(envelope => envelope.sequenceNr -> envelope.event)
      .map { case (seq, event) => event match {
        case event@CreateWalletEvent(walletEvent) =>
          logger.info(s"HourBalanceProjection stream for element: $event, seq: $seq")
          seq -> walletEvent
        case event@AddAmountToWalletEvent(walletEvent) =>
          logger.info(s"HourBalanceProjection stream for element: $event, seq: $seq")
          seq -> walletEvent
      }}
      // avoid race condition
      .mapAsync(1){ case (seq, event) => queryAndStore(event).map(_ => seq) }
      .to(ActorSink.actorRefWithBackpressure[Long, HourBalanceSeqControlMsg, AckMsg.type](
        ref = controlSeqActor,
        messageAdapter = SeqControlMsg,
        onInitMessage = ackRef => InitMsg(ackRef, lastSeqOption),
        onCompleteMessage = CompleteMsg,
        onFailureMessage = FailureMsg
      )).run()
    registerShutdownHook(killStream)
  }
}
