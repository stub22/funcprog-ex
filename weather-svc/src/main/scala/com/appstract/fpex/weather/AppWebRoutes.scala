package com.appstract.fpex.weather

import cats.effect.IO
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.io._
import org.log4s
import org.log4s.Logger

trait AppWebRoutes {
	private val myLog: Logger = log4s.getLogger

	// Our frontend HTTP operation names:
	val OP_NAME_WEATHER_WPATH = "check-weather-wpath"  	// WPATH means "with path argument"
	val OP_NAME_WEATHER_WQUERY = "check-weather-wquery" // WQUERY means "with query arguments"

	// Lat,Lon may come in as query parameters. See WQUERY route below.
	private object QPM_Latitude extends QueryParamDecoderMatcher[String]("lat")
	private object QPM_Longitude extends QueryParamDecoderMatcher[String]("lon")

	def weatherReportRoutes(frcstSupp: WeatherReportSupplier): HttpRoutes[IO] = {

		// We support two different input forms for latitude,longitude.
		// 1) "WPATH" As a comma separated string in the URL path.  This is the form used by the weather.gov backend.
		// 2) "WQUERY" As two separate URL query parameters.
		HttpRoutes.of[IO] {
			case GET -> Root / OP_NAME_WEATHER_WPATH / latLonTxt => {
				// Expects a latLon text pair in the request path, in form compatible with weather.gov/points service,
				// Example:  "39.7456,-97.0892"
				for {
					wrprtOrErr <- frcstSupp.fetchWeatherForLatLonPairTxt(latLonTxt)
					resp: Response[IO] <- weatherOutputMsgToWebResponse(wrprtOrErr)
				} yield resp
			}
			case GET -> Root / OP_NAME_WEATHER_WQUERY :? QPM_Latitude(latTxt) +& QPM_Longitude(lonTxt) => {
				// Expects two separate query parameters, which we interpret only as Strings, so far.
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
