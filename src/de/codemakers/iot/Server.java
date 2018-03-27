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

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Server
 *
 * @author Paul Hagedorn (Panzer1119)
 */
public class Server {

    private static final Map.Entry<Integer, String> STANDARD_RESPONSE = new AbstractMap.SimpleEntry<>(200, "");

    private final int port;
    private Thread thread = null;
    private HttpServer server = null;
    private BiFunction<InetSocketAddress, String, Map.Entry<Integer, String>> handler = (inetSocketAddress, input) -> new AbstractMap.SimpleEntry<>(200, input);

    public Server(int port) {
        this.port = port;
    }

    public final int getPort() {
        return port;
    }

    public final BiFunction<InetSocketAddress, String, Map.Entry<Integer, String>> getHandler() {
        return handler;
    }

    public final Server setHandler(BiFunction<InetSocketAddress, String, Map.Entry<Integer, String>> handler) {
        this.handler = handler;
        return this;
    }

    public final boolean start(boolean async) {
        if (thread != null || server != null) {
            return false;
        }
        final Runnable start = () -> {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/" + IFTTJ.URL_SUFFIX, (event) -> {
                    final BufferedReader br = new BufferedReader(new InputStreamReader(event.getRequestBody()));
                    final String request = br.lines().collect(Collectors.joining());
                    br.close();
                    final Map.Entry<Integer, String> response = handler != null ? handler.apply(event.getRemoteAddress(), request) : STANDARD_RESPONSE;
                    if (response.getValue() == null) {
                        response.setValue("");
                    }
                    event.sendResponseHeaders(response.getKey(), response.getValue().length());
                    final OutputStream os = event.getResponseBody();
                    os.write(response.getValue().getBytes());
                    os.close();
                });
                server.setExecutor(null);
                server.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                thread = null;
                if (server != null) {
                    server.stop(0);
                }
                server = null;
            }
        };
        if (async) {
            thread = new Thread(start);
            thread.start();
        } else {
            start.run();
        }
        return true;
    }

    public final boolean stop(boolean async) {
        if (thread == null && server == null) {
            return false;
        }
        final Runnable stop = () -> {
            try {
                if (server != null) {
                    server.stop(0);
                }
                server = null;
                if (thread != null) {
                    thread.interrupt();
                }
                thread = null;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };
        if (async) {
            new Thread(stop).run();
        } else {
            stop.run();
        }
        return true;
    }

}
