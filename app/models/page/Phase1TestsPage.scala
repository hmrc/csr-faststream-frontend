/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models.page

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder

case class Phase1TestsPage(
  expirationDate: DateTime,
  sjq: Option[CubiksTestPage],
  bq: Option[CubiksTestPage]
) extends DurationFormatter {

  def areStarted: Boolean = {
    sjq.exists(_.started) || bq.exists(_.started)
  }

  def allCompleted: Boolean = (sjq, bq) match {
    case (Some(anSjq), Some(aBq)) => anSjq.completed && aBq.completed
    case (Some(anSjq), None) => anSjq.completed
    case _ => false
  }

  def getDuration: String = durationFromNow(expirationDate)

  def getExpireDateTime: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder()
      .appendClockhourOfHalfday(1)
      .appendLiteral(":")
      .appendMinuteOfHour(2)
      .appendHalfdayOfDayText()
      .appendLiteral(" on ")
      .appendDayOfMonth(1)
      .appendLiteral(" ")
      .appendMonthOfYearText()
      .appendLiteral(" ")
      .appendYear(4, 4)
      .toFormatter

    dateTimeFormat.print(expirationDate)
  }

  def getExpireDate: String = {

    val dateTimeFormat = new DateTimeFormatterBuilder()
      .appendDayOfMonth(1)
      .appendLiteral(" ")
      .appendMonthOfYearText()
      .appendLiteral(" ")
      .appendYear(4, 4)
      .toFormatter

    dateTimeFormat.print(expirationDate)
  }
}

object Phase1TestsPage {

  def apply(profile: connectors.exchange.Phase1TestGroupWithNames): Phase1TestsPage = {
    Phase1TestsPage(expirationDate = profile.expirationDate,
      sjq = profile.activeTests.get("sjq").map(CubiksTestPage.apply),
      bq = profile.activeTests.get("bq").map(CubiksTestPage.apply)
    )
  }
}
