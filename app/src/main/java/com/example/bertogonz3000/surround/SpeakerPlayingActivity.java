package com.example.bertogonz3000.surround;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import com.parse.LiveQueryException;
import com.parse.ParseLiveQueryClient;
import com.parse.ParseLiveQueryClientCallbacks;
import com.parse.ParseQuery;
import com.parse.SubscriptionHandling;


public class SpeakerPlayingActivity extends AppCompatActivity {

    int centerID, frontRightID, frontLeftID, backRightID, backLeftID, phoneVol;
    boolean isPlaying, throwing;
    MediaPlayer centerMP, frontRightMP, frontLeftMP, backRightMP, backLeftMP;
    float position;
    AudioManager audioManager;
    int numberSeek;
    double movingNode = 0.5;
    View background;
    ParseLiveQueryClient parseLiveQueryClient;
    RelativeLayout loaderContainer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speaker_playing);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2, 0);

        background = findViewById(R.id.background);

        throwing = false;
        background.setAlpha(0);

        loaderContainer = findViewById(R.id.loaderContainer);
        loaderContainer.setVisibility(View.VISIBLE);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                loaderContainer.setVisibility(View.INVISIBLE);
            }
        }, 3000); // 3000 milliseconds delay


        //position selected for this phone.
        //TODO - switch from int to float from intent
        position = getIntent().getFloatExtra("position", 0);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //        // Make sure the Parse server is setup to configured for live queries
//        // URL for server is determined by Parse.initialize() call.
        parseLiveQueryClient = ParseLiveQueryClient.Factory.getClient();
        //this should respond to errors when speaker is disconnected from the server
        parseLiveQueryClient.registerListener(new ParseLiveQueryClientCallbacks() {
            @Override
            public void onLiveQueryClientConnected(ParseLiveQueryClient client) {}

            @Override
            public void onLiveQueryClientDisconnected(ParseLiveQueryClient client, boolean userInitiated) {
                disconnect();
                Log.d("speakerplaying", "onlivequerydisconnected");
            }

            @Override
            public void onLiveQueryError(ParseLiveQueryClient client, LiveQueryException reason) {
                disconnect();
                Log.d("speakerplaying", "onlivequeryerror");
            }

            @Override
            public void onSocketError(ParseLiveQueryClient client, Throwable reason) {
                disconnect();
                Log.d("speakerplaying", "onsocketerror");
            }
        });
//
        ParseQuery<Song> query = ParseQuery.getQuery(Song.class);
//        if(!query.isRunning())
//            disconnect();

        // This query can even be more granular (i.e. only refresh if the entry was added by some other user)
        // parseQuery.whereNotEqualTo(USER_ID_KEY, ParseUser.getCurrentUser().getObjectId());
//
//        // Connect to Parse server]
        SubscriptionHandling<Song> subscriptionHandling = parseLiveQueryClient.subscribe(query);
