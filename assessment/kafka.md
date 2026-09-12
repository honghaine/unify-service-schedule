# Kafka Learning Roadmap — Hector

Mục tiêu: nắm Kafka từ nền tảng đến mức đủ trả lời phỏng vấn mid/senior và áp dụng vào hệ thống payment/webhook thực tế (liên hệ trực tiếp tới công việc ở EPOS).

Format mỗi phase: Lý thuyết cốt lõi → Task thực hành (code) → Assessment (quiz/mock Q&A tiered junior→senior).

---

## Phase 0 — Tại sao cần Kafka
**Lý thuyết:** message queue vs pub-sub vs log-based streaming; so sánh Kafka vs RabbitMQ/SQS; khi nào KHÔNG nên dùng Kafka.
**Task:** Viết 1 đoạn (5-7 câu) giải thích tại sao một hệ thống webhook (như EPOS đang làm) có thể cần Kafka thay vì gọi HTTP trực tiếp.
**Assessment:**
- Junior: Kafka là gì, khác gì queue truyền thống?
- Mid: Khi nào dùng Kafka là sai lựa chọn?
- Senior: Trade-off latency vs durability giữa Kafka và gọi đồng bộ trực tiếp trong luồng thanh toán.

## Phase 1 — Core Concepts
**Lý thuyết:** Broker, Topic, Partition, Offset, Producer, Consumer, Consumer Group, Replication, Leader/Follower, ISR.
**Task:**
1. Docker Compose dựng Kafka (KRaft mode, không cần Zookeeper) + Kafka UI.
2. Tạo topic `payment-events` với 3 partitions, replication factor phù hợp cho 1-broker local.
3. Dùng CLI (`kafka-console-producer`/`consumer`) gửi/nhận message thủ công.
   **Assessment:**
- Junior: Partition dùng để làm gì? Offset có ý nghĩa gì trong 1 partition?
- Mid: Nếu tăng số partition sau khi đã có dữ liệu, điều gì xảy ra với message ordering theo key?
- Senior: Thiết kế partition key cho topic `payment-events` sao cho vừa đảm bảo ordering theo transaction, vừa tránh hot partition.

## Phase 2 — Producer/Consumer API (Java + Spring Kafka)
**Lý thuyết:** KafkaTemplate, @KafkaListener, serializer/deserializer (String/JSON/Avro), key vs value, partitioner mặc định.
**Task:**
1. Viết Spring Boot service: producer publish event `PaymentInitiated` lên `payment-events`.
2. Viết consumer service riêng (khác service) nhận và log lại event, dùng consumer group riêng.
3. Thử nghiệm 2 instance cùng consumer group → quan sát rebalance chia partition.
   **Assessment:**
- Junior: `acks=0/1/all` khác nhau thế nào?
- Mid: Vì sao 2 consumer cùng group không bao giờ nhận trùng message từ cùng 1 partition tại cùng thời điểm?
- Senior: Thiết kế lại luồng webhook nhận từ payment gateway (giống Antom bên EPOS) để publish vào Kafka đảm bảo không mất event nếu service crash giữa lúc xử lý.

## Phase 3 — Delivery Semantics & Reliability
**Lý thuyết:** at-most-once/at-least-once/exactly-once, idempotent producer, transactions, retries, `min.insync.replicas`, unclean leader election.
**Task:** Implement idempotent producer (`enable.idempotence=true`) + xử lý duplicate ở consumer bằng idempotency key (liên hệ trực tiếp kỹ thuật idempotency anh đã dùng cho refund flow).
**Assessment:**
- Junior: Phân biệt 3 loại delivery semantics.
- Mid: Idempotent producer giải quyết vấn đề gì, KHÔNG giải quyết vấn đề gì (vd. crash sau khi consumer xử lý xong nhưng trước khi commit offset)?
- Senior: Thiết kế exactly-once cho luồng "nhận webhook → ghi DB → publish Kafka" khi DB và Kafka là 2 hệ thống khác nhau (outbox pattern).

