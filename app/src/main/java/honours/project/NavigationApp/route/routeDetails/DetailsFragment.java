package honours.project.NavigationApp.route.routeDetails;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import honours.project.NavigationApp.R;
import honours.project.NavigationApp.route.Route;


public class DetailsFragment extends ListFragment {


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.route_details, container, false);
    }

    public void updateData(Context context, Route route) {
        List<Map<String,Object>> data = new ArrayList<>();
        for (Step step: route.steps ) {
            Map<String, Object> el = new HashMap<String, Object>();
            el.put("narrative", Html.fromHtml(step.narrative));
            el.put("distance", step.distance+"m");
            data.add(el);
        }
        setListAdapter(new SimpleAdapter(context,data,R.layout.list_view_detail, new String[]{"narrative", "distance"}, new int[]{R.id.titre, R.id.distance}));
    }
}
