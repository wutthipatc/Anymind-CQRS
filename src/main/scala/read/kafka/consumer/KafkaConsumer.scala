package read.kafka.consumer

import `type`.StringToObject
import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, UniqueKillSwitch}
import org.slf4j.LoggerFactory
import read.kafka.config.KafkaConsumerConfigs
import read.model._
import write.kafka.message.KafkaMsg

import scala.concurrent.Future

object KafkaConsumer {
  private val logger = LoggerFactory.getLogger(classOf[KafkaConsumer.type])
  def apply(configs: KafkaConsumerConfigs, offsetControlActor: ActorRef[KafkaOffsetControlMsg])(implicit actorSystem: ActorSystem[_]): Unit = {
    logger.info(s"KafkaConsumer::apply")
    val killStream: UniqueKillSwitch = getSource(configs)
      .viaMat(KillSwitches.single)(Keep.right)
      .to(getActorSink(offsetControlActor))
      .run()
    registerShutdownHook(killStream)
  }
  private def registerShutdownHook(killStream: UniqueKillSwitch)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"KafkaConsumer::registerShutdownHook")
    import system.executionContext
    CoordinatedShutdown(system)
      .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "StopKafkaConsumerStream") {
        () =>
          for {
            _ <- Future(killStream.shutdown())
            _ = logger.info("StopKafkaConsumerStream successfully")
          } yield akka.Done
      }
  }
  private def getSource[T <: KafkaMsg: StringToObject](configs: KafkaConsumerConfigs)(implicit actorSystem: ActorSystem[_]): Source[(Long, T), Consumer.Control] = {
    import akka.actor.typed.scaladsl.adapter._
    val stringToObject: StringToObject[T] = implicitly[StringToObject[T]]
    Consumer.plainSource(configs.getConsumerSettings(actorSystem.toClassic), Subscriptions.topics(configs.topic))
      .map(consumerRecord => consumerRecord.offset() -> stringToObject(consumerRecord.value()))
  }

  private def getActorSink(offsetControlActor: ActorRef[KafkaOffsetControlMsg]) =
    ActorSink.actorRefWithBackpressure[(Long, KafkaMsg), KafkaOffsetControlMsg, ConsumerAckMsg.type](
      ref = offsetControlActor,
      messageAdapter = (ackReceiver: ActorRef[ConsumerAckMsg.type], element) => OffsetControlMsg(ackReceiver, Offset(element._1), Command.fromKafkaMsg(offsetControlActor)(element._2)),
      onInitMessage = ConsumerInitMsg,
      ackMessage = ConsumerAckMsg,
      onCompleteMessage = ConsumerCompleteMsg,
      onFailureMessage = ConsumerFailureMsg
    )
}
