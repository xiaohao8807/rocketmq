/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.snode.processor;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.RemotingChannel;
import org.apache.rocketmq.remoting.RequestProcessor;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.transport.mqtt.MqttHeader;
import org.apache.rocketmq.snode.SnodeController;
import org.apache.rocketmq.snode.processor.mqtthandler.MessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttConnectMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttDisconnectMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPingreqMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPubackMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPubcompMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPublishMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPubrecMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttPubrelMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttSubscribeMessageHandler;
import org.apache.rocketmq.snode.processor.mqtthandler.MqttUnsubscribeMessagHandler;

public class DefaultMqttMessageProcessor implements RequestProcessor {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.SNODE_LOGGER_NAME);

    private Map<MqttMessageType, MessageHandler> type2handler = new HashMap<>();
    private final SnodeController snodeController;
    private static final int MIN_AVAILABLE_VERSION = 3;
    private static final int MAX_AVAILABLE_VERSION = 4;

    public DefaultMqttMessageProcessor(SnodeController snodeController) {
        this.snodeController = snodeController;
        registerMessageHandler(MqttMessageType.CONNECT,
            new MqttConnectMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.DISCONNECT,
            new MqttDisconnectMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PINGREQ,
            new MqttPingreqMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PUBLISH,
            new MqttPublishMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PUBACK, new MqttPubackMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PUBCOMP,
            new MqttPubcompMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PUBREC, new MqttPubrecMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.PUBREL, new MqttPubrelMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.SUBSCRIBE,
            new MqttSubscribeMessageHandler(this.snodeController));
        registerMessageHandler(MqttMessageType.UNSUBSCRIBE,
            new MqttUnsubscribeMessagHandler(this.snodeController));
    }

    @Override
    public RemotingCommand processRequest(RemotingChannel remotingChannel, RemotingCommand message)
        throws RemotingCommandException, UnsupportedEncodingException {
        MqttHeader mqttHeader = (MqttHeader) message.readCustomHeader();
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.valueOf(mqttHeader.getMessageType()),
            mqttHeader.isDup(), MqttQoS.valueOf(mqttHeader.getQosLevel()), mqttHeader.isRetain(),
            mqttHeader.getRemainingLength());
        MqttMessage mqttMessage = null;
        switch (fixedHeader.messageType()) {
            case CONNECT:
                MqttConnectVariableHeader mqttConnectVariableHeader = new MqttConnectVariableHeader(
                    mqttHeader.getName(), mqttHeader.getVersion(), mqttHeader.isHasUserName(),
                    mqttHeader.isHasPassword(), mqttHeader.isWillRetain(),
                    mqttHeader.getWillQos(), mqttHeader.isWillFlag(),
                    mqttHeader.isCleanSession(), mqttHeader.getKeepAliveTimeSeconds());
                MqttConnectPayload mqttConnectPayload = (MqttConnectPayload) message.getPayload();
                mqttMessage = new MqttConnectMessage(fixedHeader, mqttConnectVariableHeader, mqttConnectPayload);
                break;
            case PUBLISH:
                MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader(mqttHeader.getTopicName(), mqttHeader.getPacketId());
                mqttMessage = new MqttPublishMessage(fixedHeader, mqttPublishVariableHeader, (ByteBuf) message.getPayload());
                break;
            case SUBSCRIBE:
                MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(mqttHeader.getMessageId());
                mqttMessage = new MqttSubscribeMessage(fixedHeader, mqttMessageIdVariableHeader, (MqttSubscribePayload) message.getPayload());
                break;
            case UNSUBSCRIBE:
            case PINGREQ:
            case DISCONNECT:
        }
        return type2handler.get(MqttMessageType.valueOf(mqttHeader.getMessageType())).handleMessage(mqttMessage, remotingChannel);
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerMessageHandler(MqttMessageType type, MessageHandler handler) {
        type2handler.put(type, handler);
    }
}
