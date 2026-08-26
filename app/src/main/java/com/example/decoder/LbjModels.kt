package com.example.decoder

data class TrainTelemetry(
    val trainNo: String = "----",
    val direction: String = "未知", // 上行 / 下行 / 未知
    val speed: String = "---", // km/h
    val positionKm: String = "---.-", // km
    val locoModel: String = "----",
    val locoCode: String = "---",
    val route: String = "----",
    val category: String = "等待信号...",
    val isDetailed: Boolean = false,
    val isRouteValid: Boolean = false,
    val isHit: Boolean = false,
    val timestamp: Long = 0L,
    val rawBcd: String = "",
    val warning: String = "",
    val warningTime: Long = 0L
)

data class EtaInfo(
    val etaSeconds: Double? = null,
    val etaTime: String = "--:--:--",
    val etaDistanceKm: Double? = null,
    val etaStatus: String = "未设置线路位置",
    val etaTrain: String = "----",
    val etaRoute: String = "----"
)

object LocomotiveDict {
    val LOCOS: Map<Int, String> = mapOf(
        1 to "解放", 3 to "前进", 5 to "建设", 6 to "KD7", 55 to "蓝箭控车",
        81 to "东风21", 101 to "东风", 102 to "东风2", 103 to "东风3", 104 to "东风4",
        105 to "东风4客", 106 to "东风4C", 107 to "东风5", 108 to "东风5宽", 109 to "东风6",
        110 to "东风7", 111 to "东风8", 112 to "东风9", 113 to "东风10", 114 to "东方红1",
        115 to "东方红2", 116 to "东方红3", 117 to "东方红5", 118 to "北京", 119 to "北京宽",
        120 to "ND2", 121 to "ND3", 122 to "ND4", 123 to "ND5", 124 to "NY5",
        125 to "NY6", 126 to "NY7", 127 to "轻油", 128 to "东方红21", 129 to "东风7B",
        130 to "东风5S", 131 to "东风7C", 132 to "东风7S", 133 to "工矿1", 134 to "工矿1F",
        135 to "东风4E", 136 to "东风7D", 137 to "工矿1A", 138 to "东风11", 139 to "天安",
        140 to "东风10F", 141 to "东风4D", 142 to "东风8B", 143 to "东风12", 144 to "东风7E",
        145 to "NYJ1", 146 to "NZJ1", 147 to "NZJ2", 148 to "东风4DJ", 149 to "新曙光",
        150 to "神州", 151 to "NJ2", 152 to "东风7G", 153 to "NDJ3", 157 to "FXN3D",
        158 to "东风11G", 160 to "HXN3", 161 to "HXN5", 162 to "HXN3B", 163 to "HXN5B",
        167 to "FXN3B", 170 to "FXN5C", 171 to "FXN3-J", 201 to "8G", 202 to "8K",
        203 to "6G", 204 to "6K", 205 to "韶山1", 206 to "韶山3", 207 to "韶山4",
        208 to "韶山5", 209 to "韶山6", 210 to "韶山3B", 211 to "韶山7", 212 to "韶山8",
        213 to "韶山7B", 214 to "韶山7C", 215 to "韶山6B", 216 to "韶山9", 217 to "韶山7D",
        218 to "DJ熊猫", 219 to "DJ1", 220 to "DJ2", 221 to "DJF", 222 to "蓝箭动车",
        223 to "先锋号", 224 to "韶山7E", 225 to "韶山4G", 226 to "韶山3C", 228 to "天梭",
        229 to "DJ4和谐", 230 to "KTT", 231 to "HXD1", 232 to "HXD2", 233 to "HXD3",
        234 to "HXD1B", 235 to "HXD2B", 236 to "HXD3B", 237 to "HXD1C", 238 to "HXD2C",
        239 to "HXD3C", 240 to "HXD1D", 241 to "HXD2D", 242 to "HXD3D", 243 to "FXD1B",
        244 to "FXD2B", 245 to "FXD1", 246 to "FXD3", 247 to "FXD1-J", 248 to "FXD3-J",
        249 to "KZ25TA", 251 to "KZ25TB", 252 to "HXD1D-J", 254 to "FXD1H",
        299 to "雪域神州", 300 to "CRH1", 301 to "CRH2", 302 to "CRH3", 304 to "CRH5",
        305 to "CRH380A", 306 to "CRH380B", 307 to "CRH380C", 308 to "CRH380D",
        309 to "CRH6A", 310 to "CR400AF", 311 to "CR400BF", 312 to "CR300AF",
        313 to "CR300BF", 314 to "CRH2E", 315 to "CRH6F", 329 to "CJ1", 330 to "CJ2",
        331 to "CJ3", 332 to "CJ4", 333 to "CJ5", 334 to "CJ6"
    )

