/* Project definitions */

val commonsVersion  = "0.10.8"
val serviceVersion  = "0.10.6"
val sourcingVersion = "0.10.3"

val akkaVersion            = "2.5.11"
val akkaHttpVersion        = "10.0.13"
val akkaPersCassVersion    = "0.83"
val akkaStreamKafkaVersion = "0.18"
val akkaPersMemVersion     = "2.5.1.1"
val akkaHttpCorsVersion    = "0.3.0"
val akkaHttpCirceVersion   = "1.20.0"
val asmVersion             = "6.1.1"
val shapelessVersion       = "2.3.3"
val circeVersion           = "0.9.2"
val journalVersion         = "3.0.19"
val scalaTestVersion       = "3.0.5"
val pureconfigVersion      = "0.9.1"
val mockitoVersion         = "2.17.0"

val aspectJVersion     = "1.8.13"
val sigarLoaderVersion = "1.6.6"
val jwtVersion         = "0.16.0"

lazy val serviceHttp          = "ch.epfl.bluebrain.nexus" %% "service-http"          % serviceVersion
lazy val serviceKamon         = "ch.epfl.bluebrain.nexus" %% "service-kamon"         % serviceVersion
lazy val serviceIndexing      = "ch.epfl.bluebrain.nexus" %% "service-indexing"      % serviceVersion
lazy val serviceSerialization = "ch.epfl.bluebrain.nexus" %% "service-serialization" % serviceVersion

lazy val commonsTest   = "ch.epfl.bluebrain.nexus" %% "commons-test"         % commonsVersion
lazy val commonsTypes  = "ch.epfl.bluebrain.nexus" %% "commons-types"        % commonsVersion
lazy val elasticClient = "ch.epfl.bluebrain.nexus" %% "elastic-client"       % commonsVersion
lazy val elasticEmbed  = "ch.epfl.bluebrain.nexus" %% "elastic-server-embed" % commonsVersion
lazy val iamTypes      = "ch.epfl.bluebrain.nexus" %% "iam"                  % commonsVersion

lazy val sourcingMem  = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"  % sourcingVersion
lazy val sourcingCore = "ch.epfl.bluebrain.nexus" %% "sourcing-core" % sourcingVersion
lazy val sourcingAkka = "ch.epfl.bluebrain.nexus" %% "sourcing-akka" % sourcingVersion

