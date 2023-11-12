package com.appstract.fpex.weather

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
 * We represent the backend results info with a set of Scala case classes, which we populate from JSON using circe.
 * Note that the field names in our Msg_* classes must match the JSON field names, wherever we want to use automatic decoding.
 * We include only the fields we are interested in in our case class.  Other fields in the json data are ignored.
 *
 * Msg_AreaInfo type holds result from backend weather service "points".
 *
 * The AreaInfo json contains a "properties" map, containing a "forecast" value, which is a URL for local forecast info.
 * We use this followup URL to GET from backend a large blob of Json containing multiple forecast periods.
 * We decode that Json to produce one or more Msg_BackendPeriodForecast instances.
 * Currently we assume that only the first period instance (which is the most-current forecast) is relevant.
 *
 * We name these types with the "Backend" prefix, to clearly distinguish them from our frontend Weather API types.
 */
// Msg_BackendAreaInfo is our structure matching the json entity returned from https://api.weather.gov/points/
case class Msg_BackendAreaInfo(properties : Msg_BackendAreaProps)
// Props occurs nested inside the AreaInfo record, in JSON and here in Scala.
case class Msg_BackendAreaProps(forecast : String)	// forecast is a URL for backend "gridpoints" service.

// ForecastInfo types from backend weather service https://api.weather.gov/gridpoints/TOP/
// These field names match the json-field-names in one "period" block in the backend json response.
case class Msg_BackendPeriodForecast(
		isDaytime : Boolean,
		temperature : Int, temperatureUnit : Char,
		shortForecast : String, detailedForecast : String)

// We post a backend error
// Because this error type extends Throwable, we are able to capture and map it easily with cats-effect IO.
// Note that this type does NOT need to be serialized to/from JSON.
case class BackendError(opName: String, opArgs: String, exc: Throwable) extends RuntimeException {
	// Because we extend RuntimeException, .toString does not have the usual Scala case class behavior.
	// So we define our own asText method to provide a useful String dump.
	def asText : String = s"BackendError(opName=${opName}, opArgs=${opArgs}, exc=${exc})"
}

// Internal API trait exposing useful features of the backend forecast service.
trait BackendForecastProvider {
	// This "fetchForecastInfoForLatLonTxt" method is our primary method for accessing the backend.
	// SHORTCUT:  latLonTxt is in the comma separated lat-long format used by the backend weather service,
	// e.g. "39.7456,-97.0892".
	// The resulting effect will attempt to access BOTH of the backend services (in sequence) to produce a useful
	// forecast-period result.  This sequencing demonstrates functional composition of effects!
	// The returned IO may fail when it is run, in which case it should produce a BackendError.
	def fetchForecastInfoForLatLonTxt (latLonPairTxt : String) : IO[Msg_BackendPeriodForecast]

	// Expose ability to fetch just the AreaInfo, which is the results from the first stage '/points' service.
	// Useful for testing, and for other features we might reasonably add.
	// The returned IO may fail when it is run, in which case it should produce a BackendError.
	def fetchAreaInfoOrError(areaRq : Request[IO]) : IO[Msg_BackendAreaInfo]
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