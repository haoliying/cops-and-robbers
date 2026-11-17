package com.haoly.copsandrobbers

import java.util.PriorityQueue

/**
 * 网格寻路：
 *  - distanceField：从警察所在格做 BFS，得到全图"危险度"距离场
 *  - findPath：带危险权重的 Dijkstra，小偷会主动绕开警察附近
 */
object PathFinder {

    private const val INF = Float.MAX_VALUE / 4f

    /** BFS 距离场（单位：tile 步数），不可达为 -1 */
    fun distanceField(maze: Maze, sx: Int, sy: Int, out: IntArray) {
        out.fill(-1)
        val w = maze.w
        val h = maze.h
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return
        if (maze.tiles[sy * w + sx] == Cfg.T_BUILDING) return

        val n = w * h
        val q = IntArray(n)
        var head = 0
        var tail = 0
        val start = sy * w + sx
        out[start] = 0
        q[tail++] = start

        while (head < tail) {
            val cur = q[head++]
            val cx = cur % w
            val cy = cur / w
            val nd = out[cur] + 1
            if (cx > 0) {
                val i = cur - 1
                if (maze.tiles[i] != Cfg.T_BUILDING && out[i] == -1) { out[i] = nd; q[tail++] = i }
            }
            if (cx < w - 1) {
                val i = cur + 1
                if (maze.tiles[i] != Cfg.T_BUILDING && out[i] == -1) { out[i] = nd; q[tail++] = i }
            }
            if (cy > 0) {
                val i = cur - w
                if (maze.tiles[i] != Cfg.T_BUILDING && out[i] == -1) { out[i] = nd; q[tail++] = i }
            }
            if (cy < h - 1) {
                val i = cur + w
                if (maze.tiles[i] != Cfg.T_BUILDING && out[i] == -1) { out[i] = nd; q[tail++] = i }
            }
        }
    }

    private class Node(val t: Int, val d: Float)

    /**
     * Dijkstra 求路径，返回 tile 索引数组（含起点与终点），无解返回空数组。
     * @param danger 警察距离场，-1 表示不可达
     */
    fun findPath(maze: Maze, sx: Int, sy: Int, gx: Int, gy: Int, danger: IntArray): IntArray {
        val w = maze.w
        val h = maze.h
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return IntArray(0)
        if (gx < 0 || gy < 0 || gx >= w || gy >= h) return IntArray(0)
        val n = w * h
        val start = sy * w + sx
        val goal = gy * w + gx
        if (maze.tiles[start] == Cfg.T_BUILDING) return IntArray(0)
        if (maze.tiles[goal] == Cfg.T_BUILDING) return IntArray(0)

        val dist = FloatArray(n) { INF }
        val prev = IntArray(n) { -1 }
        dist[start] = 0f
        val pq = PriorityQueue<Node>(compareBy<Node> { it.d })
        pq.add(Node(start, 0f))

        var found = false
        var guard = 0
        while (pq.isNotEmpty() && guard++ < n * 4) {
            val cur = pq.poll() ?: break
            if (cur.d > dist[cur.t] + 0.0001f) continue
            if (cur.t == goal) { found = true; break }
            val cx = cur.t % w
            val cy = cur.t / w
            val nd0 = cur.d + 1f
            if (cx > 0) relax(maze, danger, dist, prev, pq, cur.t - 1, goal, nd0, cur.t)
            if (cx < w - 1) relax(maze, danger, dist, prev, pq, cur.t + 1, goal, nd0, cur.t)
            if (cy > 0) relax(maze, danger, dist, prev, pq, cur.t - w, goal, nd0, cur.t)
            if (cy < h - 1) relax(maze, danger, dist, prev, pq, cur.t + w, goal, nd0, cur.t)
        }
        if (!found) return IntArray(0)

        val rev = ArrayList<Int>()
        var cur = goal
        var steps = 0
        while (cur != -1 && steps++ <= n) {
            rev.add(cur)
            if (cur == start) break
            cur = prev[cur]
        }
        rev.reverse()
        return rev.toIntArray()
    }

    private fun relax(
        maze: Maze, danger: IntArray, dist: FloatArray, prev: IntArray, pq: PriorityQueue<Node>,
        ni: Int, goal: Int, baseCost: Float, from: Int
    ) {
        if (maze.tiles[ni] == Cfg.T_BUILDING) return
        var step = baseCost
        if (ni != goal) {
            val dg = danger[ni]
            if (dg >= 0 && dg < Cfg.T_DANGER_R) {
                step += (Cfg.T_DANGER_R - dg) * Cfg.T_DANGER_W
            }
        }
        if (step < dist[ni]) {
            dist[ni] = step
            prev[ni] = from
            pq.add(Node(ni, step))
        }
    }
}
