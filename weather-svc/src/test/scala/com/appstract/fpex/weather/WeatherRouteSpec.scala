package com.appstract.fpex.weather

import munit.CatsEffectSuite
import cats.effect.{IO, Resource}
import org.http4s.{HttpRoutes, Method, Request, Response, Status, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.log4s
import org.log4s.Logger

class WeatherRouteSpec extends CatsEffectSuite {
	private val myRoutes = new AppWebRoutes {}

	private val myLog: Logger = log4s.getLogger

	// Rudimentary smoke test suite that runs a few weather queries, but does not really "verify" much.
	// These tests assume the backend at api.weather.gov is available, so they are testing more than just our software.
	// These are not "unit" tests.

	// Note that even when backend fails we create a successful frontend HTTP Response, so these tests usually
	// don't "fail" (as far as the test runner knows).  However we may see ERROR messages in the output log.

	// TODO:  Consider treating weather-service failures as test failures.
	// TODO:  Check the contents of the HTTP response body-stream.

	// TODO:  Add cases testing failure of bad input URLs.

	// TODO:  Explore the boundaries of what is a legitimate lat,lon pair.
	// TODO:  Consider testing with some randomly generated lat,lon pairs.

	test("check-weather-wquery (for hardcoded latTxt and lonTxt) returns status code 200") {
		val weatherUrlPath = mkWqryUrlForLatLong("39.7456", "-97.0892")
		applyWeatherRouteAndAssertStatusOK(weatherUrlPath)
	}
	test("check-weather-wpath (for a sequence hardcoded lat,long pairs) returns status code 200") {
		// Here we sequence several simulated web requests into a single test-case.
		val latLonTxt_01 = "39.7456,-97.0892"	// Point in Kansas, USA used as an example in api.weather.gov docs.
		val latLonTxt_02 = "39.7255,-97.4923"	// Random spot sorta close by (also in Kansas).
		val latLonTxt_03 = "33.2214,-88.0055"	// Random spot in Alabama, USA
		// Hmm, did we see a point in Panama work one time?
		val latLonTxt_04 = "8.995929,-79.5733"	// Point near Panama City, Panama.   Backend (always?) returns 301 Moved Permanently
		// TODO:  Try out more spots on the globe.

		val points = List[String](latLonTxt_01, latLonTxt_02, latLonTxt_03, latLonTxt_04)
		val urls: List[String] = points.map(mkWpathUrlForLatLong(_))
		val checkers: List[IO[Unit]] = urls.map(applyWeatherRouteAndAssertStatusOK(_))
		import cats.implicits._
		val seqChecker : IO[List[Unit]] = checkers.sequence
		seqChecker
	}

	private def mkWpathUrlForLatLong(latLongPairTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WPATH}/${latLongPairTxt}"
	}

	private def mkWqryUrlForLatLong(latTxt : String, lonTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WQUERY}?lat=${latTxt}&lon=${lonTxt}"
	}

	private def applyWeatherRouteAndAssertStatusOK(weatherUrlPath : String) : IO[Unit] = {
		val weatherRespIO: IO[Response[IO]] = applyWeatherRoute(weatherUrlPath)
		// When run, asserts that the response-effect (upon being run) produces HTTP Status OK (200).
		val assertionEffect: IO[Unit] = assertIO(weatherRespIO.map(resp => {
			myLog.info(s"XXXXXXXXXXXXXXXX  Route for ${weatherUrlPath} Got Response=${resp}, Response.Body=${resp.body}")
			resp.status
		}), Status.Ok)
		assertionEffect
	}

	private def applyWeatherRoute(weatherUrlPath : String) : IO[Response[IO]] = {
		// Prepares to simulate execution of a frontend web request.
		// We build a response-out effect using a route constructed inside this method.

		// Eagerly construct Uri.  May throw a ParseFailure.
		val weatherUri: Uri = Uri.unsafeFromString(weatherUrlPath)
		myLog.info(s"WeatherRouteSpec.invokeForecastRoute made test-weather-uri: ${weatherUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, weatherUri)

		// Using this real web client means we are not doing "unit" testing here.
		val embCliRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

		// Prepare to build our own web route to execute.
		val routeResource: Resource[IO, HttpRoutes[IO]] = for {
			cli <- embCliRsrc
			forecastSupp: WeatherReportSupplier = new WeatherReportSupplierImpl(cli)
			route: HttpRoutes[IO] = myRoutes.weatherReportRoutes(forecastSupp)
		} yield(route)

		// When responseIO is eventually run, it will build and use the route just one time, and then release it.
		val responseIO = routeResource.use(hr => {
			hr.orNotFound(requestIO)
		})
		responseIO
	}
}
