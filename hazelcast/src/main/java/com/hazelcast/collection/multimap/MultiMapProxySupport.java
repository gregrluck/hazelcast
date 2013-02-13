/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.collection.multimap;

import com.hazelcast.collection.CollectionProxyId;
import com.hazelcast.collection.CollectionService;
import com.hazelcast.collection.operations.*;
import com.hazelcast.concurrent.lock.LockNamespace;
import com.hazelcast.concurrent.lock.LockProxySupport;
import com.hazelcast.config.MultiMapConfig;
import com.hazelcast.instance.ThreadContext;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.AbstractDistributedObject;
import com.hazelcast.spi.Invocation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.Operation;
import com.hazelcast.util.ExceptionUtil;

import java.util.*;
import java.util.concurrent.Future;

/**
 * @ali 1/2/13
 */
public abstract class MultiMapProxySupport extends AbstractDistributedObject<CollectionService> {

    protected final MultiMapConfig config;
    protected final CollectionProxyId proxyId;
    protected final LockProxySupport lockSupport;

    protected MultiMapProxySupport(CollectionService service, NodeEngine nodeEngine,
                                   MultiMapConfig config, CollectionProxyId proxyId) {
        super(nodeEngine, service);
        this.config = new MultiMapConfig(config);
        this.proxyId = proxyId;
        lockSupport = new LockProxySupport(new LockNamespace(CollectionService.SERVICE_NAME, proxyId));
    }

    public <V> Collection<V> createNew() {
        if (config.getValueCollectionType().equals(MultiMapConfig.ValueCollectionType.SET)) {
            return new HashSet<V>(10);
        } else if (config.getValueCollectionType().equals(MultiMapConfig.ValueCollectionType.LIST)) {
            return new LinkedList<V>();
        }
        throw new IllegalArgumentException("No Matching CollectionProxyType!");
    }

    protected Boolean putInternal(Data dataKey, Data dataValue, int index) {
        try {
            PutOperation operation = new PutOperation(proxyId, dataKey, getThreadId(), dataValue, index);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected CollectionResponse getAllInternal(Data dataKey) {
        try {
            GetAllOperation operation = new GetAllOperation(proxyId, dataKey);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Boolean removeInternal(Data dataKey, Data dataValue) {
        try {
            RemoveOperation operation = new RemoveOperation(proxyId, dataKey, getThreadId(), dataValue);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected CollectionResponse removeInternal(Data dataKey) {
        try {
            RemoveAllOperation operation = new RemoveAllOperation(proxyId, dataKey, getThreadId());
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Set<Data> localKeySetInternal() {
        return getService().localKeySet(proxyId);
    }

    protected Set<Data> keySetInternal() {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            KeySetOperation operation = new KeySetOperation(proxyId);
            Map<Integer, Object> results = nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
            Set<Data> keySet = new HashSet<Data>();
            for (Object result : results.values()) {
                if (result == null) {
                    continue;
                }
                CollectionResponse response = (CollectionResponse) nodeEngine.toObject(result);
                keySet.addAll(response.getCollection());
            }
            return keySet;
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Map valuesInternal() {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            ValuesOperation operation = new ValuesOperation(proxyId);
            Map<Integer, Object> results = nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
            return results;
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }


    protected Map entrySetInternal() {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            EntrySetOperation operation = new EntrySetOperation(proxyId);
            Map<Integer, Object> results = nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
            return results;
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected boolean containsInternal(Data key, Data value) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            ContainsEntryOperation operation = new ContainsEntryOperation(proxyId, key, value);
            Map<Integer, Object> results = nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
            for (Object obj : results.values()) {
                if (obj == null) {
                    continue;
                }
                Boolean result = nodeEngine.toObject(obj);
                if (result) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            return (Boolean) ExceptionUtil.rethrow(throwable);
        }
    }

    public int size() {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            SizeOperation operation = new SizeOperation(proxyId);
            Map<Integer, Object> results = nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
            int size = 0;
            for (Object obj : results.values()) {
                if (obj == null) {
                    continue;
                }
                Integer result = nodeEngine.toObject(obj);
                size += result;
            }
            return size;
        } catch (Throwable throwable) {
            return (Integer) ExceptionUtil.rethrow(throwable);
        }
    }

    public void clear() {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            ClearOperation operation = new ClearOperation(proxyId);
            nodeEngine.getOperationService().invokeOnAllPartitions(CollectionService.SERVICE_NAME, operation);
        } catch (Throwable throwable) {
            ExceptionUtil.rethrow(throwable);
        }
    }

    protected Integer countInternal(Data dataKey) {
        try {
            CountOperation operation = new CountOperation(proxyId, dataKey);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Object getInternal(Data dataKey, int index) {
        try {
            GetOperation operation = new GetOperation(proxyId, dataKey, index);
            return invokeData(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Boolean containsInternalList(Data dataKey, Data dataValue) {
        try {
            ContainsOperation operation = new ContainsOperation(proxyId, dataKey, dataValue);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Boolean containsAllInternal(Data dataKey, Set<Data> dataSet) {
        try {
            ContainsAllOperation operation = new ContainsAllOperation(proxyId, dataKey, dataSet);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Object setInternal(Data dataKey, int index, Data dataValue) {
        try {
            SetOperation operation = new SetOperation(proxyId, dataKey, getThreadId(), index, dataValue);
            return invokeData(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Object removeInternal(Data dataKey, int index) {
        try {
            RemoveIndexOperation operation = new RemoveIndexOperation(proxyId, dataKey, getThreadId(), index);
            return invokeData(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Integer indexOfInternal(Data dataKey, Data value, boolean last) {
        try {
            IndexOfOperation operation = new IndexOfOperation(proxyId, dataKey, value, last);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Boolean addAllInternal(Data dataKey, List<Data> dataList, int index) {
        try {
            AddAllOperation operation = new AddAllOperation(proxyId, dataKey, getThreadId(), dataList, index);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    protected Boolean compareAndRemoveInternal(Data dataKey, List<Data> dataList, boolean retain) {
        try {
            CompareAndRemoveOperation operation = new CompareAndRemoveOperation(proxyId, dataKey, getThreadId(), dataList, retain);
            return invoke(operation, dataKey);
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    public Object getId() {
        return proxyId;
    }

    public String getName() {
        return proxyId.getName();
    }

    public String getServiceName() {
        return CollectionService.SERVICE_NAME;
    }

    private <T> T invoke(Operation operation, Data dataKey) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            int partitionId = nodeEngine.getPartitionService().getPartitionId(dataKey);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(CollectionService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return (T) nodeEngine.toObject(f.get());
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    private Object invokeData(CollectionOperation operation, Data dataKey) {
        final NodeEngine nodeEngine = getNodeEngine();
        try {
            int partitionId = nodeEngine.getPartitionService().getPartitionId(dataKey);
            Invocation inv = nodeEngine.getOperationService().createInvocationBuilder(CollectionService.SERVICE_NAME, operation, partitionId).build();
            Future f = inv.invoke();
            return nodeEngine.toObject(f.get());
        } catch (Throwable throwable) {
            return ExceptionUtil.rethrow(throwable);
        }
    }

    private int getThreadId() {
        return ThreadContext.getThreadId();
    }

}