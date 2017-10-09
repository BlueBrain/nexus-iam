/* Project definitions */

val commonsVersion = "0.4.6"

lazy val commonTypes     = nexusDep("common-types", commonsVersion)
lazy val commonsHttp     = nexusDep("commons-http", commonsVersion)
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
      commonsHttp,
      "com.typesafe.akka"  %% "akka-http"            % akkaHttpVersion.value,
      "com.chuusai"        %% "shapeless"            % shapelessVersion.value,
      "io.circe"           %% "circe-core"           % circeVersion.value,
      "io.circe"           %% "circe-generic-extras" % circeVersion.value,
      "io.verizon.journal" %% "core"                 % journalVersion.value,
      sourcingMemTest,
      "io.circe"      %% "circe-parser"         % circeVersion.value     % Test,
      "io.circe"      %% "circe-generic-extras" % circeVersion.value     % Test,
      "org.scalatest" %% "scalatest"            % scalaTestVersion.value % Test,
      "org.mockito"   % "mockito-core"          % "2.10.0"               % Test
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
    libraryDependencies ++= kamonDeps ++ Seq(
      serviceCommon,
      sourcingAkka,
      commonsHttp,
      "ch.megard"             %% "akka-http-cors"             % akkaHttpCorsVersion.value,
      "com.github.pureconfig" %% "pureconfig"                 % pureconfigVersion.value,
      "com.github.pureconfig" %% "pureconfig-akka"            % pureconfigVersion.value,
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
      "org.scalatest"         %% "scalatest"                  % scalaTestVersion.value % Test,
      "org.mockito"           % "mockito-core"                % "2.10.0" % Test
    )
  )
  .settings(
    bashScriptExtraDefines ++= Seq(
      """addJava "-javaagent:$lib_dir/org.aspectj.aspectjweaver-1.8.10.jar"""",
      """addJava "-javaagent:$lib_dir/io.kamon.sigar-loader-1.6.6-rev002.jar""""
    ))

lazy val root = project
  .in(file("."))
  .settings(
    name := "iam",
    moduleName := "iam",
    homepage := Some(new URL("https://github.com/BlueBrain/nexus-iam")),
    description := "Nexus IAM",
    licenses := Seq(("Apache 2.0", new URL("https://github.com/BlueBrain/nexus-iam/blob/master/LICENSE")))
  )
  .settings(noPublish)
  .aggregate(core, service)

/* Common settings */

lazy val noPublish = Seq(publishLocal := {}, publish := {})

lazy val buildInfoSettings =
  Seq(buildInfoKeys := Seq[BuildInfoKey](version), buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.service.config")

lazy val packagingSettings = packageName in Docker := "iam"

def nexusDep(artifact: String, version: String, conf: Configuration = Compile): ModuleID =
  "ch.epfl.bluebrain.nexus" %% artifact % version % conf

lazy val kamonDeps = Seq(
  "io.kamon"    %% "kamon-core"            % "0.6.7",
  "io.kamon"    %% "kamon-akka-http"       % "0.6.8",
  "io.kamon"    %% "kamon-statsd"          % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-system-metrics"  % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-akka-2.5"        % "0.6.8" % Runtime,
  "io.kamon"    %% "kamon-akka-remote-2.4" % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-autoweave"       % "0.6.5" % Runtime,
  "io.kamon"    % "sigar-loader"           % "1.6.6-rev002" % Runtime,
  "org.aspectj" % "aspectjweaver"          % "1.8.10" % Runtime
)

addCommandAlias("review", ";clean;coverage;scapegoat;test;coverageReport;coverageAggregate")
addCommandAlias("rel", ";release with-defaults skip-tests")
