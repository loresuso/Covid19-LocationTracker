package it.unipi.dii.covida.ui.home_setting;

import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import it.unipi.dii.covida.R;

/**
 * This Activity is responsible to show the user the menu to change his house
 */
public class HomeSetting extends AppCompatActivity {

    private EditText editText;
    private ListView listView;
    private List<Address> listAddress;
    private Geocoder geocoder;
    private Context context;
    private AsyncGet asyncGet;
    private Button removeCurrentHomeButton;
    private Long lastClickButton = 0L;
    private Long lastClickListItem = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_setting);
        context = this;

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        String home_address = sharedPreferences.getString("home_address", getString(R.string.default_home_address));
        String latitude = sharedPreferences.getString("home_latitude", "");
        String longitude = sharedPreferences.getString("home_longitude", "");

        ConstraintLayout constraintLayout = (ConstraintLayout)findViewById(R.id.constraintLayout_current_address);
        LinearLayout linearLayout = (LinearLayout)findViewById(R.id.layout_add_home);

        if(home_address.compareTo(getString(R.string.default_home_address)) != 0) {

            constraintLayout.setVisibility(ConstraintLayout.VISIBLE);
            linearLayout.setVisibility(LinearLayout.GONE);


            TextView latitude_text = findViewById(R.id.current_latitude);
            TextView longitude_text = findViewById(R.id.current_longitude);
            TextView address_text = findViewById(R.id.current_address);

            String latitude_ = "Latitude: \n" + latitude;
            String longitude_ = "Longitude: \n" + longitude;
            latitude_text.setText(latitude_);
            longitude_text.setText(longitude_);
            address_text.setText(home_address);


            removeCurrentHomeButton = findViewById(R.id.button_remove_current_home);

            removeCurrentHomeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(SystemClock.elapsedRealtime() - lastClickButton < 1000){
                        return;
                    }
                    lastClickButton = SystemClock.elapsedRealtime();
                    AlertDialog.Builder alert = new AlertDialog.Builder(context);
                    alert.setTitle("Delete current home");
                    alert.setMessage("Are you sure you want to delete?");
                    alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("home_address", getString(R.string.default_home_address));
                            editor.putString("home_latitude", "");
                            editor.putString("home_longitude", "");
                            editor.commit();
                            finish();
                            returnToSetting();
                        }
                    });
                    alert.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // close dialog
                            dialog.cancel();
                        }
                    });

                    alert.show();
                }
            });
        } else {
            constraintLayout.setVisibility(ConstraintLayout.GONE);
            linearLayout.setVisibility(LinearLayout.VISIBLE);

            editText = findViewById(R.id.address_input);
            listView = findViewById(R.id.listView_address);
            listView.setDivider(null);
            editText.addTextChangedListener(new TextWatcher() {

                @Override
                public void afterTextChanged(Editable s) {
                    if (asyncGet != null) {
                        asyncGet.cancel(true);
                    }
                    asyncGet = new AsyncGet();
                    asyncGet.execute(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        onBackPressed();
        return true;
    }


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(editText == null)
            editText = findViewById(R.id.address_input);
        String address_tmp = editText.getText().toString();
        outState.putString("address_tmp", address_tmp);
    }


    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if(editText != null && savedInstanceState.getString("address_tmp") != null)
            editText.setText(savedInstanceState.getString("address_tmp"));
    }

    private final class AsyncGet extends AsyncTask<String, Void, List<Address>> {

        @Override
        protected List<Address> doInBackground(String... params) {
            geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
            String addressString = params[0];
            List<Address> list = createAddressList(addressString);
            return list;
        }

        @Override
        protected void onPostExecute(final List<Address> addressList) {
            updateListView(addressList);
        }

    }


    public List<Address> createAddressList(String addressString) {
        try {
            return geocoder.getFromLocationName(addressString, 5);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
    }

    public void updateListView(List<Address> addressList) {
        if(addressList == null || addressList.isEmpty()) {
            if(listAddress != null) {
                listAddress.clear();
            }
            return;
        }

        if(listAddress != null) listAddress.clear();
        listAddress = addressList;
        ArrayAdapter adapter = new ArrayAdapter<Address>(context, R.layout.address_card, listAddress){
            @Override
            public View getView(int position, View convertView, ViewGroup parent){
                // Get the view
                LayoutInflater inflater = getLayoutInflater();
                View itemView = inflater.inflate(R.layout.address_card,null,true);


                ConstraintLayout constraintLayout = itemView.findViewById(R.id.constraintLayout_address);
                TextView latitude_text = itemView.findViewById(R.id.latitude);
                TextView longitude_text = itemView.findViewById(R.id.longitude);
                TextView address_text = itemView.findViewById(R.id.address);
                Address address = listAddress.get(position);

                String latitude_ = "Latitude: \n" + Double.toString(address.getLatitude());
                String longitude_ = "Longitude: \n" + Double.toString(address.getLongitude());
                latitude_text.setText(latitude_);
                longitude_text.setText(longitude_);

                address_text.setText(address.getAddressLine(0));

                CardView cardView = itemView.findViewById(R.id.cardView_address);
                if (position % 2 == 1) {
                    cardView.setBackgroundColor(Color.parseColor("#FFFFFF"));
                } else {
                    cardView.setBackgroundColor(Color.parseColor("#f5f5f5"));
                }
                return itemView;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(SystemClock.elapsedRealtime() - lastClickListItem < 1000){
                    return;
                }
                lastClickListItem = SystemClock.elapsedRealtime();
                getInformationAndSaveToSP(listAddress.get(i));
                returnToSetting();
            }
        });
    }


    public void getInformationAndSaveToSP(Address address) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("home_address", address.getAddressLine(0));
        editor.putString("home_latitude", Double.toString(address.getLatitude()));
        editor.putString("home_longitude", Double.toString(address.getLatitude()));
        editor.commit();
    }

    public void returnToSetting() {
        final Intent intent = NavUtils.getParentActivityIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        NavUtils.navigateUpTo(this, intent);
    }

}
