package com.haoly.copsandrobbers

import java.util.Random
import kotlin.math.sqrt

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
}

/** 玩家（警察） */
class Player {
    var x = 0f
    var y = 0f
    var fx = 0f          // 朝向
    var fy = 1f
    var dashT = 0f
    var dashCd = 0f
    var boots = 0f
    var lamp = 0f
    var tracker = 0f
    var walkPhase = 0f

    fun speed(): Float = Cfg.P_SPEED * if (boots > 0f) 1.62f else 1f
    fun lightRadius(): Float = if (lamp > 0f) Cfg.LIGHT_R_LAMP else Cfg.LIGHT_R
}

/** 小偷 */
class Thief(var x: Float, var y: Float, val baseSpeed: Float) {
    var alive = true
    var caught = false
    var escaped = false
    var carrying = false        // 是否带着赃物
    var panic = 0f
    var path = IntArray(0)
    var pathIdx = 0
    var repathT = 0f
    var footT = 0f
    var fx = 0f
    var fy = 1f
    var wanderX = 0f
    var wanderY = 0f
    var wanderT = 0f
    var walkPhase = 0f
}

class Loot(val x: Float, val y: Float) {
    var taken = false           // 被小偷拿走
    var secured = false         // 被警察回收
}

class Pickup(val x: Float, val y: Float, val kind: Int) {
    var taken = false
}

class Footprint(var x: Float, var y: Float, var life: Float, val ang: Float)
class Puff(var x: Float, var y: Float, var life: Float, val color: Int, val size: Float)
class FloatText(var x: Float, var y: Float, var life: Float, val text: String, val color: Int)

/**
 * 游戏主体：关卡状态机 + 追逐 AI + 计分规则。
 * 内核沿用 Apple II 原版「街区里追小偷，撞上就抓住」，
 * 外面加了关卡递进、赃物、道具、夜间视野和脚印追踪。
 */
class Game {

    enum class State { READY, PLAY, LEVEL_CLEAR, GAME_OVER, PAUSED }

    companion object {
        const val EV_CATCH = 0
        const val EV_ESCAPE = 1
        const val EV_ITEM = 2
        const val EV_LEVEL = 3
        const val EV_OVER = 4
        const val EV_LOOT = 5
    }

    var state = State.READY
    var level = 1
    var score = 0
    var escapes = 0
    var timeLeft = 0f
    var combo = 0
    var comboT = 0f
    var heldItem = -1
    var caughtThisLevel = 0
    var totalThieves = 0
    var elapsed = 0f
    var shake = 0f
    var flash = 0f
    var flashColor = Cfg.C_RED
    var sirenT = 0f
    var hintText = ""
    var hintT = 0f

    var onEvent: ((Int) -> Unit)? = null

    val maze = Maze(Cfg.MAP_W, Cfg.MAP_H)
    val player = Player()
    val thieves = ArrayList<Thief>()
    val loots = ArrayList<Loot>()
    val pickups = ArrayList<Pickup>()
    val feet = ArrayList<Footprint>()
    val puffs = ArrayList<Puff>()
    val texts = ArrayList<FloatText>()

    private val rng = Random()
    private val danger = IntArray(Cfg.MAP_W * Cfg.MAP_H)
    private var dangerT = 0f
    private var pickupT = 3f

    // ---------------------------------------------------------------- 生命周期

    fun reset() {
        score = 0
        level = 1
        escapes = 0
        startLevel(1)
    }

    fun startLevel(n: Int) {
        level = n
        maze.generate(System.nanoTime(), n)
        thieves.clear()
        loots.clear()
        pickups.clear()
        feet.clear()
        puffs.clear()
        texts.clear()

        val center = maze.nearestFree(Cfg.MAP_W / 2f, Cfg.MAP_H / 2f)
        player.x = center.first
        player.y = center.second
        player.fx = 0f
        player.fy = 1f
        player.dashT = 0f
        player.dashCd = 0f
        player.boots = 0f
        player.lamp = 0f
        player.tracker = 0f

        totalThieves = (1 + (n - 1)).coerceAtMost(5)
        val tSpeed = (Cfg.T_SPEED_BASE + (n - 1) * Cfg.T_SPEED_STEP).coerceAtMost(Cfg.T_SPEED_MAX)

        repeat(totalThieves) {
            val p = maze.randomFreeSpot(rng, player.x, player.y, 13f)
            val t = Thief(p.first, p.second, tSpeed)
            t.repathT = rng.nextFloat() * 0.3f
            thieves.add(t)
        }

        val lootCount = (totalThieves + 1).coerceAtMost(8)
        repeat(lootCount) {
            val p = maze.randomFreeSpot(rng, player.x, player.y, 7f)
            loots.add(Loot(p.first, p.second))
        }

        timeLeft = (Cfg.START_TIME - (n - 1) * Cfg.TIME_STEP).coerceAtLeast(Cfg.MIN_TIME)
        combo = 0
        comboT = 0f
        caughtThisLevel = 0
        heldItem = -1
        pickupT = 4f
        dangerT = 0f
        sirenT = 0f
        state = State.PLAY
        showHint("第 $level 关 · 抓捕 $totalThieves 名小偷", Cfg.C_YELLOW)
    }

