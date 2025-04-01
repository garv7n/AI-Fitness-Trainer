package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var selectedExercise: String = ""
    private var selectedCheckImageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set click listeners for the ImageButtons
        binding.squatsImageButton.setOnClickListener {
            selectExercise("Squat", binding.squatsCheckImageView)
        }
        binding.pushupsImageButton.setOnClickListener {
            selectExercise("Deadlift", binding.pushupsCheckImageView)
        }
//        binding.jumpingJacksImageButton.setOnClickListener {
//            selectExercise("Jumping Jacks", binding.jumpingJacksCheckImageView)
//        }
//        binding.lungesImageButton.setOnClickListener {
//            selectExercise("Lunges", binding.lungesCheckImageView)
//        }


        binding.startButton.setOnClickListener {
            if (selectedExercise.isNotEmpty()) {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("EXERCISE_NAME", selectedExercise)
                startActivity(intent)
            }
        }
    }
    private fun selectExercise(exerciseName: String, checkImageView: ImageView) {
        // Reset the previous selection
        selectedCheckImageView?.visibility = ImageView.INVISIBLE

        // Update the selected exercise
        selectedExercise = exerciseName

        // Show the checkmark for the selected exercise
        checkImageView.visibility = ImageView.VISIBLE

        // Update the selected check image view
        selectedCheckImageView = checkImageView
    }
}