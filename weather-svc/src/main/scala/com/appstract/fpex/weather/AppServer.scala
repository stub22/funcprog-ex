package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.server.Server
import org.http4s.client.Client
import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.log4s
import org.http4s.server.middleware.Logger

trait AppServerBuilder {

	private val myLog: log4s.Logger = log4s.getLogger

	def makeAppServerIO: IO[Nothing] = {
		myLog.info(s"AppServer.makeAppServerIO BEGIN")
		val srvrRsrc: Resource[IO, Server] = makeServerResource()
		myLog.info(s"AppServer.makeAppServerIO built server resource: ${srvrRsrc}")
		val srvrIO: IO[Nothing] = srvrRsrc.useForever
		myLog.info(s"AppServer.makeAppServerIO returning srvrIO=${srvrIO}")
		srvrIO
	}

	private def makeServerResource(): Resource[IO, Server] = {
		// First we need an HTTP client resource to access backend HTTP services.
		val clientRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		// Next we make an HttpApp resource, which uses the client.
		val appRsrc: Resource[IO, HttpApp[IO]] = clientRsrc.map(makeHttpAppWithLogging(_))
		// Finally we make a runnable Server resource, which uses the HttpApp.
		val srvrRsrc: Resource[IO, Server] = appRsrc.flatMap(makeServerResourceForHttpApp(_))
		srvrRsrc
	}

	private def makeHttpAppWithLogging(dataSrcCli: => Client[IO]) : HttpApp[IO] = {
		val routesKleisli = makeAppRoutesKleisli(dataSrcCli)
		val (flag_logHeaders, flag_logBody) = (true, true)
		Logger.httpApp(flag_logHeaders, flag_logBody)(routesKleisli)
	}

	private def makeAppRoutesKleisli(dataSrcCli: => Client[IO]) : Kleisli[IO, Request[IO], Response[IO]] = {
		val weatherRoutes = new AppWebRoutes{}
		val forecastSupp : WeatherReportSupplier = new WeatherReportSupplierImpl(dataSrcCli)
		// These HttpRoutes kleislis may be composed together.
		val weatherRoutesK: HttpRoutes[IO] = weatherRoutes.weatherReportRoutes(forecastSupp)
		// .orNotFound completes our route setup with an error handler to generate responses for the "not found" case.
		val httpRoutesKleisli: Kleisli[IO, Request[IO], Response[IO]] = weatherRoutesK.orNotFound
		httpRoutesKleisli
	}

	private def makeServerResourceForHttpApp(httpApp: HttpApp[IO]):  Resource[IO, Server] = {
		// TODO:  In a real app the host and port would come from some appropriate configuration setup.
		// These macros are used in the http4s-io template.
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
