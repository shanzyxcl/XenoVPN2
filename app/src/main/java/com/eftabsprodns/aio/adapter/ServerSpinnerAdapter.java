package com.eftabsprodns.aio.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;

import com.tpv.plus.R;

public class ServerSpinnerAdapter extends ArrayAdapter<JSONObject> {

    private TextView tv1, tv2;

    private ImageView im;

    private String path;

    public ServerSpinnerAdapter(Context c, ArrayList<JSONObject> a) {
        super(c, R.layout.server_spinner, a);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return v(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return v(position, convertView, parent);
    }

    public void setPath(String path) {
        this.path = path;
    }

    private View v(int position, View c, ViewGroup parent) {
        c = LayoutInflater.from(getContext()).inflate(R.layout.server_spinner, parent, false);
        tv1 = c.findViewById(R.id.name);
        tv2 = c.findViewById(R.id.sCategory);
        im = c.findViewById(R.id.icon);
        tv1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tv2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9);
        try {
            tv1.setText(getItem(position).getString("Name"));
            tv2.setText(getServerType(getItem(position)));
            InputStream open = getContext().getAssets().open(new StringBuffer().append("flags/").append("flag_").append(getItem(position).getString("FLAG")).append(".png").toString());
            im.setImageDrawable(Drawable.createFromStream(open, (String) null));
            if (open != null) open.close();
        } catch (Exception e) {
            tv1.setText(e.getMessage());
        }
        return c;
    }

    private String getServerType(JSONObject js) throws JSONException {
        if (js.getInt("Category") == 0) {
            return "PREMIUM";
        } else if (js.getInt("Category") == 1) {
            return "VIP";
        } else if (js.getInt("Category") == 2) {
            return "PRIVATE";
        }
        return "Random";
    }


}
