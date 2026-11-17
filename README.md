# 警察抓小偷 · Cops & Robbers（Android 重制版）

致敬 Apple II 经典游戏《Cops & Robbers》（Bob Bishop, 1978）：在一张曼哈顿网格街区里，
玩家扮演警察追逐小偷，撞上就抓。这一版保留了原版的内核，并做了安卓化的加强。

## 玩法

- **目标**：在天黑前把本关所有小偷抓完；放跑 3 个即 Game Over。
- **小偷 AI**：会主动去偷金色的赃物袋，得手后跑向地图边缘的红色出口。
  他们用带「危险权重」的 Dijkstra 寻路，会主动绕开你附近 —— 想抓人得学会抄近路、卡路口。
- **夜间视野**：夜里只能看到手电光圈和路灯下的范围。小偷走过会留下**橙色脚印**，
  这是黑暗中唯一的追踪线索。
- **赃物**：小偷偷走赃物会加速；警察抢回赃物 +60 分。
- **连击**：3.6 秒内连续抓捕有最高 5 倍的分数加成。

### 道具（捡箱子获得，按 ITEM 使用）

| 道具 | 效果 |
| --- | --- |
| 加速靴 | 4 秒移动速度 ×1.62 |
| 探照灯 | 9 秒视野大幅扩大，手电光锥变长 |
| 警笛 | 全场小偷惊慌 2.6 秒，速度减半且乱跑 |
| 追踪器 | 9 秒内在暗处也能看到小偷轮廓，屏幕边缘显示方向箭头 |

### 操作

- 左下 **浮动摇杆**（按下即出现）移动；
- 右下 **DASH** 冲刺扑抓（0.2 秒爆发位移，抓捕判定范围更大，冷却 1.05 秒）；
- 右下 **ITEM** 使用道具；
- 键盘/手柄：WASD 或方向键移动，空格/Shift 冲刺，E/回车 使用道具（模拟器、电视可用）。

## 工程说明

- 语言：Kotlin；最低支持 Android 6.0（API 23），targetSdk 35。
- **零第三方依赖**，全部用 `Canvas` 自绘：像素 sprite 由 8×8 字符矩阵在运行时预渲染成 Bitmap，
  建筑/街区/车辆/路灯都是几何图元，夜幕用离屏 Bitmap + `PorterDuff.Mode.DST_OUT` 挖光洞。
- 游戏循环：`SurfaceView` + 独立线程，固定上限步长（最大 80ms，防后台切回来跳帧），约 60 FPS。
- 地图：程序化生成，每 3×3 单元放一块 2×2 楼房、其余为街道，天然保证全图连通；
  每关随机种子不同，且随关卡增加公园/空地比例。

### 目录

```
app/src/main/java/com/haoly/copsandrobbers/
├── MainActivity.kt   全屏 Activity、沉浸式、生命周期
├── GameView.kt       SurfaceView 游戏循环、触摸/键盘分发、震动反馈
├── Renderer.kt       世界与 HUD 绘制、夜幕、虚拟按键、各覆盖界面
├── Game.kt           状态机、玩家/小偷更新、抓捕与计分规则
├── Maze.kt           街区地图生成、碰撞查询、出生点
├── PathFinder.kt     BFS 危险距离场 + Dijkstra 加权寻路
├── Input.kt          虚拟摇杆与按键
└── GameConfig.kt     常量、调色板、像素 sprite 定义
```

## 如何运行

1. 用 Android Studio 打开本目录（工程已写好 `local.properties` 指向 `D:/software/android/Sdk`，
   若你的 SDK 在别处，改这一行即可）。
2. 直接 Run `app`，或用命令行：

```bash
./gradlew assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # 连接到真机/模拟器直接安装
```

> 工程已内置 Gradle Wrapper 配置；首次构建需要联网下载 Gradle 与 Android Gradle Plugin。
