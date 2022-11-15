package com.appstract.fpex.weather

import cats.effect.IO
import org.http4s.client.Client
import org.log4s
import org.log4s.Logger

class WeatherReportSupplierImpl(dataSrcCli: => Client[IO]) extends WeatherReportSupplier {

	private lazy val myLog: Logger = log4s.getLogger

	private lazy val myBFP : BackendForecastProvider = new BackendForecastProviderImpl(dataSrcCli)

	private lazy val myInterp : TemperatureInterpreter = new TempInterpImpl

	override def fetchWeatherForLatLon(latTxt : String, lonTxt : String) : IO[WReportOrErr] = {
		myLog.info(s"fetchWeatherForLatLon(lat=${latTxt}, lon=${lonTxt}")
		// TODO:  Validate input values
		val latLonPairTxt = s"${latTxt},${lonTxt}"
		fetchWeatherForLatLonPairTxt(latLonPairTxt)
	}

	// latLonTxt is in the comma separated lat-long format used by the backend weather service, e.g. "39.7456,-97.0892"
	override def fetchWeatherForLatLonPairTxt(latLonPairTxt : String) : IO[WReportOrErr] = {
		val forecastInfo: IO[Msg_BackendPeriodForecast] = myBFP.fetchForecastInfoForLatLonTxt(latLonPairTxt)
		val report: IO[Msg_WeatherReport] = forecastInfo.flatMap(buildWeatherReport(latLonPairTxt, _))
		reportToWROEIO(report, latLonPairTxt)
	}

	private def buildWeatherReport(latLonPairTxt : String, backendForecast : Msg_BackendPeriodForecast) : IO[Msg_WeatherReport]  = {
		myLog.info(s"buildWeatherReport using backendForecast : ${backendForecast}")
		val tempDesc = myInterp.describeTempFarenheit(backendForecast.temperature.toFloat, backendForecast.isDaytime)
		val report = Msg_WeatherReport(MTYPE_REPORT, latLonPairTxt, backendForecast.shortForecast, tempDesc)
		myLog.info(s"buildWeatherReport made report: ${report}")
		IO.pure(report)
	}

	// Translate any possible result from reportIO into a message (successful or error) we can output as JSON.
	// geoLoc contains a text description of the location; it is currently the same as a latLonPairTxt.
	private def reportToWROEIO(reportIO : IO[Msg_WeatherReport], geoLoc : String) : IO[WReportOrErr] = {
		reportIO.redeem(errorToLeftResult(geoLoc, _), reportToRightResult(_))
	}

	private def reportToRightResult(report : Msg_WeatherReport) : WReportOrErr = Right(report)

	private def errorToLeftResult(geoLoc : String, t : Throwable) : WReportOrErr = Left(exceptToWeatherErr(geoLoc, t))

	private def exceptToWeatherErr(geoLoc : String, t : Throwable) = {
		t match {
			case backendErr : BackendError => Msg_WeatherError(MTYPE_ERROR, geoLoc, "BACKEND_ERR", backendErr.asText)
			case other => Msg_WeatherError(MTYPE_ERROR, geoLoc, "OTHER_ERR", other.toString)
		}
	}

}
