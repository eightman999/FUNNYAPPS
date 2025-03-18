package com.shunlight_library.microwaveconverter

class MicrowaveConverter {
    // デフォルト値の設定
    private var _sourceWattage: Int = 500
    private var _targetWattage: Int = 600
    private var _sourceMinutes: Int = 2
    private var _sourceSeconds: Int = 0

    // プロパティ（getterとsetter）
    var sourceWattage: Int
        get() = _sourceWattage
        set(value) {
            _sourceWattage = validateWattage(value)
        }

    var targetWattage: Int
        get() = _targetWattage
        set(value) {
            _targetWattage = validateWattage(value)
        }

    var sourceMinutes: Int
        get() = _sourceMinutes
        set(value) {
            _sourceMinutes = validateMinutes(value)
        }

    var sourceSeconds: Int
        get() = _sourceSeconds
        set(value) {
            _sourceSeconds = validateSeconds(value)
        }

    /**
     * 時間の変換を行う
     * @return Pair<Int, Int> 分と秒のペア
     */
    fun convert(): Pair<Int, Int> {
        // 元のワット数と時間から総エネルギーを計算
        val totalSourceTimeInSeconds = sourceMinutes * 60 + sourceSeconds
        val totalEnergy = sourceWattage * totalSourceTimeInSeconds

        // 目標ワット数での時間を計算
        if (targetWattage <= 0) {
            return Pair(0, 0)  // ゼロ除算を防ぐ
        }

        val targetTimeInSeconds = kotlin.math.round(totalEnergy.toFloat() / targetWattage).toInt()
        val targetMinutes = targetTimeInSeconds / 60
        val targetSeconds = targetTimeInSeconds % 60

        return Pair(targetMinutes, targetSeconds)
    }

    /**
     * ワット数のバリデーション
     */
    private fun validateWattage(wattage: Int): Int {
        return wattage.coerceIn(100, 1500)
    }

    /**
     * 分のバリデーション
     */
    private fun validateMinutes(minutes: Int): Int {
        return minutes.coerceIn(0, 60)
    }

    /**
     * 秒のバリデーション
     */
    private fun validateSeconds(seconds: Int): Int {
        return seconds.coerceIn(0, 59)
    }

    /**
     * 文字列からIntに変換する安全なメソッド
     */
    fun safeParseInt(value: String, defaultValue: Int): Int {
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }
}