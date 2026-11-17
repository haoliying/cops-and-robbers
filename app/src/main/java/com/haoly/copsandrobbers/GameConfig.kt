package com.haoly.copsandrobbers

/**
 * 全局常量、复古调色板与像素 sprite 定义。
 * 配色参考 Apple II 高分辨率图形模式的经典 6 色（黑/绿/紫/橙/蓝/白）。
 */
object Cfg {

    // ---------- 地图 ----------
    const val MAP_W = 31          // tile 数（必须为 3n+1，保证最外圈是街道）
    const val MAP_H = 43

    const val T_STREET: Byte = 0
    const val T_BUILDING: Byte = 1
    const val T_PARK: Byte = 2
    const val T_LOT: Byte = 3

    const val BLK_BUILDING = 0
    const val BLK_PARK = 1
    const val BLK_LOT = 2

    // ---------- 视野 ----------
    const val VIEW_W = 13f        // 期望可视宽度（tile）
    const val VIEW_H = 26f        // 期望可视高度（tile）
    const val LIGHT_R = 4.6f      // 警察手电/光圈半径
    const val LIGHT_R_LAMP = 8.6f // 探照灯道具
    const val LAMP_R = 2.9f       // 路灯照明半径
    const val DARK_ALPHA = 0xBE   // 夜幕浓度

    // ---------- 玩家 ----------
    const val P_SPEED = 4.15f
    const val P_RADIUS = 0.33f
    const val DASH_SPEED = 8.2f
    const val DASH_TIME = 0.20f
    const val DASH_CD = 1.05f
    const val CATCH_DIST = 0.74f
    const val CATCH_DIST_DASH = 1.05f

    // ---------- 小偷 ----------
    const val T_RADIUS = 0.30f
    const val T_SPEED_BASE = 3.18f
    const val T_SPEED_STEP = 0.07f   // 每关加速
    const val T_SPEED_MAX = 3.95f
    const val T_SPEED_PANIC = 0.55f  // 惊慌倍率
    const val T_SPEED_LOADED = 1.08f // 带着赃物跑得更快
    const val T_REPATH = 0.34f
    const val T_DANGER_R = 6         // 危险感知半径（tile）
    const val T_DANGER_W = 0.95f     // 危险权重

    // ---------- 道具 ----------
    const val ITEM_BOOTS = 0
    const val ITEM_LAMP = 1
    const val ITEM_SIREN = 2
    const val ITEM_TRACKER = 3
    val ITEM_NAMES = arrayOf("加速靴", "探照灯", "警笛", "追踪器")
    const val BOOTS_TIME = 4.0f
    const val LAMP_TIME = 9.0f
    const val SIREN_TIME = 2.6f
    const val TRACKER_TIME = 9.0f
    const val PICKUP_INTERVAL = 7.5f
    const val PICKUP_MAX = 3

    // ---------- 规则 ----------
    const val MAX_ESCAPES = 3        // 放跑这么多小偷就 GameOver
    const val FOOT_LIFE = 3.8f       // 脚印存活（追踪线索）
    const val FOOT_STEP = 0.30f
    const val COMBO_WINDOW = 3.6f
    const val START_TIME = 96f
    const val TIME_STEP = 5f         // 每关减少
    const val MIN_TIME = 58f

    // ---------- 调色板 ----------
    const val C_BLACK = 0xFF0A0A12.toInt()
    const val C_NIGHT = 0xFF05050C.toInt()
    const val C_ROAD = 0xFF171722.toInt()
    const val C_ROAD2 = 0xFF1E1E2C.toInt()
    const val C_CURB = 0xFF2B2B3D.toInt()
    const val C_LINE = 0xFF3A3A52.toInt()
    const val C_PARK = 0xFF12301C.toInt()
    const val C_PARK2 = 0xFF0B2214.toInt()
    const val C_LOT = 0xFF20202C.toInt()
    const val C_WHITE = 0xFFF2F2F2.toInt()
    const val C_GREEN = 0xFF2BE04A.toInt()
    const val C_PURPLE = 0xFFC06BFF.toInt()
    const val C_BLUE = 0xFF4C8CFF.toInt()
    const val C_ORANGE = 0xFFFF8A28.toInt()
    const val C_YELLOW = 0xFFFFD23F.toInt()
    const val C_RED = 0xFFFF4D5E.toInt()
    const val C_CYAN = 0xFF4FE8E0.toInt()
    const val C_SKIN = 0xFFF2C49B.toInt()
    const val C_DARKBLUE = 0xFF16307A.toInt()
    const val C_COP = 0xFF2E6BE6.toInt()
    const val C_THIEF = 0xFFA05FD6.toInt()
    const val C_GOLD = 0xFFE0A020.toInt()

