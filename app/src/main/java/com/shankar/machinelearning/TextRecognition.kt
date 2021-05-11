package com.shankar.machinelearning

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.content.FileProvider
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.image_text_recognition.*
import kotlinx.android.synthetic.main.text_recognition_bottom_sheet.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class TextRecognition : AppCompatActivity() {

    var currentPhotoPath : String? = null

    var bottomSheetBehavior : BottomSheetBehavior<*>? = null

    companion object{
        private const val TAG = "TextRecognition"
        private const val TAKE_PICTURE = 1
        private const val SELECT_PICTURE = 2

        private const val PERMISSION_CAMERA = 1
        private const val PERMISSION_WRITE = 2
        private const val PERMISSION_READ = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_recognition)

        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
    }

    fun cameraTextRecognition(view : View){
        //check if we have camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            //if not request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_CAMERA
            )

            //if yes take the picture
        }else{
            dispatchTakePictureIntent()

        }
    }


    private fun dispatchTakePictureIntent() {
        //check if we have access gallery permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            //write permission is not granted lets request
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_WRITE
            )
        }else{
            //if yes take the picture
            writeOnFile()
        }

    }
    private fun writeOnFile(){
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        intent.resolveActivity(packageManager)
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.d(TAG,"exception: $ex")
            null

        }

        val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "com.shankar.machinelearning.fileprovider",
            photoFile!!
        )
        Log.d(TAG,"photo uri: $photoURI")
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(intent, TAKE_PICTURE)
    }

    fun galleryTextRecognition(view : View){
        //request permission read if not granted for gallery
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_READ
            )
        }else{
            val selectPicture = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(selectPicture, SELECT_PICTURE)
        }
    }


    private fun createImageFile() : File{

        val timeStamp = SimpleDateFormat("yyyyMMdd_hhmmss").format(Date())

        val storageDirectory : File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile("image_$timeStamp",".jpg", storageDirectory).apply {
            currentPhotoPath = this.absolutePath
        }
    }

    private fun runTextRecognition(bitmap : Bitmap){

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val recognizer = FirebaseVision.getInstance().onDeviceTextRecognizer

        recognizer.processImage(image).addOnSuccessListener {
            processTextRecognitionResult(it)
        }.addOnFailureListener {
            Toast.makeText(this, "process is failed", Toast.LENGTH_SHORT).show()
        }

    }

    private fun processTextRecognitionResult(result : FirebaseVisionText){

        val blocks = result.textBlocks

        if (blocks.size == 0){
            Toast.makeText(this, "no text recognized", Toast.LENGTH_SHORT).show()
            return
        }

        var blockText = ""
        for (block in blocks){
//            for (line in block.lines)
//                for(element in line.elements)
            blockText += block.text
        }
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        recognized_text.text = blockText
    }

    private fun rotateImage(source : Bitmap, angle : Float) : Bitmap{

        val matrix = Matrix()
        matrix.postRotate(angle)

        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            PERMISSION_CAMERA ->{
                //check if permission is granted
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    //camera permission is granted
                    dispatchTakePictureIntent()
                }
            }
            PERMISSION_WRITE ->{
                //check if permission is granted
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    writeOnFile()
                }
            }
            PERMISSION_READ -> {
                //check if permission is granted
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    val selectPicture = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(selectPicture, SELECT_PICTURE)
                }

            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK){

            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED

            if(requestCode == TAKE_PICTURE){

                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                val bitmap = BitmapFactory.decodeFile(currentPhotoPath, options)

                val ei = ExifInterface(currentPhotoPath!!)

                val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

                var rotatedBitmap : Bitmap? = null

                rotatedBitmap = when(orientation){
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90F)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180F)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270F)
                    else -> bitmap
                }

                imageTextRecognition.setImageBitmap(rotatedBitmap)
                runTextRecognition(rotatedBitmap!!)

            }else if (requestCode == SELECT_PICTURE){

                val selectedPicture = data?.data
                val selectedPictureBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(selectedPicture!!))
                imageTextRecognition.setImageBitmap(selectedPictureBitmap)
                runTextRecognition(selectedPictureBitmap)
            }
        }
    }
}
