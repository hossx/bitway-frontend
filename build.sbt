name := """bitway-frontend"""

version := "0.0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

scalariformSettings

resolvers ++= Seq("Nexus Snapshots" at "https://nexus.coinport.com/nexus/content/repositories/snapshots",
                  "Nexus release" at "https://nexus.coinport.com/nexus/content/repositories/releases")

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  filters,
  ws,
  "com.coinport"         %% "bitway-client"       % "0.2.9-SNAPSHOT",
  "net.debasishg"        %% "redisclient"         % "2.12",
  "com.github.tototoshi" %% "play-json4s-native" % "0.2.0",
  "mysql"                % "mysql-connector-java" % "5.0.3",
  "org.scalikejdbc" %% "scalikejdbc"       % "2.1.4",
  "org.scalikejdbc" %% "scalikejdbc-config"  % "2.1.4"
)