    fun getLocoName(typeCode: Int): String {
        return LOCOS[typeCode] ?: "未知机车($typeCode)"
    }
}

object TrainCategorizer {

    /**
     * 根据铁总运[2014]308号文《列车车次编排规定》进行类型映射分类
     */
    fun categorize(trainNo: String?, isDetailed: Boolean = true): String {
        if (trainNo.isNullOrBlank() || trainNo == "等待信号..." || trainNo == "----") {
            return "等待信号..."
        }
        val tn = trainNo.replace(" ", "").trim().uppercase()

        // 1. 字母字头旅客列车
        if (tn.startsWith("G")) return "高速动车组旅客列车"
        if (tn.startsWith("D")) return "动车组旅客列车"
        if (tn.startsWith("C")) return "城际动车组旅客列车"
        if (tn.startsWith("S")) return "市郊铁路列车"
        if (tn.startsWith("Z")) return "直达特快旅客列车"
        if (tn.startsWith("T")) return "特快旅客列车"
        if (tn.startsWith("K")) return "快速旅客列车"
        if (tn.startsWith("L") || tn.startsWith("A")) return "临时旅客列车"
        if (tn.startsWith("Y")) return "旅游列车"
        if (tn.startsWith("F")) return "因故折返旅客列车"

        // 2. 动车组检测、确认列车
        if (tn.startsWith("DJ")) {
            val num = tn.removePrefix("DJ").toIntOrNull()
            return when {
                num != null && num in 1..1998 -> "动车组检测列车"
                num != null && num in 5001..8998 -> "动车组确认列车"
                else -> "动车组检测、确认列车"
            }
        }

        // 3. 特快及快运货物班列 (X字头)
        if (tn.startsWith("X")) {
            val num = tn.removePrefix("X").toIntOrNull()
            return when {
                num != null && num in 1..198 -> "特快货物班列"
                num != null && num in 201..398 -> "快速货物班列"
                num != null && (num in 401..998 || num in 2401..2998) -> "货物快运列车"
                num != null && num in 8001..8998 -> "中欧、中亚集装箱班列"
                num != null && num in 9001..9500 -> "中亚集装箱班列"
                num != null && num in 9501..9998 -> "水铁联运班列"
                num != null && num > 10000 -> "货物快运列车"
                else -> "特快货物班列"
            }
        }

        // 4. 回送客车底列车
        if (tn.startsWith("00")) {
            val digits = tn.filter { it.isDigit() }
            val n = digits.toIntOrNull()
            return when {
                n != null && n in 1..100 -> "有火回送动车组车底"
                n != null && n in 101..298 -> "无火回送动车组车底"
                n != null && n in 301..498 -> "无火回送普速客车底"
                else -> "回送客车底列车"
            }
        }

        // 5. 图定客车底回送 (以单0开头)
        if (tn.startsWith("0") && tn.length >= 2 && !tn.startsWith("00")) {
            return "回送图定客车底"
        }

        // 6. 纯数字车次编号分类
        val digitsOnly = tn.filter { it.isDigit() }
        val n = digitsOnly.toIntOrNull()
        if (n != null) {
            // 普通旅客列车 (1001-7598) 与通勤列车 (7601-8998)
            if (digitsOnly.length <= 4) {
                return when (n) {
                    in 1001..5998 -> "普通旅客快车"
                    in 6001..7598 -> "普通旅客慢车"
                    in 7601..8998 -> "通勤列车"
                    in 1..1000 -> "普通旅客快车"
                    else -> "普通旅客列车"
                }
            }

            // 5位纯数字货运、单机与路用等车次分类
            return when (n) {
                in 10001..19998 -> "技术直达列车"
                in 20001..29998 -> "直通货物列车"
                in 30001..39998 -> "区段摘挂列车"
                in 40001..44998 -> "摘挂列车"
                in 45001..49998 -> "小运转列车"
                in 50001..50998 -> "客车单机"
                in 51001..51998 -> "货车单机"
                in 52001..52998 -> "小运转单机"
                in 53001..54998 -> "补机"
                in 55001..55300 -> "试运转列车"
                in 55301..55998 -> "试运转列车"
                in 56001..56998 -> "轻油动车、轨道车"
                in 57001..57998 -> "路用列车"
                in 58101..58998 -> "救援列车"
                in 60001..69998 -> "自备列车"
                in 70001..70998 -> "超限货物列车"
                in 71001..77998 -> "重载货物列车"
                in 78001..78998 -> "保温列车"
                in 80001..81998 -> "普快货物班列"
                in 82001..84998 -> "煤炭直达列车"
                in 85001..85998 -> "石油直达列车"
                in 86001..86998 -> "始发直达列车"
                in 87001..87998 -> "空车直达列车"
                in 90001..91998 -> "军用列车"
                else -> "货物列车"
            }
        }

        return "货物列车"
    }
}
