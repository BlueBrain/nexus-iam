/*
scalafmt: {
  style = defaultWithAlign
  maxColumn = 150
  align.tokens = [
    { code = "=>", owner = "Case" }
    { code = "?", owner = "Case" }
    { code = "extends", owner = "Defn.(Class|Trait|Object)" }
    { code = "//", owner = ".*" }
    { code = "{", owner = "Template" }
    { code = "}", owner = "Template" }
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "%", owner = "Term.ApplyInfix" }
    { code = "%%", owner = "Term.ApplyInfix" }
    { code = "%%%", owner = "Term.ApplyInfix" }
    { code = "->", owner = "Term.ApplyInfix" }
    { code = "?", owner = "Term.ApplyInfix" }
    { code = "<-", owner = "Enumerator.Generator" }
    { code = "?", owner = "Enumerator.Generator" }
    { code = "=", owner = "(Enumerator.Val|Defn.(Va(l|r)|Def|Type))" }
  ]
}
 */

// Dependency versions
val alpakkaVersion             = "1.0.0"
val rdfVersion                 = "0.3.6"
val commonsVersion             = "0.12.0"
val sourcingVersion            = "0.16.1"
val akkaVersion                = "2.5.22"
val akkaCorsVersion            = "0.4.0"
val akkaHttpVersion            = "10.1.8"
val akkaPersistenceCassVersion = "0.95"
val akkaPersistenceMemVersion  = "2.5.15.1"
val catsVersion                = "1.6.0"
val circeVersion               = "0.11.1"
val journalVersion             = "3.0.19"
val logbackVersion             = "1.2.3"
val mockitoVersion             = "1.3.1"
val monixVersion               = "3.0.0-RC2"
val nimbusJoseJwtVersion       = "7.1"
val pureconfigVersion          = "0.10.2"
val scalaTestVersion           = "3.0.7"
val kryoVersion                = "0.5.2"

// Dependencies modules
lazy val rdf                  = "ch.epfl.bluebrain.nexus" %% "rdf"                        % rdfVersion
lazy val sourcingCore         = "ch.epfl.bluebrain.nexus" %% "sourcing-core"              % sourcingVersion
lazy val sourcingProjections  = "ch.epfl.bluebrain.nexus" %% "sourcing-projections"       % sourcingVersion
lazy val commonsCore          = "ch.epfl.bluebrain.nexus" %% "commons-core"               % commonsVersion
lazy val commonsTest          = "ch.epfl.bluebrain.nexus" %% "commons-test"               % commonsVersion
lazy val akkaCluster          = "com.typesafe.akka"       %% "akka-cluster"               % akkaVersion
lazy val akkaClusterSharding  = "com.typesafe.akka"       %% "akka-cluster-sharding"      % akkaVersion
lazy val akkaHttp             = "com.typesafe.akka"       %% "akka-http"                  % akkaHttpVersion
lazy val akkaHttpCors         = "ch.megard"               %% "akka-http-cors"             % akkaCorsVersion
lazy val akkaHttpTestKit      = "com.typesafe.akka"       %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersistence      = "com.typesafe.akka"       %% "akka-persistence"           % akkaVersion
lazy val akkaPersistenceCass  = "com.typesafe.akka"       %% "akka-persistence-cassandra" % akkaPersistenceCassVersion
lazy val akkaPersistenceMem   = "com.github.dnvriend"     %% "akka-persistence-inmemory"  % akkaPersistenceMemVersion
lazy val akkaPersistenceQuery = "com.typesafe.akka"       %% "akka-persistence-query"     % akkaVersion
lazy val akkaTestKit          = "com.typesafe.akka"       %% "akka-testkit"               % akkaVersion
lazy val akkaSlf4j            = "com.typesafe.akka"       %% "akka-slf4j"                 % akkaVersion
lazy val akkaStream           = "com.typesafe.akka"       %% "akka-stream"                % akkaVersion
lazy val akkaStreamTestKit    = "com.typesafe.akka"       %% "akka-stream-testkit"        % akkaVersion
lazy val alpakkaSSE           = "com.lightbend.akka"      %% "akka-stream-alpakka-sse"    % alpakkaVersion
lazy val catsCore             = "org.typelevel"           %% "cats-core"                  % catsVersion
lazy val circeCore            = "io.circe"                %% "circe-core"                 % circeVersion
lazy val journalCore          = "io.verizon.journal"      %% "core"                       % journalVersion
lazy val mockitoScala         = "org.mockito"             %% "mockito-scala"              % mockitoVersion
lazy val monixEval            = "io.monix"                %% "monix-eval"                 % monixVersion
lazy val nimbusJoseJwt        = "com.nimbusds"            % "nimbus-jose-jwt"             % nimbusJoseJwtVersion
lazy val logbackClassic       = "ch.qos.logback"          % "logback-classic"             % logbackVersion
lazy val pureconfig           = "com.github.pureconfig"   %% "pureconfig"                 % pureconfigVersion
lazy val scalaTest            = "org.scalatest"           %% "scalatest"                  % scalaTestVersion
lazy val kryo                 = "com.github.romix.akka"   %% "akka-kryo-serialization"    % kryoVersion

lazy val iam = project
  .in(file("."))
  .settings(testSettings, buildInfoSettings)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin, JmhPlugin)
  .aggregate(client)
  .settings(
    name       := "iam",
    moduleName := "iam",
    libraryDependencies ++= Seq(
      commonsCore,
      rdf,
      sourcingCore,
      sourcingProjections,
      akkaHttp,
      akkaHttpCors,
      akkaPersistence,
      akkaPersistenceCass,
      akkaPersistenceQuery,
      akkaStream,
      akkaSlf4j,
      akkaCluster,
      akkaClusterSharding,
      catsCore,
      circeCore,
      journalCore,
      kryo,
      monixEval,
      nimbusJoseJwt,
      logbackClassic,
      pureconfig,
      akkaTestKit        % Test,
      akkaHttpTestKit    % Test,
      akkaStreamTestKit  % Test,
      akkaPersistenceMem % Test,
      commonsTest        % Test,
      mockitoScala       % Test,
      scalaTest          % Test,
    ),
    resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"
  )

lazy val client = project
  .in(file("client"))
  .settings(
    name                  := "iam-client",
    moduleName            := "iam-client",
    coverageFailOnMinimum := false,
    Test / testOptions    += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaStream,
      alpakkaSSE,
      catsCore,
      circeCore,
      commonsCore,
      logbackClassic,
      rdf,
      akkaTestKit     % Test,
      akkaHttpTestKit % Test,
      commonsTest     % Test,
      mockitoScala    % Test,
      scalaTest       % Test,
    )
  )

lazy val testSettings = Seq(
  Test / testOptions         += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
  Test / parallelExecution   := false,
  coverageFailOnMinimum      := true,
  sourceDirectory in Jmh     := (sourceDirectory in Test).value,
  classDirectory in Jmh      := (classDirectory in Test).value,
  dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
  // rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
  compile in Jmh := (compile in Jmh).dependsOn(compile in Test).value,
  run in Jmh     := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated,
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](version),
  buildInfoPackage := "ch.epfl.bluebrain.nexus.iam.config"
)

inThisBuild(
  List(
    homepage := Some(url("https://github.com/BlueBrain/nexus-iam")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo  := Some(ScmInfo(url("https://github.com/BlueBrain/nexus-iam"), "scm:git:git@github.com:BlueBrain/nexus-iam.git")),
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

addCommandAlias("review", ";clean;scalafmtSbt;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")
