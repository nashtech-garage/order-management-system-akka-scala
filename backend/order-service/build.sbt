val AkkaVersion = "2.10.12"
val AkkaHttpVersion = "10.7.3"
val SlickVersion = "3.4.1"
val PostgresVersion = "42.6.0"
val FlywayVersion = "9.22.3"
val LogbackVersion = "1.4.11"
val ScalaTestVersion = "3.2.17"
val H2Version = "2.2.224"
val MockitoScalaVersion = "1.17.30"

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

lazy val commonRef = RootProject(file("../common"))

lazy val `order-service` = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "order-service",
    Compile / mainClass := Some("com.oms.order.OrderMain"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "org.postgresql" % "postgresql" % PostgresVersion,
      "org.flywaydb" % "flyway-core" % FlywayVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
      "com.h2database" % "h2" % H2Version % Test,
      "org.mockito" %% "mockito-scala" % MockitoScalaVersion % Test,
      "org.mockito" %% "mockito-scala-scalatest" % MockitoScalaVersion % Test
    )
  )
  .dependsOn(commonRef)
