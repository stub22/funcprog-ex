package com.appstract.fpex.weather

import cats.effect.IO
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.io._
import org.log4s
import org.log4s.Logger

trait WeatherRoutes {
	private val myLog: Logger = log4s.getLogger

	// Our frontend HTTP operation names:
	val OP_NAME_WEATHER_DUMMY = "check-weather-dummy"
	val OP_NAME_WEATHER_FIXED = "check-weather-fixed"
	val OP_NAME_WEATHER_WPATH = "check-weather-wpath"
	val OP_NAME_WEATHER_WQRY = "check-weather-wquery"

	// Lat,Lon may come in as query parameters.
	private object QPM_Latitude extends QueryParamDecoderMatcher[String]("lat")
	private object QPM_Longitude extends QueryParamDecoderMatcher[String]("lon")

	def reportRoutes(frcstSupp: WeatherReportSupplier): HttpRoutes[IO] = {

		// We support two different forms of latitude,longitude.
		// 1) As a comma separated string in the URL path
		// 2) As two separate URL query parameters
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_WEATHER_DUMMY => {
				// Just a frontend plumbing test.  Does not access the backend weather.gov service.
				for {
					wrprtOrErr <- frcstSupp.getFakeWeather
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) // Ok(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_FIXED => {
				// Real forecast-maker effect, but doesn't use any input from the user's request.
				// Uses some fixed lat-long location.
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForFixedLocation
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr) //  Ok(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WPATH / latLonTxt => {
				// Expects a latLon text pair in the request path, in form compatible with weather.gov/points service,
				// Example:  "39.7456,-97.0892"
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForLatLonPairTxt(latLonTxt)
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WQRY :? QPM_Latitude(latTxt) +& QPM_Longitude(lonTxt) => {
				// Expects two separate query parameters.
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForLatLon(latTxt, lonTxt)
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr)
				} yield resp
			}
			// Use default HTTP4S error handling for malformed input URLs.
		}
	}

	def weatherOutputMsgToWebResponse(msg : Either[Msg_WeatherError, Msg_WeatherReport]) : IO[Response[IO]] = {
		import JsonEncoders_Report._
		myLog.info(s"weatherOutputMsgToWebResponse is mapping output message ${msg} to HTTP response")
		msg match {
			// When we encounter backend errors, we choose to return HTTP status=OK (200) with a body containing a JSON
			// description of the error.  To return an HTTP error instead, change the "Left" mapping here.
			// Note that bad HTTP input, such as a malformed URL, is not handled here.
			case Left(err) => Ok(err)
			case Right(report) => Ok(report)
		}
	}

}
