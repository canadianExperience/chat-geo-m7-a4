package com.zv.geochat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.zv.geochat.map.MapClusterItem;
import com.zv.geochat.model.ChatMessage;
import com.zv.geochat.model.ChatMessageBody;
import com.zv.geochat.provider.ChatMessageStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String TAG ="MapActivity";

    public static final int PERMISSION_ACCESS_FINE_LOCATION = 1;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    // These settings are the same as the settings for the map. They will in fact give you updates
    // at the maximal rates currently possible.
    private static final LocationRequest REQUEST = LocationRequest.create()
            .setInterval(5000)         // 5 seconds
            .setFastestInterval(16)    // 16ms = 60fps
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    private boolean navigateToMyLocation = true;
    private ChatMessageStore chatMessageStore;
    private ClusterManager<MapClusterItem> clusterManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        checkGpsEnabled();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        chatMessageStore = new ChatMessageStore(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == PERMISSION_ACCESS_FINE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission granted
                setMyLocationEnabled();
            } else {
                Toast.makeText(this, "My location is not enabled!", Toast.LENGTH_LONG).show();
            }
        }
    }
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        clusterManager = new ClusterManager<MapClusterItem>(this, mMap);

        // Zoom map by clicking on cluster
        clusterManager.getRenderer().setOnClusterClickListener(new ClusterManager.OnClusterClickListener<MapClusterItem>() {
            @Override
            public boolean onClusterClick(Cluster<MapClusterItem> cluster) {
               Float zoom =  mMap.getCameraPosition().zoom;
               zoom = zoom + 3;
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(cluster.getPosition().latitude, cluster.getPosition().longitude), zoom));
                return true;
            }
        });

        mMap.setOnCameraIdleListener(clusterManager);
        mMap.setOnMarkerClickListener(clusterManager);

        // setInfoWindowAdapter
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }

            // Set infoWindow content
            @Override
            public View getInfoContents(Marker marker) {
                View v = getLayoutInflater().inflate(R.layout.info_window, null);
                TextView userName = v.findViewById(R.id.txtUserName);
                TextView userMessage = v.findViewById(R.id.txtMessage);

                userName.setText(marker.getTitle());
                userMessage.setText(marker.getSnippet());

                return v;
            }
        });

        //---- create markers

        List<ChatMessage> chatList = chatMessageStore.getList();
        List<ChatMessage> chatMessageList = combineMessages(chatList);

        for (ChatMessage chatMessage : chatMessageList) {

            if (chatMessage.getBody().hasLocation()){
                String body = chatMessage.getBody().getText();
                String title = chatMessage.getUserName();
                Double lat = chatMessage.getBody().getLat();
                Double lng = chatMessage.getBody().getLng();

                MapClusterItem myItem = new MapClusterItem(lat,
                        lng, title, body);
                Log.v(TAG,"add cluster item: " + myItem);
                clusterManager.addItem(myItem);
            }
        }

        setMyLocationEnabled();
    }

    private List<ChatMessage> combineMessages(List<ChatMessage> chatMessageList){

        HashMap<String,ChatMessage> hashMap = new HashMap<>();

        for(ChatMessage chatMessage : chatMessageList){
            String name = chatMessage.getUserName();
            Double lat = chatMessage.getBody().getLat();
            Double lng = chatMessage.getBody().getLng();
            String message = chatMessage.getBody().getText();

            if(lat!=null && lng!=null) {
                if (hashMap.containsKey(name)) {
                    ChatMessage mapItem = hashMap.get(name);
                    ChatMessageBody body = mapItem.getBody();
                    String originalMsg = body.getText();
                    String newMessage = originalMsg + "\n" + message;
                    body.setText(newMessage);
                } else {
                    ChatMessageBody chatMessageBody = new ChatMessageBody(message, lng, lat);
                    ChatMessage mapItem = new ChatMessage(name, chatMessageBody);

                    hashMap.put(name, mapItem);
                }
            }
        }

        // Convert all hashMap values to a List
        List<ChatMessage> newList = new ArrayList(hashMap.values());

        return newList;
    }

    private void setMyLocationEnabled() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, PERMISSION_ACCESS_FINE_LOCATION);
        }
    }


    private void checkGpsEnabled() {
        if (!isGpsEnabled(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.enable_gps_title))
                    .setMessage(getString(R.string.enable_gps_message))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    public boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    //------------GoogleApiClient-----------
    /**
     * Callback called when connected to GCore. Implementation of {@link GoogleApiClient.ConnectionCallbacks}.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient,
                    REQUEST,
                    this);  // LocationListener
        } else {
            Toast.makeText(this, "Location updates are disabled!", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Callback called when disconnected from GCore. Implementation of {@link GoogleApiClient.ConnectionCallbacks}.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        // Do nothing
    }

    /**
     * Implementation of {@link GoogleApiClient.OnConnectionFailedListener}.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Do nothing
    }
    //------------------------------------------
    /**
     * Implementation of {@link LocationListener}.
     */
    @Override
    public void onLocationChanged(Location location) {
        if (navigateToMyLocation && mMap != null) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            int zoom = 10;
            CameraUpdate center =
                    CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), zoom);
            mMap.moveCamera(center);
            navigateToMyLocation = false;
        }
    }
}
