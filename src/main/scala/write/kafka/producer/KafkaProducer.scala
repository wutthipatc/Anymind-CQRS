package kafka.producer

import `type`.ObjectToString
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{CompletionStrategy, KillSwitches, UniqueKillSwitch}
import kafka.config.KafkaProducerConfigs
import kafka.message._
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

import scala.concurrent.Future

class KafkaProducer(configs: KafkaProducerConfigs)(implicit actorSystem: ActorSystem[_]) {
  private val logger = LoggerFactory.getLogger(classOf[KafkaProducer])
  def createActorFromStream[T <: KafkaMsg : ObjectToString](ackReceiver: ActorRef[KafkaCommandMsg]): ActorRef[T] = {
    logger.info("KafkaProducer::createActorFromStream")
    val commandToStringFn: ObjectToString[T] = implicitly[ObjectToString[T]]
    val source: Source[T, ActorRef[T]] = ActorSource.actorRefWithBackpressure[T, AckMessage](
      // get demand signalled to this actor receiving Ack
      ackTo = ackReceiver,
      ackMessage = SendKafkaSuccess,
      completionMatcher = {
        case Done => CompletionStrategy.immediately
      },
      failureMatcher = PartialFunction.empty
    )
    import akka.actor.typed.scaladsl.adapter._
    val (actorRef, killStream) = source
      .viaMat(KillSwitches.single)(Keep.both)
      .map(commandToStringFn)
      .map(elem => {
        logger.info(s"Create producer record with elem: $elem")
        new ProducerRecord(configs.topic, 0, elem, elem)
      })
      .to(Producer.plainSink(configs.getProducerSettings(actorSystem.toClassic))).run()
      registerShutdownHook(killStream)
      actorRef
  }
  private def registerShutdownHook(killStream: UniqueKillSwitch)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"KafkaProducer::registerShutdownHook")
    import system.executionContext
    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "StopKafkaProducerStream") {
        () =>
          for {
            _ <- Future(killStream.shutdown())
            _ = logger.info("StopKafkaProducerStream successfully")
          } yield akka.Done
      }
  }
}
object KafkaProducer {
  def fromProducerConfigs(configs: KafkaProducerConfigs)(implicit actorSystem: ActorSystem[_]): KafkaProducer =
    new KafkaProducer(configs)
}
