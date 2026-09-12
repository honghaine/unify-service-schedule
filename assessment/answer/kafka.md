## Phase 0 - why do need Kafka?

**Answer** 
- Kafka is a flatform is a flatform that has: message queue, pub-sub, log-based streaming. 
- The major difference between kafka vs MQ is they retain the log and can retry, and tradition MQ will delete log if they already processed.
- Do not use kafka if your system is small and simple, apply kafka will make your system more complicated, and the effort and operational maintenance needed to use Kafka
- Trade-off latency vs durability: 
1. Pros for kafka
- No need for waiting the whole process
- Easy to retry
- Dont get time out for calling 3rd party
2. Cons
- Complicated set up

## Phase 1 - Core Concepts