package com.shankar.machinelearning

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


    }

    fun textRecognition(view: View){
        startActivity(Intent(this@MainActivity, TextRecognition::class.java))
    }
    fun faceDetection(view: View){
        startActivity(Intent(this@MainActivity, FaceDetection::class.java))
    }
    fun barcodeScanner(view: View){
        startActivity(Intent(this@MainActivity, BarcodeScanner::class.java))
    }
    fun imageLabeling(view: View){
        startActivity(Intent(this@MainActivity, ImageLabeling::class.java))
    }
}
