package test;

import static org.junit.Assert.*;

import main.ConcurrentKeyValueStore;
import org.junit.Before;
import org.junit.Test;

import java.beans.IntrospectionException;

public class ConcurrentKeyValueStoreTest {

    String key1 = "FIRST KEY";
    String key1SameHashKey = "KEYAAAAAA";
    String key2 = "SECOND KEY";
    String value1 = "FIRST VALUE";
    String value1SameHashKey = "FIRST VALUEAAAAAA";
    String value2 = "SECOND VALUE";
    byte[] bytesValue1 = value1.getBytes();
    byte[] bytesValue1SameHashKey = value1SameHashKey.getBytes();
    byte[] bytesValue2 = value2.getBytes();
    public static final String CONCURRENT_KEY_VALUE_FILENAME = "ConcurrentKeyValueStore";
    public static final int HASH_ARRAY_SIZE = 10;
    public static final int EDGE_TRIGGERED_PERSISTENCE_CALLS = 100;

    private ConcurrentKeyValueStore createDefaultTestConcurrentKeyValueStore() {
        return new ConcurrentKeyValueStore(HASH_ARRAY_SIZE, CONCURRENT_KEY_VALUE_FILENAME, EDGE_TRIGGERED_PERSISTENCE_CALLS);
    }

