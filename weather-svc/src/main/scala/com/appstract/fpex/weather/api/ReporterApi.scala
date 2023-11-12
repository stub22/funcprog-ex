package com.appstract.fpex.weather.api

import cats.effect.IO

/*
Here is the internal API for the front end of our weather-svc.
This API is consumed by our web routes.

Our weather-svc API is motivated by these initial requirements:
1. Accepts latitude and longitude coordinates
2. Returns the short forecast for that area for Today (“Partly Cloudy” etc)
3. Returns a characterization of whether the temperature is “hot”, “cold”, or “moderate”
 */

// Result Messages:
// Each API query returns one of these two messages, which may be serialized as JSON to be sent to our end client.
// We include a copy of the latitude-longitude pair supplied by the client.
// The 'messageType' field is included to help the client determine whether it has received a success result or an error.

// Successful result message:
case class Msg_WeatherReport(messageType : String, latLonPairTxt : String, summary : String, temperatureDescription : String)
// Error diagnostic result message:
case class Msg_WeatherError(messageType : String, latLonPairTxt : String, errorName : String, errorInfo : String)

// Our internal API for handing weather-report requests.
trait WeatherReportSupplier {
	// These two String tags are used in the messageType field.
	protected val MTYPE_REPORT : String = "WEATHER_REPORT"	// Indicates successful result to JSON client
	protected val MTYPE_ERROR : String = "WEATHER_ERROR"	// Indicates error result to JSON client

	// Our result Either type, following usual Scala convention that Left is the FAILURE slot, Right is the SUCCESS slot.
	type WReportOrErr = Either[Msg_WeatherError, Msg_WeatherReport]

	// Use the input latLon pair to build an IO that produces a frontend weather result.
	// See project README.md for explanation of the latLon pair format.
	def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr]

	// Use the input lat,lon to build an IO that produces a frontend weather result.
	def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr]

	// TODO:  Allow client to supply lat-lon location input in different shapes, e.g. as two Decimals or Floats.
}
