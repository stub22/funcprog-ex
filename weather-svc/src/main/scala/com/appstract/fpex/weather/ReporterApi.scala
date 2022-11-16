package com.appstract.fpex.weather

import cats.effect.IO
import io.circe.Encoder
import org.http4s.EntityEncoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.circe.jsonEncoderOf

/***
 * Our WeatherReport API is motivated by these initial requirements for our endpoint:
 * 1. Accepts latitude and longitude coordinates
 * 2. Returns the short forecast for that area for Today (“Partly Cloudy” etc)
 * 3. Returns a characterization of whether the temperature is “hot”, “cold”, or “moderate”
 */
// For each request, we return one of these two messages, which may be serialized as JSON to be sent to our end client.
// We include a copy of the latitude-longitude pair supplied by the client.
// The messageType field is included to help the client determine whether it has received a successful result or an error.
// Successful result message:
case class Msg_WeatherReport(messageType : String, latLonPairTxt : String, summary : String, temperatureDescription : String)
// Error diagnostic message:
case class Msg_WeatherError(messageType : String, latLonPairTxt : String, errorName : String, errorInfo : String)

object JsonEncoders_Report {
	// These encoders may be pulled into scope wherever our messages need to be encoded as JSON.

	// Derive json encoding automatically, based on fields of the Msg_WeatherReport.
	implicit val weatherReportEncoder: Encoder[Msg_WeatherReport] = deriveEncoder[Msg_WeatherReport]
	implicit val weatherReportEntityEncoder: EntityEncoder[IO, Msg_WeatherReport] = jsonEncoderOf

	// Derive json encoding automatically for the Msg_WeatherError.
	implicit val weatherErrorEncoder: Encoder[Msg_WeatherError] = deriveEncoder[Msg_WeatherError]
	implicit val weatherErrorEntityEncoder: EntityEncoder[IO, Msg_WeatherError] = jsonEncoderOf
}

// Our internal API for handing weather-report requests.
trait WeatherReportSupplier {
	// These two String tags are used in the messageType field.
	protected val MTYPE_REPORT : String = "WEATHER_REPORT"	// Indicates successful result to JSON client
	protected val MTYPE_ERROR : String = "WEATHER_ERROR"	// Indicates error result to JSON client

	// Our result type, following usual convention that Left is the failure type, Right is the success type.
	type WReportOrErr = Either[Msg_WeatherError, Msg_WeatherReport]

	def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr]

	def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr]

	// TODO:  Allow client to supply lat-lon location input in different shapes, e.g. as two Decimals or Floats.
	// TODO:  Provide appropriate validation of input location data.
}