## Phase 4 — Consumer Internals
**Lý thuyết:** poll loop, `max.poll.interval.ms`, `max.poll.records`, auto-commit vs manual commit, rebalance protocols (eager vs cooperative sticky).
**Task:** Chuyển consumer sang manual commit (`enable.auto.commit=false`), commit sau khi xử lý xong logic nghiệp vụ; simulate consumer bị treo lâu hơn `max.poll.interval.ms` và quan sát rebalance.
**Assessment:**
- Mid: Vì sao auto-commit có thể làm mất message khi consumer crash?
- Senior: So sánh eager rebalance vs cooperative sticky assignor, tác động lên throughput khi consumer group lớn.

## Phase 5 — Storage & Retention
**Lý thuyết:** log segments, retention theo thời gian/size, log compaction (dùng cho changelog/state), so sánh với retention thường.
**Task:** Cấu hình topic với `cleanup.policy=compact`, thử publish nhiều lần cùng key, quan sát compaction giữ lại record mới nhất.
**Assessment:**
- Mid: Khi nào dùng compacted topic thay vì retention theo thời gian?
- Senior: Thiết kế topic lưu "trạng thái mới nhất của mỗi transaction" bằng compacted topic.

## Phase 6 — Schema Management
**Lý thuyết:** Schema Registry, Avro/Protobuf, backward/forward/full compatibility, vì sao JSON tự do gây vỡ hệ thống khi scale.
**Task:** Thêm Schema Registry vào docker-compose, chuyển producer/consumer từ JSON sang Avro, thử đổi schema (thêm field optional) và kiểm tra compatibility check.
**Assessment:**
- Senior: Giải thích vì sao thêm 1 field bắt buộc (required) vào Avro schema đang dùng production là breaking change, còn thêm field optional với default thì không.

## Phase 7 — Kafka Connect & CDC (liên hệ webhook/e-invoice work)
**Lý thuyết:** Source/Sink connector, Debezium CDC, dùng Kafka Connect thay vì tự viết producer cho use case đồng bộ DB.
**Task:** Đọc kiến trúc Debezium CDC từ MySQL → Kafka, so sánh với cách anh đang tự trigger event thủ công trong code hiện tại.
**Assessment:**
- Senior: Khi nào nên dùng CDC (Debezium) thay vì publish event trực tiếp trong application code (dual-write problem)?

## Phase 8 — Event-Driven System Design với Kafka
**Lý thuyết:** Outbox pattern (anh đã học), Saga qua Kafka, CQRS, event sourcing cơ bản.
**Task:** Vẽ + viết thiết kế: luồng refund Malaysia (đã làm ở EPOS) nếu redesign bằng Kafka + outbox pattern thay vì gọi trực tiếp Antom.
**Assessment:**
- Senior (mock system design): "Thiết kế hệ thống consolidation e-invoice hàng tháng bằng Kafka thay vì batch job hiện tại — trade-off?"

## Phase 9 — Ops & Performance
**Lý thuyết:** batching, `linger.ms`, compression (`snappy`/`lz4`), consumer lag monitoring, JMX/Prometheus metrics, broker sizing cơ bản.
**Task:** Đo throughput producer với các cấu hình batch/compression khác nhau bằng `kafka-producer-perf-test`.
**Assessment:**
- Senior: Consumer lag tăng liên tục dù CPU/network bình thường — liệt kê 3 nguyên nhân khả dĩ và cách chẩn đoán.

---

## Cách dùng roadmap này
Mỗi lần bắt đầu 1 phase mới trong hội thoại, chỉ cần nói "Phase X" — tôi sẽ hỏi thẳng bộ câu hỏi assessment + review task code nếu anh paste vào. Không cần làm tuần tự cứng nhắc nếu phase nào đã nắm vững qua kinh nghiệm thực tế (vd. idempotency, outbox).