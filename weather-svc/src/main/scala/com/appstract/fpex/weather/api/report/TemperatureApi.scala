package com.appstract.fpex.weather.api.report

object TempTypes {

}
object TempRangeNames {
	type TempRangeName = String
	val COLD: TempRangeName = "cold"
	val MODERATE: TempRangeName = "moderate"
	val HOT: TempRangeName = "hot"
}
trait TemperatureClassifier {
	import TempRangeNames.TempRangeName
	// Assign a descriptive label ("hot", "cold", "moderate") for a given temperature, occurring in either daytime or nightime.
	def findFahrenheitTempRange(tempF : Float, flg_isDaytime : Boolean) : TempRangeName

	// TODO: Consider adding Celsius API method.
	// Seems api.weather.gov returns Fahrenheit by default.  But does that vary by query location?
	// def findTempRangeCelsius(tempC : Float, flg_isDaytime : Boolean) : String
}