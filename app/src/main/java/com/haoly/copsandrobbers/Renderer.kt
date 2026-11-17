package com.haoly.copsandrobbers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canvas 自绘渲染器：像素 sprite + 曼哈顿街区 + 夜间光圈 + HUD。
 * 全部用几何图元绘制，不依赖任何图片资源。
 */
class Renderer {

    private var vw = 0f
    private var vh = 0f
    private var scale = 40f
    private var camX = 0f
    private var camY = 0f
    private var u = 1f             // UI 单位

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFlat = Paint()
    private val pAlpha = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // sprite
    private val bmpCop = makeSprite(Cfg.SPR_COP)
    private val bmpThief = makeSprite(Cfg.SPR_THIEF)
    private val bmpLoot = makeSprite(Cfg.SPR_LOOT)
    private val bmpBox = makeSprite(Cfg.SPR_BOX)
    private val bmpItems = arrayOf(
        makeSprite(Cfg.SPR_BOOTS),
        makeSprite(Cfg.SPR_LAMP),
        makeSprite(Cfg.SPR_SIREN),
        makeSprite(Cfg.SPR_TRACKER)
    )

    // 光圈 / 夜幕
    private val bmpLight = makeLight()
    private val erasePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var dark: Bitmap? = null
    private var darkCanvas: Canvas? = null

    companion object {
        private const val DOWN = 2     // 夜幕离屏降采样倍率
    }

    init {
        erasePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        pFlat.isAntiAlias = false
        pFlat.isFilterBitmap = false
    }

