lazy val scalatraVersion = "2.3.1"
lazy val root = (project in file(".")).settings(
  organization := "ldn",
  name := "search",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
  resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven",
  libraryDependencies ++= Seq(
    "org.scalatra"        %% "scalatra"          % scalatraVersion,
  //"org.scalatra"        %% "scalatra-scalate"  % scalatraVersion,
  //"org.scalatra"        %% "scalatra-specs2"   % scalatraVersion    % "test",
    "com.typesafe.akka"   %% "akka-actor"        % "2.3.13",
    "com.etaty.rediscala" %% "rediscala"         % "1.4.0",
    "ch.qos.logback"    %  "logback-classic"   % "1.1.3"            % "runtime",
    "org.eclipse.jetty" %  "jetty-webapp"      % "9.2.10.v20150310" % "container",
    "javax.servlet"     %  "javax.servlet-api" % "3.1.0"            % "provided"
  )
).settings(jetty(): _*)
