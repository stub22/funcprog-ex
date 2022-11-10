package com.appstract.fpex.weather

import cats.effect.{IO, Resource}
import fs2.Pure
import munit.CatsEffectSuite
import org.http4s.{EntityBody, HttpRoutes, Method, ParseResult, Request, Response, Status, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.log4s
import org.log4s.Logger

class ForecastRouteSpec extends CatsEffectSuite {

	val WEATHER_FAKE_URL_TXT = WeatherRoutes.OP_NAME_WEATHER_FAKE

	lazy val myLog: Logger = log4s.getLogger

	test("forecastRoutes returns status code 200") {

		val weatherUrlPath = s"/${WEATHER_FAKE_URL_TXT}"
		val forecastRspIO: IO[Response[IO]] = invokeForecastRoute(weatherUrlPath)

		assertIO(forecastRspIO.map(resp => {
			val x: EntityBody[IO] = resp.body
			//   type EntityBody[+F[_]] = Stream[F, Byte]
			myLog.info(s"ForecastRouteSpec resp=${resp}, body=${resp.body}")
			resp.status
		}), Status.Ok)
	}

	private[this] def invokeForecastRoute(weatherUrlPath : String) : IO[Response[IO]] = {
		// Builds the response-out effect for a simulated request-in message.

		// Eagerly construct Uri.  May throw a ParseFailure.
		val weatherUri: Uri = Uri.unsafeFromString(weatherUrlPath)

		myLog.info(s"ForecastRouteSpec.invokeForecastRoute made test-weatherUri: ${weatherUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, weatherUri) //  uri"/greetingForUser/world")
		val forecastSuppFact = new ForecastSupplierFactory {}
		val embCliRes: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		myLog.info(s"ForecastRouteSpec.invokeForecastRoute built embCliRes=${embCliRes}")
		val routeRes: Resource[IO, HttpRoutes[IO]] = for {
			cli <- embCliRes
			forecastSupp: ForecastSupplier = forecastSuppFact.getImpl(cli)
			route: HttpRoutes[IO] = WeatherRoutes.forecastRoutes(forecastSupp, false)
		} yield(route)
		// Now we have a potential-route wrapped in a resource
		routeRes.use(hr => {
			hr.orNotFound(requestIO)
		})
	}

}

/***
 final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, *], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.run(a).getOrElse(Response.notFound))
}

  /**
 * Allocates a resource and supplies it to the given function. The resource is released as
 * soon as the resulting `F[B]` is completed, whether normally or as a raised error.
 *
 * @param f
 *   the function to apply to the allocated resource
 * @return
 *   the result of applying [F] to
   */
  def use[B](f: A => F[B])(implicit F: MonadCancel[F, Throwable]): F[B] =
    fold(f, identity)

  /**
 * Allocates a resource with a non-terminating use action. Useful to run programs that are
 * expressed entirely in `Resource`.
 *
 * The finalisers run when the resulting program fails or gets interrupted.
   */
  def useForever(implicit F: Spawn[F]): F[Nothing] =
    use[Nothing](_ => F.never)


https://typelevel.org/cats-effect/docs/2.x/datatypes/io#raiseerror
Since there is an instance of MonadError[IO, Throwable] available in Cats Effect, all the error handling is done through it.
This means you can use all the operations available for MonadError and thus for ApplicativeError on IO as long as the
error type is a Throwable. Operations such as raiseError, attempt, handleErrorWith, recoverWith, etc.
Just make sure you have the syntax import in scope such as cats.syntax.all._.
*/