lazy val akkaTestkit         = "com.typesafe.akka"     %% "akka-testkit"               % akkaVersion
lazy val akkaHttp            = "com.typesafe.akka"     %% "akka-http"                  % akkaHttpVersion
lazy val akkaCluster         = "com.typesafe.akka"     %% "akka-cluster"               % akkaVersion
lazy val akkaClusterSharding = "com.typesafe.akka"     %% "akka-cluster-sharding"      % akkaVersion
lazy val akkaDData           = "com.typesafe.akka"     %% "akka-distributed-data"      % akkaVersion
lazy val akkaHttpTestkit     = "com.typesafe.akka"     %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersCass        = "com.typesafe.akka"     %% "akka-persistence-cassandra" % akkaPersCassVersion
lazy val akkaSlf4j           = "com.typesafe.akka"     %% "akka-slf4j"                 % akkaVersion
lazy val akkaStream          = "com.typesafe.akka"     %% "akka-stream"                % akkaVersion
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
lazy val jwtCirce            = "com.pauldijou"         %% "jwt-circe"                  % jwtVersion
lazy val asm                 = "org.ow2.asm"           % "asm"                         % asmVersion

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)
  .settings(
    name                         := "iam-docs",
    moduleName                   := "iam-docs",
    paradoxTheme                 := Some(builtinParadoxTheme("generic")),
    target in (Compile, paradox) := (resourceManaged in Compile).value / "docs",
    resourceGenerators in Compile += {
      (paradox in Compile).map { parent =>
        (parent ** "*").get
      }.taskValue
    }
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(
    name       := "iam-core",
    moduleName := "iam-core",
    libraryDependencies ++= Seq(
      commonsTypes,
      iamTypes,
      serviceKamon,
      sourcingCore,
      akkaHttp,
      akkaPersCass,
      akkaSlf4j,
      akkaStreamKafka,
      journal,
      shapeless,
      circeCore,
      circeGenericExtras,
      akkaTestkit % Test,
      circeParser % Test,
      mockitoCore % Test,
      scalaTest   % Test,
      sourcingMem % Test
    )
  )

lazy val oidcCore = project
  .in(file("modules/oidc/core"))
  .dependsOn(core)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name             := "iam-oidc-core",
    moduleName       := "iam-oidc-core",
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.oidc.config",
    libraryDependencies ++=
      Seq(
        commonsTypes,
        akkaHttp,
        akkaCluster,
        akkaClusterSharding,
        akkaDData,
        akkaHttpCirce,
        iamTypes,
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
    name                  := "iam-bbp",
    moduleName            := "iam-bbp",
    packageName in Docker := "iam-bbp",
    description           := "Nexus IAM BBP Integration Service",
    libraryDependencies ++= Seq(akkaHttp,
                                circeCore,
                                circeParser,
                                circeGenericExtras,
                                serviceHttp,
                                serviceKamon,
                                journal,
                                scalaTest % Test)
  )

lazy val oidcHbp = project
  .in(file("modules/oidc/hbp"))
  .dependsOn(oidcCore)
  .enablePlugins(ServicePackagingPlugin)
  .settings(
    name                  := "iam-hbp",
    moduleName            := "iam-hbp",
    packageName in Docker := "iam-hbp",
    description           := "Nexus IAM HBP Integration Service",
    libraryDependencies ++= Seq(akkaHttp,
                                circeCore,
                                circeParser,
                                circeGenericExtras,
                                serviceHttp,
                                serviceKamon,
                                journal,
                                scalaTest % Test)
  )

lazy val elastic = project
  .in(file("modules/elastic"))
  .dependsOn(core)
  .settings(
    name       := "iam-elastic",
    moduleName := "iam-elastic",
    libraryDependencies ++= Seq(
      elasticClient,
      commonsTest,
      asm          % Test,
      akkaTestkit  % Test,
      elasticEmbed % Test,
      scalaTest    % Test
    )
  )
  .settings(parallelExecution in Test := false)

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(core, docs, elastic)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .settings(
    name                  := "iam-service",
    moduleName            := "iam-service",
    packageName in Docker := "iam",
    description           := "Nexus IAM Service",
    buildInfoKeys         := Seq[BuildInfoKey](version),
    buildInfoPackage      := "ch.epfl.bluebrain.nexus.iam.service.config",
    libraryDependencies ++= Seq(
      serviceHttp,
      serviceKamon,
      serviceIndexing,
      serviceSerialization,
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
      jwtCirce,
      akkaTestkit     % Test,
      akkaHttpTestkit % Test,
      scalaTest       % Test,
      akkaPersMem     % Test,
      sourcingMem     % Test,
      mockitoCore     % Test,
      commonsTest     % Test
    )
  )

lazy val root = project
  .in(file("."))
  .settings(noPublish)
  .settings(name := "iam", moduleName := "iam", description := "Nexus Identity & Access Management")
  .aggregate(docs, core, elastic, service, oidcCore, oidcBbp, oidcHbp)

/* Common settings */

lazy val noPublish = Seq(
  publishLocal    := {},
  publish         := {},
  publishArtifact := false
)

inThisBuild(
  Seq(
    homepage := Some(url("https://github.com/BlueBrain/nexus-iam")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(
      ScmInfo(url("https://github.com/BlueBrain/nexus-iam"), "scm:git:git@github.com:BlueBrain/nexus-iam.git")),
    developers := List(
      Developer("bogdanromanx", "Bogdan Roman", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("hygt", "Henry Genet", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("umbreak", "Didac Montero Mendez", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/"))
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false
  )
)

addCommandAlias("review", ";clean;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")
