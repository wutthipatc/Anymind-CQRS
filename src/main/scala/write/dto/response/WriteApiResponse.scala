package write.dto.response

import spray.json.{JsString, JsonWriter}

import scala.language.implicitConversions

sealed trait WriteApiResponse
object WriteApiResponse {
  implicit val jsonWriter: JsonWriter[WriteApiResponse] = JsonWriter.func2Writer {
    case CreateWalletSuccessResp(message) => JsString(s"Create wallet success with message: $message")
    case CreateWalletFailedResp(message) => JsString(s"Create wallet failed with message: $message")
    case AddToWalletSuccessResp(message) => JsString(s"Add wallet success with message: $message")
    case AddToWalletFailedResp(message) => JsString(s"Add wallet failed with message: $message")
  }
}
case class CreateWalletSuccessResp(message: String) extends WriteApiResponse
case class CreateWalletFailedResp(message: String) extends WriteApiResponse
case class AddToWalletSuccessResp(message: String) extends WriteApiResponse
case class AddToWalletFailedResp(message: String) extends WriteApiResponse
