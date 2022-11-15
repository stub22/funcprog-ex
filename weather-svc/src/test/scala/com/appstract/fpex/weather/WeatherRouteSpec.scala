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

	test("check-weather-wquery (for hardcoded latTxt and lonTxt) returns status code 200") {
		val weatherUrlPath = mkWqryUrlForLatLong("39.7456", "-97.0892")
		applyWeatherRouteAndAssertStatusOK(weatherUrlPath)
	}
	test("check-weather-wpath (for several hardcoded lat,long pairs) returns status code 200") {
		// Here we sequence several simulated web requests.
		// These could just as well be separate tests, but it's interesting to see them combined into one IO.
		val latLonTxt_01 = "39.7456,-97.0892"	// A point in Kansas, USA used as an example in api.weather.gov docs.
		val latLonTxt_02 = "39.7755,-97.9923"
		val latLonTxt_03 = "33.2210,-88.0055"	// A point in Alabama, USA that has been failing on 2nd stage lookup

		val tstIO_01: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLonTxt_01))
		val tstIO_02: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLonTxt_02))
		val tstIO_03: IO[Unit] = applyWeatherRouteAndAssertStatusOK(mkWpathUrlForLatLong(latLonTxt_03))

		import cats.implicits._
		// Combine the three test scenarios into a List, then turn that into a single IO[List] which we can return
		// to the munit harness to be executed.
		val tstList: List[IO[Unit]] = List(tstIO_01, tstIO_02, tstIO_03)
		val tstSeq: IO[List[Unit]] = tstList.sequence
		tstSeq
	}

	// TODO:  Explore the boundaries of what is a legitimate lat,lon pair.
	// TODO:  Consider testing with some randomly generated lat,lon pairs.

	private def mkWpathUrlForLatLong(latLongPairTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WPATH}/${latLongPairTxt}"
	}

	private def mkWqryUrlForLatLong(latTxt : String, lonTxt : String) : String = {
		s"/${myRoutes.OP_NAME_WEATHER_WQUERY}?lat=${latTxt}&lon=${lonTxt}"
	}

	private def applyWeatherRouteAndAssertStatusOK(weatherUrlPath : String) : IO[Unit] = {
		val weatherRespIO: IO[Response[IO]] = applyWeatherRoute(weatherUrlPath)
		// When run, asserts that the response-effect produces HTTP Status OK (200).
		val assertionEffect: IO[Unit] = assertIO(weatherRespIO.map(resp => {
			// TODO:  Optionally pull and dump the contents of the response body-stream.
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
		val embCliRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
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
