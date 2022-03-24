
val Finagle = "22.2.0"
val Kamon = "2.5.0+3-c0fcccab"

scalaVersion := "2.13.8"

libraryDependencies += "com.twitter" %% "finagle-mysql" % Finagle
libraryDependencies += "io.kamon" %% "kamon-bundle" % Kamon
libraryDependencies += "io.kamon" %% "kamon-apm-reporter" % Kamon
