package com.appstract.fpex.weather

import cats.effect.{IO, IOApp}
import org.log4s
import org.log4s.Logger

object AppMain extends IOApp.Simple {
  def run:IO[Unit] = {
	  val logger: Logger = log4s.getLogger
	  logger.info("AppMain.run BEGIN:  making a WeatherServer")
	  val ws = new WeatherServer{}
	  logger.info("AppMain.run: invoking ws.run to build the server effect")
	  val wsr: IO[Nothing] = ws.run
	  logger.info("AppMain.run built server IO effect: " + wsr)
	  wsr
  }
}

/***
 * Work started from a skeleton project copied from this giter8 template:
 * https://github.com/http4s/http4s-io.g8
 *
 * Test results on MS Windows 10:
 *   1) sbt 1.7.3 and Oracle JDK 8
 *   We can build and launch the app with this combo, but it fails during startup with
 *   java.lang.UnsupportedClassVersionError: ch/qos/logback/classic/spi/LogbackServiceProvider
 *   indicating that our Logback 1.4.1 dependency requires at least JDK 11.
 *
 *   2) sbt 1.7.3 with GraalVM JDK 11 (Version 22.3.0)
 *   a) Builds and runs with same AnsiConsole issue as the IntelliJ setup #3 below.
 *   Setting withJansi to false clears this issue.
 *
 *   b) Note that SBT does not fork by default, so our code is run inside the SBT process.
 *   If we use SBT as an interactive shell, then we will have trouble using "run" more
 *   than once in the same session, because our network port 8080 remains bound when the
 *   run is cancelled with Ctrl-C.  This can be addressed by:
 *   a) Invoking SBT in batch mode, e.g. "sbt run".
 *   b) Using "bgRun" command from SBT shell.
 *   c) Using "fork" instruction in our build.sbt.
 *   https://www.scala-sbt.org/1.x/docs/Forking.html
 *
 *   3) IntelliJ JetBrains IDE with GraalVM JDK 11, as well as Scala and SBT plugins.
 *   + Project imports OK from the build.sbt file, and builds in the .idea format.
 *   + Both of the 2 tests in HelloWorldSpec succeed.
 *   + AppMain runs and starts web server.  Runs OK after printing 1 warning about AnsiPrintStream, below.
 *   + Responds with json blobs to GET requests on port 8080 for   /greetingForUser  and  /joke

Note that our logback.xml from the template initially contained this setting:
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