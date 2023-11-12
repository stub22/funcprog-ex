package com.appstract.fpex.weather.main

import cats.effect.IO
import com.appstract.fpex.weather.api.report.{Msg_WeatherError, Msg_WeatherReport, WeatherReportSupplier}
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Response}
import org.log4s
import org.log4s.Logger

trait AppWebRoutes {
	private def getLogger: Logger = log4s.getLogger

	// Our frontend HTTP operation names:
	val OP_NAME_WEATHER_WPATH = "check-weather-wpath"  	// WPATH means "with path argument"
	val OP_NAME_WEATHER_WQUERY = "check-weather-wquery" // WQUERY means "with query arguments"

	// Lat,Lon may come in as query parameters. See OP_NAME_WEATHER_WQUERY route below.
	private object QPM_Latitude extends QueryParamDecoderMatcher[String]("lat")
	private object QPM_Longitude extends QueryParamDecoderMatcher[String]("lon")

	def weatherReportRoutes(frcstSupp: WeatherReportSupplier): HttpRoutes[IO] = {

		// We support two different input forms for latitude,longitude.
		// 1) WPATH: As a comma separated string in the URL path.  This is the form used by the weather.gov backend.
		// 2) WQUERY: As two separate URL query parameters.
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
			// Malformed input URLs which do not match the patterns above are handled elsewhere.
			// See AppServerBuilder.makeAppRoutesKleisli.
		}
	}

	def weatherOutputMsgToWebResponse(msg : Either[Msg_WeatherError, Msg_WeatherReport]) : IO[Response[IO]] = {
		import com.appstract.fpex.weather.impl.report.JsonEncoders_Report._
		val outputLogEff = IO.blocking {
			getLogger.info(s"weatherOutputMsgToWebResponse is mapping output message ${msg} to HTTP response")
		}
		val outputResponseEff = msg match {
			// When we encounter backend errors, our frontend returns HTTP status=OK (200) with a body containing a
			// JSON description of the error.  To return an HTTP error instead, change the "Left" mapping here.
			// Note that bad frontend HTTP input, such as a malformed input URL, is not handled here.
			case Left(err) => Ok(err)
			case Right(report) => Ok(report)
		}
		outputLogEff &> outputResponseEff
	}

}
