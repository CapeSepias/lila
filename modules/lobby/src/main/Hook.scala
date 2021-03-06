package lila.lobby

import chess.{ Variant, Mode, Clock, Speed }
import org.joda.time.DateTime
import ornicar.scalalib.Random
import play.api.libs.json._

import actorApi.LobbyUser
import lila.game.PerfPicker
import lila.rating.RatingRange
import lila.user.{ User, Perfs }

case class Hook(
    id: String,
    uid: String, // owner socket uid
    sid: Option[String], // owner cookie (used to prevent multiple hooks)
    variant: Int,
    hasClock: Boolean,
    time: Option[Int],
    increment: Option[Int],
    daysPerTurn: Option[Int],
    mode: Int,
    allowAnon: Boolean,
    color: String,
    user: Option[LobbyUser],
    ratingRange: String,
    gameId: Option[String] = None,
    createdAt: DateTime) {

  def open = gameId.isEmpty
  def closed = !open

  def realColor = Color orDefault color

  def realVariant = Variant orDefault variant

  def realMode = Mode orDefault mode

  def memberOnly = !allowAnon

  def compatibleWith(h: Hook) =
    compatibilityProperties == h.compatibilityProperties &&
      (realColor compatibleWith h.realColor) &&
      (memberOnly || h.memberOnly).fold(isAuth && h.isAuth, true) &&
      ratingRangeCompatibleWith(h) && h.ratingRangeCompatibleWith(this)

  private def ratingRangeCompatibleWith(h: Hook) = realRatingRange.fold(true) {
    range => h.rating ?? range.contains
  }

  private def compatibilityProperties = (variant, time, increment, mode, daysPerTurn)

  lazy val realRatingRange: Option[RatingRange] = RatingRange noneIfDefault ratingRange

  def userId = user map (_.id)
  def isAuth = user.nonEmpty
  def username = user.fold(User.anonymous)(_.username)
  def rating = user flatMap { u => perfType map (_.key) flatMap u.ratingMap.get }
  def engine = user ?? (_.engine)

  def render: JsObject = Json.obj(
    "id" -> id,
    "uid" -> uid,
    "username" -> username,
    "rating" -> rating,
    "variant" -> realVariant.shortName,
    "mode" -> realMode.toString,
    "clock" -> clockOption.map(_.show),
    "time" -> clockOption.map(_.estimateTotalTime),
    "days" -> daysPerTurn,
    "speed" -> chess.Speed(clockOption).id,
    "color" -> chess.Color(color).??(_.name),
    "perf" -> Json.obj(
      "icon" -> perfType.map(_.iconChar.toString),
      "name" -> perfType.map(_.name))
  )

  lazy val perfType = PerfPicker.perfType(speed, realVariant, daysPerTurn)

  private lazy val clockOption = (time ifTrue hasClock) |@| increment apply Clock.apply

  private lazy val speed = Speed(clockOption)

  private def renderClock(time: Int, inc: Int) = "%d + %d".format(time / 60, inc)
}

object Hook {

  val idSize = 8

  def make(
    uid: String,
    variant: Variant,
    clock: Option[Clock],
    daysPerTurn: Option[Int],
    mode: Mode,
    allowAnon: Boolean,
    color: String,
    user: Option[User],
    sid: Option[String],
    ratingRange: RatingRange,
    blocking: Set[String]): Hook = new Hook(
    id = Random nextStringUppercase idSize,
    uid = uid,
    variant = variant.id,
    hasClock = clock.isDefined,
    time = clock map (_.limit),
    increment = clock map (_.increment),
    daysPerTurn = daysPerTurn,
    mode = mode.id,
    allowAnon = allowAnon || user.isEmpty,
    color = color,
    user = user map { LobbyUser.make(_, blocking) },
    sid = sid,
    ratingRange = ratingRange.toString,
    createdAt = DateTime.now)
}
