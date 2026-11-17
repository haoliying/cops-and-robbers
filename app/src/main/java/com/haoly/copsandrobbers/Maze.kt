package com.haoly.copsandrobbers

import java.util.Random
import kotlin.math.abs

/**
 * 程序化生成的城市街区。
 *
 * 经典 Apple II《Cops & Robbers》的地图就是一张曼哈顿网格：
 * 每 3x3 单元里放一块 2x2 的楼房，剩下的十字是街道。
 * 这种布局天然保证全图连通，不需要再做连通性检查。
 */
class Maze(val w: Int, val h: Int) {

    val tiles = ByteArray(w * h)

    /** 街区块（2x2 一组） */
    class Block(
        val bx: Int,          // 块列号
        val by: Int,          // 块行号
        var kind: Int,        // BLK_*
        val wall: Int,        // 外墙配色索引
        val roof: Int,
        val windows: Int,     // 窗户位掩码
        val litMask: Int,     // 亮灯位掩码
        val carColor: Int,
        val carDir: Int,
        val treeSeed: Int
    ) {
        val x0: Int get() = bx * 3 + 1
        val y0: Int get() = by * 3 + 1
    }

    val blocks = ArrayList<Block>()
    val lamps = ArrayList<Pair<Float, Float>>()      // 路灯（世界坐标，左下角为整数 tile 的中心）
    val escapes = ArrayList<Pair<Float, Float>>()    // 小偷逃跑出口

    fun idx(x: Int, y: Int) = y * w + x
    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun tileAt(x: Int, y: Int): Byte = if (inside(x, y)) tiles[idx(x, y)] else Cfg.T_BUILDING
    fun blocked(x: Int, y: Int): Boolean = tileAt(x, y) == Cfg.T_BUILDING

    /** 世界坐标（浮点 tile）是否可站立 */
    fun canStand(px: Float, py: Float, r: Float): Boolean {
        val x0 = kotlin.math.floor(px - r).toInt()
        val x1 = kotlin.math.floor(px + r).toInt()
        val y0 = kotlin.math.floor(py - r).toInt()
        val y1 = kotlin.math.floor(py + r).toInt()
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                if (blocked(tx, ty)) return false
            }
        }
        return true
    }

    /** 生成一张地图（level 越高，公园/停车场越多，路更通透） */
    fun generate(seed: Long, level: Int) {
        val rng = Random(seed)
        tiles.fill(Cfg.T_STREET)
        blocks.clear()
        lamps.clear()
        escapes.clear()

        val bxCount = w / 3
        val byCount = h / 3
        val openChance = 0.22f + level * 0.012f   // 越后期开阔地越多

        for (by in 0 until byCount) {
            for (bx in 0 until bxCount) {
                val r = rng.nextFloat()
                val kind = when {
                    r < openChance * 0.55f -> Cfg.BLK_PARK
                    r < openChance -> Cfg.BLK_LOT
                    else -> Cfg.BLK_BUILDING
                }
                val b = Block(
                    bx = bx, by = by, kind = kind,
                    wall = rng.nextInt(Cfg.WALL_COLORS.size),
                    roof = rng.nextInt(Cfg.ROOF_COLORS.size),
                    windows = rng.nextInt(1 shl 8),
                    litMask = rng.nextInt(1 shl 8),
                    carColor = rng.nextInt(Cfg.CAR_COLORS.size),
                    carDir = rng.nextInt(2),
                    treeSeed = rng.nextInt(1000)
                )
                blocks.add(b)
                val t: Byte = when (kind) {
                    Cfg.BLK_PARK -> Cfg.T_PARK
                    Cfg.BLK_LOT -> Cfg.T_LOT
                    else -> Cfg.T_BUILDING
                }
                for (dy in 0..1) for (dx in 0..1) {
                    val x = b.x0 + dx
                    val y = b.y0 + dy
                    if (inside(x, y)) tiles[idx(x, y)] = t
                }
            }
        }

        // 路灯：街道十字路口随机挑若干
        val crossings = ArrayList<Pair<Int, Int>>()
        for (y in 0 until h step 3) for (x in 0 until w step 3) crossings.add(Pair(x, y))
        crossings.shuffle(rng)
        val lampCount = (crossings.size * 0.30f).toInt().coerceIn(12, 34)
        for (i in 0 until lampCount.coerceAtMost(crossings.size)) {
            val c = crossings[i]
            lamps.add(Pair(c.first + 0.5f, c.second + 0.5f))
        }

        // 逃跑出口：四条边的中点 + 四角
        val ex = intArrayOf(0, w / 2, w - 1, w / 2, 0, w - 1, 0, w - 1)
        val ey = intArrayOf(0, 0, 0, h - 1, h / 2, h / 2, h - 1, h - 1)
        for (i in ex.indices) {
            var x = ex[i].coerceIn(0, w - 1)
            var y = ey[i].coerceIn(0, h - 1)
            if (blocked(x, y)) { x = if (x == 0) 1 else if (x == w - 1) w - 2 else x; }
            if (blocked(x, y)) { y = if (y == 0) 1 else if (y == h - 1) h - 2 else y }
            escapes.add(Pair(x + 0.5f, y + 0.5f))
        }
    }

    /** 随机一个可通行 tile 的中心点，可指定与某点的最小距离 */
    fun randomFreeSpot(rng: Random, awayX: Float, awayY: Float, minDist: Float): Pair<Float, Float> {
        for (i in 0 until 200) {
            val x = rng.nextInt(w)
            val y = rng.nextInt(h)
            if (blocked(x, y)) continue
            val cx = x + 0.5f
            val cy = y + 0.5f
            if (minDist > 0f && kotlin.math.hypot((cx - awayX).toDouble(), (cy - awayY).toDouble())
                    .toFloat() < minDist
            ) continue
            return Pair(cx, cy)
        }
        // 兜底：扫描第一个空地
        for (y in 0 until h) for (x in 0 until w) {
            if (!blocked(x, y)) return Pair(x + 0.5f, y + 0.5f)
        }
        return Pair(1.5f, 1.5f)
    }

    /** 距离某点最近的可通行 tile 中心 */
    fun nearestFree(x: Float, y: Float): Pair<Float, Float> {
        var bestX = 1.5f
        var bestY = 1.5f
        var best = Float.MAX_VALUE
        val tx = x.toInt().coerceIn(0, w - 1)
        val ty = y.toInt().coerceIn(0, h - 1)
        for (r in 0..8) {
            for (dy in -r..r) for (dx in -r..r) {
                if (abs(dx) != r && abs(dy) != r) continue
                val nx = tx + dx
                val ny = ty + dy
                if (blocked(nx, ny)) continue
                val cx = nx + 0.5f
                val cy = ny + 0.5f
                val d = kotlin.math.hypot((cx - x).toDouble(), (cy - y).toDouble()).toFloat()
                if (d < best) { best = d; bestX = cx; bestY = cy }
            }
            if (best < Float.MAX_VALUE) break
        }
        return Pair(bestX, bestY)
    }
}
