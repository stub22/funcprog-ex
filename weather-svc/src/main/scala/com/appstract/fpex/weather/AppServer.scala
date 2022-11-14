package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import com.appstract.fpex.extra.{ExtraRoutes, GreetingSupplier, GreetingSupplierSingleton, JokeSupplier, JokeSupplierFactory}
import com.comcast.ip4s._
import org.http4s.client.Client
import org.http4s.{HttpApp, Request, Response}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.log4s
import org.http4s.server.middleware.Logger

// These contents are adapted from code provided by the htt4s-io giter8 skeleton.

trait AppServerBuilder {

	lazy val myLog: log4s.Logger = log4s.getLogger

	def makeAppServerIO: IO[Nothing] = {
		myLog.info(s"AppServer.makeAppServerIO BEGIN")
		val srvrRsrc = mkServerResource()
		myLog.info(s"AppServer.makeAppServerIO built server resource: ${srvrRsrc}, now calling .useForever")
		val srvrIO = srvrRsrc.useForever
		myLog.info(s"AppServer.makeAppServerIO returning srvrIO=${srvrIO}")
		srvrIO
	}

	private def mkServerResource(): Resource[IO, Server] = {
		val clientRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		val appRsrc: Resource[IO, HttpApp[IO]] = clientRsrc.map(mkHttpAppWithLogging(_))
		val srvrRsrc: Resource[IO, Server] = appRsrc.flatMap(mkServerResourceForHttpApp(_))
		srvrRsrc
	}

	private def mkHttpAppWithLogging(dataSrcCli: => Client[IO]) : HttpApp[IO] = {
		val routesKleisli = mkAppRoutesKleisli(dataSrcCli)
		Logger.httpApp(true, true)(routesKleisli)
	}

	private def mkAppRoutesKleisli(dataSrcCli: => Client[IO]) : Kleisli[IO, Request[IO], Response[IO]] = {
		val jokeSuppFactory = new JokeSupplierFactory {}
		val greetingSuppFactory = GreetingSupplierSingleton

		val weatherRoutes = new WeatherRoutes{}
		val extraRoutes = new ExtraRoutes {}

		val grtSupp: GreetingSupplier = greetingSuppFactory.getImpl
		val jokeSupp: JokeSupplier = jokeSuppFactory.getImpl(dataSrcCli)
		val forecastSupp : WeatherReportSupplier = new WReportSupplierImpl(dataSrcCli)

		// https://typelevel.org/cats/datatypes/kleisli.html
		// "At its core, Kleisli[F[_], A, B] is just a wrapper around the function A => F[B]."
		//   def orNotFound: Kleisli[F, A, Response[F]] =   Kleisli(a => self.run(a).getOrElse(Response.notFound))
		val httpRoutesKleisli: Kleisli[IO, Request[IO], Response[IO]] = (
				extraRoutes.greetingRoutes(grtSupp) <+>
				extraRoutes.jokeRoutes(jokeSupp) <+>
				weatherRoutes.reportRoutes(forecastSupp)
			).orNotFound

		httpRoutesKleisli
	}

	private def mkServerResourceForHttpApp(httpApp: HttpApp[IO]):  Resource[IO, Server] = {
		// FIXME:  Hardcoded port setup copied from htt4s-io giter8 skeleton.
		val serverResource: Resource[IO, Server] = EmberServerBuilder.default[IO]
				.withHost(ipv4"0.0.0.0")
				.withPort(port"8080")
				.withHttpApp(httpApp)
				.build
		serverResource
	}
}

/***
 *
A kleisli with a [[Request]] input and a [[Response]] output.  This type
 * is useful for writing middleware that are polymorphic over the return
 * type F.
 *
 * @tparam F the effect type in which the [[Response]] is returned
 * @tparam G the effect type of the [[Request]] and [[Response]] bodies
  type Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

 A kleisli with a [[Request]] input and a [[Response]] output, such
 * that the response effect is the same as the request and response bodies'.
 * An HTTP app is total on its inputs.  An HTTP app may be runOld by a server,
 * and a client can be converted to or from an HTTP app.
 *
 * @tparam F the effect type in which the [[Response]] is returned, and also
 * of the [[Request]] and [[Response]] bodies.
  type HttpApp[F[_]] = Http[F, F]

 A kleisli with a [[Request]] input and a [[Response]] output, such
 * that the response effect is an optional inside the effect of the
 * request and response bodies.  HTTP routes can conveniently be
 * constructed from a partial function and combined as a
 * `SemigroupK`.
 *
 * @tparam F the effect type of the [[Request]] and [[Response]] bodies,
 * and the base monad of the `OptionT` in which the response is returned.
  type HttpRoutes[F[_]] = Http[OptionT[F, *], F]


Logging middleware transform:

    def httpApp[F[_]: Async](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CIString => Boolean = defaultRedactHeadersWhen,
      logAction: Option[String => F[Unit]] = None,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(logHeaders, logBody, FunctionK.id[F], redactHeadersWhen, logAction)(httpApp)
 */