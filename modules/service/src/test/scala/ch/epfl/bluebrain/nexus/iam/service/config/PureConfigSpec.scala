package ch.epfl.bluebrain.nexus.iam.service.config

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig._
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class PureConfigSpec extends WordSpecLike with Matchers with ScalatestRouteTest {

  private val valid = ConfigFactory.parseResources("test-settings.conf").resolve()

  "A pureconfig extension" should {
    "provide the appropriate config" in {
      val appConfig = new Settings(valid).appConfig

      appConfig.description.name shouldEqual "iam"
      appConfig.description.environment shouldEqual "local"

      appConfig.instance.interface shouldEqual "127.0.0.1"

      appConfig.http.interface shouldEqual "127.0.0.1"
      appConfig.http.port shouldEqual 8080
      appConfig.http.prefix shouldEqual "v0"
      appConfig.http.publicUri shouldEqual Uri("http://localhost:8080")

      appConfig.runtime.defaultTimeout shouldEqual (10 seconds)

      appConfig.cluster.passivationTimeout shouldEqual (5 seconds)
      appConfig.cluster.shards shouldEqual 100

      appConfig.persistence.journalPlugin shouldEqual "cassandra-journal"
      appConfig.persistence.snapshotStorePlugin shouldEqual "cassandra-snapshot-store"
      appConfig.persistence.queryJournalPlugin shouldEqual "cassandra-query-journal"

      appConfig.auth.testMode shouldEqual false
      appConfig.auth.adminGroups shouldEqual Set("nexus-admin-group")

      appConfig.oidc shouldEqual OidcConfig(
        List(
          OidcProviderConfig(
            "BBP",
            "http://localhost:8080/BBP/realm",
            "http://localhost:8080/BBP/cert",
            "http://localhost:8081/oauth2/authorize",
            "http://localhost:8081/oauth2/token",
            "http://localhost:8081/oauth2/userinfo"
          ),
          OidcProviderConfig(
            "HBP",
            "http://localhost:8080/HBP/realm",
            "http://localhost:8080/HBP/cert",
            "http://localhost:8081/HBP/oauth2/authorize",
            "http://localhost:8081/oauth2/HBP/token",
            "http://localhost:8081/HBP/oauth2/userinfo"
          )
        ),
        "BBP"
      )

      appConfig.context shouldEqual ContextConfig(
        ContextUri("http://localhost:8080/v0/contexts/nexus/core/error/v0.1.0"),
        ContextUri("http://localhost:8080/v0/contexts/nexus/core/iam/v0.1.0"))
    }
  }
}
