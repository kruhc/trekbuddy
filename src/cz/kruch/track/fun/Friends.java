// @LICENSE@

package cz.kruch.track.fun;

import cz.kruch.track.ui.Desktop;
import cz.kruch.track.ui.Waypoints;
import cz.kruch.track.ui.NavigationScreens;
import cz.kruch.track.location.Waypoint;
import cz.kruch.track.util.CharArrayTokenizer;
import cz.kruch.track.Resources;
import cz.kruch.track.configuration.Config;

import javax.wireless.messaging.MessageListener;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.TextMessage;
import javax.microedition.io.Connector;
import javax.microedition.io.PushRegistry;
import javax.microedition.lcdui.Displayable;
import java.io.IOException;
import java.util.Date;

import api.location.QualifiedCoordinates;

public final class Friends implements MessageListener, Runnable {
    public static final String TYPE_IAH         = "IAH";
    public static final String TYPE_MYT         = "MYT";
    public static final String SERVER_URL       = "sms://:16007";

    private static final String SMS_PROTOCOL     = "sms://";
    private static final String PORT             = ":16007";
    private static final String TBSMS_HEADER    = "$TB";
    private static final String TBSMS_IAH       = "$TBIAH";
    private static final String TBSMS_MYT       = "$TBMYT";
    private static final String CHAT_IAH        = "(I am here) ";
    private static final String CHAT_MYT        = "(Meet you there) ";

    private static final char SEPARATOR_CHAR = ',';

    private MessageConnection connection;

    private String url;
    private String text;

    public Friends() throws IOException {
        if (Config.locationSharing) {
            open();
        }
    }

    private Friends(String url, String text) {
        this.url = url;
        this.text = text;
    }

    public void notifyIncomingMessage(MessageConnection messageConnection) {
        (new Thread(this)).start();
    }

    public void destroy() {
        if (connection != null) {
            try {
                connection.setMessageListener(null);
                connection.close();
            } catch (IOException e) {
                // ignore
            } finally {
                connection = null;
            }
        }
    }

