package ch.epfl.bluebrain.nexus.iam.service.queue

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Producer
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import ch.epfl.bluebrain.nexus.commons.service.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import org.apache.kafka.clients.producer.ProducerRecord
import shapeless.Typeable
import io.circe.syntax._
import io.circe.generic.extras.auto._

object KafkaPublisher extends KafkaEncoder {

  private def flow(producerSettings: ProducerSettings[String, String],
                   topic: String): Flow[(Offset, String, Event), Offset, NotUsed] = {

    Flow[(Offset, String, Event)]
      .map {
        case (off, _, event) =>
          ProducerMessage.Message(
            new ProducerRecord[String, String](topic, event.path.toString, event.asJson.pretty(printer)),
            off)
      }
      .via(Producer.flow(producerSettings))
      .map(m => m.message.passThrough)

  }
  // $COVERAGE-OFF$
  final def start(projectionId: String,
                  pluginId: String,
                  tag: String,
                  name: String,
                  producerSettings: ProducerSettings[String, String],
                  topic: String)(implicit as: ActorSystem, T: Typeable[Event]): ActorRef =
    SequentialTagIndexer.start(
      flow(producerSettings, topic),
      projectionId,
      pluginId,
      tag,
      name
    )
  // $COVERAGE-ON$
}
