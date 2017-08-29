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

package org.wso2.carbon.transport.http.netty.contentaware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.MessageUtil;
import org.wso2.carbon.transport.http.netty.common.Constants;
import org.wso2.carbon.transport.http.netty.config.SenderConfiguration;
import org.wso2.carbon.transport.http.netty.config.TransportProperty;
import org.wso2.carbon.transport.http.netty.config.TransportsConfiguration;
import org.wso2.carbon.transport.http.netty.contract.HttpClientConnector;
import org.wso2.carbon.transport.http.netty.contract.HttpConnectorListener;
import org.wso2.carbon.transport.http.netty.contract.HttpResponseFuture;
import org.wso2.carbon.transport.http.netty.contract.HttpWsConnectorFactory;
import org.wso2.carbon.transport.http.netty.contract.ServerConnectorException;
import org.wso2.carbon.transport.http.netty.contractimpl.HttpWsConnectorFactoryImpl;
import org.wso2.carbon.transport.http.netty.message.HTTPCarbonMessage;
import org.wso2.carbon.transport.http.netty.message.HTTPMessageUtil;
import org.wso2.carbon.transport.http.netty.util.TestUtil;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A Message Processor which creates Request and Response
 */
public class RequestResponseCreationListener implements HttpConnectorListener {
    private Logger logger = LoggerFactory.getLogger(RequestResponseCreationListener.class);

    private String responseValue;
    private TransportsConfiguration configuration;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public RequestResponseCreationListener(String responseValue, TransportsConfiguration configuration) {
        this.responseValue = responseValue;
        this.configuration = configuration;
    }

    private class ClientConnectorListener implements HttpConnectorListener {
        String requestValue = "test";
        HTTPCarbonMessage request;

        public ClientConnectorListener(String requestValue, HTTPCarbonMessage request) {
            this.requestValue = requestValue;
            this.request = request;
        }

