package honours.project.NavigationApp.navigation;

import android.Manifest;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.os.ResultReceiver;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import honours.project.NavigationApp.R;
import honours.project.NavigationApp.route.Route;
import honours.project.NavigationApp.route.RouteInterface;
import honours.project.NavigationApp.route.RouteHelper;
import honours.project.NavigationApp.route.routeDetails.Step;
import honours.project.NavigationApp.route.routeDetails.TransitStep;

public class NavigationService extends Service implements LocationListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, TextToSpeech.OnInitListener, RouteInterface {

    private static final String TAG = "NavigationService";
    public static final String BROADCAST_ACTION = "eu.project.proxygps.navigation.NavigationService";
    private static final int NOTIFICATION_ID = 1;
    public static final int EXIT = 2;
    public static final int UPDATE_CODE = 1;
    private final IBinder mBinder = new LocalBinder();
    private Route route;
    private int count = 0;
    private NotificationManager mNotificationManager;
    private ResultReceiver activity;
    private boolean mNotifying = false;
    private LocationManager locationManager;
    private GoogleApiClient mGoogleApiClient;

    //Sensor stuffs
    private double bearing;
    private double mDeclination = 0;
    private Double angle = null;
    private boolean firstime = false;
    private Location lastShowDirection;
    private Step current = null;
    private int distance;
    private TextToSpeech mTts;
    private Location location = null;
    private SharedPreferences sharedPref;
    private Vibrator vibrator;
    private int current_id;
    private RouteHelper routeHelper;
    private Calendar lastCalcul = null;
    private AudioManager am = null;
    private RemoteControlReceiver remoteControlReceiver = null;
    private BroadcastReceiver receiver;
    private ComponentName componentName;
    private boolean arrived = false;

    public NavigationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        componentName = new ComponentName(this,RemoteControlReceiver.class);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String json = sharedPref.getString(getString(R.string.saved_itinerary_key), "");
        if (json != "") {
            try {
                route = new Route(this,sharedPref.getString(getString(R.string.saved_itinerary_name_key), ""), new JSONObject(json));
                current = route.steps.get(0);
                location = current.start;
                distance = Math.round(current.start.distanceTo(current.end));
                showDirection();
            } catch (JSONException e) {
                e.printStackTrace();
                quit();
                return;
            }
        } else {
            quit();
            return;
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .build();
        mGoogleApiClient.connect();
        mTts = new TextToSpeech(this, this);
        Log.i(TAG,"mTts : " + mTts);
        firstime = true;
        initService();
        routeHelper = new RouteHelper(mGoogleApiClient,getApplicationContext(),this);

    }

    public void setReceiver(ResultReceiver receiver) {
        this.activity = receiver;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "position gps accuracy : " + location.getAccuracy());
        if (location.getAccuracy() > 30) {
            return;
        }
        this.location = location;
        Log.i(TAG, "locationChanged");
        Log.i(TAG, "distance jusqu'au prochain : " + location.distanceTo(current.end));
        Step old = current;
        current = getCurrentPoint(location);

        distance = Math.round(location.distanceTo(current.end));
        //Orientation

