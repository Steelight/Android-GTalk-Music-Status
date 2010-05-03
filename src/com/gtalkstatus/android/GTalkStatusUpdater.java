/***************************************************************************
 *   Copyright 2010 Scott Ferguson                                         *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.         *
 ***************************************************************************/
package com.gtalkstatus.android;

import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Context;
import android.widget.Toast;
import android.app.Service;
import android.os.IBinder;
import android.content.ComponentName;
import android.os.IBinder;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.lang.CharSequence;
import java.lang.IllegalStateException;
import java.lang.NullPointerException;

import org.jivesoftware.smack.XMPPException; 

import com.android.music.IMediaPlaybackService;

public class GTalkStatusUpdater extends Service {
    
    public static final String LOG_NAME = "GTalkStatusUpdater";

    private NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public int onStartCommand(Intent aIntent, int aFlags, int aStartId) {
        
        onStart(aIntent, aStartId);

        return 2;
    }

    @Override
    public void onStart(Intent aIntent, int aStartId) {

        if (aIntent.getAction().equals("com.android.music.playbackcomplete")) {
            // The song has ended, stop the service
            stopSelf();
        } else if (aIntent.getAction().equals("com.android.music.playstatechanged") 
                || aIntent.getAction().equals("com.android.music.metachanged")
                || aIntent.getAction().equals("com.android.music.queuechanged")) {

            bindService(new Intent().setClassName("com.android.music", "com.android.music.MediaPlaybackService"), new ServiceConnection() {
        
                public void onServiceConnected(ComponentName aName, IBinder aService) {
                    IMediaPlaybackService service = IMediaPlaybackService.Stub.asInterface(aService);

                    try {
                        // We disconnect from XMPP if we don't need to keep the connection alive.
                        // Reconnect if necessary.
                        if (! GTalkStatusApplication.getInstance().getConnector().isConnected()) {
                            GTalkStatusApplication.getInstance().updateConnection();
                        }

                        String currentTrack = service.getTrackName();
                        String currentArtist = service.getArtistName();

                        if (service.isPlaying()) {
                            String statusMessage = "\u266B " + currentArtist + " - " + currentTrack;

                            GTalkStatusApplication.getInstance().getConnector().setStatus(statusMessage);
                        } else {
                            GTalkStatusApplication.getInstance().getConnector().disconnect();
                            stopSelf();
                        }
                    } catch (IllegalStateException e) { 
                        notifyError();
                        stopSelf();
                    } catch (NullPointerException e) {
                        Log.w(LOG_NAME, "Service was never connected!");
                        notifyError();
                        stopSelf();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }

                    unbindService(this);
                }

                public void onServiceDisconnected(ComponentName aName) {
                    GTalkStatusApplication.getInstance().getConnector().setStatus("", 0);
                }

                public void notifyError() {
                    // Occurs if the connection was never initialized
                    int icon = android.R.drawable.stat_notify_error;
                    CharSequence notificationText = "GTalk Status error";
                    long when = System.currentTimeMillis();

                    Notification notification = new Notification(icon, notificationText, when);

                    CharSequence title = "Music Status Error";
                    CharSequence text = "Music Status was unable to connect to the Google Talk server.  Did you enter your username and password correctly?";

                    Intent notificationIntent = new Intent(getApplicationContext(), GTalkStatusActivity.class);
                    PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                    notification.setLatestEventInfo(getApplicationContext(), title, text, contentIntent);

                    mNotificationManager.notify(1, notification);
                }
            }, 0);
        }
    }

    public IBinder onBind(Intent aIntent) {

        return null;
    }
}