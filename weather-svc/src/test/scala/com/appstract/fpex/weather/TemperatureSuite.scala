package com.appstract.fpex.weather

import com.appstract.fpex.weather.api.TempRangeNames._
import com.appstract.fpex.weather.api.TemperatureInterpreter
import com.appstract.fpex.weather.impl.TempInterpImpl
import munit.CatsEffectSuite

class TemperatureSuite extends CatsEffectSuite {
	private lazy val myInterp : TemperatureInterpreter = new TempInterpImpl()

	test("check-some-temperature-descriptions") {
		checkTempDesc(39.1f, false,  Some(COLD))
		checkTempDesc(35.4f, true, Some(COLD))
		checkTempDesc(64.2f, false, Some(MODERATE))
		checkTempDesc(66.3f, true, Some(MODERATE))
		checkTempDesc(88.7f, false, Some(HOT))
		checkTempDesc(88.0f, false, Some(HOT))
		checkTempDesc(3000.1f, true, Some(HOT))
		checkTempDesc(-2000.9f, false, Some(COLD))

	}
	test("print-many-temps-without-validating") {
		val temps = 20 to 120 by 10
		temps.foreach(t => {
			checkTempDesc(t.toFloat, false, None)
			checkTempDesc(t.toFloat, true, None)
		})
	}
	private def checkTempDesc(tempF : Float, flg_daytime : Boolean, expectedDesc_opt : Option[TempRangeName]): Unit = {
		val desc =  myInterp.describeTempFahrenheit(tempF, flg_daytime)
		println(s"tempF=${tempF}, daytime=${flg_daytime}, output=${desc}, expected=${expectedDesc_opt}")
		expectedDesc_opt.foreach(exp => assertEquals(desc, exp))
	}
}
