## Phase 0 - why do need Kafka?

**Answer** 
- Kafka is a distributed commit log, consumer group creates: pub-sub, queue, behavior 
- The major difference between kafka vs MQ is they retain the log and can retry, and tradition MQ will delete log if they already processed.
- Do not use kafka if your system is small and simple, apply kafka will make your system more complicated, and the effort and operational maintenance needed to use Kafka
- Trade-off latency vs durability:
- call directly will limit the latency to maximum, but it does not ensure the durability if the server is down, so push the message to kafka
- create another round-trip, and latency increase accordingly to the number of acks (if ack = all), to exchange the durability.

## Phase 1 - Core Concepts
- Partition: partition in topic, and they store the message with key + value, offset is index in partition, it will direct the next value. The ordering will only ensure inside 1 partition
- If we increase partition, it will affect the new message, the old message remains the same, but it will break message totally ordering by key, break the algorithm: hash(key) % numPartitions
- To design the partition key to make it follow ordering with transaction, and avoid hot key, we can combine the transaction_id, use high cardinality and meaningful key, 

## Phase 2 - Producer/Consumer API (Java + Spring Kafka)
- Acks=0/1/all:
  - acks = 0: Fire and forget, Producer push message and don need to care if broker receive it yet
  - acks = 1: Producer pushes message and wait until partition leader (broker) write log then ack, did not wait for replicate to follower.
    - Risk: leader crash before it sync -> lost message 
  - acks = all:  

- Why 2 consumers in the same group never consume the same message from the same partition at the same time?
- Because they have offset, exclusively partition assignment. 


