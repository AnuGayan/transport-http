/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.transport.http.netty.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.messaging.exceptions.ClientConnectorException;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.carbon.transport.http.netty.common.Constants;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketClientConnector;
import org.wso2.carbon.transport.http.netty.contract.websocket.WebSocketConnectorListener;
import org.wso2.carbon.transport.http.netty.contractimpl.HttpWsConnectorFactoryImpl;
import org.wso2.carbon.transport.http.netty.util.server.websocket.WebSocketRemoteServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.websocket.CloseReason;
import javax.websocket.Session;

/**
 * Test cases for the WebSocket Client implementation.
 */
public class WebSocketClientTestCase extends WebSocketTestCase {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientTestCase.class);

    private HttpWsConnectorFactoryImpl httpConnectorFactory = new HttpWsConnectorFactoryImpl();
    private final String url = "ws://localhost:8490/websocket";
    private final int threadSleepTime = 100;
    private WebSocketClientConnector clientConnector;
    private WebSocketRemoteServer remoteServer = new WebSocketRemoteServer(8490);

    @BeforeClass
    public void setup() throws InterruptedException, ClientConnectorException {
        remoteServer.run();
        Map<String, Object> senderProperties = new HashMap<>();

        senderProperties.put(Constants.REMOTE_ADDRESS, url);
        senderProperties.put(Constants.WEBSOCKET_SUBPROTOCOLS, null);
        clientConnector = httpConnectorFactory.createWsClientConnector(senderProperties);
    }

    @Test(description = "Test the WebSocket handshake and sending and receiving text messages.")
    public void testTextReceived() throws ClientConnectorException, InterruptedException, IOException {
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener();
        Session session = handshake(connectorListener);
        String text = "testText";
        session.getBasicRemote().sendText(text);
        assertWebSocketClientTextMessage(connectorListener, text);
        shutDownClient(session);
    }

    @Test(description = "Test binary message sending and receiving.")
    public void testBinaryReceived() throws ClientConnectorException, InterruptedException, IOException {
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener();
        Session session = handshake(connectorListener);
        byte[] bytes = {1, 2, 3, 4, 5};
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        session.getBasicRemote().sendBinary(buffer);
        assertWebSocketClientBinaryMessage(connectorListener, buffer);
        shutDownClient(session);
    }

    @Test(description = "Test ping pong messaging.")
    public void testPingPong() throws ClientConnectorException, InterruptedException, IOException {
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener();
        Session session = handshake(connectorListener);
        byte[] bytes = {1, 2, 3, 4, 5};
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        session.getBasicRemote().sendPing(buffer);
        Thread.sleep(threadSleepTime);
        Assert.assertTrue(connectorListener.isPongReceived());
        shutDownClient(session);
    }

    @Test(description = "Test multiple clients handling, sending and receiving text messages for them.")
    public void testMultipleClients() throws ClientConnectorException, InterruptedException, IOException {
        WebSocketTestClientConnectorListener connectorListener = new WebSocketTestClientConnectorListener();
        Session session1 = handshake(connectorListener);
        Session session2 = handshake(connectorListener);
        String text1 = "testText1";
        String text2 = "testText2";

        session1.getBasicRemote().sendText(text1);
        assertWebSocketClientTextMessage(connectorListener, text1);

        session2.getBasicRemote().sendText(text2);
        assertWebSocketClientTextMessage(connectorListener, text2);

        session2.getBasicRemote().sendText(text1);
        assertWebSocketClientTextMessage(connectorListener, text1);

        shutDownClient(session1);
        shutDownClient(session2);
    }

    @AfterClass
    public void cleanUp() throws ServerConnectorException, InterruptedException {
        remoteServer.stop();
    }

    private Session handshake(WebSocketConnectorListener connectorListener) throws ClientConnectorException {
        Map<String, String> customHeaders = new HashMap<>();
        return clientConnector.connect(connectorListener, customHeaders);
    }

    private void shutDownClient(Session session) throws ClientConnectorException, IOException {
        session.close(new CloseReason(
                () -> 1000,
                "Normal Closure"
        ));
    }
}
