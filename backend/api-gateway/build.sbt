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
  resolvers += "Akka library repository".at(s"https://repo.akka.io/rzfEn62Vj7gzpniEeZE9KJambot-fiba2_CUa6PHuuH-8nM4/secure"),
  coverageExcludedPackages := ".*GatewayMain.*"
)

// API Gateway doesn't need database - it proxies to other services
lazy val commonRef = RootProject(file("../common"))

lazy val `api-gateway` = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "api-gateway",
    libraryDependencies ++= Seq(
      // Akka Streams and HTTP
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      
      // Logging
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      
      // Testing
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
    )
  )
  .dependsOn(commonRef)
