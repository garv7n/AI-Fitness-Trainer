/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.*
import android.util.Log
import kotlinx.coroutines.*
import android.widget.TextView
import com.google.mediapipe.examples.poselandmarker.ml.LiteUpdown
import com.google.mediapipe.examples.poselandmarker.ml.Stance
import com.google.mediapipe.examples.poselandmarker.ml.Stancev3
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val fl = 80f

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var txtPaint = Paint()
    private var rectPaint = Paint()
    private var strokePaint = Paint()

    private var angles = mutableMapOf("left_knee" to 0f, "right_knee" to 0f, "left_elbow" to 0f)
    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scope = CoroutineScope(Dispatchers.Main)
    private var frame = 0
    private var initSquat = false
    private var numOfSquats = 0
    private var squatText = "Not ready "
    private var isSquatting = false
    private var downStartTime = 0L
    private var selectedExercise: String = ""
    private var lastPose = ""
    private var lastStanceCheck = 0L
    private var kneeList = mutableListOf<Float>()
    private var predUpDownText = ""
    private var predStanceText = ""

    private val frameGuideText = "Please get in frame"
    private val frameGuideRectPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }
    private val frameGuideTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }



    init {
        initPaints()

    }

    fun convertToByteBuffer(landmarks: MutableList<Float>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(landmarks.size * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder()) // Ensure correct byte order

        for (value in landmarks) {
            byteBuffer.putFloat(value) // Convert each float into bytes
        }

        byteBuffer.rewind() // Reset position for reading
        return byteBuffer
    }

    fun setExercise(exercise: String) {
        selectedExercise = exercise
        invalidate() // Redraw the view to reflect the new exercise
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        txtPaint.reset()
        strokePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        txtPaint.color = Color.WHITE
        txtPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        txtPaint.style = Paint.Style.FILL
        txtPaint.textSize = 80f
        txtPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 13f
        strokePaint.color = Color.BLACK
        strokePaint.setAntiAlias(true);
        strokePaint.textSize = 80f


        rectPaint.color = Color.parseColor("#970000")
        rectPaint.strokeWidth = 10f
        rectPaint.style = Paint.Style.STROKE
    }


    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (!checkPresence()){
            val rectWidth = width.toFloat()
            val rectHeight = height.toFloat()
            val rectLeft = 0f
            val rectTop = 0f
            val rectRight = rectWidth
            val rectBottom = rectHeight
            canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, frameGuideRectPaint)
            val textX = width / 2f
            val textY = height / 2f
            canvas.drawText(frameGuideText, textX, textY, frameGuideTextPaint)
            }

        else {
            val activity = context as? MainActivity
            activity?.runOnUiThread {
                activity.findViewById<TextView>(R.id.repsCount)?.text =
                    "Reps: " + numOfSquats.toString()
                activity.findViewById<TextView>(R.id.upDown)?.text =
                    "Squatting: "+predUpDownText
                activity.findViewById<TextView>(R.id.stanceIs)?.text =
                    "Stance: "+predStanceText
            }

            results?.let { poseLandmarkerResult ->
                for (landmark in poseLandmarkerResult.landmarks()) {
                    for (normalizedLandmark in landmark) {
                        canvas.drawPoint(
                            normalizedLandmark.x() * imageWidth * scaleFactor,
                            normalizedLandmark.y() * imageHeight * scaleFactor,
                            pointPaint
                        )
                    }
                }
            }

        }







        frame++
    }
    fun calcAngle(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Float {
        val radians = atan2(c.second - b.second, c.first - b.first) -
                atan2(a.second - b.second, a.first - b.first)
        var angle = abs(radians * 180.0 / Math.PI).toFloat()

        if (angle > 180.0) {
            angle = 360 - angle
        }

        return angle
    }

    fun getJoints(angles: MutableMap<String, Float>) {
        results?.let { poseLandmarkerResult ->
            if (poseLandmarkerResult.landmarks().isNotEmpty()) {
                val left_shoulder =
                    Pair(poseLandmarkerResult.landmarks()[0][11].x(), poseLandmarkerResult.landmarks()[0][11].y())
                val left_elbow =
                    Pair(poseLandmarkerResult.landmarks()[0][13].x(), poseLandmarkerResult.landmarks()[0][13].y())
                val left_wrist =
                    Pair(poseLandmarkerResult.landmarks()[0][15].x(), poseLandmarkerResult.landmarks()[0][15].y())
                angles["left_elbow"] = calcAngle(left_shoulder, left_elbow, left_wrist)
                val right_shoulder =
                    Pair(poseLandmarkerResult.landmarks()[0][12].x(), poseLandmarkerResult.landmarks()[0][12].y())
                val right_elbow =
                    Pair(poseLandmarkerResult.landmarks()[0][14].x(), poseLandmarkerResult.landmarks()[0][14].y())
                val right_wrist =
                    Pair(poseLandmarkerResult.landmarks()[0][16].x(), poseLandmarkerResult.landmarks()[0][16].y())
                angles["right_elbow"] = calcAngle(right_shoulder, right_elbow, right_wrist)

                val left_knee =
                    Pair(poseLandmarkerResult.landmarks()[0][25].x(), poseLandmarkerResult.landmarks()[0][25].y())
                val left_ankle =
                    Pair(poseLandmarkerResult.landmarks()[0][27].x(), poseLandmarkerResult.landmarks()[0][27].y())
                val left_hip =
                    Pair(poseLandmarkerResult.landmarks()[0][23].x(), poseLandmarkerResult.landmarks()[0][23].y())
                angles["left_knee"] = calcAngle(left_hip, left_knee, left_ankle)
                val right_hip =
                    Pair(poseLandmarkerResult.landmarks()[0][24].x(), poseLandmarkerResult.landmarks()[0][24].y())
                val right_knee =
                    Pair(poseLandmarkerResult.landmarks()[0][26].x(), poseLandmarkerResult.landmarks()[0][26].y())
                val right_ankle =
                    Pair(poseLandmarkerResult.landmarks()[0][28].x(), poseLandmarkerResult.landmarks()[0][28].y())
                angles["right_knee"] = calcAngle(right_hip, right_knee, right_ankle)


//                Log.i("z shoulder", (poseLandmarkerResult.landmarks()[0][12].z().toString()+" " + poseLandmarkerResult.landmarks()[0][11].z().toString()))
//                Log.d("angles", angles.toString())
            }
        }
    }

//    fun checkForm(tolerance: Double=1e-2): Boolean {
//        if(angles.isNotEmpty()) {
//            results?.let { poseLandmarkerResult ->
//
//                val functions = listOf(
////                    "checkStance" to :: checkStance,
//                    "checkKnees" to :: checkKnees
//                )
//                for ((name, func) in functions) {
//                    val (bool,result) = func(1e-2)
//                    if (!bool) {
//                        val message = result
//                        Log.d("checkForm", "failed $name because $message")
//                        return false
//                    }
//                }
//            }
//        }
//        isSquatting = true
//        return true
//    }
    fun checkPresence(): Boolean{
        results?.let { poseLandmarkerResult ->
            if (poseLandmarkerResult.landmarks().isNotEmpty()) {
                for (landmark in poseLandmarkerResult.landmarks()[0]) {
                    if (landmark.visibility().orElse(0.0f) < 0.5) {
                        return false
                    }
                }
            }
        }
    return true
    }


    fun checkStance(): Pair<Boolean, String> {
        var tolerance: Double
        results?.let { poseLandmarkerResult ->
            if (poseLandmarkerResult.landmarks().isNotEmpty()) {
                val dist_shoul: Float =
                    abs((poseLandmarkerResult.landmarks()[0][11].x()) - poseLandmarkerResult.landmarks()[0][12].x())
                val dist_knee: Float =
                    abs((poseLandmarkerResult.landmarks()[0][25].x()) - poseLandmarkerResult.landmarks()[0][26].x())
                val leftShoulderZ = poseLandmarkerResult.landmarks()[0][11].z()
                val rightShoulderZ = poseLandmarkerResult.landmarks()[0][12].z()
//                Log.d("shoul", dist_shoul.toString())
//                Log.d("knee", dist_knee.toString())

                Log.d("dist", "differennce shoulder w knees" + abs(dist_shoul - dist_knee))
                tolerance = if(isSquatting) 4e-2 else 5e-2
                Log.e("tol", tolerance.toString())
                if (abs(dist_shoul - dist_knee) < tolerance) {  // checking knees are in line w/ shoulders and user is facing camera via shoulder z values
                    return Pair(true, "Good, maintain this stance")
                } else {
                    if (dist_shoul < dist_knee) {
                        return Pair(false, "Bring knees closer together")
                    } else {
                        return Pair(false, "Widen your stance")
                    }
                }
            }
        }
        return Pair(false, "no case")
    }
    fun checkKnees() {

        if (lastPose == "Down" && predUpDownText == "Up") {
            if (System.currentTimeMillis() - downStartTime >= 1500) {
                Log.e("ang", (angles["left_knee"]!!+ angles["right_knee"]!!).toString())
                if (kneeList.min()<=350f && checkPresence()) {
                    numOfSquats++ // Increment squat count

                    kneeList.clear()
                }
            }
        }

        if (predUpDownText == "Down" && lastPose != "Down") {
            kneeList.add(angles["left_knee"]!!+ angles["right_knee"]!!)
            downStartTime = System.currentTimeMillis() // Start tracking time when user goes down
        }

        lastPose = predUpDownText
    }
//    fun checkKnedes() {
//        if (angles.isNotEmpty()) {
//            val currentTime = System.currentTimeMillis()
//            if (lastKneeY != -1f) {
//                if (predUpDownText=="Down"){    // while in squat record knee angles
//                    kneeList.add(angles["left_knee"]!!)
//                }
////                if (predUpDownText == "UP") {   // && angles["left_knee"]!! < lastKneeY - 10f
////                    isSquatting = true
////                    squatState = "DOWN"
//
//                else if (predUpDownText == "Down" && angles["left_knee"]!! < lastKneeY) {
//                    Log.i("yhhhhh","uuuuuuuu")
//                    val squatDuration = currentTime - lastSquatTime
//
//                    if (squatDuration > 3000 && kneeList.min()<95f) {   // if squat is long enough and deep enough
//                        lastSquatTime = currentTime
//                        numOfSquats++
//                        isSquatting = false
//                        kneeList.clear()
//                    }
//                }
//
//            }
////            if (angles["left_knee"]!! < 95f &&
////                angles["right_knee"]!! <95f ) {
////                Pair(true,"passed")
////            } else{
////                Pair(false, "Squat lower")
////            }
////        }
////        return Pair(false, "no case")
//            lastKneeY = angles["left_knee"]!!
//            Log.i("lastknee", lastKneeY.toString())
//            Log.i("currentknee", angles["left_knee"].toString())
//            Log.i("kneelist", kneeList.toString())
//
//        }
//    }
//
    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        getJoints(angles)
        Log.d("angles", angles.toString())
        val landmarkList = mutableListOf<Float>()
        if (poseLandmarkerResults.landmarks().isNotEmpty()) {
            for (landmark in poseLandmarkerResults.landmarks()[0]) {
                landmarkList.add(landmark.x())
                landmarkList.add(landmark.y())
                landmarkList.add(landmark.z())
                landmarkList.add(landmark.visibility().orElse(0.0f))
            }

            for (angle in angles) {
                landmarkList.add(angle.value)
            }
            val stanceLabels = arrayOf("good", "narrow", "wide")
            val currentTime = System.currentTimeMillis()
            if (landmarkList.size == 136) {
                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 136), DataType.FLOAT32)
                val landmarkByteBuffer = convertToByteBuffer(landmarkList)
                inputFeature0.loadBuffer(landmarkByteBuffer)
                if (currentTime-lastStanceCheck>=2000 && predUpDownText=="Up"){
                    val stanceModel = Stancev3.newInstance(this.context)

                    val outputsStance = stanceModel.process(inputFeature0)
                    val outputFeature0Stance = outputsStance.outputFeature0AsTensorBuffer
                    val predictionStance = outputFeature0Stance.floatArray
                    stanceModel.close()
                    predStanceText = stanceLabels[predictionStance.indices.maxByOrNull { predictionStance[it] } ?: -1]
                    Log.i("type", predictionStance.joinToString())

                    lastStanceCheck = currentTime
                }
                val upDownModel = LiteUpdown.newInstance(this.context)


                val outputsUpDown = upDownModel.process(inputFeature0)
                val outputFeature0UpDown = outputsUpDown.outputFeature0AsTensorBuffer
                val predictionUpDown = outputFeature0UpDown.floatArray[0]

                upDownModel.close()
                Log.i("type", predictionUpDown.toString())
                predUpDownText = if(predictionUpDown>0.5) "Up" else "Down"


            }
        }

        checkKnees()

//        if (checkStance().first){
//            initSquat = true
//            rectPaint.color = Color.GREEN
//        }
//        else if (!checkStance().first && numOfSquats<1){
//            initSquat = false
//            rectPaint.color = Color.RED
//        }

        results = poseLandmarkerResults
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)

            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }
}