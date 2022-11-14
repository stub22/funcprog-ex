package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import com.appstract.fpex.extra.{ExtraRoutes, GreetingSupplier, GreetingSupplierSingleton, JokeSupplier, JokeSupplierFactory}
import org.http4s.server.Server
import org.http4s.client.Client
import org.http4s.{HttpApp, Request, Response}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder

import org.log4s
import org.http4s.server.middleware.Logger

trait AppServerBuilder {

	private val myLog: log4s.Logger = log4s.getLogger

	def makeAppServerIO: IO[Nothing] = {
		myLog.info(s"AppServer.makeAppServerIO BEGIN")
		val srvrRsrc = makeServerResource
		myLog.info(s"AppServer.makeAppServerIO built server resource: ${srvrRsrc}")
		val srvrIO = srvrRsrc.useForever
		myLog.info(s"AppServer.makeAppServerIO returning srvrIO=${srvrIO}")
		srvrIO
	}

	private def makeServerResource(): Resource[IO, Server] = {
		// First we need an HTTP client resource to access backend HTTP services.
		val clientRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		val appRsrc: Resource[IO, HttpApp[IO]] = clientRsrc.map(makeHttpAppWithLogging(_))
		val srvrRsrc: Resource[IO, Server] = appRsrc.flatMap(makeServerResourceForHttpApp(_))
		srvrRsrc
	}

	private def makeHttpAppWithLogging(dataSrcCli: => Client[IO]) : HttpApp[IO] = {
		val routesKleisli = makeAppRoutesKleisli(dataSrcCli)
		Logger.httpApp(true, true)(routesKleisli)
	}

	// An HttpRoutes[F] is a simple alias for Kleisli[OptionT[F, ?], Request, Response]
	private def makeAppRoutesKleisli(dataSrcCli: => Client[IO]) : Kleisli[IO, Request[IO], Response[IO]] = {
		val jokeSuppFactory = new JokeSupplierFactory {}
		val greetingSuppFactory = GreetingSupplierSingleton

		val weatherRoutes = new WeatherRoutes{}
		val extraRoutes = new ExtraRoutes {}

		val grtSupp: GreetingSupplier = greetingSuppFactory.getImpl
		val jokeSupp: JokeSupplier = jokeSuppFactory.getImpl(dataSrcCli)
		val forecastSupp : WeatherReportSupplier = new WeatherReportSupplierImpl(dataSrcCli)

		// Bring the <+> operator into scope as shorthand for SemigroupK.combineK
		import cats.implicits._

		val servicesK = (extraRoutes.greetingRoutes(grtSupp)
					<+>	extraRoutes.jokeRoutes(jokeSupp)
					<+>	weatherRoutes.reportRoutes(forecastSupp))

		val httpRoutesKleisli: Kleisli[IO, Request[IO], Response[IO]] = servicesK.orNotFound

		httpRoutesKleisli
	}

	private def makeServerResourceForHttpApp(httpApp: HttpApp[IO]):  Resource[IO, Server] = {
		// FIXME:  Hardcoded port setup copied from htt4s-io giter8 skeleton.
		import com.comcast.ip4s._	// Brings ipv4 and port macros into scope
		val serverResource: Resource[IO, Server] = EmberServerBuilder.default[IO]
				.withHost(ipv4"0.0.0.0")
				.withPort(port"8080")
				.withHttpApp(httpApp)
				.build
		serverResource
	}
}
