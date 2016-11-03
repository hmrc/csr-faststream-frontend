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

package connectors.exchange

import org.joda.time.DateTime
import play.api.libs.json.Json

case class Phase3Test(usedForResults: Boolean,
                      testUrl: String,
                      token: String,
                      invitationDate: DateTime,
                      startedDateTime: Option[DateTime] = None,
                      completedDateTime: Option[DateTime] = None) {
  def started = startedDateTime.isDefined
  def completed = completedDateTime.isDefined
}

object Phase3Test {
  implicit def phase3TestFormat = Json.format[Phase3Test]
}

case class Phase3TestGroup(expirationDate: DateTime, tests: List[Phase3Test]) {
  def activeTests = tests.filter(_.usedForResults)
}

object Phase3TestGroup {
  implicit val phase3TestGroupFormat = Json.format[Phase3TestGroup]
}