    /** 覆盖界面点击：开始 / 下一关 / 重来 / 继续 */
    fun tap() {
        when (state) {
            State.READY -> startLevel(1)
            State.PAUSED -> state = State.PLAY
            State.LEVEL_CLEAR -> startLevel(level + 1)
            State.GAME_OVER -> reset()
            State.PLAY -> {}
        }
    }

    fun pause() {
        if (state == State.PLAY) state = State.PAUSED
    }

    fun isRunning() = state == State.PLAY

    private fun showHint(text: String, color: Int) {
        hintText = text
        hintT = 2.4f
        flashColor = color
    }

    // ---------------------------------------------------------------- 主更新

    fun update(dt: Float, inp: InputState) {
        elapsed += dt
        shake = (shake - dt * 3.2f).coerceAtLeast(0f)
        flash = (flash - dt * 2.6f).coerceAtLeast(0f)
        if (hintT > 0f) hintT -= dt

        // 特效始终推进（暂停界面也保留动画）
        var i = feet.size - 1
        while (i >= 0) { feet[i].life -= dt; if (feet[i].life <= 0f) feet.removeAt(i); i-- }
        i = puffs.size - 1
        while (i >= 0) { puffs[i].life -= dt; if (puffs[i].life <= 0f) puffs.removeAt(i); i-- }
        i = texts.size - 1
        while (i >= 0) { texts[i].life -= dt; texts[i].y -= dt * 0.9f; if (texts[i].life <= 0f) texts.removeAt(i); i-- }

        if (state != State.PLAY) return

        // ---- 计时
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver("时间到！"); return }

        if (comboT > 0f) { comboT -= dt; if (comboT <= 0f) combo = 0 }
        if (sirenT > 0f) sirenT -= dt

        updatePlayer(dt, inp)
        updateDangerField(dt)
        updateThieves(dt)
        updateLootAndItems(dt)

