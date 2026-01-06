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

// API Gateway doesn't need database - it proxies to other services
lazy val commonRef = RootProject(file("../common"))

lazy val `api-gateway` = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "api-gateway",
  )
  .dependsOn(commonRef)
