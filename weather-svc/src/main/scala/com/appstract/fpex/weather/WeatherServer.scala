package com.appstract.fpex.weather

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.{HttpApp, Request, Response}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger

// These contents are still mostly from htt4s-io giter8 skeleton.

trait WeatherServer {

	// TODO:  Move port number into a field

	def run: IO[Nothing] = {
		val jokeSuppFactory = new JokeSupplierFactory {}
		val greetingSuppFactory = GreetingSupplierSingleton
		val forecastSuppFactory = new ForecastSupplierFactory {}

		for {
			client <- EmberClientBuilder.default[IO].build
			grtSupp: GreetingSupplier = greetingSuppFactory.getImpl
			jokeSupp: JokeSupplier = jokeSuppFactory.getImpl(client)
			forecastSupp : ForecastSupplier = forecastSuppFactory.getImpl(client)
			// Combine Service Routes into an HttpApp.
			// Can also be done via a Router if you want to extract a segments not checked in the underlying routes.

			// From Cats SemigroupK.Ops:  def <+>(y: F[A]): F[A] = typeClassInstance.combineK[A](self, y)
			httpApp: Kleisli[IO, Request[IO], Response[IO]] = (
					WeatherRoutes.greetingRoutes(grtSupp) <+>
					WeatherRoutes.jokeRoutes(jokeSupp) <+>
					WeatherRoutes.forecastRoutes(forecastSupp, false)
				).orNotFound

			// Insert logging "middleware"
			finalHttpApp: HttpApp[IO] = Logger.httpApp(true, true)(httpApp)

			_ <-
					EmberServerBuilder.default[IO]
							.withHost(ipv4"0.0.0.0")
							.withPort(port"8080")
							.withHttpApp(finalHttpApp)
							.build
		} yield ()
	}.useForever
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
 * An HTTP app is total on its inputs.  An HTTP app may be run by a server,
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