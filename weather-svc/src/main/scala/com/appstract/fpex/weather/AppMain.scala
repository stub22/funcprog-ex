package com.appstract.fpex.weather

import cats.effect.{IO, IOApp}

object AppMain extends IOApp.Simple {
  def run:IO[Unit] = WeatherServer.run
}

/***
 * Work started from a skeleton project copied from this giter8 template:
 * https://github.com/http4s/http4s-io.g8
 *
 * We initially tested (on MS Windows 10) that it builds with
 *   1) sbt 1.7.3 and Oracle JDK 8
 *   We can launch the app with this combo, but it fails during startup with
 *   java.lang.UnsupportedClassVersionError: ch/qos/logback/classic/spi/LogbackServiceProvider
 *   indicating that our Logback 1.4.1 dependency requires at least JDK 11.
 *
 *   2) IntelliJ JetBrains IDE with GraalVM 11
 *   + Imports OK from the build.sbt file, and builds in the IDE format.
 *   + Both of the 2 tests in HelloWorldSpec succeed (launched with IDE select + right-click "Run")
 *   + AppMain runs and starts web server.  Runs OK after printing 1 warning about AnsiPrintStream, below.
 *   + Responds with json blobs to GET requests on port 8080 for   /hello  and  /joke

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