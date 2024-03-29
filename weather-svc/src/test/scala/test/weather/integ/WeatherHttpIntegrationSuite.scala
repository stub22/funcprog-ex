package test.weather.integ

import cats.effect.{IO, Resource}
import com.appstract.fpex.weather.api.report.WeatherReportSupplier
import com.appstract.fpex.weather.impl.report.WeatherReportSupplierImpl
import com.appstract.fpex.weather.main.AppWebRoutes
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{HttpRoutes, Method, Request, Response, Status, Uri}
import org.log4s
import org.log4s.Logger

// These are NOT 'unit' tests.
// These tests attempt to access live http servers.
class WeatherHttpIntegrationSuite extends CatsEffectSuite {

	// Marking tests with this tag prevents them from being executed during 'sbt test', as configured in build.sbt.
	val tagInteg = new munit.Tag("Integration")

	private val myRoutes = new AppWebRoutes {}

	private val myLog: Logger = log4s.getLogger

	// These are NOT 'unit' tests.

	// Rudimentary smoke test suite that runs a few weather queries, but does not really "verify" much.
	// These tests assume the backend at api.weather.gov is available, so they are testing more than just our software.

	// Note that even when backend fails we create a successful frontend HTTP Response, so these tests usually
	// don't "fail" (as far as the test runner knows).  However we may see ERROR / WARN messages in the output log.

	// TODO:  Consider treating weather-service failures as test failures.
	// TODO:  Check the contents of the HTTP response body-stream.

	// TODO:  Add cases testing failure of bad input URLs.

	// TODO:  Explore the boundaries of what is a legitimate lat,lon pair.
	// TODO:  Consider testing with some randomly generated lat,lon pairs.

	test("check-weather-wquery (for hardcoded latTxt and lonTxt) returns status code 200".tag(tagInteg)) {
		val weatherUrlPath = mkWqryUrlForLatLong("39.7456", "-97.0892")
		applyWeatherRouteAndAssertResponseStatusOK(weatherUrlPath)
	}
	test("check-weather-wpath (for a sequence hardcoded lat,long pairs) returns status code 200".tag(tagInteg)) {
		// Here we sequence several simulated web requests into a single test-case.
		val latLonTxt_01 = "39.7456,-97.0892"	// Point in Kansas, USA used as an example in api.weather.gov docs.
		val latLonTxt_02 = "39.7255,-97.4923"	// Random spot sorta close by (also in Kansas).
		val latLonTxt_03 = "33.2214,-88.0055"	// Random spot in Alabama, USA
		val latLonTxt_04 = "8.995929,-79.5733"	// Point near Panama City, Panama.  Backend (always?) returns 301 Moved Permanently
		// TODO:  Try out more spots on the globe.

		val points = List[String](latLonTxt_01, latLonTxt_02, latLonTxt_03, latLonTxt_04)
		val urls: List[String] = points.map(mkWpathUrlForLatLong)
		val checkers: List[IO[Unit]] = urls.map(applyWeatherRouteAndAssertResponseStatusOK)

		// Combine our separate testing effects
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

	private def applyWeatherRouteAndAssertResponseStatusOK(weatherUrlPath : String) : IO[Unit] = {
		val weatherRespIO: IO[Response[IO]] = applyWeatherRoute(weatherUrlPath)
		val loggedWeathRespIO = weatherRespIO.flatTap(resp => IO.blocking{
			myLog.info(s"applyWeatherRouteAndAssertResponseStatusOK(${weatherUrlPath}) got Response=${resp}, Response.Body=${resp.body}")
		})
		// When run, asserts that the response-effect (upon being run) produces HTTP Status OK (200).
		val assertEffect: IO[Unit] = assertIO(loggedWeathRespIO.map(resp => {
			resp.status
		}), Status.Ok)
		assertEffect
	}

	private def applyWeatherRoute(weatherUrlPath : String) : IO[Response[IO]] = {
		// Prepares to simulate execution of a frontend web request.
		// We build a response-out effect using a route constructed inside this method.

		// Eagerly construct Uri.  May throw a ParseFailure.
		val weatherUri: Uri = Uri.unsafeFromString(weatherUrlPath)
		val requestIO: Request[IO] = Request[IO](Method.GET, weatherUri)

		// When debugging, we immediately log the simulated request, which helps to illustrate our control flow.
		myLog.debug(s"IMMEDIATE TEST LOGGING: .applyWeatherRoute parsed test weather-uri: ${weatherUri} and created simulated request: ${requestIO}")

		// Setup a real web client to use for backend fetches.  Remember we are NOT doing 'unit' testing here!
		val embCliRsrc: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build

		// Prepare to build our own web route to execute.
		val routeResource: Resource[IO, HttpRoutes[IO]] = for {
			cli: Client[IO] <- embCliRsrc
			forecastSupp: WeatherReportSupplier = new WeatherReportSupplierImpl(cli)
			route: HttpRoutes[IO] = myRoutes.weatherReportRoutes(forecastSupp)
		} yield (route)

		// When responseIO is eventually run, it will build and use the route just one time, and then release it.
		val responseIO = routeResource.use(hr => {
			val logEff = IO.blocking{ myLog.info(s".applyWeatherRoute(${weatherUri}) is testing simulated request: ${requestIO}")}
			val routeExecEff: IO[Response[IO]] = hr.orNotFound(requestIO)
			logEff *> routeExecEff
		})
		responseIO
	}
}
