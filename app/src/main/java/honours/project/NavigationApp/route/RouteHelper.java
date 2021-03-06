package honours.project.NavigationApp.route;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import honours.project.NavigationApp.R;

public class RouteHelper {

    private static final String TAG = "RouteHelper";
    private static final String ENDPOINT = "https://maps.googleapis.com/maps/api/directions/json";
    private final Geocoder geocoder;
    private final GoogleApiClient mGoogleApiClient;
    private final Context context;
    private final RouteInterface callback;
    private boolean calculatingItinerary = false;
    private boolean gettingAddress = false;
    private boolean geocoding = false;

    public RouteHelper(GoogleApiClient mGoogleApiClient, Context context, RouteInterface callback) {
        geocoder = new Geocoder(context, Locale.getDefault());
        this.mGoogleApiClient = mGoogleApiClient;
        this.context = context;
        this.callback = callback;
    }

    public void getMyAddress(final TextToSpeech mTts, final Double bearing){
        if (mGoogleApiClient.isConnected() && !gettingAddress) {

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, context.getString(R.string.erreur_autorisation), Toast.LENGTH_LONG).show();
                return;
            }
            final Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (mLastLocation != null) {
                //List<Address> geocode = geocoder.getFromLocationName(result.get(0), 1, 43.265302, -0.443664, 43.339358, -0.280801);
                Toast.makeText(context,context.getString(R.string.recherche_en_cours),Toast.LENGTH_SHORT).show();
                new AsyncTask<Void,Void,Address>(){

                    private int error = 0;
                    private Float angle;
                    @Override
                    protected Address doInBackground(Void... params) {
                        gettingAddress = true;
                        try {
                            List<Address> geocode = geocoder.getFromLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude(), 1);
                            if (geocode != null && geocode.size() > 0) {
                                //Take nearest geocode result
                                Address returnedArrivalAddress = geocode.get(0);
                                if(returnedArrivalAddress.getSubThoroughfare() != null) {
                                    try {
                                        int streetNumber = Integer.parseInt(returnedArrivalAddress.getSubThoroughfare());
                                        List<Address> geocodeAfter = geocoder.getFromLocationName(returnedArrivalAddress.getAddressLine(0).replace(returnedArrivalAddress.getSubThoroughfare(), String.valueOf(streetNumber + 2)) + " " + returnedArrivalAddress.getLocality() + " " + returnedArrivalAddress.getCountryName(), 1);
                                        if (geocodeAfter.size() > 0) {
                                            Address after = geocodeAfter.get(0);
                                            Location afterLocation = new Location("");
                                            afterLocation.setLatitude(after.getLatitude());
                                            afterLocation.setLongitude(after.getLongitude());
                                            angle = mLastLocation.bearingTo(afterLocation);
                                            Log.i(TAG,"next : "+after.getAddressLine(0)+" "+after.getLocality());
                                            if (returnedArrivalAddress.getAddressLine(0) != null)
                                                Log.i(TAG, streetNumber+" - toto - "+returnedArrivalAddress.getAddressLine(0).replace(returnedArrivalAddress.getSubThoroughfare(), String.valueOf(streetNumber+1)));

                                        }
                                    }catch (NumberFormatException e){ Log.i(TAG, "streetNumber NaN"); }
                                }
                                else
                                Log.i(TAG,"null");
//                                geocoder.getFromLocationName(returnedArrivalAddress.,1)
                                return returnedArrivalAddress;
                            } else {
                                error = R.string.adresse_introuvable;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            error = R.string.erreur_connexion;
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Address returnedArrivalAddress) {
                        gettingAddress= false;
                        if(returnedArrivalAddress != null) {
                            String speech = context.getString(R.string.vous_vous_trouvez_ici)+" "+returnedArrivalAddress.getAddressLine(0)+" "+returnedArrivalAddress.getLocality()+". "+context.getString(R.string.precision)+" "+Math.round(mLastLocation.getAccuracy())+"m";
                            if(angle != null && bearing != null)
                                speech = speech + ". " + getDirection(bearing, angle);
                            speak(mTts, speech);
                        }
                        else if(error>0){
                            speak(mTts, context.getString(error));
                        }
                    }
                }.execute();

            } else
                Toast.makeText(context, context.getString(R.string.erreur_localisation), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, context.getString(R.string.erreur_localisation), Toast.LENGTH_LONG).show();
        }
    }

    private double normalize(double a){
        return (360 + (a % 360)) % 360;
    }
    private boolean isBetween(double b, double b1, double b2) {
        if(b1 < b2){
            return(b1 <= b && b <= b2);
        }
        return (b1 <= b || b <= b2);
    }

    private String getDirection(Double bearing, Float angle) {
        Log.i(TAG,bearing + " " + angle);
        boolean res = true;
        double[] front = {normalize(bearing-90),normalize(bearing+90)};

        double b = normalize(angle);

        if(isBetween(b,front[0],front[1])){
            return context.getString(R.string.croissant);
        } else {
            return context.getString(R.string.decroissant);
        }
    }

    public void calculateRouteWithGeocoding(final String s) {
        if(!geocoding) {

            if (mGoogleApiClient.isConnected()) {

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, context.getString(R.string.erreur_autorisation), Toast.LENGTH_LONG).show();
                    return;
                }
                final Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (mLastLocation != null) {
                    //List<Address> geocode = geocoder.getFromLocationName(result.get(0), 1, 43.265302, -0.443664, 43.339358, -0.280801);
                    Toast.makeText(context, context.getString(R.string.recherche_en_cours), Toast.LENGTH_SHORT).show();
                    new AsyncTask<Void, Void, Address>() {

                        private int error = 0;

                        @Override
                        protected Address doInBackground(Void... params) {
                            geocoding = true;
                            try {
                                Pattern regex = Pattern.compile("^-?[0-9]+\\.[0-9]+,-?[0-9]+\\.[0-9]+$");
                                Log.i(TAG,"lat lng ? : "+s+" - "+ regex.matcher(s).find());
                                List<Address> geocode = !regex.matcher(s).find() ?
                                        geocoder.getFromLocationName(s, 15, mLastLocation.getLatitude() - 0.05, mLastLocation.getLongitude() - 0.05, mLastLocation.getLatitude() + 0.05, mLastLocation.getLongitude() + 0.05)
                                        : geocoder.getFromLocationName(s, 1);
                                if (geocode != null && geocode.size() > 0) {
                                    //Take nearest geocode result
                                    Address returnedArrivalAddress = geocode.get(0);
                                    for (Address i : geocode) {
                                        Location locCurrent = new Location("");
                                        Location locI = new Location("");
                                        locCurrent.setLongitude(returnedArrivalAddress.getLongitude());
                                        locCurrent.setLatitude(returnedArrivalAddress.getLatitude());
                                        locI.setLatitude(i.getLatitude());
                                        locI.setLongitude(i.getLongitude());

                                        if (locCurrent.distanceTo(mLastLocation) > locI.distanceTo(mLastLocation)) {
                                            returnedArrivalAddress = i;
                                        }
                                    }

                                    return returnedArrivalAddress;
                                } else {
                                    //calculateItineraryWithPlaceAPI(s, mLastLocation);
                                    //                                error = R.string.adresse_introuvable;
                                    //                                Toast.makeText(context, context.getString(eu.project.proxygps.R.string.adresse_introuvable), Toast.LENGTH_LONG).show();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                error = R.string.erreur_connexion;
                                //                            Toast.makeText(context, context.getString(R.string.erreur_connexion), Toast.LENGTH_LONG).show();
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Address returnedArrivalAddress) {
                            geocoding = false;
                            if (returnedArrivalAddress != null)
                                getItinerary(returnedArrivalAddress.getAddressLine(0) + " " + returnedArrivalAddress.getLocality(), new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), new LatLng(returnedArrivalAddress.getLatitude(), returnedArrivalAddress.getLongitude()));
                            else if (error > 0) {
                                Toast.makeText(context, context.getString(error), Toast.LENGTH_LONG).show();
                            }
                        }
                    }.execute();

                } else
                    Toast.makeText(context, context.getString(R.string.erreur_localisation), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, context.getString(R.string.erreur_localisation), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void getItinerary(final String name, LatLng depart, LatLng destination){
        getItinerary(name,depart,destination,false);
    }
    public void getItinerary(final String name, final LatLng depart, final LatLng destination, final boolean walking) {
        Log.i(TAG, "\n" +
                "Address found, route calculation");
        if(calculatingItinerary)
            return;
        calculatingItinerary = true;
        RequestQueue queue = Volley.newRequestQueue(context);
        String url = ENDPOINT + "?";
        url += "origin=" + depart.latitude + "," + depart.longitude;
        url += "&destination=" + destination.latitude + "," + destination.longitude;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean bus = !walking && sharedPref.getBoolean(context.getString(R.string.pref_bus_key), false);
        Log.i(TAG, "preference bus : " + bus);
        if (!bus) {
            url += "&mode=walking";
        } else {
            url += "&mode=transit";
        }
        url += "&language=" + Locale.getDefault().getLanguage();
        Log.d(TAG, url);
        if(!walking) Toast.makeText(context, context.getString(honours.project.NavigationApp.R.string.calcul_itineraire), Toast.LENGTH_SHORT).show();
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        calculatingItinerary = false;
                        try {
                            if(response.getJSONArray("routes").length()>0){
                                Route route = new Route(context, name, response);
                                saveItinerary(route);
                                callback.callbackItinerary(route);
                            }
                            else if(!walking)
                                getItinerary(name,depart,destination,true);
                            else
                                Toast.makeText(context, context.getString(R.string.erreur_itineraire), Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(context, context.getString(R.string.erreur_505), Toast.LENGTH_LONG).show();
                        }
                    }

                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                calculatingItinerary = false;
                Log.e(TAG, "error when getting route " + error);
                error.printStackTrace();
                if(error instanceof NoConnectionError){
                    Toast.makeText(context, context.getString(R.string.erreur_connexion), Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(context, context.getString(R.string.erreur_505), Toast.LENGTH_LONG).show();
                }
                //itineraryLayout.setVisibility(View.GONE);

            }
        });
        //        // Add the request to the RequestQueue.
        queue.add(req);
    }

    private void saveItinerary(Route route) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.saved_itinerary_key), route.json);
        editor.putString(context.getString(R.string.saved_itinerary_name_key), route.name);
        editor.commit();
    }

    public void speak(TextToSpeech mTts, String speech) {
        if(mTts == null){
            Toast.makeText(context, speech, Toast.LENGTH_SHORT).show();
        } else {
            mTts.speak(speech, TextToSpeech.QUEUE_ADD, null);
        }
    }
}
