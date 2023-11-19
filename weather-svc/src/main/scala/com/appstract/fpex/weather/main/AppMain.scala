package com.appstract.fpex.weather.main

import cats.effect.{IO, IOApp}
import org.log4s
import org.log4s.Logger

object AppMain extends IOApp.Simple {
	// Entry point for our http4s + cats-effect application:
	def run:IO[Unit] = {
		val log: Logger = log4s.getLogger
		log.info("AppMain.run BEGIN")
		val appServerBuilder = new AppServerBuilder{}
		log.debug("AppMain.run: Invoking .makeAppServerIO to build our runnable server effect")
		val appServerIO: IO[Nothing] = appServerBuilder.makeAppServerIO
		log.info("AppMain.run built our server IO effect. Now returning it to IOApp to be executed FOREVER")
		log.info(
			"""Within a few seconds you should be able to access Weather data from a web browser, using URLS like:
			|Path argument example
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
 * Platform compatibility:  Tested on MS Windows 10 with GraalVM JDK 11 (Version 22.3.2)
 *
 */