//
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.CREATE, new SubscriptionHandling.HandleEventCallback<Song>() {
            @Override
            public void onEvent(ParseQuery<Song> query, Song object) {

                prepMediaPlayers(object);

                //TODO - check discrepancy between adapter and controller
                isPlaying = object.getIsPlaying();

                numberSeek = object.getNumSeek();

                //changeTime(object.getTime());  //if speaker joins late then have it match up with the others

                movingNode = object.getMovingNode();

                throwing = object.getIsThrowing();

                if (throwing){
                    //TODO - what do we do on create if throwing? (right now it'll only be throwing when button in ControllerPlaying is hit, so it will never be in CREATE)
                }

                setToMaxVol(centerMP);
                setToMaxVol(frontRightMP);
                setToMaxVol(backRightMP);
                setToMaxVol(backLeftMP);
                setToMaxVol(frontLeftMP);

                if (isPlaying){
                    playAll();
                } else {
                    pauseAll();
                }

                phoneVol = (int) object.getVolume();
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,phoneVol, 0);

            }
        });

        subscriptionHandling.handleEvent(SubscriptionHandling.Event.UPDATE, new SubscriptionHandling.HandleEventCallback<Song>() {
            @SuppressLint("ResourceAsColor")
            @Override
            public void onEvent(ParseQuery<Song> query, Song object) {
                // when volume, song, or playing status is updated

                Log.d("SpeakerPlayingActivity", "in on update");
                Log.d("SpeakerPlayingActivity", "time: " + object.getTime());

                //if the scrubber was used to change the position in the song
                if(object.getNumSeek() != numberSeek) {
                    changeTime(object.getTime());
                    numberSeek = object.getNumSeek();
                }

                if (isPlaying != object.getIsPlaying()) {
                    isPlaying = object.getIsPlaying();
                    if (!isPlaying){
                        Log.d("SpeakerPlayingActivity", "switching pause/play");
                        changeTime(object.getTime());
                        pauseAll();
                    } else {
                        Log.d("SpeakerPlayingActivity", "switching pause/play");
                        changeTime(object.getTime());
                        playAll();
                    }

                    //TODO - should object.getVolume be a float or an int? I think it depends on
                    //TODO - where we want to do the conversion between whatever input the croller gives
                    //TODO - us and what we need to set volume
                } else if (phoneVol != object.getVolume()) {
                    Log.d("SpeakerPlayingActivity", "changing volume");
                    phoneVol = (int) object.getVolume();
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,phoneVol, 0);

                }
                // TODO - check logic of these if statements, right now it'll start as if controller wants node
                // TODO - 0.5 to be where sound is and will re-ping server when node is moved, this seems like an extra step
                else if (throwing != object.getIsThrowing()) {
                    Log.d("SpeakerPlayingActivity", "throwing != object.getIsThrowing");
                    // set throwing boolean to be equal to whether controller wants to be throwing sound or not
                    throwing = object.getIsThrowing();

                    if (throwing) {
                        Log.e("SpeakerPlayingActivity", "throwing and setting volume to " + getMaxVol(movingNode) + "" + getMaxVol(movingNode));
                        centerMP.setVolume(getMaxVol(movingNode), getMaxVol(movingNode));
                        frontLeftMP.setVolume(0, 0);
                        backLeftMP.setVolume(0, 0);
                        frontRightMP.setVolume(0, 0);
                        backRightMP.setVolume(0, 0);
                    } else {
                        Log.e("THROWING", "Returned");
                        setToMaxVol(centerMP);
                        setToMaxVol(frontLeftMP);
                        setToMaxVol(backLeftMP);
                        setToMaxVol(frontRightMP);
                        setToMaxVol(backRightMP);
                    }

                } else if (movingNode != object.getMovingNode()){

                    movingNode = object.getMovingNode();
                    Log.d("SpeakerPlayingActivity", "movingnode != object.getmovingnode");
                    Log.d("SpeakerPlayingActivity", "setting volume to " + getMaxVol(movingNode) + " , " + getMaxVol(movingNode));
                    centerMP.setVolume(getMaxVol(movingNode), getMaxVol(movingNode));
                    frontLeftMP.setVolume(0, 0);
                    backLeftMP.setVolume(0, 0);
                    frontRightMP.setVolume(0, 0);
                    backRightMP.setVolume(0, 0);

                    //0 means the view is completely transparent and 1 means the view is completely opaque.
                    //sets the color to full purple if it is closest to the movingNode position
                    background.setAlpha(getMaxVol(movingNode));

                }
            }
        });

        //TODO - CHANGED
        subscriptionHandling.handleEvent(SubscriptionHandling.Event.DELETE, new SubscriptionHandling.HandleEventCallback<Song>() {
            @Override
            public void onEvent(ParseQuery<Song> query, Song object) {
                Log.d("SpeakerPlayingActivity", "onEvent leave to disconnect in DELETE");
                disconnect();
            }
        });

        //if error in subscription handling, then call disconnect
        subscriptionHandling.handleError(new SubscriptionHandling.HandleErrorCallback<Song>() {
            @Override
            public void onError(ParseQuery<Song> query, LiveQueryException exception) {
                disconnect();
                Log.d("speakerplaying", "handleerror");
            }
        });


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }

    //TODO - later make sure the speaker is connected to the master device and server
    //TODO - Why do we have 2 disconnect methods? (idk but they do the exact same thing so we should delete one)
    public void disconnect() {
        Log.d("speakerplaying", "disconnectfunction");
        if(frontRightMP != null && backRightMP != null && frontLeftMP != null && backLeftMP != null && centerMP != null) {
            pauseAll();
            releaseAll();
            nullAll();
        }

        //Run code on the UI thread
        runOnUiThread(new Runnable() {
                          @ Override
                          public void run() {
                              //Create the alert dialog
                              createAlertDialog();
                          }
                      });
    }


    public void disconnect(View view) {
        parseLiveQueryClient.disconnect();  //only if user initiated the disconnect from the server
        disconnect();
    }

    public void createAlertDialog() {

        View alertView = LayoutInflater.from(SpeakerPlayingActivity.this).inflate(R.layout.activity_lost_connection, null);
        // Create alert dialog builder
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(SpeakerPlayingActivity.this);
        // set message_item.xml to AlertDialog builder
        alertDialogBuilder.setView(alertView);

        // Create alert dialog
        final AlertDialog alertDialog = alertDialogBuilder.create();


        // Configure dialog button (Refresh)
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Reconnect",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //TODO - check again if there is a connection to the server
                        parseLiveQueryClient.reconnect();
                        //if connected, then dismiss and return to the ControllerPlayingActivity
                        dialog.dismiss();
                    }
                });

        // Configure dialog button (Restart)
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "End Session",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        //if the restart button is hit, just return to the home screen
                        Intent intent = new Intent(SpeakerPlayingActivity.this, LandingActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });

        // Display the dialog
        alertDialog.show();
        alertDialog.getWindow().setBackgroundDrawableResource(R.color.background);
    }

    public void checkConnection(View view) {
        setContentView(R.layout.activity_speaker_playing);
        parseLiveQueryClient.reconnect();
    }

    //Create mediaplayers based on given songIds
    private void prepMediaPlayers(Song object){

        centerID = object.getAudioIds().get(0);
        frontLeftID = object.getAudioIds().get(1);
        frontRightID = object.getAudioIds().get(2);
        backLeftID = object.getAudioIds().get(3);
        backRightID = object.getAudioIds().get(4);

        centerMP = MediaPlayer.create(SpeakerPlayingActivity.this, centerID);
        frontLeftMP = MediaPlayer.create(SpeakerPlayingActivity.this, frontLeftID);
        frontRightMP = MediaPlayer.create(SpeakerPlayingActivity.this, frontRightID);
        backLeftMP = MediaPlayer.create(SpeakerPlayingActivity.this, backLeftID);
        backRightMP = MediaPlayer.create(SpeakerPlayingActivity.this, backRightID);

    }

    //pause All 5 mediaplayers
    private void pauseAll(){
        centerMP.pause();
        frontLeftMP.pause();
        frontRightMP.pause();
        backLeftMP.pause();
        backRightMP.pause();
    }

    //play all 5 media players
    private void playAll(){
        centerMP.start();
        frontLeftMP.start();
        frontRightMP.start();
        backLeftMP.start();
        backRightMP.start();
    }

    //TODO - CREATED
    private void releaseAll(){
        centerMP.release();
        frontLeftMP.release();
        frontRightMP.release();
        backLeftMP.release();
        backRightMP.release();
    }

    //TODO - CREATED
    private void nullAll(){
        centerMP = null;
        frontLeftMP = null;
        frontRightMP = null;
        backLeftMP = null;
        backRightMP = null;
    }

    //change time of all 5 media players
    private void changeTime(int time){
        centerMP.seekTo(time);
        frontLeftMP.seekTo(time);
        frontRightMP.seekTo(time);
        backLeftMP.seekTo(time);
        backRightMP.seekTo(time);
    }



    private float getMaxVol(double node){
//        float denom = (float) (Math.sqrt(2*Math.PI));
//        Log.e("MATH", "denom = " + denom);
//        float left =(float) 2.5066/denom;
//        Log.e("MATH", "left = " + left);
        float expTop = (float) -(Math.pow((position - node), 2));
        //TODO - CHANGED 12:49 7/26
        double exponent = expTop/0.02;
        //TODO - add LEFT* before Math.pow....if this doesn't work..got rid of cuz it was ~1
        float maxVol = (float) Math.pow(Math.E, exponent);
        Log.e("MATH", "maxVol at " + node + " = " + maxVol);
        return maxVol;
    }

    //TODO - Might have to differentiate between nodes, not letting center extend???
    //TODO - I don't like hardcoding the nodes to the mps here...should we create a dictionary or something?
    private void setToMaxVol(MediaPlayer mp){
        double node = 0.5;
        if ( mp == centerMP){
            node = 0.5;
        } else if (mp == frontRightMP){
            node = 0.625;
        } else if (mp == backRightMP){
            node = 0.875;
        } else if (mp == backLeftMP){
            node = 0.125;
        } else if (mp == frontLeftMP){
            node = 0.375;
        }


        Log.e("Adjustment", "node = " + node);
        Log.e("Adjustment", "position = " + position);



        mp.setVolume(getMaxVol(node), getMaxVol(node));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(frontRightMP != null && backRightMP != null && frontLeftMP != null && backLeftMP != null && centerMP != null) {
            frontLeftMP.release();
            frontRightMP.release();
            backLeftMP.release();
            backRightMP.release();
            centerMP.release();
        }
    }

}
