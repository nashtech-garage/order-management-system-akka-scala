val AkkaVersion = "2.10.12"
val AkkaHttpVersion = "10.7.3"
val SlickVersion = "3.4.1"
val PostgresVersion = "42.6.0"
val LogbackVersion = "1.4.11"
val ScalaTestVersion = "3.2.17"
val BCryptVersion = "0.4"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.17",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint"
  ),
  resolvers += "Akka library repository".at(s"https://repo.akka.io/fOnF6aq4lmGHCfvMkKEDUvyyaRnfhkJFBqIcPN4r9iux7LK-/secure")
)

lazy val commonRef = RootProject(file("../common"))

lazy val `user-service` = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "user-service",
    Compile / mainClass := Some("com.oms.user.UserMain"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-pki" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "org.postgresql" % "postgresql" % PostgresVersion,
      "org.mindrot" % "jbcrypt" % BCryptVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test
    )
  )
  .dependsOn(commonRef)
