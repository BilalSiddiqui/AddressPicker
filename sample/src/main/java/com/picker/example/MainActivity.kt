package com.picker.example

import android.app.Activity
import android.content.Intent
import android.location.Address
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartlib.addresspicker.AddressPickerActivity
import com.smartlib.addresspicker.AddressPickerActivity.Companion.RESULT_ADDRESS
import com.smartlib.addresspicker.MyLatLng
import com.smartlib.addresspicker.Pin
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    companion object {
        val REQUEST_ADDRESS = 132
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_address?.setOnClickListener {
            val intent = Intent(this@MainActivity, AddressPickerActivity::class.java)
            intent.putExtra(AddressPickerActivity.ARG_LAT_LNG,MyLatLng(42.5328966, -122.7751082))
            val pinList=ArrayList<Pin>()
            pinList.add(Pin(MyLatLng(42.532890, -122.774881),"Work"))   //Right marker
            pinList.add(Pin(MyLatLng(42.532972, -122.775478),"Home"))   //Left marker
            intent.putExtra(AddressPickerActivity.ARG_LIST_PIN,  pinList)
            intent.putExtra(AddressPickerActivity.ARG_ZOOM_LEVEL,  17.0f)
            startActivityForResult(intent, REQUEST_ADDRESS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ( resultCode == Activity.RESULT_OK) {
            val address: Address? = data?.getParcelableExtra(RESULT_ADDRESS) as Address
            selected_address1.text = "No. "+ address?.subThoroughfare + ", " + address?.thoroughfare
            selected_address2.text=  address?.locality + ", " + address?.adminArea + ", " + address?.countryName+ " "+address?.postalCode;
            selected_country_code.text= "Country Code= "+ address?.countryCode

            val bundle: Bundle = data?.extras
            val userChosenLatitude: Double = bundle.getDouble("latitude")
            val userChosenLongitude: Double = bundle.getDouble("longitude")
            selected_latitude.text= "Latitude= " + userChosenLatitude.toString()
            selected_longitude.text= "Longitude= "+ userChosenLongitude.toString()
        }
    }

}
