import Versions._
import PagopaVersions._
import sbt._

object Dependencies {

  private[this] object akka {
    lazy val namespace              = "com.typesafe.akka"
    lazy val actorTyped             = namespace            %% "akka-actor-typed"             % akkaVersion
    lazy val clusterTyped           = namespace            %% "akka-cluster-typed"           % akkaVersion
    lazy val clusterSharding        = namespace            %% "akka-cluster-sharding-typed"  % akkaVersion
    lazy val discovery              = namespace            %% "akka-discovery"               % akkaVersion
    lazy val persistence            = namespace            %% "akka-persistence-typed"       % akkaVersion
    lazy val persistenceQuery       = namespace            %% "akka-persistence-query"       % akkaVersion
    lazy val projection             = "com.lightbend.akka" %% "akka-projection-eventsourced" % projectionVersion
    lazy val projectionSlick        = "com.lightbend.akka" %% "akka-projection-slick"        % slickProjectionVersion
    lazy val clusterTools           = namespace            %% "akka-cluster-tools"           % akkaVersion
    lazy val persistenceJdbc        = "com.lightbend.akka" %% "akka-persistence-jdbc"        % jdbcPersistenceVersion
    lazy val slick                  = "com.typesafe.slick" %% "slick"                        % slickVersion
    lazy val slickHikari            = "com.typesafe.slick" %% "slick-hikaricp"               % slickVersion
    lazy val s3Journal              = "com.github.j5ik2o"  %% "akka-persistence-s3-journal"  % s3Persistence
    lazy val s3Snapshot             = "com.github.j5ik2o"  %% "akka-persistence-s3-snapshot" % s3Persistence
    lazy val stream                 = namespace            %% "akka-stream-typed"            % akkaVersion
    lazy val http                   = namespace            %% "akka-http"                    % akkaHttpVersion
    lazy val httpJson               = namespace            %% "akka-http-spray-json"         % akkaHttpVersion
    lazy val httpJson4s             = "de.heikoseeberger"  %% "akka-http-json4s"             % httpJson4sVersion
    lazy val discoveryKubernetesApi =
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion
    lazy val clusterBootstrap =
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion
    lazy val clusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion
    lazy val slf4j       = namespace                       %% "akka-slf4j"                   % akkaVersion
    lazy val testkit     = namespace                       %% "akka-actor-testkit-typed"     % akkaVersion
    lazy val httpTestkit = namespace                       %% "akka-http-testkit"            % akkaHttpVersion
    lazy val management  = "com.lightbend.akka.management" %% "akka-management"              % akkaManagementVersion
    lazy val managementLogLevels =
      "com.lightbend.akka.management" %% "akka-management-loglevels-logback" % akkaManagementVersion
  }

  private[this] object cats {
    lazy val namespace = "org.typelevel"
    lazy val core      = namespace %% "cats-core" % catsVersion
  }

  lazy val Protobuf = "protobuf"

  private[this] object scalaprotobuf {
    lazy val namespace = "com.thesamet.scalapb"
    lazy val core      = namespace %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion
  }

  private[this] object json4s {
    lazy val namespace = "org.json4s"
    lazy val jackson   = namespace %% "json4s-jackson" % json4sVersion
    lazy val ext       = namespace %% "json4s-ext"     % json4sVersion
  }

  private[this] object jackson {
    lazy val namespace   = "com.fasterxml.jackson.core"
    lazy val core        = namespace % "jackson-core"        % jacksonVersion
    lazy val annotations = namespace % "jackson-annotations" % jacksonVersion
    lazy val databind    = namespace % "jackson-databind"    % jacksonVersion
  }

  private[this] object logback {
    lazy val namespace = "ch.qos.logback"
    lazy val classic   = namespace % "logback-classic" % logbackVersion
  }

  private[this] object mustache {
    lazy val mustache = "com.github.spullara.mustache.java" % "compiler" % mustacheVersion
  }

  private[this] object pagopa {
    lazy val namespace = "it.pagopa"

    lazy val commonsUtils = namespace %% "interop-commons-utils" % commonsVersion
    lazy val commonsJWT   = namespace %% "interop-commons-jwt"   % commonsVersion
    lazy val commonsCqrs  = namespace %% "interop-commons-cqrs"  % commonsVersion
  }

