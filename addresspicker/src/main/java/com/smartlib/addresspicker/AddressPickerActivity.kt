package com.smartlib.addresspicker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_address_picker.*
import java.io.Serializable
import java.text.DateFormat
import java.util.*

class AddressPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private val TAG = AddressPickerActivity::class.java.simpleName
        /**
         * Code used in requesting runtime permissions.
         */
        private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
        private val AUTOCOMPLETE_REQUEST_CODE = 12;

        /**
         * Constant used in the location settings dialog.
         */
        private val REQUEST_CHECK_SETTINGS = 0x1

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 60000   //1 min
        private val UPDATE_INTERVAL_IN_MINUTE: Long = 5 * 60 * 1000     //5 mins

        /**
         * The fastest rate for active location updates. Exact. Updates will never be more frequent
         * than this value.
         */
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

        // Keys for storing activity state in the Bundle.
        private val KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
        private val KEY_LOCATION = "location"
        private val KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string"
        /**
         * Send back the selected address*/
        val RESULT_ADDRESS = "address"

        /**
         * Initail optional coordinates*/
        val ARG_LAT_LNG = "arg_lat_lng"

        /**
         * To show markers on screen*/
        val ARG_LIST_PIN = "list_pins"

        /**
         * Set zoom level of map*/
        val ARG_ZOOM_LEVEL = "level_zoom"
    }

    private var mMap: GoogleMap? = null
    private var mAddress: Address? = null
    private var mZoomLevel = 10.0f
    private var mDefaultLocation: LatLng? = null
    private var mPinList: ArrayList<Pin>? = null

    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Provides access to the Location Settings API.
     */
    private var mSettingsClient: SettingsClient? = null

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private var mLocationRequest: LocationRequest? = null

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private var mLocationSettingsRequest: LocationSettingsRequest? = null

    /**
     * Callback for Location events.
     */
    private var mLocationCallback: LocationCallback? = null

    /**
     * Represents a geographical location.
     */
    private var mCurrentLocation: Location? = null

    /**
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    private var mRequestingLocationUpdates: Boolean? = null

    /**
     * Time when the location was updated represented as a String.
     */
    private var mLastUpdateTime: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPlacesApi()
        setContentView(R.layout.activity_address_picker)
        if (intent.hasExtra(ARG_LAT_LNG)) {
            val latLng =
                intent.getSerializableExtra(ARG_LAT_LNG) as MyLatLng
            mDefaultLocation =
                LatLng(latLng.latitude, latLng.longitude)
        }
        if (intent.hasExtra(ARG_LIST_PIN)) {
            mPinList = intent.getSerializableExtra(ARG_LIST_PIN) as ArrayList<Pin>
        }
        mZoomLevel = intent.getFloatExtra(ARG_ZOOM_LEVEL, 10.0f)



        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setSupportActionBar(toolbar)
        mRequestingLocationUpdates = true
        mLastUpdateTime = ""

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSettingsClient = LocationServices.getSettingsClient(this)

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()
        val mapFragment: SupportMapFragment =
            this.supportFragmentManager?.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        use_this_location.setOnClickListener {
            val intent = Intent()
            intent.putExtra(RESULT_ADDRESS, mAddress)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        fab_current_location.setOnClickListener {
            if (mRequestingLocationUpdates!! && checkPermissions()) {
                if (mCurrentLocation != null && mMap != null) {
                    mMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                            LatLng(
                                mCurrentLocation?.latitude!!,
                                mCurrentLocation?.longitude!!
                            )
                        )
                    )
                } else {
                    startLocationUpdates()
                }
            } else if (!checkPermissions()) {
                requestPermissions()
            }
        }
    }

    private fun initPlacesApi() {
        try {
            val applicationInfo = getPackageManager().getApplicationInfo(
                getPackageName(),
                PackageManager.GET_META_DATA
            );
            val bundle = applicationInfo.metaData;
            val apiKey = bundle.getString("com.google.android.geo.API_KEY");
            if (!apiKey.isNullOrEmpty()) {
                // Initialize the SDK
                Places.initialize(getApplicationContext(), apiKey);
            }

        } catch (e: java.lang.Exception) {
            //Resolve error for not existing meta-tag, inform the developer about adding his api key
        }
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                    KEY_REQUESTING_LOCATION_UPDATES
                )
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable<Location>(KEY_LOCATION)
                moveMapToLocation(
                    LatLng(
                        mCurrentLocation?.latitude!!,
                        mCurrentLocation?.longitude!!
                    )
                )
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime = savedInstanceState.getString(KEY_LAST_UPDATED_TIME_STRING)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        startLocationUpdates()
        mMap = googleMap;

        if (!mPinList?.isNullOrEmpty()!!) {
            for (pin in mPinList!!) {
                val options =
                    MarkerOptions().position(LatLng(pin.latLng.latitude, pin.latLng.longitude))
                if (pin.title?.isNotEmpty()!!) {
                    options.title(pin.title)
                    mMap?.addMarker(options)
                }
            }
        }

        if (mDefaultLocation != null) {
            moveMapToLocation(
                LatLng(
                    mDefaultLocation?.latitude!!,
                    mDefaultLocation?.longitude!!
                )
            );
        } else if (mCurrentLocation != null) {
            mMap?.animateCamera(
                CameraUpdateFactory.newLatLng(
                    LatLng(
                        mCurrentLocation?.latitude!!,
                        mCurrentLocation?.longitude!!
                    )
                )
            )
        }
        mMap?.setOnCameraIdleListener {
            val midLatLng = mMap?.cameraPosition?.target;
            setLocationFromGeoCoder(midLatLng)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setLocationFromGeoCoder(midLatLng: LatLng?) {
        try {
            val geo = Geocoder(
                applicationContext,
                Locale.getDefault()
            );
            selected_cordinates.text =
                "(" + midLatLng?.latitude!!.toString() + "," + midLatLng.longitude + ")"

            val addresses =
                geo.getFromLocation(midLatLng.latitude, midLatLng.longitude, 1);
            if (addresses.isEmpty()) {
                selected_address.text = "Waiting for Location";
            } else {
                if (addresses.size > 0) {
                    mAddress = addresses[0]
                    selected_address.text = addresses[0].getAddressLine(0);
                    //Toast.makeText(getApplicationContext(), "Address:- " + addresses.get(0).getFeatureName() + addresses.get(0).getAdminArea() + addresses.get(0).getLocality(), Toast.LENGTH_LONG).show();
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace(); // getFromLocation() may sometimes fail
        }
    }


    /**
     * Sets up the location request. Android has two location request settings:
     * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     *
     *
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     *
     *
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest!!.setInterval(UPDATE_INTERVAL_IN_MINUTE)

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest!!.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)

        mLocationRequest!!.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    }

    /**
     * Creates a callback for receiving location events.
     */
    private fun createLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
//                super.onLocationResult(locationResult)
                val isFoundFirstTime = mCurrentLocation == null
                mCurrentLocation = locationResult.getLastLocation()
                if (isFoundFirstTime && mCurrentLocation != null && mDefaultLocation == null) {
                    moveMapToLocation(
                        LatLng(
                            mCurrentLocation?.latitude!!,
                            mCurrentLocation?.longitude!!
                        )
                    )
                }
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
            }
        }
    }

    /**
     * Uses a [com.google.android.gms.location.LocationSettingsRequest.Builder] to build
     * a [com.google.android.gms.location.LocationSettingsRequest] that is used for checking
     * if a device has the needed location settings.
     */
    private fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        mLocationSettingsRequest = builder.build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                Activity.RESULT_OK -> Log.i(
                    TAG,
                    "User agreed to make required location settings changes."
                )
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "User chose not to make required location settings changes.")
                    mRequestingLocationUpdates = false
                }
            }// Nothing to do. startLocationupdates() gets called in onResume again.

            AUTOCOMPLETE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    var place = Autocomplete.getPlaceFromIntent(data!!);
                    moveMapToLocation(place.latLng!!)
                    selected_address?.text = place.address
                    Log.i(TAG, "Place: " + place.getName() + ", " + place.getId());
                } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                    // TODO: Handle the error.
                    var status = Autocomplete.getStatusFromIntent(data!!);
                    Log.i(TAG, status.getStatusMessage());
                } else if (resultCode == RESULT_CANCELED) {
                    // The user canceled the operation.
                }
            }

        }
    }

    private fun moveMapToLocation(latLng: LatLng) {
        val center = CameraUpdateFactory.newLatLng(latLng)
        val zoom = CameraUpdateFactory.zoomTo(mZoomLevel)
        mMap?.moveCamera(zoom)
        mMap?.moveCamera(center)
    }


    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private fun startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient!!.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener(this, object : OnSuccessListener<LocationSettingsResponse> {
                override fun onSuccess(locationSettingsResponse: LocationSettingsResponse) {
                    Log.i(TAG, "All location settings are satisfied.")


                    mFusedLocationClient!!.requestLocationUpdates(
                        mLocationRequest,
                        mLocationCallback, Looper.myLooper()
                    )
                    mRequestingLocationUpdates = true
                }
            })
            .addOnFailureListener(this, object : OnFailureListener {
                override fun onFailure(e: Exception) {
                    val statusCode = (e as ApiException).getStatusCode()
                    when (statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                            Log.i(
                                TAG,
                                "Location settings are not satisfied. Attempting to upgrade " + "location settings "
                            )
                            try {
                                // Show the dialog by calling startResolutionForResult(), and check the
                                // result in onActivityResult().
                                val rae = e as ResolvableApiException
                                rae.startResolutionForResult(
                                    this@AddressPickerActivity,
                                    REQUEST_CHECK_SETTINGS
                                )
                            } catch (sie: IntentSender.SendIntentException) {
                                Log.i(TAG, "PendingIntent unable to execute request.")
                            }

                        }
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                            val errorMessage =
                                "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                            Log.e(TAG, errorMessage)
                            Toast.makeText(
                                this@AddressPickerActivity,
                                errorMessage,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            mRequestingLocationUpdates = false
                        }
                    }

                }
            })
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private fun stopLocationUpdates() {
        if ((!mRequestingLocationUpdates!!)) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.")
            return
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
            .addOnCompleteListener(this, object : OnCompleteListener<Void> {
                override fun onComplete(task: Task<Void>) {
                    mRequestingLocationUpdates = false
                }
            })
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val menuInflater = getMenuInflater()
        menuInflater.inflate(R.menu.menu_home, menu);
        return super.onCreateOptionsMenu(menu);
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.action_search) {

            var fields: List<Place.Field> = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS
            )

            var intent: Intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN, fields
            )
                .build(this);
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);

        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onResume() {
        super.onResume()
        // Within {@code onPause()}, we remove location updates. Here, we resume receiving
        // location updates if the user has requested them.
        if (mRequestingLocationUpdates!! && checkPermissions()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()

        // Remove location updates to save battery.
        stopLocationUpdates()
    }

    /**
     * Stores activity data in the Bundle.
     */
    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates!!)
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation)
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime)
        super.onSaveInstanceState(savedInstanceState)
    }

    /**
     * Shows a [Snackbar].
     *
     * @param mainTextStringId The id for the string resource for the Snackbar text.
     * @param actionStringId   The text of the action item.
     * @param listener         The listener associated with the Snackbar action.
     */
    private fun showSnackbar(
        mainTextStringId: Int, actionStringId: Int,
        listener: View.OnClickListener
    ) {
        Snackbar.make(
            findViewById<View>(android.R.id.content),
            getString(mainTextStringId),
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(getString(actionStringId), listener).show()
    }

    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale,
                android.R.string.ok, View.OnClickListener {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@AddressPickerActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this@AddressPickerActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission granted, updates requested, starting location updates")
                startLocationUpdates()
            } else {
                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                showSnackbar(R.string.permission_denied_explanation,
                    R.string.settings, View.OnClickListener {
                        // Build intent that displays the App settings screen.
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts(
                            "package",
                            BuildConfig.APPLICATION_ID, null
                        )
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    })
            }
        }
    }
}

class MyLatLng(var latitude: Double, var longitude: Double) : Serializable
class Pin(var latLng: com.smartlib.addresspicker.MyLatLng, var title: String?) : Serializable
