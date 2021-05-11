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
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions
import kotlinx.android.synthetic.main.bottom_sheet_face_detection.*
import kotlinx.android.synthetic.main.image_image_labeling.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ImageLabeling : AppCompatActivity() {

    companion object {
        private const val TAG = "ImageLabeling"
        private const val TAKE_PICTURE = 0
        private const val SELECT_PICTURE = 1

        private const val PERMISSION_CAMERA = 1
        private const val PERMISSION_WRITE = 2
        private const val PERMISSION_READ = 3

    }
    var currentPhotoPath: String? = null
    var bottomSheetBehavior : BottomSheetBehavior<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_labeling)

        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
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

    fun cameraImageLabeling(view: View){
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

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = this.absolutePath
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

    fun galleryImageLabeling(view : View){
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK){

            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED

            if (requestCode == SELECT_PICTURE) {
                val selectedImage = data?.data
                val selectedImageBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(selectedImage!!))
                image_image_labeling.setImageBitmap(selectedImageBitmap)

                runImageLabeling(selectedImageBitmap)

            }else if(requestCode == TAKE_PICTURE){

                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                val bitmap = BitmapFactory.decodeFile(currentPhotoPath, options)

                val ei = ExifInterface(currentPhotoPath!!)
                val orientation = ei.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED)
                var rotatedBitmap : Bitmap? = null

                rotatedBitmap = when(orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 ->  rotateImage(bitmap, 90F)
                    ExifInterface.ORIENTATION_ROTATE_180 ->  rotateImage(bitmap, 180F)
                    ExifInterface.ORIENTATION_ROTATE_270 ->  rotateImage(bitmap, 270F);
                    else ->  bitmap
                }

                image_image_labeling.setImageBitmap(rotatedBitmap)
                runImageLabeling(rotatedBitmap)
            }
        }
    }

    private fun runImageLabeling(bitmap : Bitmap?){

        val image = FirebaseVisionImage.fromBitmap(bitmap!!)

        val options = FirebaseVisionOnDeviceImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()

        val detector = FirebaseVision.getInstance().getOnDeviceImageLabeler(options)

        detector.processImage(image)
            .addOnSuccessListener {
                processImageLabelerResult(it)
            }
            .addOnFailureListener {
                Toast.makeText(this, "process Failed", Toast.LENGTH_SHORT).show()
                Log.d(TAG,"$it")
            }
    }

    private fun processImageLabelerResult(imageLabels : MutableList<FirebaseVisionImageLabel>){

        if (imageLabels.size == 0){
            Toast.makeText(this, "no label has found", Toast.LENGTH_SHORT).show()
            return
        }

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        var labelText = ""

        for (imageLabel in imageLabels){

            val text = imageLabel.text
            val confidence = imageLabel.confidence
            val entityId = imageLabel.entityId

            labelText += "entityId = $entityId, text = $text, confidence = $confidence \n"
        }

        text_view_image_labeling.text = labelText

    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

}
