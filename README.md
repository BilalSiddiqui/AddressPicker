# AddressPicker
A simple light weight android library to pick address from google map and places API

![Alt text](https://github.com/BilalSiddiqui/AddressPicker/blob/master/sc1.png "Pick address")
![Alt text](https://github.com/BilalSiddiqui/AddressPicker/blob/master/sc2.png "Search in places API")

Usage:

Step 1. Add it in your root build.gradle at the end of repositories:
            
      allprojects {
              repositories {
                ...
                maven { url 'https://jitpack.io' }
              }
            }


Step 2. Add the dependency

	dependencies {
	        implementation 'com.github.BilalSiddiqui:AddressPicker:Tag'
	}

Step 3. Add Google Places API key in manifest

            <meta-data
            android:name="com.google.android.geo.API_KEY"
            android:value="YOUR_KEY" />

Step 4. Start address picker activity.

            val intent = Intent(this@MainActivity, AddressPickerActivity::class.java)
            intent.putExtra(AddressPickerActivity.ARG_LAT_LNG,MyLatLng(42.5328966, -122.7751082))
            val pinList=ArrayList<Pin>()
            pinList.add(Pin(MyLatLng(42.329989, -122.3100),"Work"))
            pinList.add(Pin(MyLatLng(42.023123, -122.23414),"Home"))
            intent.putExtra(AddressPickerActivity.ARG_LIST_PIN,  pinList)
            intent.putExtra(AddressPickerActivity.ARG_ZOOM_LEVEL,  1.0f)
            startActivityForResult(intent,REQUEST_ADDRESS )

Step 5. Get result in onActivityResult.
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADDRESS && resultCode == Activity.RESULT_OK) {
            val address: Address? = data?.getParcelableExtra(RESULT_ADDRESS) as Address
            selected_address.text =
                address?.featureName + ", " + address?.locality + ", " + address?.adminArea + ", " + address?.countryName

        }
    } 

Features:

1- Search in PLACES API.

2- Search and select on map.

3- Set zoom level of map.

4- You can provide list of pin/marker for map to show

5- You can provide lat/lng to set initial postion of map through intent extras.
