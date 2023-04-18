package write.service

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.model.StatusCode
import akka.util.Timeout
import common.dto.BaseResponse
import org.slf4j.LoggerFactory
import write.actor.WalletState
import write.dto.request.AddBitCoinRequest
import write.dto.response.WriteApiResponse
import write.model.{AskState, Command, CreateWallet, WalletSnapshot}

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class AddBitCoinService(actorRef: ActorRef[Command])(implicit system: ActorSystem[_]) {
  private val logger = LoggerFactory.getLogger(classOf[AddBitCoinService])
  private val initBalance = BigDecimal(1000)
  implicit val askTimeout: Timeout = Timeout(5.seconds)
  implicit val scheduler: Scheduler = system.scheduler
  // initiate wallet with 1000 balance, will be ignored if already created
  Await.result(actorRef ? CreateWallet.curried(WalletSnapshot(initBalance, Instant.now)), 3000.seconds)
  import system.executionContext
  def addBitCoin(request: AddBitCoinRequest): Future[BaseResponse[WriteApiResponse]] = {
    logger.info(s"AddBitCoinService::addBitCoin with request: $request")
    val isRequestDateTimeValid: (Instant, Instant) => Boolean = (reqDateTime, currentStateDateTime) =>
      reqDateTime.compareTo(currentStateDateTime) >= 0
    val stateFuture: Future[WalletState] = actorRef ? AskState
    (for {
      state <- stateFuture if isRequestDateTimeValid(request.dateTime, state.lastDateTime)
      resp <- actorRef ? AddBitCoinRequest.toActorCommand(request)
    } yield BaseResponse.getSuccessResponse(resp)) recoverWith {
      case _: NoSuchElementException =>
        stateFuture.map(state => BaseResponse.getErrorResponse[WriteApiResponse](
          s"Request date time must be greater than or equal ${state.lastDateTime} UTC time",
          Some(StatusCode.int2StatusCode(400))
        ))
      case ex => Future.failed(ex)
    }
  }
}
object AddBitCoinService {
  def apply(actorRef: ActorRef[Command])(implicit system: ActorSystem[_]): AddBitCoinService =
    new AddBitCoinService(actorRef)
}
