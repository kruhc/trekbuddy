// @LICENSE@

package cz.kruch.track.fun;

//#ifndef __CN1__

import cz.kruch.track.configuration.Config;
import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Waypoints;
import cz.kruch.track.Resources;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.location.StampedWaypoint;
import cz.kruch.track.util.CharArrayTokenizer;

import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.MessageListener;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.TextMessage;
import javax.microedition.lcdui.Displayable;
import javax.microedition.io.PushRegistry;
import javax.microedition.io.Connector;
import java.io.IOException;

import api.location.QualifiedCoordinates;

/**
 * JSR-120 (SMS) friends.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
final class Jsr120Friends extends Friends implements MessageListener, Runnable {

    private MessageConnection connection;

    private String url;
    private String text;

    Jsr120Friends() {
    }

    private Jsr120Friends(String url, String text) {
        this.url = url;
        this.text = text;
    }

    public void start() throws IOException {
        open();
    }

    public void destroy() {
        if (connection != null) {
            try {
                connection.setMessageListener(null);
                connection.close();
            } catch (Exception e) {
                // ignore
            } finally {
                connection = null;
            }
        }
    }

    public void reconfigure(Displayable next) {
        if (Config.locationSharing) {
            if (PushRegistry.getMIDlet(SERVER_URL) == null) {
                destroy(); // avoid IOException in registerConnection
                try {
                    PushRegistry.registerConnection(SERVER_URL, "cz.kruch.track.TrackingMIDlet", "*");
                } catch (Exception e) {
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_PUSH_SMS_FAILED), e, next);
                }
            }
            try {
                open();
            } catch (Exception e) {
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_FRIENDS_FAILED), e, next);
            }
        } else {
            if (PushRegistry.getMIDlet(SERVER_URL) != null) {
                PushRegistry.unregisterConnection(SERVER_URL);
            }
            destroy();
        }
    }

    public void send(String phone, String type, String message,
                     QualifiedCoordinates coordinates, long time) {
        // check address
        if (phone == null || phone.length() == 0) {
            throw new IllegalArgumentException("Null recipient");
        }

        // create SMS text
        final StringBuffer sb = new StringBuffer(32);
        sb.append(TBSMS_HEADER).append(type).append(SEPARATOR_CHAR);
        sb.append(time / 1000).append(SEPARATOR_CHAR);
        sb.append(toSentence(QualifiedCoordinates.LAT, coordinates.getLat()));
        sb.append(SEPARATOR_CHAR);
        sb.append(toSentence(QualifiedCoordinates.LON, coordinates.getLon()));
        sb.append(SEPARATOR_CHAR);
        sb.append(message.replace(',', ' ').replace('*', ' '));
        sb.append('*').append('0').append('0');
        final String text = sb.toString();
        sb.setLength(0);
        sb.append(SMS_PROTOCOL).append(phone).append(PORT);
        final String url = sb.toString();

//#ifdef __LOG__
        debug(text);
//#endif

        // send the SMS
        (new Thread(new Jsr120Friends(url, text))).start();
    }

    public void notifyIncomingMessage(MessageConnection messageConnection) {
        (new Thread(this)).start();
    }

    public void run() {
        if (connection == null) {
            execSend();
        } else {
            execPop();
        }
    }

    private void execPop() {
        try {
            // pop message
            final Message message = connection.receive();
            if (message instanceof TextMessage) {

                // get payload
                final String text = ((TextMessage) message).getPayloadText();

                // decode message type
                final CharArrayTokenizer tokenizer = new CharArrayTokenizer();
                tokenizer.init(text, new char[]{ ',', '*' }, false);
                final String header = tokenizer.nextTrim();
                String type = null;
                String chat = null;
                if (TBSMS_IAH.equals(header)) {
                    type = TYPE_IAH;
                    chat = headerify(Resources.getString(Resources.NAV_ITEM_SMS_IAH));
                } else if (TBSMS_MYT.equals(header)) {
                    type = TYPE_MYT;
                    chat = headerify(Resources.getString(Resources.NAV_ITEM_SMS_MYT));
                }
                if (type == null) {
                    // unknown message
                    Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_SMS) + " '" + text + "'", null, null);
                } else {
                    // get tokens
                    final String times = tokenizer.nextTrim();
                    final String latv = tokenizer.nextTrim();
                    final String lats = tokenizer.nextTrim();
                    final String lonv = tokenizer.nextTrim();
                    final String lons = tokenizer.nextTrim();
                    final String unknown = tokenizer.nextTrim();
                    if (tokenizer.hasMoreTokens()) {
                        chat += unknown;
                    } else {
                        // unknown is checksum with leading '*'
                    }

                    // parse tokens
                    final long time = Long.parseLong(times) * 1000;
                    final double lat = parseSentence(latv, lats);
                    final double lon = parseSentence(lonv, lons);
                    String address = message.getAddress();
                    if (address.startsWith(SMS_PROTOCOL)) {
                        address = address.substring(6);
                    }
                    final int i = address.indexOf(':');
                    if (i > -1) {
                        address = address.substring(0, i);
                    }

                    // create waypoint
                    final Waypoint wpt = new StampedWaypoint(QualifiedCoordinates.newInstance(lat, lon),
                                                             address, chat, time);

                    // notify - can call directly, we are running in our own thread
                    Waypoints.getInstance().invoke(wpt, null, this);
                }
            }
        } catch (Throwable t) {
            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_SMS_RECEIVE_FAILED), t, null);
        }
    }

    private void execSend() {
        Throwable throwable = null;
        MessageConnection connection = null;
        try {
            connection = (MessageConnection) Connector.open(url, Connector.WRITE);
            final TextMessage sms = (TextMessage) connection.newMessage(MessageConnection.TEXT_MESSAGE);
            sms.setPayloadText(text);
            connection.send(sms);
        } catch (Throwable t) {
            throwable = t;
        } finally {
            try {
                connection.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
            connection = null;
        }
        if (throwable == null) {
            Desktop.showConfirmation(Resources.getString(Resources.DESKTOP_MSG_SMS_SENT), null);
        } else {
            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_SMS_SEND_FAILED)+ " [" + url + "]",
                              throwable, null);
        }
    }

    private void open() throws IOException {
        if (connection == null) {
            connection = (MessageConnection) Connector.open(SERVER_URL, Connector.READ);
            connection.setMessageListener(this);
        }
    }
}

//#endif
