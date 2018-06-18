package test

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import chameleon._
import chameleon.ext.boopickle._
import mycelium.client._
import mycelium.core._
import mycelium.core.message._
import mycelium.server._
import org.scalatest._
import monix.eval.Task

class MyceliumSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {
  import monix.execution.Scheduler.Implicits.global

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  type Payload = Int
  type Failure = Int
  type State = String

  "client" in {
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Failure](
      new AkkaWebsocketConnection(bufferSize = 100, overflowStrategy = OverflowStrategy.fail), WebsocketClientConfig())

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, 1, SendType.NowOrFail, None)
    val res2 = client.send("foo" :: "bar" :: Nil, 1, SendType.WhenConnected, None)

    res.failed.map(_ mustEqual RequestException.Dropped)
    res2.runAsync.value mustEqual None
  }

  "server" in {
    val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
    val handler = new StatelessRequestHandler[Payload, Failure] {
      def onRequest(client: ClientId, path: List[String], payload: Payload) = ResponseValue(Task(Right(payload)))
    }

    val server = WebsocketServer.withPayload(config, handler)
    val flow = server.flow()

    val payloadValue = 1
    val builder = implicitly[AkkaMessageBuilder[ByteBuffer]]
    val serializer = implicitly[Serializer[ClientMessage[Payload], ByteBuffer]]
    val deserializer = implicitly[Deserializer[ServerMessage[Payload, Failure], ByteBuffer]]
    val request = CallRequest(1, "foo" :: "bar" :: Nil, payloadValue)
    val msg = builder.pack(serializer.serialize(request))

    val (_, received) = flow.runWith(Source(msg :: Nil), Sink.head)
    val response = received.flatMap { msg =>
      builder.unpack(msg).map(_.map(s => deserializer.deserialize(s).right.get))
    }

    val expected = SingleResponse(1, payloadValue)
    response.map(_ mustEqual Some(expected))
  }
}
