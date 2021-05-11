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
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.bottom_sheet_barcode_scanner.*
import kotlinx.android.synthetic.main.image_barcode_scanner.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class BarcodeScanner : AppCompatActivity() {


    companion object {
        private const val TAG = "BarcodeScanner"
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
        setContentView(R.layout.activity_barcode_scanner)

        bottomSheetBehavior = BottomSheetBehavior.from(bottom_sheet)
    }

    fun cameraBarcodeScanner(view: View){
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

    fun galleryBarcodeScanner(view : View){
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

            if (requestCode == SELECT_PICTURE) {
                val selectedImage = data?.data
                val selectedImageBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(selectedImage!!))
                image_barcode_scanner.setImageBitmap(selectedImageBitmap)

                runBarcodeScanner(selectedImageBitmap)

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

                image_barcode_scanner.setImageBitmap(rotatedBitmap)
                runBarcodeScanner(rotatedBitmap)
            }
        }
    }

    private fun runBarcodeScanner(bitmap : Bitmap?){

        val image = FirebaseVisionImage.fromBitmap(bitmap!!)

        val options = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE, FirebaseVisionBarcode.FORMAT_AZTEC)
            .build()

        val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options)

        detector.detectInImage(image)
            .addOnSuccessListener {
                processBarcodeScanner(it)
            }.addOnFailureListener {
                Toast.makeText(this, "process Failed", Toast.LENGTH_SHORT).show()
            }



    }

    private fun processBarcodeScanner(barcodes : MutableList<FirebaseVisionBarcode>){

        if (barcodes.size == 0){
            Toast.makeText(this, "no barcodes found", Toast.LENGTH_SHORT).show()
            return
        }

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        for(barcode in barcodes){

            val valueType = barcode.valueType

            when(valueType){

                FirebaseVisionBarcode.TYPE_WIFI ->{
                    val ssid = barcode.wifi?.ssid
                    val password = barcode.wifi?.password
                    val encryption = barcode.wifi?.encryptionType

                    text_view_barcode_scanner.text = "WIFI \n ssid : $ssid \n password : $password \n encryption : $$encryption"
                }
                FirebaseVisionBarcode.TYPE_CALENDAR_EVENT -> {
                    val description = barcode.calendarEvent?.description
                    val start = barcode.calendarEvent?.start
                    val end = barcode.calendarEvent?.end
                    val organizer = barcode.calendarEvent?.organizer
                    val summary = barcode.calendarEvent?.summary
                    val status = barcode.calendarEvent?.status
                    val location = barcode.calendarEvent?.location
                    text_view_barcode_scanner.text = "calendar\ndescription : $description \nstart: $start \nend: $end \norganizer: $organizer \nsummary: $summary\n status: $status \nlocation: $location"
                }
                FirebaseVisionBarcode.TYPE_CONTACT_INFO -> {
                    val addresses = barcode.contactInfo?.addresses
                    val emails = barcode.contactInfo?.emails
                    val phones = barcode.contactInfo?.phones
                    val names = barcode.contactInfo?.name
                    val organization = barcode.contactInfo?.organization
                    val title = barcode.contactInfo?.title
                    val urls = barcode.contactInfo?.urls
                    text_view_barcode_scanner.text = "contact\naddresses: $addresses\nemails: $emails\nphones: $phones\nnames: $names\norganization: $organization\ntitle: $title\nurl: $urls"
                }
                FirebaseVisionBarcode.TYPE_DRIVER_LICENSE -> {
                    val city = barcode.driverLicense?.addressCity
                    val state = barcode.driverLicense?.addressState
                    val street = barcode.driverLicense?.addressStreet
                    val zip = barcode.driverLicense?.addressZip
                    val birthDate = barcode.driverLicense?.birthDate
                    val document = barcode.driverLicense?.documentType
                    val expiry = barcode.driverLicense?.expiryDate
                    val firstName = barcode.driverLicense?.firstName
                    val middleName = barcode.driverLicense?.middleName
                    val lastName = barcode.driverLicense?.lastName
                    val gender = barcode.driverLicense?.gender
                    val issueDate = barcode.driverLicense?.issueDate
                    val issueCountry = barcode.driverLicense?.issuingCountry
                    val licenseNumber = barcode.driverLicense?.licenseNumber
                }
                FirebaseVisionBarcode.TYPE_EMAIL -> {
                    val type = barcode.email?.type
                    val address = barcode.email?.address
                    val body = barcode.email?.body
                    val subject = barcode.email?.subject
                }
                FirebaseVisionBarcode.TYPE_GEO -> {
                    val lat = barcode.geoPoint?.lat
                    val lng = barcode.geoPoint?.lng
                }
                FirebaseVisionBarcode.TYPE_PHONE -> {
                    val number = barcode.phone?.number
                    val type = barcode.phone?.type
                }
                FirebaseVisionBarcode.TYPE_SMS -> {
                    val message = barcode.sms?.message
                    val number = barcode.sms?.phoneNumber
                }
                FirebaseVisionBarcode.TYPE_URL -> {
                    val title = barcode.url?.title
                    val url = barcode.url?.url
                }
            }
        }
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
