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

package controllers.forms

import forms.FastPassForm.{ form => fastPassForm, _ }
import org.scalatestplus.play.PlaySpec


class FastPassFormSpec extends PlaySpec {

  "FastPass form" should {
    "be valid when fast pass is not applicable" in {
      val form = fastPassForm.bind(Map("applicable" -> "false"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("false")
    }

    "be valid when fast pass is applicable and fast pass type is CivilServant" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "CivilServant"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("CivilServant"))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship and internship type is " in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship", "internshipTypes[0]" -> "EDIP"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("EDIP")))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear and " +
      "fast pass is not received" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship",
        "internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "false"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("SDIPCurrentYear")), Some(false))
    }

    "be valid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear, " +
      "fast pass is received and has a valid certificate number" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship",
        "internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "true", "certificateNumber" -> "1234567"))
      form.hasErrors mustBe false
      form.hasGlobalErrors mustBe false
      form.value.get mustBe Data("true", Some("DiversityInternship"), Some(Seq("SDIPCurrentYear")), Some(true), Some("1234567"))
    }
  }

  "FastPass form" should {
    "be invalid when fast pass is applicable and fast pass type is not supplied" in {
      val form = fastPassForm.bind(Map("applicable" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship and internship type is not supplied" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear " +
      "and fast pass received is not supplied" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship",
        "internshipTypes[0]" -> "SDIPCurrentYear"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }

    "be invalid when fast pass is applicable, fast pass type is DiversityInternship, internship type is SDIPCurrentYear, " +
      "fast pass is received and certificate number is not supplied" in {
      val form = fastPassForm.bind(Map("applicable" -> "true", "fastPassType" -> "DiversityInternship",
        "internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "true"))
      form.hasErrors mustBe true
      form.hasGlobalErrors mustBe false
    }
  }

  "cleanup fast pass params" should {
    "remove other fields when fast pass is not applicable" in {
      val data = Map("fastPassDetails.applicable" -> "false", "fastPassDetails.fastPassType" -> "DiversityInternship",
        "fastPassDetails.internshipTypes[0]" -> "SDIPCurrentYear")
      data.cleanupFastPassFields() mustBe Map("fastPassDetails.applicable" -> "false")
    }

    "remove internship types when fast pass type is CivilServant" in {
      val data = Map("fastPassDetails.applicable" -> "true", "fastPassDetails.fastPassType" -> "CivilServant",
        "fastPassDetails.internshipTypes[0]" -> "SDIPCurrentYear")
      data.cleanupFastPassFields() must contain theSameElementsAs
        Map("fastPassDetails.applicable" -> "true", "fastPassDetails.fastPassType" -> "CivilServant")
    }

    "remove certificate number when fast pass is not received" in {
      val data = Map("fastPassDetails.applicable" -> "true", "fastPassDetails.fastPassType" -> "DiversityInternship",
        "fastPassDetails.internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "false", "certificateNumber" -> "1234567")
      data.cleanupFastPassFields() must contain theSameElementsAs
        Map("fastPassDetails.applicable" -> "true", "fastPassDetails.fastPassType" -> "DiversityInternship",
          "fastPassDetails.internshipTypes[0]" -> "SDIPCurrentYear", "fastPassReceived" -> "false")
    }
  }

}