  private[this] object postgres {
    lazy val namespace = "org.postgresql"
    lazy val jdbc      = namespace % "postgresql" % postgresVersion
  }

  private[this] object scalatest {
    lazy val namespace = "org.scalatest"
    lazy val core      = namespace %% "scalatest" % scalatestVersion
  }

  private[this] object scalamock {
    lazy val namespace = "org.scalamock"
    lazy val core      = namespace %% "scalamock" % scalaMockVersion
  }

  private[this] object spray {
    lazy val core = "io.spray" %% "spray-json" % sprayVersion
  }

  private[this] object nimbus {
    lazy val namespace = "com.nimbusds"
    lazy val joseJwt   = namespace % "nimbus-jose-jwt" % nimbusVersion
  }

  private[this] object bouncycastle {
    lazy val namespace = "org.bouncycastle"
    lazy val provider  = namespace % "bcprov-jdk15on" % bouncycastleVersion
    lazy val kix       = namespace % "bcpkix-jdk15on" % bouncycastleVersion
  }

  object Jars {
    lazy val overrides: Seq[ModuleID] =
      Seq(jackson.annotations % Compile, jackson.core % Compile, jackson.databind % Compile)
    lazy val `server`: Seq[ModuleID]  = Seq(
      // For making Java 12 happy
      "javax.annotation"          % "javax.annotation-api"            % "1.3.2"                    % "compile",
      //
      akka.actorTyped             % "compile,it",//Compile,
      akka.clusterBootstrap       % "compile,it",//Compile,
      akka.clusterHttp            % "compile,it",//Compile,
      akka.clusterSharding        % "compile,it",//Compile,
      akka.clusterTools           % "compile,it",//Compile,
      akka.clusterTyped           % "compile,it",//Compile,
      akka.discovery              % "compile,it",//Compile,
      akka.discoveryKubernetesApi % "compile,it",//Compile,
      akka.http                   % "compile,it",//Compile,
      akka.httpJson               % "compile,it",//Compile,
      akka.management             % "compile,it",//Compile,
      akka.managementLogLevels    % "compile,it",//Compile,
      akka.persistence            % "compile,it",//Compile,
      akka.persistenceJdbc        % "compile,it",//Compile,
      akka.persistenceQuery       % "compile,it",//Compile,
      akka.projection             % "compile,it",//Compile,
      akka.projectionSlick        % "compile,it",//Compile,
      akka.s3Journal              % "compile,it",//Compile,
      akka.s3Snapshot             % "compile,it",//Compile,
      akka.slf4j                  % "compile,it",//Compile,
      akka.slick                  % "compile,it",//Compile,
      akka.slickHikari            % "compile,it",//Compile,
      akka.stream                 % "compile,it",//Compile,
      bouncycastle.kix            % Compile,
      bouncycastle.provider       % Compile,
      cats.core                   % Compile,
      logback.classic             % Compile,
      mustache.mustache           % Compile,
      nimbus.joseJwt              % Compile,
      pagopa.commonsUtils         % "compile,it",
      pagopa.commonsJWT           % Compile,
      pagopa.commonsCqrs          % "compile,it",
      postgres.jdbc               % "compile,it",
      scalaprotobuf.core          % "protobuf,compile",
      akka.testkit                % "test,it",
      akka.httpTestkit            % "test,it",
      scalatest.core              % "test,it",
      scalamock.core              % "test,it",
      "org.scalameta"            %% "munit-scalacheck"                % "0.7.29"                   % "test,it",
      "com.softwaremill.diffx"   %% "diffx-munit"                     % "0.7.0"                    % Test,
      "com.dimafeng"             %% "testcontainers-scala-scalatest"  % testcontainersScalaVersion % IntegrationTest,
      "com.dimafeng"             %% "testcontainers-scala-postgresql" % testcontainersScalaVersion % IntegrationTest,
      "com.dimafeng"             %% "testcontainers-scala-mongodb"    % testcontainersScalaVersion % IntegrationTest
    )

    val models: Seq[ModuleID] = Seq(spray.core, cats.core, pagopa.commonsUtils).map(_ % Compile)

    lazy val client: Seq[ModuleID] =
      Seq(akka.stream, akka.http, akka.httpJson4s, akka.slf4j, json4s.jackson, json4s.ext, pagopa.commonsUtils).map(
        _ % Compile
      )
  }
}
