package controllers

import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.mvc._

import lila.api.Context
import lila.app._
import lila.common.LilaCookie
import views._

object Lobby extends LilaController {

  def home = Open { implicit ctx =>
    renderHome(Results.Ok).map(_.withHeaders(
      CACHE_CONTROL -> "no-cache", PRAGMA -> "no-cache"
    ))
  }

  def handleStatus(req: RequestHeader, status: Results.Status): Fu[Result] =
    reqToCtx(req) flatMap { ctx => renderHome(status)(ctx) }

  def renderHome(status: Results.Status)(implicit ctx: Context): Fu[Result] =
    Env.current.preloader(
      posts = Env.forum.recent(ctx.me, Env.team.cached.teamIds),
      tours = Env.tournament promotable true,
      filter = Env.setup.filter
    ).map {
        case (preload, entries, posts, tours, featured, lead, tWinners, puzzle, reminds, streams, nbRounds) =>
          val response = status(html.lobby.home(
            Json stringify preload, entries, posts, tours, featured, lead, tWinners,
            puzzle, reminds, streams, Env.blog.lastPostCache.apply, nbRounds
          ))
          // the session cookie is required for anon lobby filter storage
          ctx.req.session.data.contains(LilaCookie.sessionId).fold(
            response,
            response withCookies LilaCookie.makeSessionId(ctx.req)
          )
      }

  def playing = Auth { implicit ctx =>
    me =>
      lila.game.GameRepo nowPlaying me map { povs =>
        html.lobby.playing(povs)
      }
  }

  def socket(apiVersion: Int) = Socket[JsValue] { implicit ctx =>
    get("sri") ?? { uid =>
      Env.lobby.socketHandler(uid = uid, user = ctx.me)
    }
  }

  def timeline = Auth { implicit ctx =>
    me =>
      Env.timeline.getter.userEntries(me.id) map { html.timeline.entries(_) }
  }

  def timelineMore = Auth { implicit ctx =>
    me =>
      Env.timeline.getter.moreUserEntries(me.id) map { html.timeline.more(_) }
  }
}