        GeomagneticField field = new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                System.currentTimeMillis()
        );

        // getDeclination returns degrees
        mDeclination = field.getDeclination();


        double lat1 = location.getLatitude();
        double lng1 = location.getLongitude();

        double lat2 = current.end.getLatitude();
        double lng2 = current.end.getLongitude();

        double dLon = (lng2 - lng1);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        angle = Math.toDegrees((Math.atan2(y, x)));


        Calendar before = Calendar.getInstance();
        before.add(Calendar.SECOND, -40);
        if (!current.equals(old) || firstime) {
            showDirection(old);
            //showDirection();
            firstime = false;
            lastShowDirection = location;
            if(!firstime && sharedPref.getBoolean(getString(R.string.pref_vibrate_key),false) && vibrator != null){
                vibrator.vibrate(500);
            }
        } else if (lastShowDirection.distanceTo(location)>50 && (!(current instanceof TransitStep) || current.end.distanceTo(location)<100)) {
            showDirection();
            lastShowDirection = location;
        } else if (!this.arrived && current.equals(route.steps.getLast()) && current.end.distanceTo(location) < 10){
            //Arrived at Destination
            this.arrived = true;
            showDirection(old);
        }
        send(UPDATE_CODE, new Bundle());

    }

    private void send(int code, Bundle bundle) {
        if (activity != null) {
            activity.send(code, new Bundle());
        }
    }

    public String getNarrative(){
        if(current instanceof TransitStep) {
            return current.narrative;
        }
        else if(current == route.steps.getLast()){
            String[] end = Html.fromHtml(current.narrative).toString().split("\n\n");
            if(end.length > 1)
                return end[1];
            else
                return ("You have arrived at your destination");
        }
        else{
            return route.steps.get(current_id+1).narrative.split("<div")[0];
        }
    }
    public String getManeuver(){
        if(current instanceof TransitStep){
            return current.maneuver;
        }
        else if(current == route.steps.getLast()){
            return "";
        }
        else{
            return route.steps.get(current_id+1).maneuver;
        }
    }
    private void showDirection(){showDirection(null);}
    private void showDirection(Step old) {
//        Log.i(TAG, current.narrative);
        if(sharedPref.getBoolean(getString(R.string.pref_tts_key),false) && mTts != null) {
            mTts.stop();
            if((current instanceof TransitStep)){
                mTts.speak(Html.fromHtml(getNarrative()).toString(), TextToSpeech.QUEUE_ADD, null);
            }
            else if(old == null)
                mTts.speak(getString(R.string.dans)+" "+getDistance()+"m, "+Html.fromHtml(getNarrative()).toString(), TextToSpeech.QUEUE_ADD, null);
            else
                mTts.speak(Html.fromHtml(current.narrative+" "+getString(R.string.et)+" "+getString(R.string.dans)+" "+getDistance()+"m "+getNarrative()).toString(), TextToSpeech.QUEUE_ADD, null);
        }
        //setNotification();
    }

    public Step getCurrentPoint(Location location) {
        int size = route.steps.size();
        Step first = route.steps.get(0);
        Step last = route.steps.get(size - 1);

        for (int i = size - 1; i >= current_id; i--) {
            Step point = route.steps.get(i);
            if (PolyUtil.isLocationOnPath(new LatLng(location.getLatitude(), location.getLongitude()), point.polyline, false, 18)) {
                Log.i(TAG, "Proche prolyline : " + i);
                current_id = i;
                return point;
            }
        }

        boolean onTheLine = PolyUtil.isLocationOnPath(new LatLng(location.getLatitude(), location.getLongitude()), PolyUtil.decode(route.polyline), false, 35);

        Calendar before = Calendar.getInstance();
        before.add(Calendar.SECOND,-20);
        if(!onTheLine && location.getAccuracy()<25 && (lastCalcul == null || lastCalcul.before(before))){
            lastCalcul = Calendar.getInstance();
            routeHelper.getItinerary(route.name,new LatLng(location.getLatitude(),location.getLongitude()), route.getDest());
        }
        Log.i(TAG, "Exit");
        return current;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        if (!this.firstime && provider.equals(LocationManager.GPS_PROVIDER)){
            Toast.makeText(NavigationService.this, getString(R.string.gps_found), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)){
            Toast.makeText(NavigationService.this, getString(R.string.gps_lost), Toast.LENGTH_SHORT).show();
        }
    }

    public Double getAngle() {
        return angle;
    }

    public double getDeclination() {
        return mDeclination;
    }

    @Override
    public void onInit(int status) {

    }

    public Location getLocation() {
        return location;
    }

    public Iterable<LatLng> getPolyline() {
        return PolyUtil.decode(route.polyline);
    }

    @Override
    public void callbackItinerary(Route route) {
        this.route = route;
        current = route.steps.getFirst();
        current_id = 0;
        send(UPDATE_CODE,new Bundle());
    }

    public void getMyAddress(Double bearing) {
        routeHelper.getMyAddress(mTts, bearing);
    }

    public LatLng getLatLng() {
        return new LatLng(location.getLatitude()
        ,location.getLongitude());
    }


    public class LocalBinder extends Binder {
        NavigationService getService() {
            // Return this instance of LocalService so clients can call public methods
            return NavigationService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG,"start command");
        am.registerMediaButtonEventReceiver(componentName);
        if (intent != null) {
            if (intent.getExtras() != null && intent.getExtras().getBoolean("close", false)) {
                Log.i(TAG, "close " + startId);
                send(EXIT, new Bundle());
                quit();
                return 0;
            }
            else if(intent.getExtras() != null && intent.getExtras().getBoolean("speak", false)) {
                Log.i(TAG, "speak ! ");
                showDirection();
            }
        }
        return super.onStartCommand(intent, START_FLAG_REDELIVERY, startId);
    }

    private void initService() {
        //setNotification();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(NavigationService.this, getString(R.string.erreur_autorisation), Toast.LENGTH_SHORT).show();
            quit();
            return;
        }

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                builder.build());


        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);


    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public int getDistance() {
        return distance;
    }

    public void increment() {
        count++;
        send(count, new Bundle());
    }

    public int getCount() {
        return count;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "fin du service :'(");
        if (mNotifying)
            mNotificationManager.cancel(NOTIFICATION_ID);
        if (locationManager != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this);
        }
        if(mTts != null){
            mTts.stop();
            mTts.shutdown();
        }
        if(am != null){
            Log.i(TAG,"am ok");
            am.unregisterMediaButtonEventReceiver(componentName);
        }
        super.onDestroy();
    }

    public void quit() {
        stopSelf();
    }

    public Route getRoute(){
        return route;
    }

    public Step getCurrent(){
        return current;
    }

    public void onTaskRemoved(Intent rootIntent) {

        //unregister listeners
        //do any other cleanup if required

        //stop service
        stopSelf();
    }
}
