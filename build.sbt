/* Project definitions */

val commonsVersion = "0.5.3"

val akkaVersion            = "2.5.4"
val akkaHttpVersion        = "10.0.10"
val akkaPersCassVersion    = "0.55"
val akkaStreamKafkaVersion = "0.17"
val akkaPersMemVersion     = "2.5.1.1"
val akkaHttpCorsVersion    = "0.2.2"
val akkaHttpCirceVersion   = "1.18.0"
val shapelessVersion       = "2.3.2"
val circeVersion           = "0.8.0"
val journalVersion         = "3.0.18"
val scalaTestVersion       = "3.0.4"
val pureconfigVersion      = "0.8.0"
val mockitoVersion         = "2.10.0"

val aspectJVersion     = "1.8.11"
val sigarLoaderVersion = "1.6.6-rev002"

lazy val commonsTypes   = "ch.epfl.bluebrain.nexus" %% "commons-types"   % commonsVersion
lazy val commonsHttp    = "ch.epfl.bluebrain.nexus" %% "commons-http"    % commonsVersion
lazy val commonsService = "ch.epfl.bluebrain.nexus" %% "commons-service" % commonsVersion
lazy val sourcingCore   = "ch.epfl.bluebrain.nexus" %% "sourcing-core"   % commonsVersion
lazy val sourcingMem    = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"    % commonsVersion
lazy val sourcingAkka   = "ch.epfl.bluebrain.nexus" %% "sourcing-akka"   % commonsVersion
lazy val iamTypes       = "ch.epfl.bluebrain.nexus" %% "iam"             % commonsVersion

lazy val akkaTestkit         = "com.typesafe.akka"     %% "akka-testkit"               % akkaVersion
lazy val akkaHttp            = "com.typesafe.akka"     %% "akka-http"                  % akkaHttpVersion
lazy val akkaCluster         = "com.typesafe.akka"     %% "akka-cluster"               % akkaVersion
lazy val akkaClusterSharding = "com.typesafe.akka"     %% "akka-cluster-sharding"      % akkaVersion
lazy val akkaDData           = "com.typesafe.akka"     %% "akka-distributed-data"      % akkaVersion
lazy val akkaHttpTestkit     = "com.typesafe.akka"     %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersCass        = "com.typesafe.akka"     %% "akka-persistence-cassandra" % akkaPersCassVersion
lazy val akkaStreamKafka     = "com.typesafe.akka"     %% "akka-stream-kafka"          % akkaStreamKafkaVersion
lazy val akkaPersMem         = "com.github.dnvriend"   %% "akka-persistence-inmemory"  % akkaPersMemVersion
lazy val akkaHttpCirce       = "de.heikoseeberger"     %% "akka-http-circe"            % akkaHttpCirceVersion
lazy val akkaHttpCors        = "ch.megard"             %% "akka-http-cors"             % akkaHttpCorsVersion
lazy val shapeless           = "com.chuusai"           %% "shapeless"                  % shapelessVersion
lazy val circeCore           = "io.circe"              %% "circe-core"                 % circeVersion
lazy val circeParser         = "io.circe"              %% "circe-parser"               % circeVersion
lazy val circeJava8          = "io.circe"              %% "circe-java8"                % circeVersion
lazy val circeGenericExtras  = "io.circe"              %% "circe-generic-extras"       % circeVersion
lazy val journal             = "io.verizon.journal"    %% "core"                       % journalVersion
lazy val pureconfig          = "com.github.pureconfig" %% "pureconfig"                 % pureconfigVersion
lazy val pureconfigAkka      = "com.github.pureconfig" %% "pureconfig-akka"            % pureconfigVersion
lazy val scalaTest           = "org.scalatest"         %% "scalatest"                  % scalaTestVersion
lazy val mockitoCore         = "org.mockito"           % "mockito-core"                % mockitoVersion

