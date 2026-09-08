package com.snatik.storage.app.feature.disk

import androidx.compose.ui.geometry.Rect

/** One tile of a treemap, carrying the item it stands for. */
data class TreemapTile<T>(val item: T, val rect: Rect)

/**
 * Squarified treemap layout (Bruls, Huizing, van Wijk). Items must be sorted by weight, largest
 * first. Returns one tile per item with a positive weight.
 */
fun <T> squarify(items: List<T>, weight: (T) -> Long, bounds: Rect): List<TreemapTile<T>> {
    val positive = items.filter { weight(it) > 0 }
    val total = positive.sumOf { weight(it) }.toDouble()
    if (positive.isEmpty() || total <= 0 || bounds.width <= 0 || bounds.height <= 0) return emptyList()
    val area = bounds.width.toDouble() * bounds.height.toDouble()
    val scaled = positive.map { it to weight(it) / total * area }

    val tiles = ArrayList<TreemapTile<T>>(positive.size)
    var free = bounds
    var row = ArrayList<Pair<T, Double>>()
    var index = 0
    while (index < scaled.size) {
        val side = minOf(free.width, free.height).toDouble()
        val next = scaled[index]
        if (row.isEmpty() || worst(row, side) >= worst(row + next, side)) {
            row.add(next)
            index++
        } else {
            free = layoutRow(row, free, tiles)
            row = ArrayList()
        }
    }
    if (row.isNotEmpty()) layoutRow(row, free, tiles)
    return tiles
}

private fun <T> worst(row: List<Pair<T, Double>>, side: Double): Double {
    val sum = row.sumOf { it.second }
    if (sum <= 0.0 || side <= 0.0) return Double.MAX_VALUE
    val max = row.maxOf { it.second }
    val min = row.minOf { it.second }
    val s2 = side * side
    return maxOf(s2 * max / (sum * sum), sum * sum / (s2 * min))
}

private fun <T> layoutRow(row: List<Pair<T, Double>>, free: Rect, out: MutableList<TreemapTile<T>>): Rect {
    val sum = row.sumOf { it.second }
    val horizontal = free.width >= free.height
    return if (horizontal) {
        // Row occupies a vertical strip on the left of the free space.
        val stripWidth = (sum / free.height).toFloat()
        var y = free.top
        for ((item, a) in row) {
            val h = (a / stripWidth).toFloat()
            out += TreemapTile(item, Rect(free.left, y, free.left + stripWidth, y + h))
            y += h
        }
        Rect(free.left + stripWidth, free.top, free.right, free.bottom)
    } else {
        val stripHeight = (sum / free.width).toFloat()
        var x = free.left
        for ((item, a) in row) {
            val w = (a / stripHeight).toFloat()
            out += TreemapTile(item, Rect(x, free.top, x + w, free.top + stripHeight))
            x += w
        }
        Rect(free.left, free.top + stripHeight, free.right, free.bottom)
    }
}
