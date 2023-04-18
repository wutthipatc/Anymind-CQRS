import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import common.route.AllRoute
import read.actor.{HourBalanceSeqController, KafkaConsumerOffsetController}
import read.kafka.config.KafkaConsumerConfigs
import read.kafka.consumer.KafkaConsumer
import read.projection.HourBalanceProjection
import read.route.ReadRoute
import read.service.GetHourBalanceService
import write.actor.BitcoinWallet
import write.kafka.config.KafkaProducerConfigs
import write.route.WriteRoute
import write.service.AddBitCoinService

object Main {
  def main(args: Array[String]): Unit = {
    val rootConfigs = ConfigFactory.load()
    val kafkaProducerConfigs: KafkaProducerConfigs = KafkaProducerConfigs(rootConfigs).get
    val kafkaConsumerConfigs: KafkaConsumerConfigs = KafkaConsumerConfigs(rootConfigs).get

    val root = Behaviors.setup[Any] { case ctx =>
      implicit val actorSystem: ActorSystem[Nothing] = ctx.system
      val wallet = ctx.spawn(BitcoinWallet("TestWallet")(kafkaProducerConfigs), "TestWallet")
      val hourBalanceSeqController = ctx.spawn(HourBalanceSeqController("TestWallet"), "seqController")
      val offsetControlActor = ctx.spawn(KafkaConsumerOffsetController(), "Consumer-TestWallet")
      HourBalanceProjection("TestWallet", hourBalanceSeqController)(ctx.system)
      KafkaConsumer(kafkaConsumerConfigs, offsetControlActor)
      val addBitCoinService = AddBitCoinService(wallet)
      val writeRoute = WriteRoute(addBitCoinService)
      val getHourBalanceService = GetHourBalanceService()
      val readRoute = ReadRoute(getHourBalanceService)
      Http().newServerAt("localhost", 8080).bind(AllRoute(writeRoute, readRoute))
      Behaviors.empty
    }
    ActorSystem(root, "WalletTest")
  }
}
