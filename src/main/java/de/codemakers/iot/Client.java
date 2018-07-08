/*
 * Copyright 2018 Paul Hagedorn (Panzer1119).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codemakers.iot;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * IFTTJ Client
 *
 * @author Paul Hagedorn (Panzer1119)
 */
public class Client {

    public static final int SLEEP_TIME_AFTER_EVENT = 10;

    private String ip;
    private int port;
    private String url_suffix;
    private URL url;
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private BiConsumer<String, String> handler = null;
    private final Map<String, BiConsumer<String, String>> handlers = new ConcurrentHashMap<>();

    /**
     * Constructs a new Server for IFTTT POSTs and IFTTJ Clients
     *
     * @param ip IP Address to connect to
     * @param port Port to connect to
     */
    public Client(String ip, int port) {
        this(ip, port, IFTTJ.URL_SUFFIX);
    }

    /**
     * Constructs a new Server for IFTTT POSTs and IFTTJ Clients
     *
     * @param ip IP Address to connect to
     * @param port Port to connect to
     * @param url_suffix URL suffix after "http://ip:port/"
     */
    public Client(String ip, int port, String url_suffix) {
        this.ip = ip;
        this.port = port;
        this.url_suffix = url_suffix;
        update();
    }

    /**
     * Returns the IP Address to connect to
     *
     * @return IP Address
     */
    public final String getIP() {
        return ip;
    }

    /**
     * Sets the IP Address to connect to
     *
     * @param ip IP Address
     * @return A reference to this Client
     */
    public final Client setIP(String ip) {
        this.ip = ip;
        update();
        return this;
    }

    /**
     * Returns the Port to connect to
     *
     * @return Port
     */
    public final int getPort() {
        return port;
    }

    /**
     * Sets the Port to connect to
     *
     * @param port Port
     * @return A reference to this Client
     */
    public final Client setPort(int port) {
        this.port = port;
        update();
        return this;
    }

    /**
     * Returns the URL Suffix after "http://ip:port/"
     *
     * @return URL suffix
     */
    public final String getURLSuffix() {
        return url_suffix;
    }

    /**
     * Sets the URL Suffix after "http://ip:port/"
     *
     * @param url_suffix URL suffix
     * @return A reference to this Client
     */
    public final Client setURLSuffix(String url_suffix) {
        this.url_suffix = url_suffix;
        update();
        return this;
    }

    /**
     * Returns the URL Object
     *
     * @return URL
     */
    public final URL getURL() {
        return url;
    }

    private final void update() {
        try {
            url = new URL(String.format("http://%s:%d/%s", ip, port, url_suffix));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Returns the handler, which handles every event coming from IFTTT
     *
     * @return Main handler
     */
    public final BiConsumer<String, String> getHandler() {
        return handler;
    }

    /**
     * Sets the handler, which handles every event coming from IFTTT
     *
     * @param handler Main handler
     * @return A reference to this Client
     */
    public final Client setHandler(BiConsumer<String, String> handler) {
        this.handler = handler;
        return this;
    }

    /**
     * Returns a handler, which handles every event coming from a specific
     * Applet
     *
     * @param id Applet ID
     * @return Handler
     */
    public final BiConsumer<String, String> getHandler(String id) {
        return handlers.get(id);
    }

    /**
     * Sets a handler, which handles every event coming from a specific Applet
     *
     * @param id Applet ID
     * @param handler Handler
     * @return A reference to this Client
     */
    public final Client addHandler(String id, BiConsumer<String, String> handler) {
        handlers.put(id, handler);
        return this;
    }

    /**
     * Removes a handler, which handles every event coming from a specific
     * Applet
     *
     * @param id Applet ID
     * @return Handler
     */
    public final BiConsumer<String, String> removeHandler(String id) {
        return handlers.remove(id);
    }

    /**
     * Grabs the newest event
     *
     * @param id Applet ID
     * @return null Reference or the text of the event
     */
    public final String grabEvent(String id) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            final String request = IFTTJ.IFTTJ_GET_EVENTS_PREFIX + id;
            connection.setDoOutput(true);
            final DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
            dos.writeBytes(request);
            dos.flush();
            dos.close();
            final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = null;
            final StringBuffer response = new StringBuffer();
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            return convertNullToNull(response.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Starts some listeners for some specific Applets
     *
     * @param period Time period to check for updates in milliseconds
     * @param ids Applet IDs
     * @return <tt>true</tt> if some listeners were started successfully
     */
    public final boolean start(int period, final String... ids) {
        if (ids == null || ids.length == 0) {
            if (handlers.isEmpty()) {
                return false;
            }
            handlers.keySet().forEach((id) -> start(id, period));
            return true;
        }
        for (String id : ids) {
            start(id, period);
        }
        return true;
    }

    /**
     * Starts a listener for a specific Applet
     *
     * @param id Applet ID
     * @param period Time period to check for updates in milliseconds
     * @return <tt>true</tt> if the listener was started successfully
     */
    public final boolean start(final String id, int period) {
        if (timers.get(id) != null) {
            return false;
        }
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public final void run() {
                try {
                    String event = null;
                    while ((event = grabEvent(id)) != null) {
                        if (event != null) {
                            if (handler != null) {
                                handler.accept(id, event);
                            }
                            final BiConsumer<String, String> consumer = handlers.get(id);
                            if (consumer != null) {
                                consumer.accept(id, event);
                            }
                        }
                        Thread.sleep(SLEEP_TIME_AFTER_EVENT);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, 0, period);
        timers.put(id, timer);
        return true;
    }

    /**
     * Stops a listener for a specific Applet
     *
     * @param id Applet ID
     * @return <tt>true</tt> if the listener was stopped successfully
     */
    public final boolean stop(String id) {
        final Timer timer = timers.get(id);
        if (timer == null) {
            return false;
        }
        timer.cancel();
        timers.remove(id);
        return true;
    }

    /**
     * Stops all listeners
     *
     * @return <tt>true</tt> if some listeners were stopped successfully
     */
    public final boolean stopAll() {
        if (timers.isEmpty()) {
            return false;
        }
        timers.keySet().forEach(this::stop);
        return true;
    }

    /**
     * Converts an empty or 'null' containing String to a null Reference
     *
     * @param temp Text
     * @return Converted text
     */
    protected static final String convertNullToNull(String temp) {
        return temp == null ? null : ((temp.equals("" + null) || temp.isEmpty()) ? null : temp);
    }

}
