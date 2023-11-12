package com.appstract.fpex.weather.main

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import com.appstract.fpex.weather.api.report.WeatherReportSupplier
import com.appstract.fpex.weather.impl.report.WeatherReportSupplierImpl
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import org.log4s

// The heart of our application is setup in .makeAppRoutesKleisli.
// The rest of this code sets up the wiring of our HTTP service.
trait AppServerBuilder {

	private val myLog: log4s.Logger = log4s.getLogger

	def makeAppServerIO: IO[Nothing] = {
		myLog.debug(s"AppServer.makeAppServerIO BEGIN")
		val srvrRsrc: Resource[IO, Server] = makeServerResource()
		myLog.debug(s"AppServer.makeAppServerIO built server resource: ${srvrRsrc}")
		val srvrIO: IO[Nothing] = srvrRsrc.useForever
		myLog.debug(s"AppServer.makeAppServerIO returning srvrIO that is intended to run 'forever'")
		srvrIO
	}

	private def makeServerResource(): Resource[IO, Server] = {
		// It's important for the reader to recognize these stages of the server launch.
		// First we need an HTTP client resource to access backend HTTP services.
		val clientRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		// Next we make an HttpApp resource, which uses the client.
		val appRsrc: Resource[IO, HttpApp[IO]] = clientRsrc.map(makeHttpAppWithLogging(_))
		// Finally we make a runnable Server resource, which uses the HttpApp.
		val srvrRsrc: Resource[IO, Server] = appRsrc.flatMap(makeServerResourceForHttpApp(_))
		srvrRsrc
	}


	// We pass dataSrcCli client as a lazy BY-NAME parameter to each application component that needs it.
	private def makeHttpAppWithLogging(dataSrcCli: => Client[IO]) : HttpApp[IO] = {
		val routesKleisli = makeAppRoutesKleisli(dataSrcCli)
		val (flag_logHeaders, flag_logBody) = (true, true)
		Logger.httpApp(flag_logHeaders, flag_logBody)(routesKleisli)
	}

	private def makeAppRoutesKleisli(dataSrcCli: => Client[IO]) : Kleisli[IO, Request[IO], Response[IO]] = {
		// Heart of our application is defined by the routes we can respond to.
		val weatherRoutes = new AppWebRoutes{}
		val forecastSupp : WeatherReportSupplier = new WeatherReportSupplierImpl(dataSrcCli)
		// These HttpRoutes kleislis may be composed together.
		val weatherRoutesK: HttpRoutes[IO] = weatherRoutes.weatherReportRoutes(forecastSupp)
		// .orNotFound completes our route setup with an error handler to generate responses for a bad URL "not found" case.
		val httpRoutesK: Kleisli[IO, Request[IO], Response[IO]] = weatherRoutesK.orNotFound
		httpRoutesK
	}

	private def makeServerResourceForHttpApp(httpApp: HttpApp[IO]):  Resource[IO, Server] = {
		// TODO:  In a real app the host and port would come from some appropriate configuration setup.
		import com.comcast.ip4s._	// Brings ipv4 and port macros into scope
		val host: Ipv4Address = ipv4"0.0.0.0"
		val portNum: Port = port"8080"
		val serverResource: Resource[IO, Server] = EmberServerBuilder.default[IO]
				.withHost(host)
				.withPort(portNum)
				.withHttpApp(httpApp)
				.build
		serverResource
	}
}
