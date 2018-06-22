package mycelium.server

import akka.actor.{Actor, ActorRef}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Scheduler => MonixScheduler}
import mycelium.core.message._

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait DisconnectReason
object DisconnectReason {
  case object Stopped extends DisconnectReason
  case object Killed extends DisconnectReason
  case class StateFailed(failure: Throwable) extends DisconnectReason
}

case class ClientId(id: Int) extends AnyVal {
  override def toString = s"Client(${Integer.toString(id.hashCode, 36)})"
}

private[mycelium] class ConnectedClient[Payload, Failure, State](
  handler: RequestHandler[Payload, Failure, State])(implicit scheduler: MonixScheduler) extends Actor {
  import ConnectedClient._
  import handler._

  //TODO: observable state?
  def connected(outgoing: ActorRef) = {
    val cancelables = CompositeCancelable()
    val clientId = ClientId(self.hashCode)
    def stopActor(state: Future[State], reason: DisconnectReason): Unit = {
      onClientDisconnect(clientId, state, reason)
      cancelables.cancel()
      context.stop(self)
    }
    def safeWithState(state: Future[State]): Receive = {
      state.failed.foreach { t =>
        scribe.info("Shutting down actor, because the state future failed", t)
        stopActor(state, DisconnectReason.StateFailed(t))
      }
      withState(state)
    }
    def withState(state: Future[State]): Receive = {
      case Ping => outgoing ! Pong

      case CallRequest(seqId, path, args: Payload@unchecked) =>
        val response = onRequest(clientId, state, path, args)
        response match {
          case HandlerResponse.Single(state, task) =>
            cancelables += task
              .runOnComplete {
                case Success(value) => value match {
                  case Right(value) => outgoing ! SingleResponse(seqId, value)
                  case Left(err) => outgoing ! FailureResponse(seqId, err)
                }
                case Failure(t) =>
                  scribe.warn("Response task threw exception", t)
                  outgoing ! ErrorResponse(seqId)
              }

          case HandlerResponse.Stream(state, observable) =>
            cancelables += observable
              .map {
                case Right(value) => StreamResponse(seqId, value)
                case Left(value) => FailureResponse(seqId, value)
              }
              .endWith(StreamCloseResponse(seqId) :: Nil)
              .doOnError { t =>
                scribe.warn("Response stream threw exception", t)
                outgoing ! ErrorResponse(seqId)
              }
              .foreach(outgoing ! _)
        }

        context.become(safeWithState(response.state))

      case Stop => stopActor(state, DisconnectReason.Stopped)
    }

    val firstState = initialState
    onClientConnect(clientId, firstState)
    safeWithState(firstState)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing))
    case Stop => context.stop(self)
  }
}
private[mycelium] object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