        @Override
        public void onMessage(HTTPCarbonMessage httpResponse) {
            int length = httpResponse.getFullMessageLength();
            List<ByteBuffer> byteBufferList = httpResponse.getFullMessageBody();

            ByteBuffer byteBuffer = ByteBuffer.allocate(length);
            byteBufferList.forEach(buf -> byteBuffer.put(buf));
            String responseValue = new String(byteBuffer.array()) + ":" + requestValue;
            if (requestValue != null) {
                byte[] array = new byte[0];
                try {
                    array = responseValue.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {

                }

                ByteBuffer byteBuff = ByteBuffer.allocate(array.length);
                byteBuff.put(array);
                byteBuff.flip();
                CarbonMessage carbonMessage = MessageUtil.cloneCarbonMessageWithOutData(httpResponse);
                if (carbonMessage.getHeader(Constants.HTTP_TRANSFER_ENCODING) == null) {
                    carbonMessage.setHeader(Constants.HTTP_CONTENT_LENGTH, String.valueOf(array.length));
                }
                carbonMessage.addMessageBody(byteBuff);
                carbonMessage.setEndOfMsgAdded(true);

                HTTPCarbonMessage httpCarbonMessage = HTTPMessageUtil.convertCarbonMessage(carbonMessage);
                try {
                    request.respond(httpCarbonMessage);
                } catch (ServerConnectorException e) {
                    logger.error("Error occurred during message notification: " + e.getMessage());
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {

        }
    }

    @Override
    public void onMessage(HTTPCarbonMessage httpRequest) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int length = httpRequest.getFullMessageLength();
                    List<ByteBuffer> byteBufferList = httpRequest.getFullMessageBody();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                    byteBufferList.forEach(buf -> byteBuffer.put(buf));
                    String requestValue = new String(byteBuffer.array());
                    byte[] arry = responseValue.getBytes("UTF-8");

                    CarbonMessage newMsg = MessageUtil.cloneCarbonMessageWithOutData(httpRequest);
                    if (newMsg.getHeader(Constants.HTTP_TRANSFER_ENCODING) == null) {
                        newMsg.setHeader(Constants.HTTP_CONTENT_LENGTH, String.valueOf(arry.length));
                    }
                    ByteBuffer byteBuffer1 = ByteBuffer.allocate(arry.length);
                    byteBuffer1.put(arry);
                    byteBuffer1.flip();
                    newMsg.addMessageBody(byteBuffer1);
                    newMsg.setEndOfMsgAdded(true);
                    newMsg.setProperty(Constants.HOST, TestUtil.TEST_HOST);
                    newMsg.setProperty(Constants.PORT, TestUtil.TEST_HTTP_SERVER_PORT);

                    Map<String, Object> transportProperties = new HashMap<>();
                    Set<TransportProperty> transportPropertiesSet = configuration.getTransportProperties();
                    if (transportPropertiesSet != null && !transportPropertiesSet.isEmpty()) {
                        transportProperties = transportPropertiesSet.stream().collect(
                                Collectors.toMap(TransportProperty::getName, TransportProperty::getValue));

                    }

                    SenderConfiguration senderConfiguration = HTTPMessageUtil.getSenderConfiguration(configuration);

                    HttpWsConnectorFactory httpWsConnectorFactory = new HttpWsConnectorFactoryImpl();
                    HttpClientConnector clientConnector =
                            httpWsConnectorFactory.createHttpClientConnector(transportProperties, senderConfiguration);

                    HTTPCarbonMessage httpCarbonMessage = HTTPMessageUtil.convertCarbonMessage(newMsg);
                    HttpResponseFuture future = clientConnector.send(httpCarbonMessage);
                    future.setHttpConnectorListener(new HttpConnectorListener() {
                        @Override
                        public void onMessage(HTTPCarbonMessage httpResponse) {
                            executor.execute(() -> {
                                int length = httpResponse.getFullMessageLength();
                                List<ByteBuffer> byteBufferList = httpResponse.getFullMessageBody();

                                ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                                byteBufferList.forEach(buf -> byteBuffer.put(buf));
                                String responseValue = new String(byteBuffer.array()) + ":" + requestValue;
                                if (requestValue != null) {
                                    byte[] array = new byte[0];
                                    try {
                                        array = responseValue.getBytes("UTF-8");
                                    } catch (UnsupportedEncodingException e) {

                                    }

                                    ByteBuffer byteBuff = ByteBuffer.allocate(array.length);
                                    byteBuff.put(array);
                                    byteBuff.flip();
                                    CarbonMessage carbonMessage = MessageUtil
                                            .cloneCarbonMessageWithOutData(httpResponse);
                                    if (carbonMessage.getHeader(Constants.HTTP_TRANSFER_ENCODING) == null) {
                                        carbonMessage.setHeader(Constants.HTTP_CONTENT_LENGTH,
                                                String.valueOf(array.length));
                                    }
                                    carbonMessage.addMessageBody(byteBuff);
                                    carbonMessage.setEndOfMsgAdded(true);

                                    HTTPCarbonMessage httpCarbonMessage = HTTPMessageUtil
                                            .convertCarbonMessage(carbonMessage);
                                    try {
                                        httpRequest.respond(httpCarbonMessage);
                                    } catch (ServerConnectorException e) {
                                        logger.error("Error occurred during message notification: "
                                                + e.getMessage());
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }
                    });
                } catch (UnsupportedEncodingException e) {
                    logger.error("Encoding is not supported", e);
                } catch (Exception e) {
                    logger.error("Failed to send the message to the back-end", e);
                } finally {
                }
            }

        });

    }

    @Override
    public void onError(Throwable throwable) {

    }

    //    private class EngineCallBack implements CarbonCallback {
    //
    //        String requestValue = "test";
    //        CarbonCallback requestCallBack;
    //
    //        EngineCallBack(String requestValue, CarbonCallback carbonCallback) {
    //            this.requestValue = requestValue;
    //            requestCallBack = carbonCallback;
    //        }
    //
    //        @Override
    //        public void done(CarbonMessage carbonMessage) {
    //            int length = carbonMessage.getFullMessageLength();
    //            List<ByteBuffer> byteBufferList = carbonMessage.getFullMessageBody();
    //
    //            ByteBuffer byteBuffer = ByteBuffer.allocate(length);
    //            byteBufferList.forEach(buf -> byteBuffer.put(buf));
    //            String responseValue = new String(byteBuffer.array()) + ":" + requestValue;
    //            if (requestValue != null) {
    //                byte[] array = new byte[0];
    //                try {
    //                    array = responseValue.getBytes("UTF-8");
    //                } catch (UnsupportedEncodingException e) {
    //
    //                }
    //                ByteBuffer byteBuff = ByteBuffer.allocate(array.length);
    //                byteBuff.put(array);
    //                byteBuff.flip();
    //                carbonMessage = MessageUtil.cloneCarbonMessageWithOutData(carbonMessage);
    //                if (carbonMessage.getHeader(Constants.HTTP_TRANSFER_ENCODING) == null) {
    //                    carbonMessage.setHeader(Constants.HTTP_CONTENT_LENGTH, String.valueOf(array.length));
    //                }
    //
    //                carbonMessage.addMessageBody(byteBuff);
    //                carbonMessage.setEndOfMsgAdded(true);
    //                requestCallBack.done(carbonMessage);
    //            }
    //
    //        }
    //    }


    //    @Override
    //    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback) throws Exception {
    //        executor.execute(new Runnable() {
    //            @Override
    //            public void run() {
    //                try {
    //                    if (carbonMessage.getProperty(org.wso2.carbon.messaging.Constants.DIRECTION) != null
    //                            && carbonMessage.getProperty(org.wso2.carbon.messaging.Constants.DIRECTION)
    //                            .equals(org.wso2.carbon.messaging.Constants.DIRECTION_RESPONSE)) {
    //
    //                        carbonCallback.done(carbonMessage);
    //                    } else {
    //                        int length = carbonMessage.getFullMessageLength();
    //                        List<ByteBuffer> byteBufferList = carbonMessage.getFullMessageBody();
    //                        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
    //                        byteBufferList.forEach(buf -> byteBuffer.put(buf));
    //                        String requestValue = new String(byteBuffer.array());
    //                        byte[] arry = responseValue.getBytes("UTF-8");
    //                        CarbonMessage newMsg = MessageUtil.cloneCarbonMessageWithOutData(carbonMessage);
    //                        if (newMsg.getHeader(Constants.HTTP_TRANSFER_ENCODING) == null) {
    //                            newMsg.setHeader(Constants.HTTP_CONTENT_LENGTH, String.valueOf(arry.length));
    //                        }
    //                        ByteBuffer byteBuffer1 = ByteBuffer.allocate(arry.length);
    //                        byteBuffer1.put(arry);
    //                        byteBuffer1.flip();
    //                        newMsg.addMessageBody(byteBuffer1);
    //                        newMsg.setEndOfMsgAdded(true);
    //                        newMsg.setProperty(Constants.HOST, TestUtil.TEST_HOST);
    //                        newMsg.setProperty(Constants.PORT, TestUtil.TEST_HTTP_SERVER_PORT);
    //                        RequestResponseCreationListener.EngineCallBack engineCallBack =
    //                                new RequestResponseCreationListener.EngineCallBack(requestValue, carbonCallback);
    //                        clientConnector.send(newMsg, engineCallBack);
    //                    }
    //                } catch (UnsupportedEncodingException e) {
    //                    logger.error("Encoding is not supported", e);
    //                } catch (ClientConnectorException e) {
    //                    logger.error("MessageProcessor is not supported ", e);
    //                } finally {
    //                }
    //            }
    //
    //        });
    //
    //        return false;
    //    }
}
