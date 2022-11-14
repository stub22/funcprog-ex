package com.appstract.fpex.weather

import cats.effect.{IO, Resource}
import fs2.Pure
import munit.CatsEffectSuite
import org.http4s.{EntityBody, HttpRoutes, Method, ParseResult, Request, Response, Status, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.log4s
import org.log4s.Logger

class WeatherRouteSpec extends CatsEffectSuite {
	private val myRoutes = new WeatherRoutes {}

	private val myLog: Logger = log4s.getLogger

	test("Dummy forecast returns status code 200") {
		val weatherUrlPath = s"/${myRoutes.OP_NAME_WEATHER_DUMMY}"
		applyWeatherRouteAndAssertStatusOK(weatherUrlPath)
	}
	test("Forecast for fixed location returns status code 200") {
		val weatherUrlPath = s"/${myRoutes.OP_NAME_WEATHER_FIXED}"
		applyWeatherRouteAndAssertStatusOK(weatherUrlPath)
	}
	test("Forecast for hardcoded latTxt and lonTxt returns status code 200") {
		val weatherUrlPath = mkWqryUrlForLatLong("39.7456", "-97.0892")
		applyWeatherRouteAndAssertStatusOK(weatherUrlPath)
	}
	test("Forecast for some lat,long pairs supplied in path returns status code 200") {
		val latLongTxt_01 = "39.7456,-97.0892"	// A point in Kansas, USA used as an example in api.weather.gov docs.
		val latLongTxt_02 = "39.7755,-97.9923"
		val latLongTxt_03 = "33.2210,-88.0055"	// A point in Alabama, USA that has been failing on 2nd stage lookup

		val tstIO_01: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLongTxt_01))
		val tstIO_02: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLongTxt_02))
		val tstIO_03: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLongTxt_03))

		// https://stackoverflow.com/questions/49800446/cats-effect-how-to-transform-listio-to-iolist
		import cats.implicits._
		val tstList: List[IO[Unit]] = List(tstIO_01, tstIO_02, tstIO_03)
		val tstSeq: IO[List[Unit]] = tstList.sequence
		val comboIO = tstIO_01.flatMap(_ => tstIO_02).flatMap(_ => tstIO_03)

		// comboIO
		tstSeq
	}
	private def mkWpathUrlForLatLong(latLongPairTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WPATH}/${latLongPairTxt}"
	}

	private def mkWqryUrlForLatLong(latTxt : String, lonTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WQRY}?lat=${latTxt}&lon=${lonTxt}"
	}

	private def applyWeatherRouteAndAssertStatusOK(weatherUrlPath : String) : IO[Unit] = {

		val forecastRspIO: IO[Response[IO]] = applyWeatherRoute(weatherUrlPath)

		val zzz: IO[Unit] = assertIO(forecastRspIO.map(resp => {
			val x: EntityBody[IO] = resp.body		//   type EntityBody[+F[_]] = Stream[F, Byte]
			myLog.info(s"XXXXXXXXXXXXXXXXXXXX  Route for ${weatherUrlPath} Got Response=${resp}, Response.Body=${resp.body}")
			resp.status
		}), Status.Ok)
		zzz
	}

	private def applyWeatherRoute(weatherUrlPath : String) : IO[Response[IO]] = {
		// Build a response-out effect using a route constructed inside this method.

		// Eagerly construct Uri.  May throw a ParseFailure.
		val weatherUri: Uri = Uri.unsafeFromString(weatherUrlPath)
		myLog.info(s"WeatherRouteSpec.invokeForecastRoute made test-weather-uri: ${weatherUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, weatherUri) //  uri"/greetingForUser/world")
		val embCliRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		myLog.info(s"WeatherRouteSpec.applyWeatherRoute built embCliRsrc=${embCliRsrc}")
		val routeResource: Resource[IO, HttpRoutes[IO]] = for {
			cli <- embCliRsrc
			forecastSupp: WeatherReportSupplier = new WeatherReportSupplierImpl(cli)
			route: HttpRoutes[IO] = myRoutes.reportRoutes(forecastSupp)
		} yield(route)
		// Now we have a potential-route wrapped in a resource.
		// When the responseIO is eventually run, it will build and use the route just one time, and then release it.
		val responseIO = routeResource.use(hr => {
			hr.orNotFound(requestIO)
		})
		responseIO
	}

}

/***
 final class KleisliResponseOps[F[_]: Functor, A](self: Kleisli[OptionT[F, *], A, Response[F]]) {
  def orNotFound: Kleisli[F, A, Response[F]] =
    Kleisli(a => self.runOld(a).getOrElse(Response.notFound))
}

 * Allocates a resource and supplies it to the given function. The resource is released as
 * soon as the resulting `F[B]` is completed, whether normally or as a raised error.
 *
 * @param f
 *   the function to apply to the allocated resource
 * @return
 *   the result of applying [F] to

  def use[B](f: A => F[B])(implicit F: MonadCancel[F, Throwable]): F[B] =
    fold(f, identity)


Allocates a resource with a non-terminating use action. Useful to runOld programs that are
expressed entirely in `Resource`.
The finalisers runOld when the resulting program fails or gets interrupted.
  def useForever(implicit F: Spawn[F]): F[Nothing] =
    use[Nothing](_ => F.never)

https://typelevel.org/cats-effect/docs/2.x/datatypes/io#raiseerror
Since there is an instance of MonadError[IO, Throwable] available in Cats Effect, all the error handling is done through it.
This means you can use all the operations available for MonadError and thus for ApplicativeError on IO as long as the
error type is a Throwable. Operations such as raiseError, attempt, handleErrorWith, recoverWith, etc.
Just make sure you have the syntax import in scope such as cats.syntax.all._.
*/