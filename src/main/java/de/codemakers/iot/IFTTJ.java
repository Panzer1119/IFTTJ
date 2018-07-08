/*
 * Copyright 2018 Paul Hagedorn (Panzer1119)
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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * If This Than Java (IFTTT for Java)
 *
 * @author Paul Hagedorn
 */
public class IFTTJ {
    
    public static final String IFTTT_TRIGGER_ENDPOINT = "https://maker.ifttt.com/trigger/%s/with/key/%s";
    public static String KEY = null;
    public static long MAX_EVENT_TIME = 5000;
    public static long MAX_CLIENT_AFK_TIME = 60000;
    public static final String INET_ADDRESS_OUT = getOutInetAddress();
    public static String URL_SUFFIX = "requests";
    private static Server SERVER;
    private static final Map.Entry<Long, String> EMPTY_EVENT = new AbstractMap.SimpleEntry<>(0L, null);
    public static final String IFTTT_APPLET_REGEX = "IFTTT_APPLET_([A-Za-z0-9]+)(?: (.*))?";
    public static final Pattern IFTTT_APPLET_REGEX_PATTERN = Pattern.compile(IFTTT_APPLET_REGEX);
    public static final String IFTTJ_GET_EVENTS_PREFIX = "IFTTJ_GET_EVENTS_";
    public static final Map<String, LinkedList<Map.Entry<Long, String>>> EVENTS = new ConcurrentHashMap<>();
    public static final Map<InetSocketAddress, Long> CLIENTS_LAST_UPDATE_TIMES = new ConcurrentHashMap<>();
    private static boolean DEBUG = false;
    
    /**
     * Main method, which contains helpfull Information, when the JAR is started
     * lonely
     *
     * @param args Arguments (Port and Debug mode)
     *
     * @throws Exception Exception
     */
    public static void main(String[] args) throws Exception {
        if (INET_ADDRESS_OUT == null) {
            System.err.println("Maybe you have no internet connection? Program stopped!");
            return;
        }
        int port = 8080;
        int maxSize = 100;
        if (args != null && args.length > 0) {
            for (String temp : args) {
                try {
                    port = Integer.parseInt(temp);
                    if (port < 0 || port > 65535) {
                        System.out.println("Invalid port!");
                        return;
                    }
                } catch (Exception ex) {
                    if (temp.equalsIgnoreCase("debug")) {
                        DEBUG = true;
                    }
                }
            }
        }
        System.out.println("Running now as an IFTTT Server (IP: " + INET_ADDRESS_OUT + " Port: " + port + ").");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("To connect an IFTTT Applet to this Server, you need to create a new Applet and select 'Webhooks' (Make a web request) as the 'that' Action.");
        System.out.println("Copy this URL into the IFTTT Applet URL field: " + String.format("http://%s:%d/%s", INET_ADDRESS_OUT, port, URL_SUFFIX));
        System.out.println("For the next step you will need the Applet ID, which you will get AFTER you have created the Action.");
        System.out.println("Just edit your Applet after you have finished the Applet and you will see the ID at the top of the page.");
        System.out.println("Set the Method to 'POST' (Content Type does not matter) and write 'IFTTT_APPLET_{your Applet ID} [Some Text]' in the Body field.");
        System.out.println("Important is, that you need a whitespace after your ID in the Body field, when you type some more text in!");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("To use this Server from a Java Program, you need to create a new Client and give it via the constructor the ip and port of this Server.");
        System.out.println("Then you can add Handlers to the Client for specific Applets (via their IDs), or set one Handler, which handles all monitored events.");
        System.out.println("Last but not least you need to start the Client and give it a timeperiod in which it checks for updates (e.g. 500ms).");
        System.out.println("The Client can automatically monitor every registered Handler or just the ones you want to be monitored.");
        System.out.println("---------------------------------------------------------------------");
        System.out.println("Example Code:");
        System.out.printf("    final Client client = new Client(\"%1$s\", %2$d); //Creates a new Client, which connects to %1$s:%2$d\n" + "    client.addHandler(\"{APPLET ID}\", (id, event) -> {\n" + "        System.out.println(\"Do Something\");\n" + "    }); //Adds a handler that listens for {APPLET ID}\n" + "    client.start(500); //Starts the client and checks every 500ms for updates\n", INET_ADDRESS_OUT, port);
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("You can stop the server by typing 'stop' or 'shutdown'.");
        System.out.println("You can start the server by typing 'start' or 'boot'.");
        System.out.println("You can restart the server by typing 'restart' or 'reboot'.");
        System.out.println("You can exit toggle the debug mode by typing 'd' or 'debug'");
        System.out.println("You can exit the program by typing 'q', 'quit' or 'exit'");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------");
        SERVER = new Server(port);
        SERVER.setHandler((inetSocketAddress, input) -> {
            int responseCode = 200;
            String output = input;
            final long now = System.currentTimeMillis();
            try {
                if (input.startsWith(IFTTJ_GET_EVENTS_PREFIX)) {
                    final LinkedList<Map.Entry<Long, String>> events = EVENTS.get(input.substring(IFTTJ_GET_EVENTS_PREFIX.length()));
                    if (events != null) {
                        clearOldData(events, now);
                        output = getEvent(events, CLIENTS_LAST_UPDATE_TIMES.get(inetSocketAddress)).getValue();
                    } else {
                        output = null;
                    }
                    CLIENTS_LAST_UPDATE_TIMES.put(inetSocketAddress, now);
                } else {
                    final Matcher matcher = IFTTT_APPLET_REGEX_PATTERN.matcher(input);
                    if (matcher.find()) {
                        final String id = matcher.group(1);
                        final String data = matcher.group(2);
                        EVENTS.computeIfAbsent(id, (key) -> newLinkedList(maxSize)).add(new AbstractMap.SimpleEntry<>(now, data));
                    } else {
                        responseCode = 404;
                        output = "Not recognized any commands!";
                    }
                }
                if (DEBUG) {
                    System.out.println(String.format("[SERVER] Request from '%s': \"%s\", response: \"%s\"", inetSocketAddress.getAddress(), input, output));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return new AbstractMap.SimpleEntry<>(responseCode, output);
        });
        SERVER.start(true);
        new Thread(() -> {
            try {
                final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.equalsIgnoreCase("q") || line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                        SERVER.stop(false);
                        System.out.println("Server stopped!");
                        br.close();
                        System.exit(0);
                    } else if (line.equalsIgnoreCase("stop") || line.equalsIgnoreCase("shutdown")) {
                        SERVER.stop(false);
                        System.out.println("Server stopped!");
                    } else if (line.equalsIgnoreCase("start") || line.equalsIgnoreCase("boot")) {
                        SERVER.start(false);
                        System.out.println("Server started!");
                    } else if (line.equalsIgnoreCase("restart") || line.equalsIgnoreCase("reboot")) {
                        SERVER.stop(false);
                        SERVER.start(false);
                        System.out.println("Server restarted!");
                    } else if (line.equalsIgnoreCase("d") || line.equalsIgnoreCase("debug")) {
                        DEBUG = !DEBUG;
                        System.out.println("Toggled Debug Mode to " + DEBUG);
                    } else {
                        System.err.println("Input not recognized: " + line);
                    }
                }
                br.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(-1);
            }
        }).start();
    }
    
