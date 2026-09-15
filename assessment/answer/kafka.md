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
  - acks = all: Producer pushes message and wait until partition leader and its replication sync

- Why 2 consumers in the same group never consume the same message from the same partition at the same time?
- Because they have offset, exclusively partition assignment. 
- Kafka assigns only one consumer instance in a consumer group to a specific partition at any given time

**Senior: Redesign webhook nhận từ payment gateway (Antom) → Kafka, không mất event nếu crash giữa chừng**
- Vấn đề gốc: dual-write - nếu handler vừa xử lý business logic vừa publish Kafka trong cùng flow, crash giữa 2 bước sẽ mất event (hoặc publish nhưng chưa lưu DB, hoặc lưu DB nhưng chưa publish).
- Bước 1 (webhook receive): handler chỉ làm 2 việc - verify signature, rồi persist raw payload vào 1 outbox table (cùng transaction với DB write nghiệp vụ nếu có) - KHÔNG gọi Kafka publish trực tiếp trong request thread. Return 200 cho Antom ngay sau khi persist thành công.
  - Cần idempotency key (Antom transaction_id/notify_id) unique constraint ở outbox table, vì gateway sẽ retry webhook nếu không nhận 200 đúng hạn -> tránh insert trùng.
- Bước 2 (publish): 1 process riêng (poller đọc outbox theo interval, hoặc CDC/Debezium tail binlog của outbox table) đọc row chưa publish, gửi vào Kafka với producer idempotent (`enable.idempotence=true`, acks=all), sau khi broker ack thì mark row là published.
- Vì DB write (outbox) và publish Kafka tách rời, crash ở bất kỳ đâu đều recover được:
  - Crash sau khi persist outbox nhưng trước khi publish -> row còn ở trạng thái chưa publish, poller/CDC pick up lại sau khi service restart -> không mất event.
  - Crash sau khi publish nhưng trước khi mark published -> lần sau publish lại -> Kafka nhận duplicate -> consumer phía sau phải idempotent theo transaction_id (giống kỹ thuật idempotency anh đang dùng cho refund flow) để xử lý an toàn (at-least-once, không phải exactly-once).
- CDC (Debezium) tốt hơn polling ở chỗ không cần tự quản lý polling interval/lock, và không tạo thêm write amplification lên outbox table.

