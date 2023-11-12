package com.appstract.fpex.weather.impl

import cats.effect.IO
import org.http4s.{Method, ParseResult, Request, Uri}
import com.appstract.fpex.weather.api.Msg_BackendAreaInfo


// We produce request-builder effects that could fail during parsing.
trait BackendRequestBuilder {
	private val POINTS_BASE_URL = "https://api.weather.gov/points/"

	// Build a request-effect (for the '/points' service) that should result in an AreaInfo JSON response from backend server.
	def buildAreaInfoGetRqIO(latLonTxt : String) : IO[Request[IO]] = {
		val fullUriTxt = POINTS_BASE_URL + latLonTxt
		buildGetRequestIO(fullUriTxt)
	}

	// Build a request-effect (for the '/gridpoints' service) that should result in an Forecast JSON response from backend server.
	def buildForecastRqIoFromAreaInfo(areaInfo : Msg_BackendAreaInfo) : IO[Request[IO]] = {
		// Extract forecastURL from AreaInfo.  Note that these scala field names match the expected Json field names.
		val forecastUrlTxt : String = areaInfo.properties.forecast
		// Build a new request using the forecastURL, or an error if something goes wrong, e.g. if the URL is bad.
		val forecastRqIO: IO[Request[IO]] = buildGetRequestIO(forecastUrlTxt)
		forecastRqIO
	}

	// Utility method to build HTTP Request for a simple GET query, keeping in mind that uri-parse might fail.
	private def buildGetRequestIO(uriTxt : String) : IO[Request[IO]] = {
		// "total" meaning "not partial", i.e. all failures are encompassed as variations of ParseResult.
		val totalUri: ParseResult[Uri] = Uri.fromString(uriTxt) //   type ParseResult[+A] = Either[ParseFailure, A]
		// Any ParseFailure will be absorbed into the IO instance, which short-circuits any downstream effects.
		val uriIO: IO[Uri] = IO.fromEither(totalUri)
		val requestIO: IO[Request[IO]] = uriIO.map(uri => Request[IO](Method.GET, uri))
		requestIO
	}
}