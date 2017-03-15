/*
 * Copyright 2017 HM Revenue & Customs
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

package forms

import controllers.UnitSpec
import forms.SelectedSchemesForm.{ form => selectedSchemesForm }
import testkit.UnitWithAppSpec

class SelectedSchemesFormSpec extends UnitWithAppSpec {

  "Selected Schemes form" should {
    "be valid when required values are supplied" in {
       val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
         "eligible" -> "true"))
       form.hasErrors mustBe false
       form.hasGlobalErrors mustBe false
    }

    "be valid when multiple schemes are selected" in {
      val form = selectedSchemesForm.bind(Map(
        "scheme_0" -> "Finance",
        "scheme_1" -> "GovernmentEconomicsService",
        "scheme_2" -> "Commercial",
        "scheme_3" -> "DigitalAndTechnology",
        "scheme_4" -> "DiplomaticService",
        "scheme_5" -> "GovernmentOperationalResearchService",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
    }

    "be invalid when schemes are not supplied" in {
      val form = selectedSchemesForm.bind(Map("orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when invalid schemes are supplied" in {
      val form = selectedSchemesForm.bind(Map(
        "scheme_0" -> "InvalidScheme",
        "orderAgreed" -> "true",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when scheme order is not agreed by the candidate" in {
      val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "false",
        "eligible" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when eligibility criteria is not met by the candidate for selected scheme" in {
      val form = selectedSchemesForm.bind(Map("scheme_0" -> "Finance", "orderAgreed" -> "true",
        "eligible" -> "false"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }
  }
}
