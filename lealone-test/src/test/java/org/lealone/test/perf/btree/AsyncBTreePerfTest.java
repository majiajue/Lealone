/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.test.perf.btree;

import java.util.concurrent.CountDownLatch;

import org.lealone.db.value.ValueInt;
import org.lealone.db.value.ValueString;
import org.lealone.storage.PageOperation;
import org.lealone.storage.PageOperationHandler;
import org.lealone.storage.aose.btree.BTreeMap;
import org.lealone.storage.aose.btree.BTreePage;

// -Xms512M -Xmx512M -XX:+PrintGCDetails -XX:+PrintGCTimeStamps
public class AsyncBTreePerfTest extends StorageMapPerfTestBase {

    public static void main(String[] args) throws Exception {
        new AsyncBTreePerfTest().run();
    }

    private BTreeMap<Integer, String> btreeMap;

    @Override
    public void run() {
        init();

        singleThreadSerialWrite(); // 先生成初始数据
        System.out.println("map size: " + map.size());
        btreeMap.disableParallel = false;
        btreeMap.disableSplit = true;

        int loop = 20;
        for (int i = 1; i <= loop; i++) {
            // testWakeUp();

            // map.clear();
            asyncRandomWrite(i);
            asyncSerialWrite(i);

            multiThreadsRandomRead(i);
            multiThreadsSerialRead(i);

            // asyncRandomRead();
            // asyncSerialRead();

            System.out.println();
        }
    }

    @Override
    protected void init() {
        PageOperationHandlerImpl.setPageOperationHandlersCount(threadsCount);
        PageOperationHandlerImpl.startNodePageOperationHandler(null);
        PageOperationHandlerImpl.startPageOperationHandlers(null);
        super.init();
    }

    @Override
    protected void openMap() {
        if (map == null || map.isClosed()) {
            map = btreeMap = storage.openBTreeMap(AsyncBTreePerfTest.class.getSimpleName(), ValueInt.type,
                    ValueString.type, null);
        }
    }

    void testCopy() {
        int count = 50000;
        BTreePage root = ((BTreeMap<Integer, String>) map).getRootPage();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            root.copy();
        }
        long t2 = System.currentTimeMillis();
        System.out.println("write time: " + (t2 - t1) + " ms, count: " + count);
    }

    void testWakeUp() {
        class TestPageOperation implements PageOperation {
        }
        PageOperationHandler handler = PageOperationHandlerImpl.getNextHandler();
        TestPageOperation test = new TestPageOperation();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            handler.handlePageOperation(test);
            // handler.wakeUp();
        }
        long t2 = System.currentTimeMillis();
        System.out.println("testWakeUp time: " + (t2 - t1) + " ms, count: " + count);
    }

    void asyncRandomWrite(int loop) {
        asyncRandomWrite1(loop);
        // asyncRandomWrite2();
    }

    void asyncRandomWrite1(int loop) {
        PageOperationHandlerImpl.pauseAll();
        CountDownLatch latch = new CountDownLatch(count);
        // long t11 = System.currentTimeMillis();
        int[] keys = randomKeys;
        // 这个循环只是给PageOperationHandler的队列准备Put操作任务，所以还不算写时间
        for (int i = 0; i < count; i++) {
            int key = keys[i];
            String value = "value-";// "value-" + key;
            btreeMap.put(key, value, ar -> {
                latch.countDown();
            });
        }
        // long t21 = System.currentTimeMillis();
        // System.out.println("async put time: " + (t21 - t11) + " ms, count: " + btreeMap.size());
        PageOperationHandlerImpl.resumeAll(); // 唤醒线程，让它开始处理Put操作任务
        long t1 = System.currentTimeMillis();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long t2 = System.currentTimeMillis();
        System.out.println(map.getName() + " loop: " + loop + ", rows: " + btreeMap.size()
                + ", multi-threads random write time: " + (t2 - t1) + " ms");

        // System.out.println("async random write time: " + (t2 - t1) + " ms, put time: " + (t21 - t11) + " ms, count: "
        // + btreeMap.size());
        // // btreeMap.printPage();
    }

    void asyncRandomWrite2() {
        // // count = 1000;
        // // count = 5000;
        // // count = 20000;
        // // count = 100;
        // // count = 5;
        // // count = 13;
        //
        CountDownLatch latch = new CountDownLatch(count);
        long t1 = System.currentTimeMillis();
        int[] keys = randomKeys;
        for (int i = 0; i < count; i++) {
            int key = keys[i];
            // String value = "value-w9999-w9999-w9999value-w9999-w9999-w9999value-w9999-w9999-w9999";// "value-" + key;
            String value = "value-";// "value-" + key;
            btreeMap.put(key, value, ar -> {
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long t2 = System.currentTimeMillis();
        while (true) {
            if (!PageOperationHandlerImpl.getNodePageOperationHandler().getTasks().isEmpty()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        System.out.println("async random write time: " + (t2 - t1) + " ms, count: " + btreeMap.size());
        // btreeMap.printPage();
    }

    void asyncSerialWrite(int loop) {
        asyncSerialWrite1(loop);
        // asyncSerialWrite2();
    }

    void asyncSerialWrite1(int loop) {
        CountDownLatch latch = new CountDownLatch(count);
        PageOperationHandlerImpl.pauseAll();
        for (int i = 0; i < count; i++) {
            int key = i;
            String value = "value-";// "value-" + key;
            btreeMap.put(key, value, ar -> {
                latch.countDown();
            });

            // btreeMap.put(i, "value-" + i);
            // latch.countDown();
        }
        PageOperationHandlerImpl.resumeAll();
        long t1 = System.currentTimeMillis();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long t2 = System.currentTimeMillis();
        while (true) {
            if (!PageOperationHandlerImpl.getNodePageOperationHandler().getTasks().isEmpty()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        System.out.println(map.getName() + " loop: " + loop + ", rows: " + btreeMap.size()
                + ", multi-threads serial write time: " + (t2 - t1) + " ms");
    }

    void asyncSerialWrite2() {
        CountDownLatch latch = new CountDownLatch(count);
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            int key = i;
            String value = "value-";// "value-" + key;
            btreeMap.put(key, value, ar -> {
                latch.countDown();
            });

            // btreeMap.put(i, "value-" + i);
            // latch.countDown();
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long t2 = System.currentTimeMillis();
        while (true) {
            if (!PageOperationHandlerImpl.getNodePageOperationHandler().getTasks().isEmpty()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        System.out.println("async serial write time: " + (t2 - t1) + " ms, count: " + btreeMap.size());
        // btreeMap.printPage();
    }

    void asyncRandomRead() {
        int[] keys = randomKeys;
        // CountDownLatch latch = new CountDownLatch(count);
        // long t1 = System.currentTimeMillis();
        // for (int i = 0; i < count; i++) {
        // btreeMap.get(keys[i], ar -> {
        // latch.countDown();
        // });
        // }
        // try {
        // latch.await();
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            btreeMap.get(keys[i]);
        }
        long t2 = System.currentTimeMillis();
        System.out.println("async random read time: " + (t2 - t1) + " ms, count: " + btreeMap.size());
    }

    void asyncSerialRead() {
        // CountDownLatch latch = new CountDownLatch(count);
        // long t1 = System.currentTimeMillis();
        // for (int i = 0; i < count; i++) {
        // btreeMap.get(i, ar -> {
        // latch.countDown();
        // });
        // }
        // try {
        // latch.await();
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            btreeMap.get(i);
        }
        long t2 = System.currentTimeMillis();
        System.out.println("async serial read time: " + (t2 - t1) + " ms, count: " + btreeMap.size());
    }
}
