package honours.project.NavigationApp;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import honours.project.NavigationApp.route.Route;
import honours.project.NavigationApp.route.RouteInterface;
import honours.project.NavigationApp.route.RouteHelper;
import honours.project.NavigationApp.route.routeDetails.DetailsActivity;
import honours.project.NavigationApp.route.routeDetails.DetailsFragment;
import honours.project.NavigationApp.navigation.NavigationActivity;
import honours.project.NavigationApp.navigation.NavigationService;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, RouteInterface, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final String TAG = "MainActivity";
    private static final int REQ_CODE_SPEECH_INPUT = 1;
    private static final int REQ_PROXIMITE = 2;
    private GoogleApiClient mGoogleApiClient;
    private RouteHelper routeHelper;
    private AutoCompleteTextView searchText;
    private GoogleMap mMap;
    private Polyline polyline;
    private Marker markerDepart;
    private Marker markerEnd;
    private Route route;
    private TextView infodistance;
    private boolean isExploreByTouchEnabled;
    private DetailsFragment detailsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        boolean isAccessibilityEnabled = am.isEnabled();
        isExploreByTouchEnabled = am.isTouchExplorationEnabled();

        if(isExploreByTouchEnabled)
            setContentView(R.layout.activity_main);
        else
            setContentView(R.layout.activity_main);


        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setLogo(R.drawable.compasslogo1);
        setSupportActionBar(myToolbar);
        myToolbar.setContentInsetsAbsolute(-10,-10);
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            Log.i(TAG, "Request permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION);
        } else {

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(Places.GEO_DATA_API)
                    .build();
            mGoogleApiClient.connect();

            routeHelper = new RouteHelper(mGoogleApiClient, getApplicationContext(), this);

            infodistance = (TextView) findViewById(R.id.info_distance);
            searchText = (AutoCompleteTextView) findViewById(R.id.visual_search);
            searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    Log.i(TAG, "keyboard " + actionId + " " + EditorInfo.IME_ACTION_DONE + " " + KeyEvent.ACTION_UP + " " + (event == null ? "null" : event.getAction()));
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        search(null);
                        return false;
                    }
                    return true;
                }
            });
            searchText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    searchText.showDropDown();
                    return false;
                }
            });
            searchText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    search(null);
                }
            });

        }

    }

    public void search(View view) {
        //Select text to remove replace easily for new input
        View v = this.getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
       else {
            routeHelper.calculateRouteWithGeocoding(searchText.getText().toString());
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission result " + requestCode);
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.erreur_autorisation), Toast.LENGTH_LONG).show();
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                            MainActivity.REQUEST_ACCESS_FINE_LOCATION);
                }
                return;
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                builder.build());

        if (isExploreByTouchEnabled) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            checkSavedItinerary(); //Because for other, the check is done onMapReady
            Intent intent = new Intent(this, MyAddressService.class);
            startService(intent);
        }

    }

    private void checkSavedItinerary() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String json = sharedPref.getString(getString(R.string.saved_itinerary_key),"");
        if(json != "") {
            try {
                callbackItinerary(new Route(this,sharedPref.getString(getString(R.string.saved_itinerary_name_key),""), new JSONObject(json)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onPause() {
//        if (mGoogleApiClient != null)
//            mGoogleApiClient.disconnect();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient != null)
            mGoogleApiClient.connect();
        if (mMap != null){
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String json = sharedPref.getString(getString(R.string.saved_itinerary_key),"");
            try {
                route = new Route(this,sharedPref.getString(getString(R.string.saved_itinerary_name_key),""), new JSONObject(json));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG,"resume mainActivity");
        if(isExploreByTouchEnabled) {
            Intent intent = new Intent(this, MyAddressService.class);
            startService(intent);
        }
    }

    @Override
    public void callbackItinerary(Route route) {
        searchText.setText(route.name);
        if (mMap != null) {
            PolylineOptions options = new PolylineOptions();
            options.addAll(PolyUtil.decode(route.polyline)).color(getResources().getColor(R.color.colorAccent));
            clearMap();
            polyline = mMap.addPolyline(options);
            Location start = route.steps.get(0).start;
            markerDepart = mMap.addMarker(new MarkerOptions().position(new LatLng(start.getLatitude(), start.getLongitude())).title(route.startAddress));
            Location end = route.steps.get(route.steps.size() - 1).end;
            markerEnd = mMap.addMarker(new MarkerOptions().position(new LatLng(end.getLatitude(), end.getLongitude())).title(route.destination));
            LatLngBounds bounds = LatLngBounds.builder().include(markerDepart.getPosition()).include(markerEnd.getPosition()).build();
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
            //findViewById(R.id.layout_info).setVisibility(View.VISIBLE);
        }
        infodistance.setText(route.distanceText + " - " + route.tempsText);
        infodistance.setTextAppearance(this, android.R.style.TextAppearance_DeviceDefault_Medium);
        infodistance.setTextColor(getResources().getColor(R.color.colorAccent));

        if(isExploreByTouchEnabled){
            Toast.makeText(this, String.format(getText(R.string.itineraire).toString(), route.name, route.distanceText, route.tempsText), Toast.LENGTH_LONG).show();
            infodistance.setText(String.format(getText(R.string.itineraire).toString(), route.name, route.distanceText, route.tempsText));
        }

        searchText.clearFocus();
        this.route = route;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 15));
        }
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                clearMap();
                markerEnd = mMap.addMarker(new MarkerOptions().position(latLng));
                routeHelper.calculateRouteWithGeocoding(latLng.latitude+","+latLng.longitude);
            }
        });
        checkSavedItinerary();

    }

    private void clearMap() {
        if(markerEnd != null)
            markerEnd.remove();
        if(markerDepart != null)
            markerDepart.remove();
        if(polyline != null)
            polyline.remove();
    }

    public void VoiceSearch(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Speak an Address or Place");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(this,
                    ("Sorry Voice Recognition not Supported"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void StartNavigation(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.erreur_autorisation), Toast.LENGTH_LONG).show();
            return;
        }
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if(mLastLocation == null){
            Toast.makeText(this, "Unable to Locate You", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intentService = new Intent(this,MyAddressService.class);
        intentService.putExtra("close", true);
        startService(intentService);

        intentService = new Intent(this,NavigationService.class);
        startService(intentService);

        Intent intent = new Intent(this, NavigationActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    searchText.setText(result.get(0));
                    routeHelper.calculateRouteWithGeocoding(result.get(0));
                } else {
                    Toast.makeText(this, getText(R.string.erreur_vocale), Toast.LENGTH_LONG).show();
                }
                break;
            }

        }
    }

    public void MyLocation(View v){
//        routeHelper.getMyAddress();
        Log.i(TAG,"play myaddress pressed !");
        Intent myIntent = new Intent(this, MyAddressService.class);
        myIntent.putExtra("myAddress", true);
        startService(myIntent);
    }

    @Override
    public void onBackPressed() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(getString(R.string.saved_itinerary_key));
        editor.commit();
        super.onBackPressed();
    }

    public void details(View view) {
        Intent intent = new Intent(this, DetailsActivity.class);
        startActivity(intent);
    }
}