    public void reconfigure(Displayable next) {
        if (Config.locationSharing) {
            if (PushRegistry.getMIDlet(Friends.SERVER_URL) == null) {
                destroy(); // avoid IOException in registerConnection
                try {
                    PushRegistry.registerConnection(Friends.SERVER_URL,
                                                    "cz.kruch.track.TrackingMIDlet", "*");
                } catch (Exception e) {
                    Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_PUSH_SMS_FAILED), e, next);
                }
            }
            try {
                open();
            } catch (Throwable t) {
                Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_FRIENDS_FAILED), t, next);
            }
        } else {
            destroy();
            if (PushRegistry.getMIDlet(Friends.SERVER_URL) != null) {
                PushRegistry.unregisterConnection(Friends.SERVER_URL);
            }
        }
    }

    public static void send(String phone, String type, String message,
                            QualifiedCoordinates coordinates, final long time) {
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
        sb.delete(0, sb.length());
        sb.append(SMS_PROTOCOL).append(phone).append(PORT);
        final String url = sb.toString();

//#ifdef __LOG__
        debug(text);
//#endif

        // send the SMS
        (new Thread(new Friends(url, text))).start();
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
                final String header = tokenizer.next().toString();
                String type = null;
                String chat = null;
                if (TBSMS_IAH.equals(header)) {
                    type = TYPE_IAH;
                    chat = CHAT_IAH;
                } else if (TBSMS_MYT.equals(header)) {
                    type = TYPE_MYT;
                    chat = CHAT_MYT;
                }
                if (type == null) {
                    // unknown message
                    Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_SMS) + " '" + text + "'", null, null);
                } else {
                    // get tokens
                    final String times = tokenizer.next().toString();
                    final String latv = tokenizer.next().toString();
                    final String lats = tokenizer.next().toString();
                    final String lonv = tokenizer.next().toString();
                    final String lons = tokenizer.next().toString();
                    final String unknown = tokenizer.next().toString();
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
                    final Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                                      address, chat, time);

                    // notify user
                    Desktop.showAlarm(Resources.getString(Resources.DESKTOP_MSG_SMS_RECEIVED) + wpt.getName(),
                                      null, !Config.autohideNotification);
                    Thread.yield();

                    // notify
                    Waypoints.getInstance().invoke(wpt, null, this);
                }
            }
        } catch (Throwable t) {
            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_SMS_RECEIVE_FAILED), t, null);
        }
    }

    private void execSend() {
        MessageConnection connection = null;
        try {
            connection = (MessageConnection) Connector.open(url, Connector.WRITE);
            final TextMessage sms = (TextMessage) connection.newMessage(MessageConnection.TEXT_MESSAGE);
            sms.setPayloadText(text);
            connection.send(sms);
            Desktop.showConfirmation(Resources.getString(Resources.DESKTOP_MSG_SMS_SENT), null);
        } catch (Throwable t) {
            Desktop.showError(Resources.getString(Resources.DESKTOP_MSG_SMS_SEND_FAILED)+ " [" + url + "]", t, null);
        } finally {
            try {
                connection.close();
            } catch (Exception e) { // IOE or NPE
                // ignore
            }
        }
    }

    private void open() throws IOException {
        if (connection == null) {
            connection = (MessageConnection) Connector.open(SERVER_URL, Connector.READ);
            connection.setMessageListener(this);
        }
    }

    private static double parseSentence(final String value, final String letter) {
        int degl, type, sign;
        switch (letter.charAt(0)) {
            case 'N': {
                type = QualifiedCoordinates.LAT;
                sign = 1;
                degl = 2;
            } break;
            case 'S': {
                type = QualifiedCoordinates.LAT;
                sign = -1;
                degl = 2;
            } break;
            case 'E': {
                type = QualifiedCoordinates.LON;
                sign = 1;
                degl = 3;
            } break;
            case 'W': {
                type = QualifiedCoordinates.LON;
                sign = -1;
                degl = 3;
            } break;
            default:
                throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int i = value.indexOf('.');
        if ((type == QualifiedCoordinates.LAT && (i != 4)) || (type == QualifiedCoordinates.LON && i != 5)) {
            throw new IllegalArgumentException("Malformed coordinate: " + value);
        }

        final int deg = Integer.parseInt(value.substring(0, degl));
        final double min = Double.parseDouble(value.substring(degl));

        return sign * (deg + min / 60D);
    }

    private static String toSentence(final int type, double value) {
        final int sign = value < 0D ? -1 : 1;
        value = Math.abs(value);
        int d = (int) Math.floor(value);
        value -= d;
        value *= 60D;
        double min = value;
        int m = (int) Math.floor(min);
        double l = min - m;
        l *= 100000D;
        int s = (int) Math.floor(l);
        if ((l - s) > 0.5D) {
            s++;
            if (s == 100000) {
                s = 0;
                m++;
                if (m == 60) {
                    m = 0;
                    d++;
                }
            }
        }

        StringBuffer sb = new StringBuffer(32);
        if (type == QualifiedCoordinates.LON && d < 100) {
            sb.append('0');
        }
        if (d < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, d);
        if (m < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, m).append('.');
        if (s < 10000) {
            sb.append('0');
        }
        if (s < 1000) {
            sb.append('0');
        }
        if (s < 100) {
            sb.append('0');
        }
        if (s < 10) {
            sb.append('0');
        }
        NavigationScreens.append(sb, s);
        sb.append(',').append(type == QualifiedCoordinates.LAT ? (sign == -1 ? "S" : "N") : (sign == -1 ? "W" : "E"));

        return sb.toString();
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
            chat = CHAT_IAH;
        } else if (TBSMS_MYT.equals(header)) {
            type = TYPE_MYT;
            chat = CHAT_MYT;
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
            double lat = parseSentence(latv, lats);
            double lon = parseSentence(lonv, lons);
            String xxx = (new Date(time)).toString();
            Waypoint wpt = new Waypoint(QualifiedCoordinates.newInstance(lat, lon),
                                        null, chat, time);

        } else {
            Desktop.showWarning(Resources.getString(Resources.DESKTOP_MSG_UNKNOWN_SMS) + " '" + text + "'", null, null);
        }
    }
//#endif
}