    private static final String getOutInetAddress() {
        try {
            final URL url = new URL("http://checkip.amazonaws.com");
            final BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            final String ip = br.lines().collect(Collectors.joining());
            br.close();
            return ip;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
    private static final Map.Entry<Long, String> getEvent(LinkedList<Map.Entry<Long, String>> events, long lastUpdateTime) {
        return events.stream().filter((event) -> event.getKey() > lastUpdateTime).findFirst().orElse(EMPTY_EVENT);
    }
    
    private static final void clearOldData(LinkedList<Map.Entry<Long, String>> events, long now) {
        events.removeIf((event) -> (now - event.getKey()) >= MAX_EVENT_TIME);
        CLIENTS_LAST_UPDATE_TIMES.entrySet().removeIf((entry) -> (now - entry.getValue()) >= MAX_CLIENT_AFK_TIME);
    }
    
    private static final LinkedList<Map.Entry<Long, String>> newLinkedList(final int maxSize) {
        return new LinkedList<Map.Entry<Long, String>>() {
            
            @Override
            public final boolean addAll(Collection<? extends Map.Entry<Long, String>> c) {
                if (c != null) {
                    c.forEach(this::add);
                }
                return c != null;
            }
            
            @Override
            public final boolean add(Map.Entry<Long, String> e) {
                if (size() >= maxSize) {
                    remove();
                }
                return super.add(e);
            }
            
            @Override
            public final String toString() {
                return stream().map(Object::toString).collect(Collectors.joining("#"));
            }
            
        };
    }
    
    /**
     * First set your IFTTT Webhook Key with 'IFTTJ.KEY = "Your Key";'
     *
     * @param event Event name
     * @param values Optionally up to 3 values
     *
     * @return <tt>true</tt> if the Applet was successfully triggered
     */
    public static final boolean trigger(String event, Object... values) {
        try {
            final CloseableHttpClient client = HttpClients.createDefault();
            final HttpPost post = new HttpPost(String.format(IFTTT_TRIGGER_ENDPOINT, event, KEY));
            final StringEntity entity = new StringEntity(toJSON(values));
            System.out.println("VALUES: " + toJSON(values));
            post.setEntity(entity);
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");
            final CloseableHttpResponse response = client.execute(post);
            final int responseCode = response.getStatusLine().getStatusCode();
            response.close();
            client.close();
            return responseCode == 200;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
    
    private static final String toJSON(Object... values) {
        return IntStream.range(0, values.length).boxed().collect(Collectors.toMap((i) -> "value" + (i + 1), (i) -> values[i])).entrySet().stream().map((entry) -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"").collect(Collectors.joining(",", "{", "}"));
    }
    
}
