package com.appstract.fpex.weather.api.backend

/***
 * We represent results from the backend service as a set of Scala case classes, which we populate from JSON using circe.
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
case class Msg_BackendAreaInfo(properties: Msg_BackendAreaProps)

// Props occurs nested inside the AreaInfo record, in JSON and here in Scala.
case class Msg_BackendAreaProps(forecast: String) // forecast contains a URL for backend "gridpoints" service.

// ForecastInfo types from backend weather service https://api.weather.gov/gridpoints/TOP/
// These field names match the json-field-names in one "period" block we expect to find in the backend json response.
case class Msg_BackendPeriodForecast(isDaytime: Boolean,
									temperature: Int, temperatureUnit: Char,
									shortForecast: String, detailedForecast: String)

trait BackendError {
	def asText : String = toString
}

// DataFetchError = Any error/failure we encounter attempting to interact with backend HTTP services
// (but excluding failures in interpreting backend responses - those fall under DataDecodeFailure below ).
case class BackendFetchError(opName: String, opArgs: String, exc: Throwable) extends BackendError

// DataDecodeFailure = Failures that occur during decoding+interpretation of backend data received.
// That data may be passed in the opArgs String (possibly truncated).
// TODO: Define a snazzy way to summarize the input data (Json) that we failed on.
case class BackendDecodeFailure(opName: String, opArgs: String, exc: Throwable) extends BackendError


