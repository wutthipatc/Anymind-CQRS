package kafka.config

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import com.typesafe.config.Config
import org.apache.kafka.common.serialization.StringSerializer

import java.time.Duration
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.util.Try

case class KafkaProducerConfigs(
  parallelism: Int,
  closeTimeout: Duration,
  useDispatcher: String,
  kafkaClients: Config,
  bootstrapServers: String,
  topic: String
) {
  private val notIncludedProperties = Set("bootstrap-server", "topic")
  def getProducerSettings(implicit actorSystem: ActorSystem): ProducerSettings[String, String] =
    ProducerSettings(actorSystem, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServers)
      .withParallelism(parallelism)
      .withCloseTimeout(closeTimeout)
      .withDispatcher(useDispatcher)
      .withProperties(kafkaClients.entrySet().asScala
        .map(entry => entry.getKey -> entry.getValue.unwrapped().toString).toMap
        .filterNot{case (key, _) => notIncludedProperties.contains(key)}
      )
}
object KafkaProducerConfigs {
  def apply(config: Config): Try[KafkaProducerConfigs] =
    for {
      c <- Try(config.getConfig("kafka-producer"))
      parallelism <- Try(c.getInt("parallelism"))
      closeTimeout <- Try(c.getDuration("close-timeout"))
      useDispatcher <- Try(c.getString("use-dispatcher"))
      kafkaClients <- Try(c.getConfig("kafka-clients"))
      bootstrapServers <- Try(kafkaClients.getString("bootstrap-server"))
      topic <- Try(kafkaClients.getString("topic"))
    } yield KafkaProducerConfigs(parallelism, closeTimeout, useDispatcher, kafkaClients, bootstrapServers, topic)
}