    // 建筑外墙配色（暖色砖房 + 冷色楼宇）
    val WALL_COLORS = intArrayOf(
        0xFF6B3F2A.toInt(), 0xFF7A4A2E.toInt(), 0xFF5A3546.toInt(),
        0xFF3F4A6B.toInt(), 0xFF6A5A3A.toInt(), 0xFF4A3F52.toInt(),
        0xFF825046.toInt(), 0xFF38445C.toInt()
    )
    val ROOF_COLORS = intArrayOf(
        0xFF3A2318.toInt(), 0xFF40281C.toInt(), 0xFF331E28.toInt(),
        0xFF222B3E.toInt(), 0xFF3C3320.toInt(), 0xFF2A2433.toInt()
    )
    val CAR_COLORS = intArrayOf(
        0xFFD8483C.toInt(), 0xFF3E7BD6.toInt(), 0xFFE8C33A.toInt(),
        0xFF6FBF5A.toInt(), 0xFFB0B6C4.toInt(), 0xFF9A5FD0.toInt()
    )

    // ---------- 像素 sprite（8x8）----------
    // '.' 透明 / 颜色键见 Renderer 的 palette 映射
    val SPR_COP = arrayOf(
        "...22...",
        "..2222..",
        "..FFFF..",
        "..FKFK..",
        ".111111.",
        "Y111111Y",
        "..1111..",
        "..2..2.."
    )
    val SPR_THIEF = arrayOf(
        "...KK...",
        "..KKKK..",
        "..FFFF..",
        "..FKKF..",
        ".333333.",
        "33333333",
        "..3333..",
        "..K..K.."
    )
    val SPR_LOOT = arrayOf(
        "........",
        "...KK...",
        "..YYYY..",
        ".YWWGGY.",
        ".YWGGWY.",
        ".YGGWWY.",
        "..YYYY..",
        "........"
    )
    val SPR_BOX = arrayOf(
        "........",
        ".CCCCCC.",
        ".CWWWWC.",
        ".CWPPWC.",
        ".CWPPWC.",
        ".CWWWWC.",
        ".CCCCCC.",
        "........"
    )
    val SPR_BOOTS = arrayOf(
        "........",
        "..GG.GG.",
        ".OOOOOO.",
        ".OGGGGO.",
        ".OGGGGO.",
        "..OOOO..",
        "........",
        "........"
    )
    val SPR_LAMP = arrayOf(
        "........",
        "....Y...",
        "...YYY..",
        "..YYYYY.",
        "...YYY..",
        "....Y...",
        "...KK...",
        "...KK..."
    )
    val SPR_SIREN = arrayOf(
        "........",
        "...RR...",
        "..RRRR..",
        ".RRRRRR.",
        "..KKKK..",
        ".K.KK.K.",
        "........",
        "........"
    )
    val SPR_TRACKER = arrayOf(
        "........",
        "..PPPP..",
        ".PPPPPP.",
        ".PPWWPP.",
        ".PPWWPP.",
        ".PPPPPP.",
        "..PPPP..",
        "........"
    )

    /** sprite 字符 -> 颜色（'.' 表示透明） */
    fun spriteColor(ch: Char): Int = when (ch) {
        '1' -> C_COP
        '2' -> C_DARKBLUE
        '3' -> C_THIEF
        'F' -> C_SKIN
        'K' -> C_BLACK
        'W' -> C_WHITE
        'Y' -> C_YELLOW
        'G' -> C_GOLD
        'O' -> C_ORANGE
        'R' -> C_RED
        'P' -> C_PURPLE
        'C' -> C_CYAN
        else -> 0
    }
}
