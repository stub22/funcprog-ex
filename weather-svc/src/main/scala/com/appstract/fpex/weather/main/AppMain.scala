package com.appstract.fpex.weather.main

import cats.effect.{IO, IOApp}
import org.log4s
import org.log4s.Logger

object AppMain extends IOApp.Simple {
	// Entry point for our http4s + cats-effect application:
	def run:IO[Unit] = {
		val log: Logger = log4s.getLogger
		log.info("AppMain.run BEGIN: Making AppServerBuilder")
		val appServerBuilder = new AppServerBuilder{}
		log.debug("AppMain.run: Invoking .makeAppServerIO to build our runnable server effect")
		val appServerIO: IO[Nothing] = appServerBuilder.makeAppServerIO
		log.info("AppMain.run built our server IO effect. Now returning it to IOApp to be executed and RUN FOREVER")
		log.info(
			"""Within a few seconds you should be able to access Weather data from a web browser, using URLS like:
			| Path argument example
			|http://localhost:8080/check-weather-wpath/40.2222,-97.0997
			|
			|Query param arguments example
			|http://localhost:8080/check-weather-wquery?lat=40.2222&lon=-97.0997
			""".stripMargin)
		appServerIO
	}
}

/***
 * This code started from a skeleton project provided by this giter8 template:
 * https://github.com/http4s/http4s-io.g8
 *
 * Platform compatibility:  So far Stu has tested only on MS Windows 10, in the following launch configurations:
 *
 *   1) sbt 1.7.3 with GraalVM JDK 11 (Version 22.3.0)
 *
 *	Launching our server from sbt works with the following noted platform issues:
	 *   A) JDK 11 is required by Logback 1.4.1.
	 *
	 *   B) We must set withJansi to false in the logback appender config when running on Windows.
	 *   Otherwise we see errors at startup, described below.
	 *   (YMMV with other platforms).
	 *
	 *   C) SBT does not fork by default, so by default will run our code INSIDE the SBT process.
	 *   If we use SBT as an interactive shell, then we will have trouble using our ".run" more
	 *   than once in the same session, because our network port 8080 remains bound when the
	 *   run is cancelled with Ctrl-C.  This issue can be addressed by running in one of these other ways:
	 *   a) Invoking SBT in batch mode, e.g. "sbt run" from bash command line.
	 *   b) Using "bgRun" command from SBT shell.
	 *   c) Using "fork" instruction in our build.sbt.
	 *   https://www.scala-sbt.org/1.x/docs/Forking.html
 *
 *   2) IntelliJ JetBrains IDE with GraalVM JDK 11, as well as Scala and SBT plugins.
 *   Project imports OK from the build.sbt file, and builds in the .idea format.
 *   Can launch "AppMain" and "WeatherRouteSpec" using "Run" context menu.

Note that our logback.xml from the giter8 template initially contained this setting:
<withJansi>true</withJansi>

22:21:28,907 |-INFO in ch.qos.logback.classic.LoggerContext[default] - This is logback-classic version 1.4.1
22:21:28,925 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback-test.xml]
22:21:28,926 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Found resource [logback.xml] at [file:/D:/_dmnt/fpex_clnz/fpex_01/weather-svc/target/scala-2.13/classes/logback.xml]
22:21:28,996 |-INFO in ch.qos.logback.core.model.processor.AppenderModelHandler - Processing appender named [STDOUT]
22:21:28,996 |-INFO in ch.qos.logback.core.model.processor.AppenderModelHandler - About to instantiate appender of type [ch.qos.logback.core.ConsoleAppender]
22:21:28,999 |-INFO in ch.qos.logback.core.model.processor.ImplicitModelHandler - Assuming default type [ch.qos.logback.classic.encoder.PatternLayoutEncoder] for [encoder] property
22:21:28,999 |-INFO in ch.qos.logback.core.ConsoleAppender[STDOUT] - Enabling JANSI AnsiPrintStream for the console.
22:21:28,999 |-WARN in ch.qos.logback.core.ConsoleAppender[STDOUT] - Failed to create AnsiPrintStream. Falling back on the default stream. java.lang.ClassNotFoundException: org.fusesource.jansi.AnsiConsole
	at java.lang.ClassNotFoundException: org.fusesource.jansi.AnsiConsole
	at 	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:581)
	at 	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:178)
	at 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522)
	at 	at ch.qos.logback.core.ConsoleAppender.wrapWithJansi(ConsoleAppender.java:96)
 ...
22:21:28,999 |-INFO in ch.qos.logback.classic.model.processor.RootLoggerModelHandler - Setting level of ROOT logger to INFO
22:21:29,015 |-INFO in ch.qos.logback.core.model.processor.AppenderRefModelHandler - Attaching appender named [STDOUT] to Logger[ROOT]
22:21:29,015 |-INFO in ch.qos.logback.core.model.processor.DefaultProcessor@2b5cb9b2 - End of configuration.
22:21:29,016 |-INFO in ch.qos.logback.classic.joran.JoranConfigurator@35038141 - Registering current configuration as safe fallback point
[io-compute-9] INFO  o.h.e.s.EmberServerBuilderCompanionPlatform - Ember-Server service bound to address: [::]:8080

 */