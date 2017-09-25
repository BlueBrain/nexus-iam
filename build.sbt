/* Project definitions */

val commonsVersion    = "0.4.3"
val pureconfigVersion = "0.7.2"

lazy val commonTypes     = nexusDep("common-types", commonsVersion)
lazy val sourcingCore    = nexusDep("sourcing-core", commonsVersion)
lazy val sourcingMemTest = nexusDep("sourcing-mem", commonsVersion, Test)
lazy val sourcingAkka    = nexusDep("sourcing-akka", commonsVersion)
lazy val serviceCommon   = nexusDep("service-commons", commonsVersion)

lazy val core = project
  .in(file("modules/core"))
  .settings(
    name := "iam-core",
    moduleName := "iam-core",
    libraryDependencies ++= Seq(
      sourcingCore,
      "com.typesafe.akka"  %% "akka-http"  % akkaHttpVersion.value,
      "com.chuusai"        %% "shapeless"  % shapelessVersion.value,
      "io.circe"           %% "circe-core" % circeVersion.value,
      "io.verizon.journal" %% "core"       % journalVersion.value,
      sourcingMemTest,
      "io.circe"      %% "circe-parser"         % circeVersion.value     % Test,
      "io.circe"      %% "circe-generic-extras" % circeVersion.value     % Test,
      "org.scalatest" %% "scalatest"            % scalaTestVersion.value % Test
    )
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(core)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .settings(buildInfoSettings, packagingSettings)
  .settings(
    name := "iam-service",
    moduleName := "iam-service",
    libraryDependencies ++= Seq(
      serviceCommon,
      sourcingAkka,
      "ch.megard"             %% "akka-http-cors"             % akkaHttpCorsVersion.value,
      "com.github.pureconfig" %% "pureconfig"                 % pureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-akka"            % pureconfigVersion,
      "com.typesafe.akka"     %% "akka-http"                  % akkaHttpVersion.value,
      "com.typesafe.akka"     %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion.value,
      "de.heikoseeberger"     %% "akka-http-circe"            % akkaHttpCirceVersion.value,
      "com.chuusai"           %% "shapeless"                  % shapelessVersion.value,
      "io.circe"              %% "circe-parser"               % circeVersion.value,
      "io.circe"              %% "circe-java8"                % circeVersion.value,
      "io.circe"              %% "circe-generic-extras"       % circeVersion.value,
      "com.github.dnvriend"   %% "akka-persistence-inmemory"  % akkaPersistenceInMemVersion.value % Test,
      "com.typesafe.akka"     %% "akka-testkit"               % akkaVersion.value % Test,
      "com.typesafe.akka"     %% "akka-http-testkit"          % akkaHttpVersion.value % Test,
      "org.scalatest"         %% "scalatest"                  % scalaTestVersion.value % Test
    )
  )

lazy val root = project
  .in(file("."))
  .settings(name := "iam", moduleName := "iam")
  .settings(noPublish)
  .aggregate(core, service)

/* Common settings */

lazy val noPublish = Seq(publishLocal := {}, publish := {})

lazy val buildInfoSettings =
  Seq(buildInfoKeys := Seq[BuildInfoKey](version), buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.service.config")

lazy val packagingSettings = packageName in Docker := "iam"

def nexusDep(artifact: String, version: String, conf: Configuration = Compile): ModuleID =
  "ch.epfl.bluebrain.nexus" %% artifact % version % conf

addCommandAlias("review", ";clean;coverage;scapegoat;test;coverageReport;coverageAggregate")
addCommandAlias("rel", ";release with-defaults")
