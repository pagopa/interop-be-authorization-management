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
      "javax.annotation"          % "javax.annotation-api" % "1.3.2"  % "compile",
      //
      akka.actorTyped             % Compile,
      akka.clusterBootstrap       % Compile,
      akka.clusterHttp            % Compile,
      akka.clusterSharding        % Compile,
      akka.clusterTools           % Compile,
      akka.clusterTyped           % Compile,
      akka.discovery              % Compile,
      akka.discoveryKubernetesApi % Compile,
      akka.http                   % Compile,
      akka.httpJson               % Compile,
      akka.management             % Compile,
      akka.managementLogLevels    % Compile,
      akka.persistence            % Compile,
      akka.persistenceJdbc        % Compile,
      akka.persistenceQuery       % Compile,
      akka.projection             % Compile,
      akka.projectionSlick        % Compile,
      akka.s3Journal              % Compile,
      akka.s3Snapshot             % Compile,
      akka.slf4j                  % Compile,
      akka.slick                  % Compile,
      akka.slickHikari            % Compile,
      akka.stream                 % Compile,
      bouncycastle.kix            % Compile,
      bouncycastle.provider       % Compile,
      cats.core                   % Compile,
      logback.classic             % Compile,
      mustache.mustache           % Compile,
      nimbus.joseJwt              % Compile,
      pagopa.commonsUtils         % Compile,
      pagopa.commonsJWT           % Compile,
      postgres.jdbc               % Compile,
      scalaprotobuf.core          % "protobuf,compile",
      akka.testkit                % Test,
      akka.httpTestkit            % Test,
      scalatest.core              % Test,
      scalamock.core              % Test,
      "org.scalameta"            %% "munit-scalacheck"     % "0.7.29" % Test,
      "com.softwaremill.diffx"   %% "diffx-munit"          % "0.7.0"  % Test
    )

    val models: Seq[ModuleID] = Seq(spray.core, cats.core, pagopa.commonsUtils).map(_ % Compile)

    lazy val client: Seq[ModuleID] =
      Seq(akka.stream, akka.http, akka.httpJson4s, akka.slf4j, json4s.jackson, json4s.ext, pagopa.commonsUtils).map(
        _ % Compile
      )
  }
}
