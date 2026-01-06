val AkkaVersion = "2.10.12"
val AkkaHttpVersion = "10.7.3"
val LogbackVersion = "1.4.11"
val ScalaTestVersion = "3.2.17"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.17",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint"
  ),
  resolvers += "Akka library repository".at(s"https://repo.akka.io/rzfEn62Vj7gzpniEeZE9KJambot-fiba2_CUa6PHuuH-8nM4/secure")
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
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    )
  )
  .dependsOn(commonRef)
