package main;

import main.delay.*;
import java.io.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentKeyValueStore implements Serializable {

    private CollisionList[] hashArray;
    private ReentrantReadWriteLock[] locks;
    private int hashArraySize;
    private String fileName;
    private PersistentDataStorage persistentDataStorage;
    private int numberOfPutCalls;
    private int nPutCalls;
    private final Object putCallsLock = new Object();
    private static DelayProvider delayProvider = new ProductionDelayProvider();

    private void initializeConcurrentKeyValueStore(int hashArraySize, String fileName, int nPutCalls) {
        hashArray = new CollisionList[hashArraySize];
        locks = new ReentrantReadWriteLock[hashArraySize];
        this.hashArraySize = hashArraySize;
        this.fileName = fileName;
        this.nPutCalls = nPutCalls;
        this.numberOfPutCalls = 0;
        persistentDataStorage = new PersistentDataStorage();

        for(int index = 0; index < hashArraySize; index++) {
            locks[index] = new ReentrantReadWriteLock();
        }
    }

    // User wants to create a new store with default Array Size and file names
    public ConcurrentKeyValueStore() {
        initializeConcurrentKeyValueStore(
                Constants.HASH_ARRAY_SIZE,
                Constants.CONCURRENT_KEY_VALUE_FILENAME,
                Constants.EDGE_TRIGGERED_PERSISTENCE_CALLS
        );
        saveData();
    }

    /* User wants to use and existing store
     * While the KeyValueStore itself will retain date during runtime, in order for data to persist we will store data in a serializable object.
     * First objects will be the number of lists, hashArraySize, and nPutCalls
     * Subsequent objects will be the index and then the CollisionLists.
     * These are stored as DataStorageNodes which retains the CollisionList and the index its stored at
    */
    public void loadData(String fileName) {

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileName))) {
            int numLists = (int) ois.readObject();
            int hashArraySize = (int) ois.readObject();
            int nPutCalls = (int) ois.readObject();
            initializeConcurrentKeyValueStore(hashArraySize, fileName, nPutCalls);


            for (int numList = 0; numList < numLists; numList++) {
                int index = (int) ois.readObject();
                CollisionList collisionList = (CollisionList) ois.readObject();
                hashArray[index] = collisionList;
                persistentDataStorage.addCollisionList(collisionList, index);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public ConcurrentKeyValueStore(String fileName) {
        loadData(fileName);
    }

    // User wants to create a new store with specified file name
    public ConcurrentKeyValueStore(int hashArraySize, String fileName, int nPutCalls) {
        initializeConcurrentKeyValueStore(hashArraySize, fileName, nPutCalls);
        saveData();
    }

    // Used only for testing. Changes the delay provider implement a delay for testing purposes
    public static void changeToTestDelayProvider() {
        delayProvider = new TestDelayProvider();
    }

    public static void changeToProductionDelayProvider() {
        delayProvider = new ProductionDelayProvider();
    }

    private int getHashKey(String key) {
        return Math.abs(key.hashCode()) % hashArraySize;
    }

    private void saveData() {
        persistentDataStorage.saveData(hashArraySize, fileName, nPutCalls, locks);
    }

    public byte[] get(String key) {
        int index = getHashKey(key);
        locks[index].readLock().lock();
        if (hashArray[index] == null) {
            return null;
        }
        byte[] value = hashArray[index].get(key);
        locks[index].readLock().unlock();
        return value;
    }

    public void put(String key, byte[] value) {
        int index = getHashKey(key);
        locks[index].writeLock().lock();
        if (hashArray[index] == null) {
            CollisionList newCollisionList = new CollisionList();
            hashArray[index] = newCollisionList;
            persistentDataStorage.addCollisionList(newCollisionList, index);
        }
        hashArray[index].put(key, value);
        locks[index].writeLock().unlock();

        // Edge Triggered Persistence
        synchronized (putCallsLock) {
            numberOfPutCalls++;
            if (numberOfPutCalls == nPutCalls) {
                saveData();
                numberOfPutCalls = 0;
            }
        }
    }

    // Lightweight implementation of LinkedList to handle collisions in the Key Value store
    private static class CollisionList implements Serializable {

        private KeyValueNode root;

        private byte[] get(String key) {

            KeyValueNode currentNode = root;
            while (currentNode != null) {
                delayProvider.delay(); // Artificial delay to simulate searching for the current key in the collision list
                if (currentNode.key.equals(key)) {
                    byte[] returnValue = new byte[currentNode.valueSize];
                    for (int index = 0; index < currentNode.valueSize; index++) {
                        delayProvider.delay(); // Artificial delay to simulate copying data into a spot in memory
                        returnValue[index] = currentNode.value[index];
                    }
                    return returnValue;
                } else {
                    currentNode = currentNode.next;
                }
            }
            return null;
        }

        private KeyValueNode putHelper(KeyValueNode node, String key, byte[] value) {

            delayProvider.delay(); // Artificial delay to simulate searching for the current key in the collision list
            if (node == null) {
                return new KeyValueNode(key, value, value.length, null);
            } else {
                if (node.key.equals(key)) {
                    node.value = value;
                    node.valueSize = value.length;
                } else {
                    node.next = putHelper(node.next, key, value);
                }
                return node;
            }
        }

        private void put(String key, byte[] value) {

            byte[] valueToPut = new byte[value.length];
            for (int index = 0; index < value.length; index++) {
                delayProvider.delay(); // Artificial delay to simulate copying data into a spot in memory
                valueToPut[index] = value[index];
            }

            root = putHelper(root, key, valueToPut);
        }

        private static class KeyValueNode implements Serializable {
            String key;
            byte[] value;
            int valueSize;
            KeyValueNode next;

            public KeyValueNode(String key, byte[] value, int valueSize, KeyValueNode next) {
                this.key = key;
                this.value = value;
                this.valueSize = valueSize;
                this.next = next;
            }
        }
    }

    /* Lightweight implementation of a linked list that stores Nodes for persistent data storage.
    *  This data type stores the KeyValue store in a serializable format
    */
    public static class PersistentDataStorage implements Serializable {

        private DataStorageNode root;
        private int numLists;
        private final Object dataStorageLock = new Object();

        public PersistentDataStorage() {
            numLists = 0;
        }

        private void addCollisionList (CollisionList collisionList, int index) {
            synchronized (dataStorageLock) {
                root = new DataStorageNode(collisionList, index, root);
                numLists++;
            }
        }

        public void saveData(int hashArraySize, String fileName, int nPutCalls, ReentrantReadWriteLock[] locks) {
            synchronized (dataStorageLock) {
                DataStorageNode node = root;
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileName))) {

                    oos.writeObject(numLists);
                    oos.writeObject(hashArraySize);
                    oos.writeObject(nPutCalls);
                    while (node != null) {

                        oos.writeObject(node.index);
                        locks[node.index].readLock().lock();
                        oos.writeObject(node.collisionList);
                        locks[node.index].readLock().lock();

                        node = node.next;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private static class DataStorageNode implements Serializable{
            CollisionList collisionList;
            int index;
            DataStorageNode next;

                DataStorageNode(CollisionList collisionList, int index, DataStorageNode next) {
                this.collisionList = collisionList;
                this.index = index;
                this.next = next;
            }
        }
    }
}
