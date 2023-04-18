package read.service

import akka.actor.typed.ActorSystem
import common.dto.BaseResponse
import read.dto.response.{GetHourBalanceResponse, HourBalanceResponse}
import slick.jdbc.JdbcBackend.Database

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.concurrent.Future

class GetHourBalanceService(implicit system: ActorSystem[_]) {
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  def getHourBalance(walletId: String, startTime: Instant, endTime: Instant): Future[BaseResponse[GetHourBalanceResponse]] = {
    val db = Database.forConfig("slick-mysql")
    val startTimeStr = dateTimeFormatter.format(startTime.atZone(ZoneId.of("UTC")))
    val endTimeStr = dateTimeFormatter.format(endTime.atZone(ZoneId.of("UTC")))
    import slick.jdbc.MySQLProfile.api._
    import system.executionContext
    for {
      queryResult <- db.run(sql"""select balance, date_time_projection
               from wallet_hour_projection
               where wallet_id = '#$walletId' and
               date_time_projection >= '#$startTimeStr' and
               date_time_projection <= '#$endTimeStr'""".as[(BigDecimal, Timestamp)]
        )
      _ = db.close()
      respList = queryResult.map{ case (balance, timestamp) => HourBalanceResponse(timestamp.toInstant, balance)}.toList
      resp = GetHourBalanceResponse(respList)
    } yield BaseResponse.getSuccessResponse(resp)
  }
}
object GetHourBalanceService {
  def apply()(implicit system: ActorSystem[_]): GetHourBalanceService =
    new GetHourBalanceService
}
