package com.onebitmonochrome.blacksbbox.view.fake


import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import android.view.MenuItem
import com.onebitmonochrome.blacksbbox.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import top.niunaijun.blackbox.entity.location.BLocation
import com.onebitmonochrome.blacksbbox.databinding.ActivityOsmdroidBinding
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast



class FollowMyLocationOverlay : AppCompatActivity() {
    val TAG: String = "FollowMyLocationOverlay"

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1

    private val binding: ActivityOsmdroidBinding by inflate()

    lateinit var startPoint: GeoPoint
    private var targetPkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        

        
        
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        
        
        
        
        

        
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.inflateMenu(R.menu.menu_fake_location_pick)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_done) {
                finishWithResult(startPoint)
                true
            } else {
                false
            }
        }

        val location: BLocation? = intent.getParcelableExtra("location")
        targetPkg = intent.getStringExtra("pkg")

        startPoint = if (location == null) {
            GeoPoint(30.2736, 120.1563)
        } else {
            GeoPoint(location.latitude, location.longitude)
        }


        val startMarker = Marker(binding.map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        binding.map.overlays.add(startMarker)
        val mReceive: MapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                startPoint = p
                startMarker.position = p
                startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                binding.map.overlays.add(startMarker)
                toast(p.latitude.toString() + " - " + p.longitude)
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }
        binding.map.overlays.add(MapEventsOverlay(mReceive))
        val mapController = binding.map.controller
        mapController.setZoom(12.5)

        mapController.setCenter(startPoint)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult(startPoint)
                }
            }
        )
    }

    override fun onBackPressed() {
        finishWithResult(startPoint)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finishWithResult(startPoint)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        
        
        
        
        binding.map.onResume() 
    }

    override fun onPause() {
        super.onPause()
        
        
        
        
        binding.map.onPause()  
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun finishWithResult(geoPoint: GeoPoint) {
        val result = android.content.Intent()
        result.putExtra("latitude", geoPoint.latitude)
        result.putExtra("longitude", geoPoint.longitude)
        targetPkg?.let { result.putExtra("pkg", it) }
        setResult(Activity.RESULT_OK, result)
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.run {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
        finish()
    }

}
