package com.appstract.fpex.weather.api.backend

import cats.data.EitherT
import cats.effect.IO
import org.http4s.Request

/***
 * Backend weather API docs:
 * https://www.weather.gov/documentation/services-web-api
 * See our project README.md for more background.
 * Also see quote from weather.gov docs at bottom of this file.
 *
 * Example URL for the "points" service, with lat,long at the end of the PATH (not as query params).
 * https://api.weather.gov/points/39.7456,-97.0892
 * That service returns JSON describing a chunk of grid area, including a URL for the "gridpoints" service.
 * Result fragment:   { "properties": { "forecast": "https://api.weather.gov/gridpoints/TOP/31,80/forecast"
 * Accessing the "gridpoints" service returns JSON containing an array of forecast "periods".
 * https://api.weather.gov/gridpoints/TOP/31,80/forecast =>
	{ "properties": {	 "periods": [   {
	 			"name": "Tonight",	...  "isDaytime": false,
                "temperature": 63, "temperatureUnit": "F",  ... "shortForecast": "Cloudy"
 * See more complete Json example at bottom of this source file.
 *
*/

object BackendEffectTypes {
	type BackendResult[MsgT] = Either[OurBackendError, MsgT]
	type BackendETIO[MsgT] = EitherT[IO, OurBackendError, MsgT]
}

// Internal API trait exposing useful features of the backend forecast service.
trait BackendForecastProvider {

	import BackendEffectTypes._

	// This "fetchAndExtractPeriodForecast" method is our primary method for accessing the backend.
	// SHORTCUT:  latLonTxt is in the comma separated lat-long format used by the backend weather service,
	// e.g. "39.7456,-97.0892".
	// The resulting effect will attempt to access BOTH of the backend services (in sequence) to produce a useful
	// forecast-period result.  This sequencing demonstrates functional composition of effects!
	// The returned IO may fail when it is run, in which case it should produce a BackendError.

	def fetchAndExtractPeriodForecast(latLonPairTxt : String) : BackendETIO[Msg_BackendPeriodForecast]

	// Expose ability to fetch just the AreaInfo, which is the results from the first stage '/points' service.
	// Useful for testing, and for other features we might reasonably add.
	// The returned IO may fail when it is run, in which case it should produce a BackendError.
	def fetchAreaInfo(areaRq : Request[IO]) : BackendETIO[Msg_BackendAreaInfo]
}

/***
 * Quoting from "Examples" tab at https://www.weather.gov/documentation/services-web-api
 * ===================================================================================
 * "How do I get the forecast?
 * Forecasts are divided into 2.5km grids.
 * Each NWS office is responsible for a section of the grid.
 * The API endpoint for the forecast at a specific grid is:
 * https://api.weather.gov/gridpoints/{office}/{grid X},{grid Y}/forecast
 * For example: https://api.weather.gov/gridpoints/TOP/31,80/forecast
 * If you do not know the grid that correlates to your location, you can use the /points endpoint to retrieve
 * the exact grid endpoint by coordinates:
 * https://api.weather.gov/points/{latitude},{longitude}
 * For example: https://api.weather.gov/points/39.7456,-97.0892
 * This will return the grid endpoint in the "forecast" property. Applications may cache the grid for a location
 * to improve latency and reduce the additional lookup request.
 * This endpoint also tells the application where to
 * find information for issuing office, observation stations, and zones."
 */