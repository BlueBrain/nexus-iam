package ch.epfl.bluebrain.nexus.iam.service.queue

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Producer
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization._
import ch.epfl.bluebrain.nexus.commons.service.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.Encoder
import org.apache.kafka.clients.producer.ProducerRecord
import shapeless.Typeable

/**
  * Class responsible for starting publishing to Kafka using a `SequentialTagIndexer`
  */
object KafkaPublisher {

  private def flow(producerSettings: ProducerSettings[String, String], topic: String)(
      implicit api: ApiUri): Flow[(Offset, String, Event), Offset, NotUsed] = {
    implicit val ee: Encoder[Event] = eventEncoder(api.base.copy(path = api.base.path / "realms"))
    val m                           = jsonLdMarshaller(api.base)
    Flow[(Offset, String, Event)]
      .map {
        case (off, _, event) =>
          ProducerMessage.Message(new ProducerRecord[String, String](topic, event.path.toString, m(event)), off)
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
                  topic: String)(implicit as: ActorSystem, T: Typeable[Event], api: ApiUri): ActorRef =
    SequentialTagIndexer.start(
      flow(producerSettings, topic),
      projectionId,
      pluginId,
      tag,
      name
    )
  // $COVERAGE-ON$
}
