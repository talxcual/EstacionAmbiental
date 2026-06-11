package com.ktasoporte.estacionambiental.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.ktasoporte.estacionambiental.utils.TelemetryRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CyberpunkChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var records: List<TelemetryRecord> = emptyList()

    // Paint Objects
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A00D9FF") // Cyan con 10% opacidad
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D00D9FF") // Cyan con 30% opacidad
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7DB0D6") // color text_secondary
        textSize = 24f // Se escalará o ajustará según tamaño
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Colores de los sensores
    private val colorCO2 = Color.parseColor("#00FFAA")     // Mint
    private val colorTemp = Color.parseColor("#00D9FF")    // Cyan
    private val colorHum = Color.parseColor("#FFEB3B")     // Yellow
    private val colorTVOC = Color.parseColor("#FF4DFF")    // Magenta

    fun setData(data: List<TelemetryRecord>) {
        this.records = data
        invalidate() // Forzar redibujado
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 60f
        val paddingTop = 40f
        val paddingRight = 40f
        val paddingBottom = 120f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Dibujar Cuadrícula de Fondo (5 líneas horizontales, 6 verticales)
        drawGrid(canvas, paddingLeft, paddingTop, chartWidth, chartHeight)

        if (records.isEmpty()) {
            drawNoDataText(canvas, paddingLeft + chartWidth / 2f, paddingTop + chartHeight / 2f)
            return
        }

        // 2. Dibujar las Líneas de los Sensores con Efecto Neón
        drawSensorLines(canvas, paddingLeft, paddingTop, chartWidth, chartHeight)

        // 3. Dibujar Leyenda e Indicadores de tiempo
        drawLegend(canvas, paddingLeft, paddingTop + chartHeight + 60f, chartWidth)
        drawTimeLabels(canvas, paddingLeft, paddingTop + chartHeight + 35f, chartWidth)
    }

    private fun drawGrid(canvas: Canvas, xStart: Float, yStart: Float, width: Float, height: Float) {
        // Marco exterior
        canvas.drawRect(xStart, yStart, xStart + width, yStart + height, gridBorderPaint)

        // Líneas horizontales (25%, 50%, 75%)
        for (i in 1..3) {
            val y = yStart + (height * (i / 4f))
            canvas.drawLine(xStart, y, xStart + width, y, gridPaint)
            
            // Texto del porcentaje en el eje Y
            val pctLabel = "${(100 - i * 25)}%"
            canvas.drawText(pctLabel, xStart - 50f, y + 8f, textPaint.apply { textSize = 20f })
        }

        // Líneas verticales (simulando intervalos de tiempo)
        val numVerticalLines = 5
        for (i in 1 until numVerticalLines) {
            val x = xStart + (width * (i / numVerticalLines.toFloat()))
            canvas.drawLine(x, yStart, x, yStart + height, gridPaint)
        }
    }

    private fun drawSensorLines(canvas: Canvas, xStart: Float, yStart: Float, width: Float, height: Float) {
        val numPoints = records.size
        if (numPoints < 2) return

        val pathCO2 = Path()
        val pathTemp = Path()
        val pathHum = Path()
        val pathTVOC = Path()

        for (i in 0 until numPoints) {
            val record = records[i]
            
            // Mapear X
            val x = xStart + i * (width / (numPoints - 1))

            // Mapear e normalizar Y (0.0 a 1.0)
            val pctCO2 = ((record.co2 - 400.0) / 1600.0).coerceIn(0.0, 1.0).toFloat()
            val pctTemp = ((record.temperature - 0.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
            val pctHum = ((record.humidity - 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            val pctTVOC = ((record.tvoc - 0.0) / 500.0).coerceIn(0.0, 1.0).toFloat()

            val yCO2 = yStart + (1.0f - pctCO2) * height
            val yTemp = yStart + (1.0f - pctTemp) * height
            val yHum = yStart + (1.0f - pctHum) * height
            val yTVOC = yStart + (1.0f - pctTVOC) * height

            if (i == 0) {
                pathCO2.moveTo(x, yCO2)
                pathTemp.moveTo(x, yTemp)
                pathHum.moveTo(x, yHum)
                pathTVOC.moveTo(x, yTVOC)
            } else {
                pathCO2.lineTo(x, yCO2)
                pathTemp.lineTo(x, yTemp)
                pathHum.lineTo(x, yHum)
                pathTVOC.lineTo(x, yTVOC)
            }
        }

        // Dibujar cada línea con 3 pasadas para simular el brillo Neón
        drawNeonLine(canvas, pathCO2, colorCO2)
        drawNeonLine(canvas, pathTemp, colorTemp)
        drawNeonLine(canvas, pathHum, colorHum)
        drawNeonLine(canvas, pathTVOC, colorTVOC)
    }

    private fun drawNeonLine(canvas: Canvas, path: Path, lineColor: Int) {
        // Pasada 1: Brillo exterior grueso (muy transparente)
        linePaint.color = lineColor
        linePaint.alpha = 25 // ~10% opacidad
        linePaint.strokeWidth = 12f
        canvas.drawPath(path, linePaint)

        // Pasada 2: Brillo medio (opacidad media)
        linePaint.alpha = 90 // ~35% opacidad
        linePaint.strokeWidth = 6f
        canvas.drawPath(path, linePaint)

        // Pasada 3: Núcleo brillante delgado (opacidad total)
        linePaint.alpha = 255
        linePaint.strokeWidth = 3f
        canvas.drawPath(path, linePaint)
    }

    private fun drawTimeLabels(canvas: Canvas, xStart: Float, yStart: Float, width: Float) {
        val numPoints = records.size
        if (numPoints == 0) return

        textPaint.textSize = 20f
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Mostramos etiquetas de hora para 4 puntos equidistantes en el eje X
        val steps = listOf(0, numPoints / 3, (numPoints * 2) / 3, numPoints - 1)
        for (index in steps) {
            if (index < numPoints) {
                val record = records[index]
                val x = xStart + index * (width / (numPoints - 1))
                val hourStr = timeFormat.format(Date(record.timestamp))

                // Centrar texto horizontalmente sobre la coordenada X
                val textWidth = textPaint.measureText(hourStr)
                canvas.drawText(hourStr, x - textWidth / 2f, yStart, textPaint)
            }
        }
    }

    private fun drawLegend(canvas: Canvas, xStart: Float, yStart: Float, width: Float) {
        val labels = listOf("CO2", "TEMP", "HUM", "TVOC")
        val colors = listOf(colorCO2, colorTemp, colorHum, colorTVOC)
        
        textPaint.textSize = 22f
        val spacePerItem = width / labels.size
        
        for (i in labels.indices) {
            val x = xStart + (i * spacePerItem)
            
            // Dibujar círculo/indicador de color
            linePaint.color = colors[i]
            linePaint.style = Paint.Style.FILL
            canvas.drawCircle(x + 10f, yStart - 8f, 8f, linePaint)
            
            // Dibujar texto de leyenda
            canvas.drawText(labels[i], x + 25f, yStart, textPaint)
        }
        linePaint.style = Paint.Style.STROKE // Restaurar estilo
    }

    private fun drawNoDataText(canvas: Canvas, cx: Float, cy: Float) {
        textPaint.textSize = 26f
        val text = "ESPERANDO CONEXIÓN / SIN DATOS"
        val textWidth = textPaint.measureText(text)
        canvas.drawText(text, cx - textWidth / 2f, cy, textPaint)
    }
}
