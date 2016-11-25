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

package forms

import forms.Mappings._
import models.ApplicationRoute
import models.view.CampaignReferrers
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.Constraints
import play.api.data.{ Form, FormError }
import play.api.i18n.Messages

object SignUpForm {
  val passwordField = "password"
  val confirmPasswordField = "confirmpwd"
  val fakePasswordField = "fake-password" // Used only in view (to prevent auto-fill)

  val passwordMinLength = 9
  val passwordMaxLength = 128

  val passwordFormatter = new Formatter[String] {
    // scalastyle:off cyclomatic.complexity
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val passwd = data("password").trim
      val confirm = data("confirmpwd").trim

      def formError(id: String) = Left(List(FormError("password", Messages(id))))

      (passwd, confirm) match {
        case (password, _) if password.length == 0 => formError("error.password.empty")
        case (password, _) if password.length < passwordMinLength => formError("error.password")
        case (password, _) if password.length > passwordMaxLength => formError("error.password")
        case (password, _) if "[A-Z]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, _) if "[a-z]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, _) if "[0-9]".r.findFirstIn(password).isEmpty => formError("error.password")
        case (password, confipw) if password != confipw => formError("error.password.dontmatch")
        case _ => Right(passwd)
      }
    }

    // scalastyle:on cyclomatic.complexity

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  val emailConfirm = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val email: Option[String] = data.get("email")
      val confirm: Option[String] = data.get("email_confirm")

      (email, confirm) match {
        case (Some(e), Some(v)) if e.toLowerCase == v.toLowerCase => Right(e)
        case _ => Left(List(
          FormError("email_confirm", Messages("error.emailconfirm.notmatch"))
        ))
      }

    }

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  val applicationRouteFormatter = new Formatter[String] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      data.get(key) match {
        case Some(appRoute) if appRoute.nonEmpty =>

          ApplicationRoute.withName(appRoute) match {
            case ApplicationRoute.Faststream => val fsEligable = data.getOrElse("faststreamEligible", "false").toBoolean
              if (fsEligable) { Right(appRoute) }
                else { Left(List(FormError("faststreamEligible", Messages("agree.faststreamEligible")))) }

            case ApplicationRoute.Edip => val edipEligable = data.getOrElse("edipEligible", "false").toBoolean
              if (edipEligable) { Right(appRoute) }
                else { Left(List(FormError("edipEligible", Messages("agree.edipEligible")))) }

            case ApplicationRoute.Sdip => sdipEligibiliyCheck(data)

            case unknown => Left(List(FormError("eligible", s"Unrecognised application route $unknown")))
          }

        case _ => Left(List(FormError("applicationRoute", Messages("error.appRoute"))))
      }
    }

    override def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
  }

  private def sdipEligibiliyCheck(postData: Map[String, String]): Either[Seq[FormError], String] = {
    val sdipEligable = postData.getOrElse("sdipEligible", "false").toBoolean
    val hasAppliedtoFaststream = postData.lift("hasAppliedToFaststream").map(_.toBoolean)

    val errors = (hasAppliedtoFaststream match {
                    case Some(true) => List(FormError("hasAppliedToFaststream", Messages("error.hasAppliedToFaststream")))
                    case Some(false) => Nil
                    case None => List(FormError("hasAppliedToFaststream", Messages("agree.hasAppliedToFaststream")))
                  }) ++
      (if (!sdipEligable) { List(FormError("sdipEligible", Messages("agree.sdipEligible"))) } else { Nil })

    if (errors.isEmpty) {
      Right(ApplicationRoute.Sdip)
    } else {
      Left(errors)
    }
  }

  def form = Form(
    mapping(
      "firstName" -> nonEmptyTrimmedText("error.firstName", 256),
      "lastName" -> nonEmptyTrimmedText("error.lastName", 256),
      "email" -> (email verifying Constraints.maxLength(128)),
      "email_confirm" -> of(emailConfirm),
      passwordField -> of(passwordFormatter),
      confirmPasswordField -> nonEmptyTrimmedText("error.confirmpwd", passwordMaxLength),
      "campaignReferrer" -> Mappings.optionalTrimmedText(64),
      "campaignOther" -> of(campaignOtherFormatter),
      "applicationRoute" -> of(applicationRouteFormatter),
      "agree" -> checked(Messages("agree.accept")),
      "faststreamEligible" -> boolean,
      "edipEligible" -> boolean,
      "sdipEligible" -> boolean,
      "hasAppliedToFaststream" -> optional(boolean)
    )(Data.apply)(Data.unapply)
  )

  def campaignOtherFormatter = new Formatter[Option[String]] {
    override def bind(key: String, request: Map[String, String]): Either[Seq[FormError], Option[String]] = {
      val optCampaignOther = request.get("campaignOther")
      if (request.hasOptionalInfoProvided) {
        optCampaignOther match {
          case Some(campaignOther) if campaignOther.trim.length > 256 => Left(List(FormError(key, Messages(s"error.$key.maxLength"))))
          case _ => Right(optCampaignOther.map(_.trim))
        }
      } else {
        Right(None)
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.map(_.trim).getOrElse(""))
  }

  implicit class RequestValidation(request: Map[String, String]) {
      def hasOptionalInfoProvided = CampaignReferrers.list.find(pair =>
        pair._1 == request.getOrElse("campaignReferrer", "")).exists(_._2)

      def sanitize = request.filterKeys {
        case "campaignOther" => hasOptionalInfoProvided
        case _ => true
      }
  }

  case class Data(
    firstName: String,
    lastName: String,
    email: String,
    confirmEmail: String,
    password: String,
    confirmpwd: String,
    campaignReferrer: Option[String],
    campaignOther: Option[String],
    applicationRoute: String,
    agree: Boolean,
    faststreamEligible: Boolean,
    edipEligible: Boolean,
    sdipEligible: Boolean,
    hasAppliedToFaststream: Option[Boolean]
  )
}
