/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.fun;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Waypoints;
import cz.kruch.track.location.Navigator;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.wireless.messaging.MessageListener;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.TextMessage;
import javax.microedition.io.Connector;
import java.io.IOException;
import java.util.Date;

import api.location.QualifiedCoordinates;
import api.location.MinDec;

public final class Friends implements MessageListener, Runnable {
    public static final String TYPE_IAH = "IAH";
    public static final String TYPE_MYT = "MYT";

    private static final String TBSMS_HEADER  = "$TB";
    private static final String TBSMS_IAH  = TBSMS_HEADER + TYPE_IAH;
    private static final String TBSMS_MYT  = TBSMS_HEADER + TYPE_MYT;
    private static final char SEPARATOR_CHAR = ',';
    private static final String PORT = ":16007";

    private static class Sender implements Runnable {
        private String url;
        private String text;

        public Sender(String url, String text) {
            this.url = url;
            this.text = text;
        }

        public void run() {
            MessageConnection connection = null;
            try {
                connection = (MessageConnection) Connector.open(url, Connector.WRITE);
                TextMessage sms = (TextMessage) connection.newMessage(MessageConnection.TEXT_MESSAGE);
                sms.setPayloadText(text);
                connection.send(sms);
                Desktop.showConfirmation("Location sent", null);
            } catch (Throwable t) {
                Desktop.showError("Failed to send SMS [" + url + "]", t, null);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private Navigator navigator;
    private MessageConnection connection;

    public Friends(Navigator navigator) throws IOException {
        this.navigator = navigator;
        this.connection = (MessageConnection) Connector.open("sms://" + PORT, Connector.READ);
        this.connection.setMessageListener(this);
    }

    public void destroy() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                // ignore
            } finally {
                connection = null;
            }
        }
    }

    public void notifyIncomingMessage(MessageConnection messageConnection) {
        (new Thread(this)).start();
    }

    public static void send(String phone, String type, String message,
                            QualifiedCoordinates coordinates, long time) {
        // check address
        if (phone == null || phone.length() == 0) {
            throw new IllegalArgumentException("Null recipient");
        }

        // create SMS text
        StringBuffer sb = new StringBuffer();
        sb.append(TBSMS_HEADER).append(type).append(SEPARATOR_CHAR);
        sb.append(time / 1000).append(SEPARATOR_CHAR);
        sb.append(MinDec.toSentence(QualifiedCoordinates.LAT,
                                    coordinates.getLat()));
        sb.append(SEPARATOR_CHAR);
        sb.append(MinDec.toSentence(QualifiedCoordinates.LON,
                                    coordinates.getLon()));
        sb.append(SEPARATOR_CHAR);
        sb.append(message.replace(',', ' ').replace('*', ' '));
        sb.append("*00");

        String text = sb.toString();
        String url = "sms://" + phone + PORT;

//#ifdef __LOG__
        debug(text);
//#endif

        // send the SMS
        (new Thread(new Sender(url, text))).start();
    }

    public void run() {
        Message message;
        try {
            // pop message
            message = connection.receive();
            if (message instanceof TextMessage) {

                // get payload
                String text = ((TextMessage) message).getPayloadText();

                // decode message type
                CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                tokenizer.init(text, new char[]{ ',', '*' }, false);
                String header = tokenizer.next().toString();
                String type = null;
                String chat = null;
                if (TBSMS_IAH.equals(header)) {
                    type = TYPE_IAH;
                    chat = "(I am here) ";
                } else if (TBSMS_MYT.equals(header)) {
                    type = TYPE_MYT;
                    chat = "(Meet you there) ";
                }
                if (type == null) {
                    // unknown message
                    Desktop.showWarning("Unknown message '" + text + "'", null, null);
                } else {
                    // get tokens
                    String times = tokenizer.next().toString();
                    String latv = tokenizer.next().toString();
                    String lats = tokenizer.next().toString();
                    String lonv = tokenizer.next().toString();
                    String lons = tokenizer.next().toString();
                    String unknown = tokenizer.next().toString();
                    if (tokenizer.hasMoreTokens()) {
                        chat += unknown;
                    } else {
                        // unknown is checksum with leading '*'
                    }

                    // parse tokens
                    long time = Long.parseLong(times) * 1000;
                    double lat = MinDec.fromSentence(latv, lats).doubleValue();
                    double lon = MinDec.fromSentence(lonv, lons).doubleValue();
                    String address = message.getAddress();
                    if (address.startsWith("sms://")) {
                        address = address.substring(6);
                    }
                    int i = address.indexOf(':');
                    if (i > -1) {
                        address = address.substring(0, i);
                    }

                    // create waypoint
                    Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                                address, chat, time);

                    // notify
                    Waypoints.getInstance().invoke(new Object[]{ type, wpt }, null, this);

                    // UI notification
                    Desktop.showAlarm("Received location info from " + wpt.getName(), null);
                }
            }
        } catch (Throwable t) {
            Desktop.showError("Failed to receive SMS", t, null);
        }
    }

//#ifdef __LOG__
    private static void debug(String text) {
        // decode
        CharArrayTokenizer tokenizer = new CharArrayTokenizer();
        tokenizer.init(text, new char[]{ ',', '*' }, false);
        String header = tokenizer.next().toString();
        String type = null;
        String chat = null;
        if (TBSMS_IAH.equals(header)) {
            type = TYPE_IAH;
            chat = "(I am here) ";
        } else if (TBSMS_MYT.equals(header)) {
            type = TYPE_MYT;
            chat = "(Meet you there) ";
        }
        if (type != null) {
            // get tokens
            String times = tokenizer.next().toString();
            String latv = tokenizer.next().toString();
            String lats = tokenizer.next().toString();
            String lonv = tokenizer.next().toString();
            String lons = tokenizer.next().toString();
            String unknown = tokenizer.next().toString();
            if (tokenizer.hasMoreTokens()) {
                chat += unknown;
            } else {
                // unknown is checksum with leading '*'
            }

            long time = Long.parseLong(times) * 1000;
            double lat = MinDec.fromSentence(latv, lats).doubleValue();
            double lon = MinDec.fromSentence(lonv, lons).doubleValue();
            String xxx = (new Date(time)).toString();
            Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                        null, chat, time);

        } else {
            Desktop.showWarning("Unknown message '" + text + "'", null, null);
        }
    }
//#endif
}