lazy val kamonDeps = Seq(
  "io.kamon"    %% "kamon-core"            % "0.6.7",
  "io.kamon"    %% "kamon-akka-http"       % "0.6.8",
  "io.kamon"    %% "kamon-statsd"          % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-system-metrics"  % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-akka-2.5"        % "0.6.8" % Runtime,
  "io.kamon"    %% "kamon-akka-remote-2.4" % "0.6.7" % Runtime,
  "io.kamon"    %% "kamon-autoweave"       % "0.6.5" % Runtime,
  "io.kamon"    % "sigar-loader"           % sigarLoaderVersion % Runtime,
  "org.aspectj" % "aspectjweaver"          % aspectJVersion % Runtime
)

lazy val core = project
  .in(file("modules/core"))
  .settings(
    common,
    name := "iam-core",
    moduleName := "iam-core",
    libraryDependencies ++= Seq(
      sourcingCore,
      commonsService,
      commonsHttp,
      iamTypes,
      akkaPersCass,
      akkaStreamKafka,
      journal,
      akkaHttp,
      shapeless,
      circeCore,
      circeGenericExtras,
      sourcingMem % Test,
      circeParser % Test,
      scalaTest   % Test,
      mockitoCore % Test,
      akkaTestkit % Test
    )
  )

lazy val oidcCore = project
  .in(file("modules/oidc/core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    common,
    name := "iam-oidc-core",
    moduleName := "iam-oidc-core",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.oidc.config",
    libraryDependencies ++= kamonDeps ++
      Seq(
        commonsHttp,
        akkaHttp,
        akkaCluster,
        akkaClusterSharding,
        akkaDData,
        akkaHttpCirce,
        pureconfig,
        pureconfigAkka,
        circeCore,
        circeParser,
        circeGenericExtras,
        journal,
        akkaHttpTestkit % Test,
        scalaTest       % Test,
        mockitoCore     % Test
      )
  )

lazy val oidcBbp = project
  .in(file("modules/oidc/bbp"))
  .dependsOn(oidcCore)
  .enablePlugins(ServicePackagingPlugin)
  .settings(
    common,
    monitoringSettings,
    name := "iam-bbp",
    moduleName := "iam-bbp",
    packageName in Docker := "iam-bbp",
    description := "Nexus IAM BBP Integration Service",
    libraryDependencies ++= Seq(akkaHttp, circeCore, circeParser, circeGenericExtras, journal, scalaTest % Test)
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(core)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .settings(
    common,
    monitoringSettings,
    name := "iam-service",
    moduleName := "iam-service",
    packageName in Docker := "iam",
    description := "Nexus IAM Service",
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.service.config",
    libraryDependencies ++= Seq(
      commonsService,
      sourcingAkka,
      akkaHttp,
      akkaHttpCors,
      akkaHttpCirce,
      pureconfig,
      pureconfigAkka,
      akkaPersCass,
      shapeless,
      circeParser,
      circeJava8,
      circeGenericExtras,
      akkaTestkit     % Test,
      akkaHttpTestkit % Test,
      scalaTest       % Test,
      akkaPersMem     % Test,
      mockitoCore     % Test
    )
  )

lazy val root = project
  .in(file("."))
  .settings(common, noPublish, name := "iam", moduleName := "iam", description := "Nexus IAM")
  .aggregate(core, service, oidcCore, oidcBbp)

/* Common settings */

lazy val noPublish = Seq(publishLocal := {}, publish := {})

lazy val common = Seq(
  homepage := Some(new URL("https://github.com/BlueBrain/nexus-iam")),
  licenses := Seq(("Apache 2.0", new URL("https://github.com/BlueBrain/nexus-iam/blob/master/LICENSE")))
)

lazy val monitoringSettings = Seq(
  libraryDependencies ++= kamonDeps,
  bashScriptExtraDefines ++= Seq(
    s"""addJava "-javaagent:$$lib_dir/org.aspectj.aspectjweaver-$aspectJVersion.jar"""",
    s"""addJava "-javaagent:$$lib_dir/io.kamon.sigar-loader-$sigarLoaderVersion.jar""""
  )
)

addCommandAlias("review", ";clean;coverage;scapegoat;test;coverageReport;coverageAggregate")
addCommandAlias("rel", ";release with-defaults skip-tests")
