/*
 * Copyright (c) 2008-2012, Hazel Bilisim Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.instance;

import com.hazelcast.cluster.Joiner;
import com.hazelcast.nio.NodeIOService;
import com.hazelcast.nio.TcpIpConnectionManager;
import com.hazelcast.spi.ConnectionManager;

import java.nio.channels.ServerSocketChannel;

public class DefaultNodeContext implements NodeContext {
    public AddressPicker createAddressPicker(Node node) {
        return new DefaultAddressPicker(node);
    }

    public Joiner createJoiner(Node node) {
        return node.createJoiner();
    }

    public ConnectionManager createConnectionManager(Node node, ServerSocketChannel serverSocketChannel) {
        return new TcpIpConnectionManager(new NodeIOService(node),serverSocketChannel);
    }
}