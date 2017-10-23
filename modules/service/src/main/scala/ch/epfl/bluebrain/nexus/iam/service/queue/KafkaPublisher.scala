package ch.epfl.bluebrain.nexus.iam.service.queue

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Uri
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

/**
  * Class responsible for starting publishing to Kafka using a `SequentialTagIndexer`
  *
  * @param baseUri uri used as base for `@context` and `@id`'s
  */
case class KafkaPublisher(baseUri: Uri) extends KafkaEncoder {

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
  /**
    * Start publishing events to Kafka
    *
    * @param projectionId projection to user for publishing events
    * @param pluginId query pluging ID
    * @param tag events with which tag to publish
    * @param name name of the actor
    * @param producerSettings Akka StreamsKafka producer settings
    * @param topic topic to publish to
    * @param as implicit ActorSystem
    * @param T implicit Typeable
    * @return ActorRef for the started actor
    */
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