        if (thieves.all { !it.alive }) levelClear()
    }

    private fun updatePlayer(dt: Float, inp: InputState) {
        val p = player
        var mx = inp.mx
        var my = inp.my
        val len = sqrt(mx * mx + my * my)
        if (len > 1f) { mx /= len; my /= len }
        val moving = len > 0.12f
        if (moving) {
            val nl = sqrt(mx * mx + my * my).coerceAtLeast(0.0001f)
            p.fx = mx / nl
            p.fy = my / nl
            p.walkPhase += dt * 9f
        }

        // 冲刺
        if (inp.dash && p.dashCd <= 0f && p.dashT <= 0f) {
            p.dashT = Cfg.DASH_TIME
            p.dashCd = Cfg.DASH_CD
            for (k in 0..5) {
                puffs.add(Puff(p.x, p.y + 0.15f, 0.30f + k * 0.03f, Cfg.C_CYAN, 0.10f + k * 0.02f))
            }
            onEvent?.invoke(EV_ITEM)
        }
        if (p.dashT > 0f) {
            p.dashT -= dt
            if (rng.nextFloat() < 0.8f) {
                puffs.add(Puff(p.x, p.y, 0.22f, Cfg.C_BLUE, 0.14f))
            }
        }
        if (p.dashCd > 0f) p.dashCd -= dt
        if (p.boots > 0f) p.boots -= dt
        if (p.lamp > 0f) p.lamp -= dt
        if (p.tracker > 0f) p.tracker -= dt

        // 使用道具
        if (inp.useItem && heldItem >= 0) useItem(heldItem)

        val sp = if (p.dashT > 0f) Cfg.DASH_SPEED else p.speed()
        val step = sp * dt
        val dx = mx * step
        val dy = my * step
        if (dx != 0f) {
            if (maze.canStand(p.x + dx, p.y, Cfg.P_RADIUS)) p.x += dx
        }
        if (dy != 0f) {
            if (maze.canStand(p.x, p.y + dy, Cfg.P_RADIUS)) p.y += dy
        }
    }

    private fun useItem(kind: Int) {
        heldItem = -1
        when (kind) {
            Cfg.ITEM_BOOTS -> {
                player.boots = Cfg.BOOTS_TIME
                showHint("加速靴！", Cfg.C_GREEN)
            }
            Cfg.ITEM_LAMP -> {
                player.lamp = Cfg.LAMP_TIME
                showHint("探照灯！", Cfg.C_YELLOW)
            }
            Cfg.ITEM_SIREN -> {
                sirenT = Cfg.SIREN_TIME
                for (t in thieves) if (t.alive) {
                    t.panic = Cfg.SIREN_TIME
                    t.path = IntArray(0)
                    t.pathIdx = 0
                }
                showHint("警笛！小偷慌了", Cfg.C_RED)
                shake = 1f
            }
            Cfg.ITEM_TRACKER -> {
                player.tracker = Cfg.TRACKER_TIME
                showHint("追踪器启动", Cfg.C_PURPLE)
            }
        }
        onEvent?.invoke(EV_ITEM)
    }

    private fun updateDangerField(dt: Float) {
        dangerT -= dt
        if (dangerT <= 0f) {
            dangerT = 0.18f
            PathFinder.distanceField(maze, player.x.toInt(), player.y.toInt(), danger)
        }
    }

    private fun updateThieves(dt: Float) {
        for (t in thieves) {
            if (!t.alive) continue

            if (t.panic > 0f) t.panic -= dt

            // ---- 选目标
            var tx: Float
            var ty: Float
            if (t.panic > 0f) {
                t.wanderT -= dt
                if (t.wanderT <= 0f) {
                    val p = maze.randomFreeSpot(rng, t.x, t.y, 0f)
                    t.wanderX = p.first
                    t.wanderY = p.second
                    t.wanderT = 1.1f
                    t.path = IntArray(0)
                    t.pathIdx = 0
                }
                tx = t.wanderX
                ty = t.wanderY
            } else if (!t.carrying) {
                val l = nearestLoot(t.x, t.y)
                if (l != null) {
                    tx = l.x
                    ty = l.y
                } else {
                    t.carrying = true
                    val e = nearestEscape(t.x, t.y)
                    tx = e.first
                    ty = e.second
                }
            } else {
                val e = nearestEscape(t.x, t.y)
                tx = e.first
                ty = e.second
            }

            // ---- 重新寻路
            t.repathT -= dt
            if (t.repathT <= 0f || t.path.isEmpty() || t.pathIdx >= t.path.size) {
                t.repathT = Cfg.T_REPATH
                val g = maze.nearestFree(tx, ty)
                val path = PathFinder.findPath(
                    maze,
                    t.x.toInt(), t.y.toInt(),
                    g.first.toInt(), g.second.toInt(),
                    danger
                )
                t.path = path
                t.pathIdx = if (path.size > 1) 1 else 0
            }

            // ---- 沿路径移动
            var vx = 0f
            var vy = 0f
            if (t.path.isNotEmpty() && t.pathIdx < t.path.size) {
                val ti = t.path[t.pathIdx]
                val cx = (ti % maze.w) + 0.5f
                val cy = (ti / maze.w) + 0.5f
                val ddx = cx - t.x
                val ddy = cy - t.y
                val d = sqrt(ddx * ddx + ddy * ddy)
                if (d < 0.2f) {
                    t.pathIdx++
                } else {
                    vx = ddx / d
                    vy = ddy / d
                }
            } else {
                val ddx = tx - t.x
                val ddy = ty - t.y
                val d = sqrt(ddx * ddx + ddy * ddy).coerceAtLeast(0.0001f)
                vx = ddx / d
                vy = ddy / d
            }

            var sp = t.baseSpeed
            if (t.carrying) sp *= Cfg.T_SPEED_LOADED
            if (t.panic > 0f) sp *= Cfg.T_SPEED_PANIC
            if (vx != 0f || vy != 0f) {
                t.fx = vx
                t.fy = vy
                t.walkPhase += dt * 8f
                val step = sp * dt
                if (maze.canStand(t.x + vx * step, t.y, Cfg.T_RADIUS)) t.x += vx * step
                if (maze.canStand(t.x, t.y + vy * step, Cfg.T_RADIUS)) t.y += vy * step

                // 脚印（暗处追踪线索）
                t.footT -= dt
                if (t.footT <= 0f) {
                    t.footT = Cfg.FOOT_STEP
                    feet.add(Footprint(t.x, t.y, Cfg.FOOT_LIFE, Math.atan2(vy.toDouble(), vx.toDouble()).toFloat()))
                    if (feet.size > 160) feet.removeAt(0)
                }
            }

            // ---- 偷到赃物
            if (!t.carrying) {
                for (l in loots) {
                    if (l.taken || l.secured) continue
                    if (dist(t.x, t.y, l.x, l.y) < 0.62f) {
                        l.taken = true
                        t.carrying = true
                        t.path = IntArray(0)
                        t.pathIdx = 0
                        texts.add(FloatText(l.x, l.y, 1.1f, "偷走了！", Cfg.C_RED))
                        for (k in 0..7) {
                            puffs.add(Puff(l.x, l.y, 0.4f + k * 0.02f, Cfg.C_GOLD, 0.09f))
                        }
                        break
                    }
                }
            }

            // ---- 逃出城
            if (t.carrying) {
                val e = nearestEscape(t.x, t.y)
                if (dist(t.x, t.y, e.first, e.second) < 0.75f) {
                    t.alive = false
                    t.escaped = true
                    escapes++
                    flash = 1f
                    flashColor = Cfg.C_RED
                    shake = 0.8f
                    texts.add(FloatText(t.x, t.y, 1.3f, "逃脱！", Cfg.C_RED))
                    onEvent?.invoke(EV_ESCAPE)
                    if (escapes >= Cfg.MAX_ESCAPES) {
                        gameOver("放跑太多小偷了")
                        return
                    }
                }
            }
        }

        // ---- 抓捕判定
        val catchR = if (player.dashT > 0f) Cfg.CATCH_DIST_DASH else Cfg.CATCH_DIST
        for (t in thieves) {
            if (!t.alive) continue
            if (dist(player.x, player.y, t.x, t.y) < catchR) {
                catchThief(t)
            }
        }
    }

    private fun catchThief(t: Thief) {
        t.alive = false
        t.caught = true
        caughtThisLevel++
        combo = (combo + 1).coerceAtMost(5)
        comboT = Cfg.COMBO_WINDOW
        val gain = 100 * combo
        score += gain
        shake = 0.9f
        flash = 0.7f
        flashColor = Cfg.C_YELLOW
        texts.add(FloatText(t.x, t.y, 1.2f, "+$gain", Cfg.C_YELLOW))
        for (k in 0..11) {
            puffs.add(Puff(t.x, t.y, 0.45f + k * 0.03f, Cfg.C_YELLOW, 0.10f + k * 0.012f))
        }
        onEvent?.invoke(EV_CATCH)
    }

    private fun updateLootAndItems(dt: Float) {
        // 警察回收赃物
        for (l in loots) {
            if (l.taken || l.secured) continue
            if (dist(player.x, player.y, l.x, l.y) < 0.62f) {
                l.secured = true
                score += 60
                texts.add(FloatText(l.x, l.y, 1.1f, "+60", Cfg.C_GREEN))
                for (k in 0..6) puffs.add(Puff(l.x, l.y, 0.35f + k * 0.02f, Cfg.C_GREEN, 0.09f))
                onEvent?.invoke(EV_LOOT)
            }
        }

        // 道具箱刷新
        pickupT -= dt
        if (pickupT <= 0f) {
            pickupT = Cfg.PICKUP_INTERVAL
            if (pickups.size < Cfg.PICKUP_MAX) {
                val p = maze.randomFreeSpot(rng, player.x, player.y, 5f)
                pickups.add(Pickup(p.first, p.second, rng.nextInt(4)))
            }
        }
        val pi = pickups.iterator()
        while (pi.hasNext()) {
            val pk = pi.next()
            if (dist(player.x, player.y, pk.x, pk.y) < 0.62f) {
                heldItem = pk.kind
                showHint("获得 ${Cfg.ITEM_NAMES[pk.kind]}", Cfg.C_CYAN)
                pi.remove()
                onEvent?.invoke(EV_ITEM)
            }
        }
    }

    private fun nearestLoot(x: Float, y: Float): Loot? {
        var best: Loot? = null
        var bd = Float.MAX_VALUE
        for (l in loots) {
            if (l.taken || l.secured) continue
            val d = dist(x, y, l.x, l.y)
            if (d < bd) { bd = d; best = l }
        }
        return best
    }

    private fun nearestEscape(x: Float, y: Float): Pair<Float, Float> {
        var best = maze.escapes[0]
        var bd = Float.MAX_VALUE
        for (e in maze.escapes) {
            val d = dist(x, y, e.first, e.second)
            if (d < bd) { bd = d; best = e }
        }
        return best
    }

    private fun levelClear() {
        val timeBonus = (timeLeft * 5).toInt()
        val clearBonus = 200
        score += timeBonus + clearBonus
        state = State.LEVEL_CLEAR
        onEvent?.invoke(EV_LEVEL)
    }

    private fun gameOver(reason: String) {
        state = State.GAME_OVER
        overReason = reason
        flash = 1f
        flashColor = Cfg.C_RED
        shake = 1f
        onEvent?.invoke(EV_OVER)
    }

    var overReason = ""

    // ---------------------------------------------------------------- 可见性

    /** 0=不可见 1=可见 2=仅追踪器轮廓 */
    fun visibility(t: Thief): Int {
        if (!t.alive) return 0
        if (dist(player.x, player.y, t.x, t.y) < player.lightRadius()) return 1
        for (l in maze.lamps) {
            if (dist(l.first, l.second, t.x, t.y) < Cfg.LAMP_R) return 1
        }
        return if (player.tracker > 0f) 2 else 0
    }

    fun remainingThieves(): Int = thieves.count { it.alive }
}
