val Http4sVersion = "0.23.16"
val CirceVersion = "0.14.3"
val MunitVersion = "0.7.29"
val LogbackVersion = "1.4.1"    // Note: Logback 1.4.1 requires JDK 11+.
val MunitCatsEffectVersion = "1.0.6"

val MUnitFramework = new TestFramework("munit.Framework")

// We get a lot of compiler flags from the tpolecat plugin, configured in project/plugins.sbt.
// Here is one way to disable the -Xfatal-warnings flag:
Compile / scalacOptions --= Seq("-Xfatal-warnings")

Compile / run / fork := true
Test / parallelExecution := false
Test / logBuffered := false

Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=Integration")

lazy val root = (project in file("."))
	.settings(
		organization := "com.appstract",
		name := "weather-svc",
		version := "0.0.2-SNAPSHOT",
		scalaVersion := "2.13.9",
		libraryDependencies ++= Seq(
			"org.http4s"      %% "http4s-ember-server" % Http4sVersion,
			"org.http4s"      %% "http4s-ember-client" % Http4sVersion,
			"org.http4s"      %% "http4s-circe"        % Http4sVersion,
			"org.http4s"      %% "http4s-dsl"          % Http4sVersion,
			"io.circe"        %% "circe-generic"       % CirceVersion,
			"org.scalameta"   %% "munit"               % MunitVersion           % Test,
			"org.typelevel"   %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test,
			"ch.qos.logback"  %  "logback-classic"     % LogbackVersion,
		),
		addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full),
		addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
		testFrameworks += new TestFramework("munit.Framework")
	)
