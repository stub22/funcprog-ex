package com.appstract.fpex.weather.impl.backend

import cats.effect.IO
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.log4s
import org.log4s.Logger
import com.appstract.fpex.weather.api.backend.{Msg_BackendAreaInfo, Msg_BackendAreaProps, Msg_BackendPeriodForecast}

private object JsonDecoders_BackendAreaInfo {
	// Circe decoder bindings for building Msg_AreaInfo* from JSON tree.
	// These implicits could instead be defined locally in the BackendForecastProviderImpl,
	// but Stu chose to share them in this top-level object, following common practice in Scala dev community.
	import org.http4s.circe.jsonOf

	implicit val areaPropsDecoder: Decoder[Msg_BackendAreaProps] = deriveDecoder[Msg_BackendAreaProps]
	implicit val areaInfoDecoder: Decoder[Msg_BackendAreaInfo] = deriveDecoder[Msg_BackendAreaInfo]
	implicit val areaInfoEntityDecoder: EntityDecoder[IO, Msg_BackendAreaInfo] = jsonOf
}


trait PeriodForecastExtractor {
	/***
	 * Empirically, we observed that the backend provides a time-ordered sequence of forecast `periods`, alternating
	 * between "daytime" and "nightime" (indicated by the 'isDaytime' flag in each `period` record).
	 *
	 * Our requirements are to supply a toy current-weather feature, without much detail.
	 * To simplify our coding task, we have chosen to use only the most current forecast period, regardless of day/night.
	 * However we do capture the isDaytime flag, so that client code may interpret the weather accordingly.
	 * If needed, we could build a larger data structure to capture multiple periods.
	 * But here in this toy, we assume that returning a single period forecast is sufficient.
	 */

	private val myLog: Logger = log4s.getLogger
	private val FLD_PROPS = "properties"
	private val FLD_PERIODS = "periods"

	// We need only this one Circe decoder, to extract the data from a a leaf record for the current period.
	private implicit val periodForecastDecoder: Decoder[Msg_BackendPeriodForecast] = deriveDecoder[Msg_BackendPeriodForecast]
	// To find that leaf record we use explicit Circe cursor navigation.

	def extractFirstPeriodForecast(forecastJson: Json): Either[DecodingFailure, Msg_BackendPeriodForecast] = {
		val (flg_dumpFullJson, flg_dumpPeriodJson) = (false, false)
		if (flg_dumpFullJson) {
			val jsonTxt = forecastJson.spaces4
			myLog.info(s"Extracing first period block from forecast-json: ${jsonTxt}")
		}
		// Use Circe cursors to navigate to the 'periods' array in the JSON, found at ROOT.properties.periods
		val rootCursor: HCursor = forecastJson.hcursor
		val propCursor = rootCursor.downField(FLD_PROPS)
		val periodsArrayCursor = propCursor.downField(FLD_PERIODS)
		// We expect 'periods' array to contain multiple entries, ordered by time, alternating between daytime and nighttime.
		// The first period in the array corresponds to the 'current' forecast, which may be for either day or night.
		// Currently we assume that only this first period is interesting.
		val pCurs0: ACursor = periodsArrayCursor.downN(0)

		def jsonPeriodDumper = pCurs0.focus.map(_.spaces4).getOrElse({
			"No json found at period[0]"
		})

		myLog.debug(s"period[0].history: ${pCurs0.history}")
		if (flg_dumpPeriodJson) {
			myLog.info(s"period[0].json: ${jsonPeriodDumper}")
		}
		val p0Json: Option[Json] = pCurs0.focus

		// Use our implicit periodForecastDecoder, defined above.
		//   final type Result[A] = Either[DecodingFailure, A]
		val p0Rec_orFailure: Decoder.Result[Msg_BackendPeriodForecast] = p0Json.map(_.as[Msg_BackendPeriodForecast])
				.getOrElse(Left(DecodingFailure("Could not focus on period[0]", pCurs0.history)))
		p0Rec_orFailure
	}
}

/** *
 * Example of JSON to be navigated.
 * Here is a fragment of an example JSON response from the api.weather.gov/gridpoints service,
 * containing a sequence of forecast periods.
 *
 * "properties": {
 * "periods": [
 * {
 * "number": 1,
 * "name": "Tonight",
 * "startTime": "2022-11-08T23:00:00-06:00",
 * "endTime": "2022-11-09T06:00:00-06:00",
 * "isDaytime": false,
 * "temperature": 63,
 * "temperatureUnit": "F",
 * "temperatureTrend": null,
 * "windSpeed": "15 mph",
 * "windDirection": "S",
 * "icon": "https://api.weather.gov/icons/land/night/ovc?size=medium",
 * "shortForecast": "Cloudy",
 * "detailedForecast": "Cloudy, with a low around 63. South wind around 15 mph, with gusts as high as 35 mph."
 * },
 * {
 * "number": 2,
 * "name": "Wednesday",
 * "startTime": "2022-11-09T06:00:00-06:00",
 * "endTime": "2022-11-09T18:00:00-06:00",
 * "isDaytime": true,
 * "temperature": 74,
 * "temperatureUnit": "F",
 * "temperatureTrend": null,
 * "windSpeed": "15 to 25 mph",
 * "windDirection": "S",
 * "icon": "https://api.weather.gov/icons/land/day/wind_bkn?size=medium",
 * "shortForecast": "Mostly Cloudy",
 * "detailedForecast": "Mostly cloudy, with a high near 74. South wind 15 to 25 mph, with gusts as high as 40 mph."
 * },
 * ...
 *
 * Common error response from gridpoints is:
 *
 * {
 * "correlationId": "1ed872c0",
 * "title": "Unexpected Problem",
 * "type": "https://api.weather.gov/problems/UnexpectedProblem",
 * "status": 500,
 * "detail": "An unexpected problem has occurred.",
 * "instance": "https://api.weather.gov/requests/1ed872c0"
 * }
 *
 */