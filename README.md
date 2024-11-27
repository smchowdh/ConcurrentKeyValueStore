# ConcurrentKeyValueStore

This is an implementation of concurrent key value store get and put calls. This includes get() and put() functionality

## Design Details
### Data processing
The implementation stores the keyValue store as a hashmap structure where indexes in the hashArray are obtained through the key's string (see **getHashKey()**). The size of the hash array can be defined by the constructor's input parameter, **hashArraySize**. When collisions (hashKeys are the same) occur, the location in hashArraySize will store the data in a linkedList (named **CollisionList**). 

### Data locking
In order to allow multiple threads to work efficiently on the store without triggering race conditions, when a key is sent to **get()** or **put()** the relevant lock is put in place denoted by the hashKey. This is to allow simultaneous updates to the hashArray to maximize concurrency usage. This level of concurrent threads working on the store can be specified by the hashArraySize. In general, larger hashArraySize means more threads can manipulate data in the store at the same time however more space is needed to store the reference to the CollisionList and to the new locks.

### Strongly Consistent
When multiple threads act on the same key in the key value store, the most recent put() call is saved and all subsequent threads will read that new value.

### Handles Read Heavy Traffic In Embedded Settings
Since it's likely that traffic will be heavily read skewed, Read/Write locks are used in order to avoid race conditions while allowing for read heavy traffic. These locks allow **get()** operations for the same hashKeys to run concurrently however **put()** operations pause execution for **get()** and **put()** for the same hashKeys. 

TLDR: read locks are used for **get()** and write locks are used for **put()**


### Edge Triggered Persistence
As defined by fileName and **nPutCalls**, the store operates on Edge Triggered Persistence. When the user makes **nPutCalls** number of **put()** calls on the store, the store will serialized and save the store as a list of serializable objects in the file specified by fileName. The user may then reload the file with the file name with the **ConcurrentKeyValueStore(fileName)** constructor.

This data is stored as a DataStorageNode object which is a linkedList that contains the details of the store including, hashArraySize, collisionLists, nPutCalls

While data is being saved to DataStorageNode, this is considered be a read operation on the data so read locks are put in place to prevent write operations to the collisionsList.

In the event that there are many put() calls and a relatively low number of nPutCalls such that the writeLock is relatively inaccessible, it's recommended to use a larger nPutCalls edge trigger.

## Constructor Summary

<table>
  <tr>
    <td>ConcurrentKeyValueStore()</td>
    <td>Creates the key value store with the constants defined in main.Constants.java file</td>
 
  </tr>
  <tr>
    <td>ConcurrentKeyValueStore(String fileName)</td>
    <td>Creates the key value store based on stored data in the file name</td>

  </tr>
  <tr>
    <td>ConcurrentKeyValueStore(int hashArraySize, String fileName, int nPutCalls)</td>
    <td>Creates the store with the defined values</td>
   
  </tr>
</table>


## Method Summary
<table>
  <tr>
    <td>byte[] get(String key)()</td>
    <td>Gets the value at key</td>

  </tr>
  <tr>
    <td>void put(String key, byte[] value)</td>
    <td>Puts the value at key. After nPutCalls, the data in the store will be saved to the file specified in the fileName</td>

  </tr>
</table>

## Tests Implementation Details

In order to simulate testing functionality in embedded-ready environments multiple design features were made.

### DelayProvider
I have chosen to artificially create delay to simulate searching for data and copying memory into/from the store to thoroughly test race conditions and concurrency capabilities in an embedded environment. For multithreaded tests, the delay provider is set to delay based on the timer in **main.delay.TestDelayProvider.java** When using the production delay, the function is a no-op resulting in no added delay to production code.
