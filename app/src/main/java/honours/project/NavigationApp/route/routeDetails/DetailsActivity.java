package honours.project.NavigationApp.route.routeDetails;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import honours.project.NavigationApp.R;
import honours.project.NavigationApp.route.Route;

public class DetailsActivity extends AppCompatActivity {

    private static final String TAG = "MainSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String json = sharedPref.getString(getString(R.string.saved_itinerary_key), "");
        Route route;
        if (json != "") {
            try {
                route = new Route(this,sharedPref.getString(getString(R.string.saved_itinerary_name_key), ""), new JSONObject(json));
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(DetailsActivity.this, getString(R.string.error_lancement_navigation), Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        else{
            Toast.makeText(DetailsActivity.this, getString(R.string.error_lancement_navigation), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DetailsFragment detailsFragment = (DetailsFragment) getFragmentManager()
                .findFragmentById(R.id.details);
        detailsFragment.updateData(this, route);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case android.R.id.undo:
                onBackPressed();
                return true;
             default:
                 // Log.i(TAG,"Item : "+item+" id : "+item.getItemId());
                return super.onOptionsItemSelected(item);
        }
    }
}
