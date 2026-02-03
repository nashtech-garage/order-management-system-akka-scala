val AkkaVersion = "2.10.12"
val AkkaHttpVersion = "10.7.3"
val LogbackVersion = "1.4.11"
val ScalaTestVersion = "3.2.17"
val SlickVersion = "3.4.1"
val PostgresVersion = "42.6.0"
val FlywayVersion = "9.22.3"
val MockitoScalaVersion = "1.17.30"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.17",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint"
  ),
  resolvers += "Akka library repository".at(s"https://repo.akka.io/rzfEn62Vj7gzpniEeZE9KJambot-fiba2_CUa6PHuuH-8nM4/secure"),
  coverageEnabled := true,
  coverageMinimumStmtTotal := 80,
  coverageFailOnMinimum := false,
  // Exclude main entry point from coverage
  coverageExcludedPackages := ".*ReportMain.*"
)

// Report service doesn't need database - it calls Order Service API
lazy val commonRef = RootProject(file("../common"))

lazy val `report-service` = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "report-service",
    Compile / mainClass := Some("com.oms.report.ReportMain"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      // Database dependencies
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "org.postgresql" % "postgresql" % PostgresVersion,
      "org.flywaydb" % "flyway-core" % FlywayVersion,
      // Test dependencies
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "org.mockito" %% "mockito-scala" % MockitoScalaVersion % Test,
      "org.mockito" %% "mockito-scala-scalatest" % MockitoScalaVersion % Test
    )
  )
  .dependsOn(commonRef)
