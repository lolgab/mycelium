package mycelium.client

import mycelium.core.message._
import chameleon._

import scala.concurrent.{ ExecutionContext, Future }

case object DroppedMessageException extends Exception

class WebsocketClientWithPayload[PickleType, Payload, Event, Failure](
  wsConfig: WebsocketConfig,
  ws: WebsocketConnection[PickleType],
  handler: IncidentHandler[Event],
  callRequests: CallRequests[Either[Failure, Payload]])(implicit
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Event, Failure], PickleType]) {

  def send(path: List[String], payload: Payload, sendType: SendType)(implicit ec: ExecutionContext): Future[Either[Failure, Payload]] = {
    val (id, promise) = callRequests.open()
    val request = CallRequest(id, path, payload)
    val pickledRequest = serializer.serialize(request)

    def startTimeout(): Unit = {
      callRequests.startTimeout(promise)
    }
    def fail(): Unit = {
      promise tryFailure DroppedMessageException
      ()
    }

    val message = sendType match {
      case SendType.NowOrFail =>
        WebsocketMessage.Direct(pickledRequest, startTimeout _, fail _)
      case SendType.WhenConnected(priority) =>
        WebsocketMessage.Buffered(pickledRequest, startTimeout _, priority)
    }

    ws.send(message)

    promise.future.failed.foreach { err =>
      scribe.error(s"Request $id failed: $err")
    }

    promise.future
  }

  def run(location: String): Unit = ws.run(location, wsConfig, serializer.serialize(Ping()), new WebsocketListener[PickleType] {
    def onConnect() = handler.onConnect()
    def onClose() = handler.onClose()
    def onMessage(msg: PickleType): Unit = {
      deserializer.deserialize(msg) match {
        case Right(CallResponse(seqId, result)) =>
          callRequests.get(seqId) match {
            case Some(promise) =>
              val completed = promise trySuccess result
              if (!completed) scribe.warn(s"Ignoring incoming response ($seqId), it already timed out.")
            case None => scribe.warn(s"Ignoring incoming response ($seqId), unknown sequence id.")
          }
        case Right(Notification(events)) =>
          handler.onEvents(events)
        case Right(Pong()) =>
          // do nothing
        case Left(error) =>
          scribe.warn(s"Ignoring message. Deserializer failed: $error")
      }
    }
  })
}

object WebsocketClient {
  def apply[PickleType, Event, Failure](
    connection: WebsocketConnection[PickleType],
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], PickleType]) =
    withPayload[PickleType, PickleType, Event, Failure](connection, config, handler)

  def withPayload[PickleType, Payload, Event, Failure](
    connection: WebsocketConnection[PickleType],
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, Event, Failure], PickleType]) = {
    val callRequests = new CallRequests[Either[Failure, Payload]](config.requestTimeout)
    new WebsocketClientWithPayload(config.websocketConfig, connection, handler, callRequests)
  }
}
