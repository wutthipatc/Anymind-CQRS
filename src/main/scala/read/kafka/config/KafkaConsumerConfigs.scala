package read.kafka.config

import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import com.typesafe.config.Config
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.Duration
import scala.jdk.CollectionConverters._
import scala.util.Try

case class KafkaConsumerConfigs(
 pollInterval: Duration,
 pollTimeout: Duration,
 stopTimeout: Duration,
 closeTimeout: Duration,
 commitTimeout: Duration,
 useDispatcher: String,
 kafkaClients: Config,
 bootstrapServers: String,
 topic: String,
 groupId: String
) {
  private val notIncludedProperties = Set("bootstrap-server", "topic", "group-id")
  def getConsumerSettings(implicit actorSystem: ActorSystem): ConsumerSettings[String, String] =
    ConsumerSettings(actorSystem, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withPollInterval(pollInterval)
      .withPollTimeout(pollTimeout)
      .withStopTimeout(stopTimeout)
      .withCloseTimeout(closeTimeout)
      .withCommitTimeout(commitTimeout)
      .withDispatcher(useDispatcher)
      .withProperties(kafkaClients.entrySet().asScala
        .map(entry => entry.getKey -> entry.getValue.unwrapped().toString).toMap
        .filterNot{case (key, _) => notIncludedProperties.contains(key)}
      )
}
object KafkaConsumerConfigs {
  def apply(config: Config): Try[KafkaConsumerConfigs] =
    for {
      c <- Try(config.getConfig("kafka-consumer"))
      pollInterval <- Try(c.getDuration("poll-interval"))
      pollTimeout <- Try(c.getDuration("poll-timeout"))
      stopTimeout <- Try(c.getDuration("stop-timeout"))
      closeTimeout <- Try(c.getDuration("close-timeout"))
      commitTimeout <- Try(c.getDuration("commit-timeout"))
      useDispatcher <- Try(c.getString("use-dispatcher"))
      kafkaClients <- Try(c.getConfig("kafka-clients"))
      bootstrapServers <- Try(kafkaClients.getString("bootstrap-server"))
      topic <- Try(kafkaClients.getString("topic"))
      groupId <- Try(kafkaClients.getString("group-id"))
    } yield KafkaConsumerConfigs(pollInterval, pollTimeout, stopTimeout, closeTimeout, commitTimeout, useDispatcher, kafkaClients, bootstrapServers, topic, groupId)
}
