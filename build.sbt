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
val rdfVersion                 = "0.2.28"
val commonsVersion             = "0.10.36"
val serviceVersion             = "0.10.18"
val sourcingVersion            = "0.12.0"
val akkaVersion                = "2.5.17"
val akkaCorsVersion            = "0.3.1"
val akkaHttpVersion            = "10.1.5"
val akkaPersistenceCassVersion = "0.91"
val catsVersion                = "1.4.0"
val circeVersion               = "0.10.0"
val journalVersion             = "3.0.19"
val logbackVersion             = "1.2.3"
val mockitoVersion             = "1.0.1"
val monixVersion               = "3.0.0-RC2"
val pureconfigVersion          = "0.9.2"
val scalaTestVersion           = "3.0.5"
val kryoVersion                = "0.5.2"

// Dependencies modules
lazy val rdfJena             = "ch.epfl.bluebrain.nexus" %% "rdf-jena"                   % rdfVersion
lazy val rdfCirce            = "ch.epfl.bluebrain.nexus" %% "rdf-circe"                  % rdfVersion
lazy val rdfAkka             = "ch.epfl.bluebrain.nexus" %% "rdf-akka"                   % rdfVersion
lazy val rdfNexus            = "ch.epfl.bluebrain.nexus" %% "rdf-nexus"                  % rdfVersion
lazy val serviceIndexing     = "ch.epfl.bluebrain.nexus" %% "service-indexing"           % serviceVersion
lazy val serviceKamon        = "ch.epfl.bluebrain.nexus" %% "service-kamon"              % serviceVersion
lazy val serviceHttp         = "ch.epfl.bluebrain.nexus" %% "service-http"               % serviceVersion
lazy val sourcingAkka        = "ch.epfl.bluebrain.nexus" %% "sourcing-akka"              % sourcingVersion
lazy val commonTest          = "ch.epfl.bluebrain.nexus" %% "commons-test"               % commonsVersion
lazy val akkaCluster         = "com.typesafe.akka"       %% "akka-cluster"               % akkaVersion
lazy val akkaHttp            = "com.typesafe.akka"       %% "akka-http"                  % akkaHttpVersion
lazy val akkaHttpCors        = "ch.megard"               %% "akka-http-cors"             % akkaCorsVersion
lazy val akkaHttpTestKit     = "com.typesafe.akka"       %% "akka-http-testkit"          % akkaHttpVersion
lazy val akkaPersistenceCass = "com.typesafe.akka"       %% "akka-persistence-cassandra" % akkaPersistenceCassVersion
lazy val akkaSlf4j           = "com.typesafe.akka"       %% "akka-slf4j"                 % akkaVersion
lazy val akkaStream          = "com.typesafe.akka"       %% "akka-stream"                % akkaVersion
lazy val catsCore            = "org.typelevel"           %% "cats-core"                  % catsVersion
lazy val circeCore           = "io.circe"                %% "circe-core"                 % circeVersion
lazy val circeJava8          = "io.circe"                %% "circe-java8"                % circeVersion
lazy val journalCore         = "io.verizon.journal"      %% "core"                       % journalVersion
lazy val mockitoScala        = "org.mockito"             %% "mockito-scala"              % mockitoVersion
lazy val monixEval           = "io.monix"                %% "monix-eval"                 % monixVersion
lazy val logbackClassic      = "ch.qos.logback"          % "logback-classic"             % logbackVersion
lazy val pureconfig          = "com.github.pureconfig"   %% "pureconfig"                 % pureconfigVersion
lazy val scalaTest           = "org.scalatest"           %% "scalatest"                  % scalaTestVersion
lazy val kryo                = "com.github.romix.akka"   %% "akka-kryo-serialization"    % kryoVersion

lazy val iam = project
  .in(file("."))
  .settings(testSettings, buildInfoSettings)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin, JmhPlugin)
  .settings(
    name       := "iam",
    moduleName := "iam",
    libraryDependencies ++= Seq(
      rdfAkka,
      rdfCirce,
      rdfJena,
      rdfNexus,
      serviceIndexing,
      sourcingAkka,
      akkaHttp,
      akkaHttpCors,
      akkaPersistenceCass,
      akkaStream,
      akkaSlf4j,
      akkaCluster,
      catsCore,
      circeCore,
      circeJava8,
      journalCore,
      kryo,
      monixEval,
      logbackClassic,
      pureconfig,
      serviceKamon,
      serviceHttp,
      akkaHttpTestKit % Test,
      commonTest      % Test,
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