    @Test
    public void testGetPut() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();
        store.put(key1, bytesValue1);
        assertEquals(value1, new String(store.get(key1)));
    }

    @Test
    public void testGetPutSameHashKey() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();

        // Key1 and key1SameHashKey generates identical hashKey with hashArraySize 10
        store.put(key1, bytesValue1);
        store.put(key1SameHashKey, bytesValue1SameHashKey);

        assertEquals(value1, new String(store.get(key1)));
        assertEquals(value1SameHashKey, new String(store.get(key1SameHashKey)));
    }

    @Test
    public void testGetPutDifferentHashKey() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();

        // Key1 and Key2 generates different hashKeys with hashArraySize 10
        store.put(key1, bytesValue1);
        store.put(key2, bytesValue2);

        assertEquals(value1, new String(store.get(key1)));
        assertEquals(value2, new String(store.get(key2)));
    }

    @Test
    public void testReplacement() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();
        store.put(key1, bytesValue1);
        store.put(key1, bytesValue2);
        assertEquals(value2, new String(store.get(key1)));
    }

    @Test
    public void testEdgeNotTriggeredSave() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = new ConcurrentKeyValueStore(HASH_ARRAY_SIZE, CONCURRENT_KEY_VALUE_FILENAME, 2);
        store.put(key1, bytesValue1);
        store = new ConcurrentKeyValueStore(CONCURRENT_KEY_VALUE_FILENAME);
        assertNull(store.get(key1));
    }

    @Test
    public void testSave() {
        ConcurrentKeyValueStore.changeToProductionDelayProvider();
        ConcurrentKeyValueStore store = new ConcurrentKeyValueStore(HASH_ARRAY_SIZE, CONCURRENT_KEY_VALUE_FILENAME, 2);

        store.put(key1, bytesValue1);
        store.put(key2, bytesValue2);
        store = new ConcurrentKeyValueStore(CONCURRENT_KEY_VALUE_FILENAME);

        assertEquals(value1, new String(store.get(key1)));
        assertEquals(value2, new String(store.get(key2)));
    }


    // <--------- Unit Tests End, Multithreaded Tests Start --------->
    @Test
    /* Compare the times of singleThread store and multithreaded store
     * With the single thread we will sequentially get an element 5 times and measure system time
     * With the multi thread we will create 5 threads to get an element and measure system time
     * Both executions runs 5 gets for the same element
     * Compare Execution times at the end of the thread
     */
    public void testMultithreadedGetCondition() throws InterruptedException {
        ConcurrentKeyValueStore.changeToTestDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();
        store.put(key1, bytesValue1);

        long startSingleThreadedTime = System.currentTimeMillis();
        for (int index = 0; index < 5; index ++) {
            store.get(key1);
        }
        long endSingleThreadedTime = System.currentTimeMillis();

        int numThreads = 1;
        Thread[] threads = new Thread[numThreads];
        for (int numThread = 0; numThread < numThreads; numThread++) {
            threads[numThread] = new Thread(() -> {
                store.get(key1);
            });
        }

        long startMultiThreadedTime = System.currentTimeMillis();
        for(Thread thread: threads) {
            thread.start();
        }
        for(Thread thread: threads) {
            thread.join();
        }
        long endMultiThreadedTime = System.currentTimeMillis();

        long singleExecutionTime = endSingleThreadedTime - startSingleThreadedTime;
        long multiExecutionTime = endMultiThreadedTime - startMultiThreadedTime;

        // Uncomment if you want to see runtimes. Should be ~5 times faster with multithreading
        // System.out.println("Single Threaded Execution Time: " + singleExecutionTime);
        // System.out.println("Multi Threaded Execution Time: " + multiExecutionTime);

        assert(multiExecutionTime < singleExecutionTime);
    }

    @Test
    /* Compare the times of singleThread store and multithreaded store
     * With the single thread we will sequentially put 2 elements 5 times and measure system time
     * With the multi thread we will create 2 threads put each element 5 times and measure system time
     * Compare Execution times at the end of the thread
     */
    public void testMultithreadedPutCondition() throws InterruptedException {

        ConcurrentKeyValueStore.changeToTestDelayProvider();
        ConcurrentKeyValueStore singleThreadStore = createDefaultTestConcurrentKeyValueStore();
        ConcurrentKeyValueStore multiThreadStore = createDefaultTestConcurrentKeyValueStore();


        long startSingleThreadedTime = System.currentTimeMillis();
        for (int index = 0; index < 5; index ++) {
            singleThreadStore.put(key1, bytesValue1);
            singleThreadStore.put(key2, bytesValue2);
        }
        long endSingleThreadedTime = System.currentTimeMillis();

        Thread thread1 = new Thread(() -> {
            for (int index = 0; index < 5; index++) {
                multiThreadStore.put(key1, bytesValue1);
            }
        });
        Thread thread2 = new Thread(() -> {
            for (int index = 0; index < 5; index++) {
                multiThreadStore.put(key2, bytesValue2);
            }
        });

        long startMultiThreadedTime = System.currentTimeMillis();
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        long endMultiThreadedTime = System.currentTimeMillis();

        long singleExecutionTime = endSingleThreadedTime - startSingleThreadedTime;
        long multiExecutionTime = endMultiThreadedTime - startMultiThreadedTime;

        // Uncomment if you want to see example run. Should be ~2 times faster with multithreading
        // System.out.println("Single Threaded Execution Time: " + singleExecutionTime);
        // System.out.println("Multi Threaded Execution Time: " + multiExecutionTime);
        assert(multiExecutionTime < singleExecutionTime);
    }


    /* Test ensures get/put race condition where the value for a specific key being updated is working properly.
     * If the read or write lock didn't function properly and with the delay timer, these tests will yield corrupted data
     * The test starts up multiple threads that will try to put() two different values into the same key.
     * If synchronization locks are not present in the put() and get() steps of this operation, it will result in corrupted data
     * There is a small chance that this test will pass however given the delay timer and number of threads with different possible values, this is miniscule
     */
    volatile boolean assertionsPassed;
    @Test
    public void testRaceConditions() throws InterruptedException {

        ConcurrentKeyValueStore.changeToTestDelayProvider();
        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();
        assertionsPassed = true;
        int numThreads = 5;
        Thread[] threads = new Thread[numThreads];

        for (int numThread = 0; numThread < numThreads; numThread++) {
            final byte[] value = (numThread % 2 == 0) ? bytesValue1 : bytesValue2;
            threads[numThread] = new Thread(() -> {

                store.put(key1, value);
                String actualResult = new String(store.get(key1));
                try {
                    assertTrue(value1.equals(actualResult) || value2.equals(actualResult));
                } catch (Error e){
                    assertionsPassed = false;
                }

            });
            threads[numThread].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assert(assertionsPassed);
    }

    /* Puts in value2 on the main thread,
     * then puts in value1 with the same key on a different thread after some time
     * Strong consistency will have the main thread get() value be value1 since it was overwritten after the the main thread put().
     */
    @Test
    public void testStronglyConsistent() throws InterruptedException {

        ConcurrentKeyValueStore store = createDefaultTestConcurrentKeyValueStore();
        Thread thread = new Thread(() -> {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            store.put(key1, bytesValue1);
        });

        thread.start();
        store.put(key1, bytesValue2);
        thread.join();

        assertEquals(value1, new String(store.get(key1)));
    }
}
