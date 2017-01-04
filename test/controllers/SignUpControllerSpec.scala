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

package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import config.{ CSRCache, CSRHttp }
import connectors.ApplicationClient
import forms.SignupFormGenerator
import models.ApplicationRoute._
import models.SecurityUserExamples._
import models.{ CachedDataExample, CachedDataWithApp }
import org.mockito.Mockito._
import play.api.test.Helpers._
import security.UserService
import testkit.BaseControllerSpec

class SignUpControllerSpec extends BaseControllerSpec {

  override def currentCandidateWithApp: CachedDataWithApp = CachedDataWithApp(ActiveCandidate.user,
    CachedDataExample.InProgressInPreviewApplication.copy(userId = ActiveCandidate.user.userID))

  val continueAsSdipPanelId = "id=\"existingFSApply\""
  val applicationsClosedPanelId = "id=\"applicationsClosed\""
  val faststreamClosed = "Unfortunately, applications for the Civil Service Fast Stream are now closed."
  val faststreamEligible = "Are you eligible to apply for the Civil Service Fast Stream?"
  val edipClosed = "Unfortunately, applications for the Early Diversity Internship Programme are now closed."
  val edipEligible = "Are you eligible to apply for the Early Diversity Internship Programme (EDIP)?"
  val sdipClosed = "Unfortunately, applications for the Summer Diversity Internship Programme are now closed."
  val sdipEligible = "Are you eligible to apply for the Summer Diversity Internship Programme (SDIP)?"

  "present" should {
    "display the sign up page and allow new accounts to be created" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = true
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(continueAsSdipPanelId)
      content mustNot include(faststreamClosed)
      content mustNot include(edipClosed)
      content mustNot include(sdipClosed)
    }

    "display the sign up page but not allow new sdip accounts to be created when sdip is closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> defaultAppRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content mustNot include(continueAsSdipPanelId)
      content mustNot include(sdipEligible)
      content must include(faststreamEligible)
      content must include(sdipClosed)
      content must include(edipEligible)
    }

    "display the sign up page but not allow new edip accounts to be created when edip is closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> appRouteState, Sdip -> defaultAppRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(sdipEligible)
      content must include(faststreamEligible)
      content must include(edipClosed)
      content mustNot include(edipEligible)
    }

    "prevent any new accounts being created when all application routes are closed" in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> appRouteState, Sdip -> appRouteState)
      val result = controller(appRouteConfigMap).present()(fakeRequest)
      status(result) mustBe OK
      val content = contentAsString(result)
      content must include(applicationsClosedPanelId)
      content mustNot include("Create account")
    }
  }

  "sign up" should {
    "display fast stream applications closed message " in new TestFixture {
      val appRouteState = new ApplicationRouteState {
        val newAccountsStarted = true
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = true
        val applicationsStartDate = None
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val (data, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(appRouteConfigMap).signUp()(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present().url))
      flash(result).data must be (Map("warning" -> "Sorry, applications for the Civil Service Fast Stream are now closed"))
    }

    "display fast stream applications not started message" in new TestFixture {
      val appRouteState =  new ApplicationRouteState {
        val newAccountsStarted = false
        val newAccountsEnabled = false
        val applicationsSubmitEnabled = false
        val applicationsStartDate = Some(LocalDateTime.parse("2016-12-06T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      }
      val appRouteConfigMap = Map(Faststream -> appRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)
      val (data, signUpForm) = SignupFormGenerator().get
      val Request = fakeRequest.withFormUrlEncodedBody(signUpForm.data.toSeq:_*)
      val result = controller(appRouteConfigMap).signUp()(Request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(routes.SignUpController.present().url))
      flash(result).data must be (Map("warning" -> "Sorry, applications for the Civil Service Fast Stream are opened from 06 Dec 2016"))
    }
  }

  trait TestFixture {
    val mockApplicationClient = mock[ApplicationClient]
    val mockCacheClient = mock[CSRCache]
    val mockSecurityEnvironment = mock[security.SecurityEnvironment]
    val mockUserService = mock[UserService]
    val defaultAppRouteState = new ApplicationRouteState {
      val newAccountsStarted = true
      val newAccountsEnabled = true
      val applicationsSubmitEnabled = true
      val applicationsStartDate = None
    }

    val defaultAppRouteConfigMap = Map(Faststream -> defaultAppRouteState, Edip -> defaultAppRouteState, Sdip -> defaultAppRouteState)

    class TestableSignUpController(val testAppRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState])
      extends SignUpController(mockApplicationClient, mockCacheClient) with TestableSecureActions {
      val http: CSRHttp = CSRHttp
      override protected def env = mockSecurityEnvironment
      val appRouteConfigMap = testAppRouteConfigMap
      when(mockSecurityEnvironment.userService).thenReturn(mockUserService)
    }

    def controller(appRouteConfigMap: Map[ApplicationRoute, ApplicationRouteState]) = new TestableSignUpController(appRouteConfigMap)
  }
}
