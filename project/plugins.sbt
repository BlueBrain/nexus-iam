resolvers += Resolver.bintrayRepo("bbp", "nexus-releases")

addSbtPlugin("ch.epfl.bluebrain.nexus" % "sbt-nexus"     % "0.14.0")
addSbtPlugin("com.eed3si9n"            % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("pl.project13.scala"      % "sbt-jmh"       % "0.3.7")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.11")