    private fun makeSprite(rows: Array<String>): Bitmap {
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pp = Paint()
        for (y in 0..7) {
            val row = rows[y]
            for (x in 0 until row.length.coerceAtMost(8)) {
                val ch = row[x]
                if (ch == '.') continue
                pp.color = Cfg.spriteColor(ch)
                c.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, pp)
            }
        }
        return b
    }

    private fun makeLight(): Bitmap {
        val size = 256
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xF2FFFFFF.toInt(), 0x66FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(size / 2f, size / 2f, size / 2f, pp)
        return b
    }

    fun resize(w: Int, h: Int) {
        vw = w.toFloat()
        vh = h.toFloat()
        u = minOf(vw, vh) / 400f
        scale = minOf(vw / Cfg.VIEW_W, vh / Cfg.VIEW_H)
        val fitAll = minOf(vw / (Cfg.MAP_W + 1f), vh / (Cfg.MAP_H + 1f))
        if (scale < fitAll) scale = fitAll
        val dw = (w / DOWN).coerceAtLeast(1)
        val dh = (h / DOWN).coerceAtLeast(1)
        dark = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        darkCanvas = Canvas(dark!!)
    }

    // ------------------------------------------------------------------ 主绘制

    fun draw(canvas: Canvas, game: Game, input: InputController) {
        val viewWT = vw / scale
        val viewHT = vh / scale
        camX = if (viewWT >= Cfg.MAP_W) (Cfg.MAP_W - viewWT) / 2f
        else (game.player.x - viewWT / 2f).coerceIn(0f, Cfg.MAP_W - viewWT)
        camY = if (viewHT >= Cfg.MAP_H) (Cfg.MAP_H - viewHT) / 2f
        else (game.player.y - viewHT / 2f).coerceIn(0f, Cfg.MAP_H - viewHT)

        canvas.drawColor(Cfg.C_NIGHT)

        // 震屏
        var sx = 0f
        var sy = 0f
        if (game.shake > 0f) {
            val amp = game.shake * game.shake * 14f * u
            sx = (Math.random().toFloat() - 0.5f) * amp
            sy = (Math.random().toFloat() - 0.5f) * amp
        }

        canvas.save()
        canvas.translate(sx, sy)
        canvas.scale(scale, scale)
        canvas.translate(-camX, -camY)
        drawWorld(canvas, game, viewWT, viewHT)
        canvas.restore()

        drawDarkness(canvas, game, sx, sy)
        drawHud(canvas, game)
        drawTrackerArrows(canvas, game)
        drawControls(canvas, game, input)
        drawOverlay(canvas, game)

        if (game.flash > 0f) {
            pAlpha.color = Color.argb((game.flash * 90).toInt().coerceIn(0, 255), 0, 0, 0)
            pAlpha.color = game.flashColor
            pAlpha.alpha = (game.flash * 92).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, vw, vh, pAlpha)
            pAlpha.alpha = 255
        }
    }

    // ------------------------------------------------------------------ 世界

    private fun drawWorld(canvas: Canvas, game: Game, viewWT: Float, viewHT: Float) {
        val maze = game.maze
        val tx0 = floor(camX).toInt() - 1
        val ty0 = floor(camY).toInt() - 1
        val tx1 = (camX + viewWT).toInt() + 1
        val ty1 = (camY + viewHT).toInt() + 1

        // ---- 地面
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                val t = maze.tileAt(tx, ty)
                val x = tx.toFloat()
                val y = ty.toFloat()
                when (t) {
                    Cfg.T_PARK -> {
                        pFlat.color = if (((tx + ty) and 1) == 0) Cfg.C_PARK else Cfg.C_PARK2
                        canvas.drawRect(x, y, x + 1f, y + 1f, pFlat)
                    }
                    Cfg.T_LOT -> {
                        pFlat.color = Cfg.C_LOT
                        canvas.drawRect(x, y, x + 1f, y + 1f, pFlat)
                    }
                    Cfg.T_BUILDING -> {
                        pFlat.color = Cfg.C_ROAD
                        canvas.drawRect(x, y, x + 1f, y + 1f, pFlat)
                    }
                    else -> {
                        pFlat.color = if (((tx * 7 + ty * 13) and 1) == 0) Cfg.C_ROAD else Cfg.C_ROAD2
                        canvas.drawRect(x, y, x + 1f, y + 1f, pFlat)
                        drawRoadMark(canvas, tx, ty)
                    }
                }
            }
        }

        // ---- 街区装饰
        for (b in maze.blocks) {
            val bx = b.x0.toFloat()
            val by = b.y0.toFloat()
            if (bx + 2f < camX - 1f || by + 2f < camY - 1f) continue
            if (bx > camX + viewWT + 1f || by > camY + viewHT + 1f) continue
            when (b.kind) {
                Cfg.BLK_BUILDING -> drawBuilding(canvas, b)
                Cfg.BLK_PARK -> drawPark(canvas, b)
                Cfg.BLK_LOT -> drawLot(canvas, b)
            }
        }

        // ---- 路灯
        for (l in maze.lamps) {
            if (l.first < camX - 3f || l.first > camX + viewWT + 3f) continue
            if (l.second < camY - 3f || l.second > camY + viewHT + 3f) continue
            pAlpha.color = Color.argb(46, 255, 220, 140)
            canvas.drawCircle(l.first, l.second, Cfg.LAMP_R, pAlpha)
            pFlat.color = 0xFF3A3A4A.toInt()
            canvas.drawRect(l.first - 0.045f, l.second - 0.16f, l.first + 0.045f, l.second + 0.30f, pFlat)
            pFlat.color = Cfg.C_YELLOW
            canvas.drawRect(l.first - 0.13f, l.second - 0.34f, l.first + 0.13f, l.second - 0.16f, pFlat)
        }

        // ---- 逃跑出口
        val pulse = 0.5f + 0.5f * sin(game.elapsed * 3.2f)
        for (e in maze.escapes) {
            pAlpha.color = Color.argb((40 + pulse * 60).toInt(), 255, 80, 120)
            canvas.drawCircle(e.first, e.second, 0.58f + pulse * 0.16f, pAlpha)
            pStroke.color = Color.argb(180, 255, 90, 130)
            pStroke.strokeWidth = 0.06f
            canvas.drawCircle(e.first, e.second, 0.72f, pStroke)
        }

        // ---- 脚印
        for (f in game.feet) {
            val a = (f.life / Cfg.FOOT_LIFE).coerceIn(0f, 1f)
            pFlat.color = Color.argb((a * 150).toInt(), 255, 150, 60)
            canvas.drawOval(RectF(f.x - 0.11f, f.y - 0.06f, f.x + 0.11f, f.y + 0.06f), pFlat)
        }

        // ---- 赃物
        val bob = sin(game.elapsed * 3f) * 0.06f
        for (l in game.loots) {
            if (l.taken || l.secured) continue
            pAlpha.color = Color.argb(70, 255, 210, 90)
            canvas.drawCircle(l.x, l.y + bob, 0.62f, pAlpha)
            drawSprite(canvas, bmpLoot, l.x, l.y + bob, 0.80f)
        }

        // ---- 道具箱
        for (pk in game.pickups) {
            drawSprite(canvas, bmpBox, pk.x, pk.y + bob * 0.6f, 0.78f)
            drawSprite(canvas, bmpItems[pk.kind], pk.x, pk.y + bob * 0.6f - 0.04f, 0.42f)
        }

        // ---- 小偷
        for (t in game.thieves) {
            if (!t.alive) continue
            val vis = game.visibility(t)
            if (vis == 0) continue
            val swing = sin(t.walkPhase) * 0.05f
            if (vis == 2) pFlat.alpha = 110
            else pFlat.alpha = 255
            drawSprite(canvas, bmpThief, t.x, t.y + swing, 0.86f)
            if (t.carrying) {
                pFlat.color = Cfg.C_GOLD
                canvas.drawRect(t.x - 0.13f, t.y - 0.60f, t.x + 0.13f, t.y - 0.34f, pFlat)
            }
            if (t.panic > 0f) {
                pFlat.color = Cfg.C_RED
                canvas.drawRect(t.x - 0.05f, t.y - 0.86f, t.x + 0.05f, t.y - 0.62f, pFlat)
                canvas.drawRect(t.x - 0.05f, t.y - 0.56f, t.x + 0.05f, t.y - 0.50f, pFlat)
            }
            pFlat.alpha = 255
        }

        // ---- 警察
        val swing = sin(game.player.walkPhase) * 0.05f
        if (game.player.dashT > 0f) {
            pAlpha.color = Color.argb(110, 80, 160, 255)
            canvas.drawCircle(game.player.x, game.player.y, 0.62f, pAlpha)
        }
        drawSprite(canvas, bmpCop, game.player.x, game.player.y + swing, 0.94f)
        // 手电光锥
        drawTorch(canvas, game)

        // ---- 粒子
        for (pf in game.puffs) {
            val a = (pf.life / 0.6f).coerceIn(0f, 1f)
            pFlat.color = pf.color
            pFlat.alpha = (a * 230).toInt()
            val r = pf.size
            canvas.drawRect(pf.x - r, pf.y - r, pf.x + r, pf.y + r, pFlat)
        }
        pFlat.alpha = 255

        // ---- 飘字
        for (ft in game.texts) {
            val a = (ft.life / 1.3f).coerceIn(0f, 1f)
            pText.color = ft.color
            pText.alpha = (a * 255).toInt()
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 0.52f
            canvas.drawText(ft.text, ft.x, ft.y - 0.5f, pText)
        }
        pText.alpha = 255
    }

    private fun drawRoadMark(canvas: Canvas, tx: Int, ty: Int) {
        val onCol = tx % 3 == 0
        val onRow = ty % 3 == 0
        pFlat.color = Cfg.C_LINE
        if (onCol && !onRow) {
            if (ty % 2 == 0) canvas.drawRect(tx + 0.46f, ty + 0.20f, tx + 0.54f, ty + 0.80f, pFlat)
        } else if (onRow && !onCol) {
            if (tx % 2 == 0) canvas.drawRect(tx + 0.20f, ty + 0.46f, tx + 0.80f, ty + 0.54f, pFlat)
        }
    }

    private fun drawBuilding(canvas: Canvas, b: Maze.Block) {
        val x = b.x0.toFloat()
        val y = b.y0.toFloat()
        // 投影
        pFlat.color = 0x66000000
        canvas.drawRect(x + 0.10f, y + 0.18f, x + 2f, y + 2.18f, pFlat)
        // 墙体
        pFlat.color = Cfg.WALL_COLORS[b.wall]
        canvas.drawRect(x, y, x + 2f, y + 2f, pFlat)
        // 屋顶
        pFlat.color = Cfg.ROOF_COLORS[b.roof]
        canvas.drawRect(x - 0.05f, y - 0.05f, x + 2.05f, y + 0.44f, pFlat)
        // 屋檐高光
        pFlat.color = 0x22000000
        canvas.drawRect(x - 0.05f, y + 0.44f, x + 2.05f, y + 0.52f, pFlat)
        // 窗户 2x2
        for (i in 0..3) {
            val bit = (b.windows shr i) and 1
            if (bit == 0) continue
            val wx = x + 0.34f + (i % 2) * 0.98f
            val wy = y + 0.66f + (i / 2) * 0.70f
            val lit = ((b.litMask shr i) and 1) == 1
            pFlat.color = if (lit) 0xFFFFD98A.toInt() else 0xFF20202C.toInt()
            canvas.drawRect(wx, wy, wx + 0.62f, wy + 0.48f, pFlat)
            pFlat.color = 0x33000000
            canvas.drawRect(wx, wy + 0.21f, wx + 0.62f, wy + 0.25f, pFlat)
        }
        // 门
        pFlat.color = 0xFF2A1A12.toInt()
        canvas.drawRect(x + 0.80f, y + 1.62f, x + 1.20f, y + 2f, pFlat)
    }

    private fun drawPark(canvas: Canvas, b: Maze.Block) {
        val x = b.x0.toFloat()
        val y = b.y0.toFloat()
        for (i in 0..2) {
            val r = b.treeSeed % (i + 3)
            val tx = x + 0.3f + (r % 2) * 1.1f + i * 0.22f
            val ty = y + 0.35f + ((r / 2) % 2) * 1.05f
            pFlat.color = 0xFF3A2A18.toInt()
            canvas.drawRect(tx - 0.05f, ty + 0.10f, tx + 0.05f, ty + 0.42f, pFlat)
            p.color = if (i % 2 == 0) 0xFF1E6B32.toInt() else 0xFF175526.toInt()
            canvas.drawCircle(tx, ty, 0.34f, p)
            p.color = 0x33000000
            canvas.drawCircle(tx + 0.10f, ty + 0.10f, 0.30f, p)
        }
    }

    private fun drawLot(canvas: Canvas, b: Maze.Block) {
        val x = b.x0.toFloat()
        val y = b.y0.toFloat()
        // 车位线
        pFlat.color = 0xFF3A3A48.toInt()
        for (i in 0..1) {
            val cy = y + 0.52f + i * 1.0f
            canvas.drawRect(x + 0.12f, cy, x + 1.88f, cy + 0.05f, pFlat)
        }
        // 停着的车
        val horizontal = b.carDir == 0
        pFlat.color = Cfg.CAR_COLORS[b.carColor]
        if (horizontal) {
            canvas.drawRect(x + 0.28f, y + 0.62f, x + 1.72f, y + 1.30f, pFlat)
            pFlat.color = 0x66202030.toInt()
            canvas.drawRect(x + 0.52f, y + 0.74f, x + 1.48f, y + 1.16f, pFlat)
        } else {
            canvas.drawRect(x + 0.62f, y + 0.22f, x + 1.30f, y + 1.86f, pFlat)
            pFlat.color = 0x66202030.toInt()
            canvas.drawRect(x + 0.74f, y + 0.52f, x + 1.18f, y + 1.44f, pFlat)
        }
    }

    private fun drawTorch(canvas: Canvas, game: Game) {
        val pl = game.player
        val len = if (pl.lamp > 0f) 3.4f else 2.2f
        val half = 0.30f
        val dx = pl.fx
        val dy = pl.fy
        val px = -dy
        val py = dx
        pAlpha.shader = LinearGradient(
            pl.x, pl.y, pl.x + dx * len, pl.y + dy * len,
            Color.argb(70, 255, 240, 180), Color.argb(0, 255, 240, 180),
            Shader.TileMode.CLAMP
        )
        val path = android.graphics.Path()
        path.moveTo(pl.x + px * half, pl.y + py * half)
        path.lineTo(pl.x + dx * len + px * half * 3.2f, pl.y + dy * len + py * half * 3.2f)
        path.lineTo(pl.x + dx * len - px * half * 3.2f, pl.y + dy * len - py * half * 3.2f)
        path.lineTo(pl.x - px * half, pl.y - py * half)
        path.close()
        canvas.drawPath(path, pAlpha)
        pAlpha.shader = null
    }

    private fun drawSprite(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, size: Float) {
        val h = size
        val top = cy - h * 0.92f
        canvas.drawBitmap(bmp, null, RectF(cx - size / 2f, top, cx + size / 2f, top + h), pFlat)
    }

    // ------------------------------------------------------------------ 夜幕

    private fun drawDarkness(canvas: Canvas, game: Game, shakeX: Float, shakeY: Float) {
        val d = dark ?: return
        val dc = darkCanvas ?: return
        dc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        p.color = Color.argb(Cfg.DARK_ALPHA, 4, 5, 14)
        dc.drawRect(0f, 0f, d.width.toFloat(), d.height.toFloat(), p)

        val k = scale / DOWN
        // 路灯洞
        for (l in game.maze.lamps) {
            hole(dc, l.first, l.second, Cfg.LAMP_R * 1.05f, k, shakeX, shakeY)
        }
        // 玩家光圈
        hole(dc, game.player.x, game.player.y, game.player.lightRadius(), k, shakeX, shakeY)
        // 探照灯外围柔光
        if (game.player.lamp > 0f) {
            hole(dc, game.player.x, game.player.y, Cfg.LIGHT_R_LAMP * 1.35f, k, shakeX, shakeY)
        }

        canvas.drawBitmap(d, null, Rect(0, 0, vw.toInt(), vh.toInt()), null)
    }

    private fun hole(dc: Canvas, wx: Float, wy: Float, r: Float, k: Float, sx: Float, sy: Float) {
        val cx = ((wx - camX) * scale + sx) / DOWN
        val cy = ((wy - camY) * scale + sy) / DOWN
        val rr = r * k
        if (cx + rr < 0 || cy + rr < 0 || cx - rr > dc.width || cy - rr > dc.height) return
        dc.drawBitmap(bmpLight, null, RectF(cx - rr, cy - rr, cx + rr, cy + rr), erasePaint)
    }

    // ------------------------------------------------------------------ HUD

    private fun drawHud(canvas: Canvas, game: Game) {
        val pad = 12f * u
        val barH = 30f * u
        p.color = Color.argb(150, 6, 8, 18)
        canvas.drawRect(0f, 0f, vw, barH + pad * 1.6f, p)
        p.color = Color.argb(90, 80, 140, 255)
        canvas.drawRect(0f, barH + pad * 1.6f, vw, barH + pad * 1.6f + 1.5f * u, p)

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 15f * u
        pText.color = Cfg.C_CYAN
        canvas.drawText("LV %02d".format(game.level), pad, barH * 0.62f + pad * 0.3f, pText)

        // 分数
        pText.textAlign = Paint.Align.RIGHT
        pText.color = Cfg.C_YELLOW
        canvas.drawText("SCORE %06d".format(game.score), vw - pad, barH * 0.62f + pad * 0.3f, pText)

        // 时间条
        val barW = vw * 0.42f
        val barX = (vw - barW) / 2f
        val barY = barH * 0.30f + pad * 0.2f
        p.color = Color.argb(120, 30, 30, 46)
        canvas.drawRoundRect(RectF(barX, barY, barX + barW, barY + 11f * u), 4f * u, 4f * u, p)
        val ratio = (game.timeLeft / Cfg.START_TIME).coerceIn(0f, 1f)
        val urgent = game.timeLeft < 15f
        p.color = if (urgent && (game.elapsed * 6).toInt() % 2 == 0) Cfg.C_RED
        else if (urgent) Cfg.C_ORANGE else Cfg.C_GREEN
        canvas.drawRoundRect(RectF(barX, barY, barX + barW * ratio, barY + 11f * u), 4f * u, 4f * u, p)
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 11f * u
        pText.color = Cfg.C_WHITE
        canvas.drawText("%ds".format(game.timeLeft.toInt()), vw / 2f, barY + 26f * u, pText)

        // 第二行：剩余小偷 / 逃脱 / 道具
        val y2 = barH + pad * 1.6f + 18f * u
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 12f * u
        pText.color = Cfg.C_PURPLE
        canvas.drawText("小偷", pad, y2, pText)
        var ix = pad + 32f * u
        for (i in 0 until game.totalThieves) {
            val alive = i < game.thieves.size && game.thieves[i].alive
            pFlat.color = if (alive) Cfg.C_THIEF else 0xFF3A3A46.toInt()
            canvas.drawRect(ix, y2 - 9f * u, ix + 9f * u, y2, pFlat)
            ix += 12f * u
        }

        // 逃脱槽
        pText.color = Cfg.C_RED
        canvas.drawText("放跑", ix + 8f * u, y2, pText)
        var jx = ix + 40f * u
        for (i in 0 until Cfg.MAX_ESCAPES) {
            pFlat.color = if (i < game.escapes) Cfg.C_RED else 0xFF3A3A46.toInt()
            canvas.drawRect(jx, y2 - 9f * u, jx + 9f * u, y2, pFlat)
            jx += 12f * u
        }

        // 道具槽
        val slot = 30f * u
        val sx0 = vw - pad - slot
        val sy0 = y2 - slot + 2f * u
        p.color = Color.argb(120, 20, 24, 40)
        canvas.drawRoundRect(RectF(sx0, sy0, sx0 + slot, sy0 + slot), 5f * u, 5f * u, p)
        pStroke.color = Color.argb(150, 90, 200, 255)
        pStroke.strokeWidth = 1.4f * u
        canvas.drawRoundRect(RectF(sx0, sy0, sx0 + slot, sy0 + slot), 5f * u, 5f * u, pStroke)
        if (game.heldItem >= 0) {
            canvas.drawBitmap(
                bmpItems[game.heldItem], null,
                RectF(sx0 + 4f * u, sy0 + 4f * u, sx0 + slot - 4f * u, sy0 + slot - 4f * u), pFlat
            )
        }
        pText.textAlign = Paint.Align.RIGHT
        pText.textSize = 10f * u
        pText.color = Cfg.C_WHITE
        canvas.drawText(if (game.heldItem >= 0) Cfg.ITEM_NAMES[game.heldItem] else "空", sx0 - 6f * u, y2, pText)

        // 提示条
        if (game.hintT > 0f) {
            val a = (game.hintT / 0.6f).coerceIn(0f, 1f)
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 16f * u
            pText.color = game.flashColor
            pText.alpha = (a * 255).toInt()
            canvas.drawText(game.hintText, vw / 2f, vh * 0.30f, pText)
            pText.alpha = 255
        }
    }

    /** 追踪器：屏幕外小偷的方向指示箭头 */
    private fun drawTrackerArrows(canvas: Canvas, game: Game) {
        if (game.player.tracker <= 0f) return
        val margin = 26f * u
        val bob = sin(game.elapsed * 5f) * 3f * u
        for (t in game.thieves) {
            if (!t.alive) continue
            var sx = (t.x - camX) * scale
            var sy = (t.y - camY) * scale
            val inside = sx in margin..(vw - margin) && sy in margin..(vh - margin)
            if (inside && game.visibility(t) == 1) continue
            sx = sx.coerceIn(margin, vw - margin)
            sy = sy.coerceIn(margin, vh - margin)
            val ang = Math.atan2((t.y - game.player.y).toDouble(), (t.x - game.player.x).toDouble())
            canvas.save()
            canvas.translate(sx, sy + bob)
            canvas.rotate(Math.toDegrees(ang).toFloat())
            pFlat.color = Cfg.C_PURPLE
            pFlat.alpha = 200
            val r = 8f * u
            val path = android.graphics.Path()
            path.moveTo(r, 0f)
            path.lineTo(-r * 0.7f, r * 0.7f)
            path.lineTo(-r * 0.7f, -r * 0.7f)
            path.close()
            canvas.drawPath(path, pFlat)
            pFlat.alpha = 255
            canvas.restore()
        }
    }

    // ------------------------------------------------------------------ 控件

    private fun drawControls(canvas: Canvas, game: Game, input: InputController) {
        if (game.state != Game.State.PLAY) return

        // 摇杆
        if (input.joyActive) {
            val cx = input.joyCx
            val cy = input.joyCy
            val r = input.joyR
            p.color = Color.argb(46, 255, 255, 255)
            canvas.drawCircle(cx, cy, r, p)
            pStroke.color = Color.argb(90, 255, 255, 255)
            pStroke.strokeWidth = 2f * u
            canvas.drawCircle(cx, cy, r, pStroke)
            p.color = Color.argb(140, 120, 190, 255)
            canvas.drawCircle(input.joyKx, input.joyKy, r * 0.42f, p)
        } else {
            val cx = input.joyR + 14f * u
            val cy = vh - input.joyR - 16f * u
            p.color = Color.argb(22, 255, 255, 255)
            canvas.drawCircle(cx, cy, input.joyR, p)
            pStroke.color = Color.argb(52, 255, 255, 255)
            pStroke.strokeWidth = 1.6f * u
            canvas.drawCircle(cx, cy, input.joyR, pStroke)
            p.color = Color.argb(70, 255, 255, 255)
            canvas.drawCircle(cx, cy, input.joyR * 0.42f, p)
        }

        // 按钮
        drawButton(canvas, input.dashCx, input.dashCy, input.btnR,
            Color.argb(input.dashHold.let { if (it) 170 else 90 }, 255, 140, 40),
            "DASH", if (game.player.dashCd > 0f) (1f - game.player.dashCd / Cfg.DASH_CD) else 1f)
        drawButton(canvas, input.itemCx, input.itemCy, input.btnR,
            Color.argb(input.itemHold.let { if (it) 170 else 90 }, 80, 230, 220),
            "ITEM", if (game.heldItem >= 0) 1f else 0.35f)
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, label: String, fill: Float) {
        p.color = Color.argb(40, 10, 12, 24)
        canvas.drawCircle(cx, cy, r * 1.12f, p)
        p.color = color
        canvas.drawCircle(cx, cy, r, p)
        // 冷却环
        pStroke.color = Color.argb(200, 255, 255, 255)
        pStroke.strokeWidth = 2.4f * u
        val sweep = -360f * fill.coerceIn(0f, 1f)
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, sweep, false, pStroke)
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = r * 0.34f
        pText.color = Cfg.C_WHITE
        canvas.drawText(label, cx, cy + r * 0.13f, pText)
        pText.textSize = r * 0.26f
        pText.color = Color.argb(170, 255, 255, 255)
        canvas.drawText(if (label == "DASH") "冲刺" else "道具", cx, cy + r * 0.48f, pText)
    }

    // ------------------------------------------------------------------ 覆盖界面

    private fun drawOverlay(canvas: Canvas, game: Game) {
        if (game.state == Game.State.PLAY) return

        p.color = Color.argb(196, 4, 6, 14)
        canvas.drawRect(0f, 0f, vw, vh, p)

        val cx = vw / 2f
        pText.textAlign = Paint.Align.CENTER
        val blink = ((game.elapsed * 2).toInt() % 2 == 0)

        when (game.state) {
            Game.State.READY -> {
                bigText(canvas, cx, vh * 0.26f, "警察抓小偷", 34f * u, Cfg.C_YELLOW)
                pText.textSize = 15f * u
                pText.color = Cfg.C_GREEN
                canvas.drawText("C O P S   &   R O B B E R S", cx, vh * 0.32f, pText)
                pText.textSize = 12f * u
                pText.color = Color.argb(190, 230, 230, 240)
                canvas.drawText("Apple II 经典街区追逐 · 安卓重制版", cx, vh * 0.365f, pText)

                // 装饰：警察追小偷
                canvas.drawBitmap(bmpCop, null,
                    RectF(cx - 62f * u, vh * 0.42f, cx - 62f * u + 34f * u, vh * 0.42f + 34f * u), pFlat)
                canvas.drawBitmap(bmpThief, null,
                    RectF(cx + 26f * u, vh * 0.42f, cx + 26f * u + 34f * u, vh * 0.42f + 34f * u), pFlat)
                pText.textSize = 16f * u
                pText.color = Cfg.C_ORANGE
                canvas.drawText("▶ ▶ ▶", cx, vh * 0.42f + 22f * u, pText)

                val lines = arrayOf(
                    "左下摇杆移动　右下 DASH 冲刺扑抓",
                    "ITEM 使用道具：加速靴 / 探照灯 / 警笛 / 追踪器",
                    "小偷得手后会跑向城边红色出口，别放跑 3 个",
                    "抢回金色赃物 +60　连续抓捕有连击加成"
                )
                pText.textSize = 12.5f * u
                pText.color = Color.argb(215, 220, 226, 240)
                var ly = vh * 0.56f
                for (l in lines) {
                    canvas.drawText(l, cx, ly, pText)
                    ly += 19f * u
                }

                if (blink) {
                    pText.textSize = 15f * u
                    pText.color = Cfg.C_CYAN
                    canvas.drawText("点击屏幕开始追捕", cx, vh * 0.82f, pText)
                }
            }
            Game.State.PAUSED -> {
                bigText(canvas, cx, vh * 0.44f, "已暂停", 30f * u, Cfg.C_CYAN)
                pText.textSize = 14f * u
                pText.color = Cfg.C_WHITE
                canvas.drawText("点击屏幕继续", cx, vh * 0.53f, pText)
            }
            Game.State.LEVEL_CLEAR -> {
                bigText(canvas, cx, vh * 0.30f, "第 ${game.level} 关完成！", 30f * u, Cfg.C_GREEN)
                pText.textSize = 14f * u
                pText.color = Cfg.C_WHITE
                canvas.drawText("本关抓捕 ${game.caughtThisLevel} 人　放跑 ${game.escapes} 人", cx, vh * 0.40f, pText)
                canvas.drawText("总分 ${game.score}", cx, vh * 0.45f, pText)
                if (blink) {
                    pText.color = Cfg.C_YELLOW
                    canvas.drawText("点击进入第 ${game.level + 1} 关", cx, vh * 0.58f, pText)
                }
            }
            Game.State.GAME_OVER -> {
                bigText(canvas, cx, vh * 0.32f, "GAME OVER", 32f * u, Cfg.C_RED)
                pText.textSize = 14f * u
                pText.color = Cfg.C_ORANGE
                canvas.drawText(game.overReason, cx, vh * 0.40f, pText)
                pText.color = Cfg.C_WHITE
                canvas.drawText("到达第 ${game.level} 关　总分 ${game.score}", cx, vh * 0.46f, pText)
                if (blink) {
                    pText.color = Cfg.C_CYAN
                    canvas.drawText("点击重新开始", cx, vh * 0.60f, pText)
                }
            }
            Game.State.PLAY -> {}
        }
    }

    private fun bigText(canvas: Canvas, x: Float, y: Float, text: String, size: Float, color: Int) {
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = size
        pText.color = Color.BLACK
        pStroke.color = Color.BLACK
        pStroke.strokeWidth = size * 0.22f
        pStroke.textSize = size
        pStroke.textAlign = Paint.Align.CENTER
        pStroke.typeface = Typeface.MONOSPACE
        canvas.drawText(text, x, y, pStroke)
        pText.color = color
        canvas.drawText(text, x, y, pText)
    }

    /** 屏幕坐标 -> 世界 tile（用于调试/扩展） */
    fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> =
        Pair(sx / scale + camX, sy / scale + camY)

    fun currentScale(): Float = scale
    fun viewSize(): Pair<Float, Float> = Pair(vw, vh)
    fun camera(): Pair<Float, Float> = Pair(camX, camY)
}
