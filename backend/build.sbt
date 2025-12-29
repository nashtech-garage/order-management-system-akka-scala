import sbt._
import Keys._

ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.oms"

lazy val root = (project in file("."))
  .aggregate(
    common,
    LocalProject("user-service"),
    LocalProject("customer-service"),
    LocalProject("product-service"),
    LocalProject("order-service"),
    LocalProject("payment-service"),
    LocalProject("report-service"),
    LocalProject("api-gateway")
  )
  .settings(
    name := "order-management-system",
    publish / skip := true
  )

lazy val common = (project in file("common"))
