package com.appstract.fpex.extra

import cats.effect.IO
import com.appstract.fpex.weather.Msg_WeatherError
import munit.CatsEffectSuite
import org.http4s.{HttpRoutes, Method, Request, Response, Status, Uri}
import org.log4s
import org.log4s.Logger


class GreetingRouteSpec extends CatsEffectSuite {
	val myRoutes = new ExtraRoutes {}
	val USERNAME_01 = "gilligan"
	val OP_NM_GREET = myRoutes.OP_NAME_GREET
	val expectedMsgFname = JsonEnc_Greeting.FIELD_NAME_MSG_TXT // "messageTxt"

	test("Regular greetingRoutes returns status code 200") {
		val greetResponseIO = invokeGreetingRoute(USERNAME_01, false)
		assertIO(greetResponseIO.map(_.status), Status.Ok)
	}
	test("otherGreetingRoutes returns status code 200") {
		val greetResponseIO = invokeGreetingRoute(USERNAME_01, true)
		assertIO(greetResponseIO.map(_.status), Status.Ok)
	}
	test("Regular greetingRoutes returns expected greeting message") {
		val unm = USERNAME_01
		val greetResponseIO = invokeGreetingRoute(unm, false)
		val expectedRespTxt = mkExpectedResponseTxt(unm)
		assertIO(greetResponseIO.flatMap(_.as[String]), expectedRespTxt) //"{\"message\":\"Hello, world\"}")
	}
	test("otherGreetingRoutes returns expected greeting message") {
		val unm = USERNAME_01
		val greetResponseIO = invokeGreetingRoute(unm, true)
		val greetMsgIO: IO[String] = greetResponseIO.flatMap(_.as[String])
		val expectedRespTxt = mkExpectedResponseTxt(unm)
		assertIO(greetMsgIO, expectedRespTxt)
	}
	test("Always raiseError") {
		import cats.implicits._
		// Create an IO[unit] from an exception
		val boom: IO[Unit] = IO.raiseError(new Exception("boom"))
		val failedToMakeInt: IO[Int] = IO.raiseError[Int](new Exception("badNum"))
		/*

https://softwaremill.com/practical-guide-to-error-handling-in-scala-cats-and-cats-effect/

orElse
Replaces error regardless of its error type with the given value.  The provided value has to be wrapped into F and
can be a failure as well, so it can be used to replace an existing error with the given one.
def   orElse(other: ⇒ F[A])
		 */
		val x: IO[Int] = failedToMakeInt.orElse(IO.pure(-100))

// 		val zjjz = failedToMakeInt.redeem()



		val other: IO[Int] = IO.raiseError[Int](new Exception("badNum")).handleError(t => {
			-100
		})



		boom
		failedToMakeInt
	}

	private[this] def invokeGreetingRoute(unm : String, flg_useOther : Boolean) : IO[Response[IO]] = {
		// Builds the response-out effect for a simulated request-in message.
		val log: Logger = log4s.getLogger
		val greetUriPath = s"/${OP_NM_GREET}/${unm}"
		val greetUri = Uri.unsafeFromString(greetUriPath)
		log.info(s"GreetingRouteSpec.invokeGreetingRoute made greetUri: ${greetUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, greetUri) //  uri"/greetingForUser/world")
		val greetSupp: GreetingSupplier = GreetingSupplierSingleton.getImpl

		val route: HttpRoutes[IO] = if (flg_useOther) {
			myRoutes.otherGreetingRoutes(greetSupp)
		} else {
			myRoutes.greetingRoutes(greetSupp)
		}
		route.orNotFound(requestIO)
	}
	private[this] def mkExpectedResponseTxt(unm : String) : String = {
		val expectedMsgTxt = s"${GreetingSupplierSingleton.GREETING_TEXT_PREFIX}${unm}"
		// val expectedRespTxt_OLD  = s"{\"${expectedMsgFname}\":\"${expectedMsgTxt}\"}"
		val expectedRespTxt = "{" + doubleQuoted(expectedMsgFname) + ':' + doubleQuoted(expectedMsgTxt) + "}"
		expectedRespTxt
	}
	// method + in class Char is deprecated (since 2.13.0): Adding a number and a String is deprecated. Use the string interpolation `s"$num$str"`
	val DQ_CHAR = '"'
	// val DQ_STRING : String = '"'.toString
	private def doubleQuoted(txt : String) : String =  s"${DQ_CHAR}${txt}${DQ_CHAR}"

}
