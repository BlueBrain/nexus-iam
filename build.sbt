/* Project definitions */

val commonsVersion = "0.4.0"

lazy val commonTypes     = nexusDep("common-types", commonsVersion)
lazy val sourcingCore    = nexusDep("sourcing-core", commonsVersion)
lazy val sourcingMemTest = nexusDep("sourcing-mem", commonsVersion, Test)
lazy val sourcingAkka    = nexusDep("sourcing-akka", commonsVersion)

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
  .enablePlugins(ServicePackagingPlugin)
  .settings(buildInfoSettings, packagingSettings)
  .settings(name := "iam-service", moduleName := "iam-service")

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
