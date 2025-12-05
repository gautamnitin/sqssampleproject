# SQS Message Replay Functionality

This package provides functionality to replay messages from Dead Letter Queues (DLQs) back to their original queues after issues have been fixed.

## Features

- Generic implementation that works with any message type
- Configurable source (DLQ) and target queues
- Support for message transformation before replaying
- REST API for triggering manual replay operations
- Scheduled automatic replay at configurable intervals
- Batch processing for efficient message handling

## Configuration

Configure the replay functionality in your `application.properties` file:

```properties
# Enable/disable replay functionality
app.sqs.replay.enabled=true

# Configure queue pairs (can have multiple configurations)
app.sqs.replay.queues.employee.sourceQueue=employee-queue-dlq
app.sqs.replay.queues.employee.targetQueue=employee-queue
app.sqs.replay.queues.employee.messageType=com.sqs.sqsproject.employee.Employee
app.sqs.replay.queues.employee.batchSize=10
# Optional transformer - can be specified in two ways:
# Option 1: Using fully qualified class name
app.sqs.replay.queues.employee.transformerClass=com.sqs.sqsproject.replay.transform.EmployeeStatusTransformer
# Option 2: Using Spring bean name (prefixed with '@')
# app.sqs.replay.queues.employee.transformerClass=@employeeStatusTransformer

# Another example configuration
app.sqs.replay.queues.order.sourceQueue=order-queue-dlq
app.sqs.replay.queues.order.targetQueue=order-queue
app.sqs.replay.queues.order.messageType=com.example.Order
app.sqs.replay.queues.order.batchSize=5

# Scheduled replay configuration
app.sqs.replay.scheduled.enabled=true
app.sqs.replay.scheduled.fixed-rate-ms=300000
app.sqs.replay.scheduled.initial-delay-ms=60000
app.sqs.replay.scheduled.max-messages=50
# Comma-separated list of queue IDs eligible for scheduled replay
app.sqs.replay.scheduled.eligible-queue-ids=employee,order
```

### Scheduled Replay Configuration

The scheduled replay feature automatically processes eligible queues at fixed intervals:

- `app.sqs.replay.scheduled.enabled`: Enable/disable scheduled replay (default: false)
- `app.sqs.replay.scheduled.fixed-rate-ms`: Interval between executions in milliseconds (default: 300000 - 5 minutes)
- `app.sqs.replay.scheduled.initial-delay-ms`: Delay before first execution in milliseconds (default: 60000 - 1 minute)
- `app.sqs.replay.scheduled.max-messages`: Maximum messages to process per queue in each run (default: 50)
- `app.sqs.replay.scheduled.eligible-queue-ids`: Comma-separated list of queue configuration IDs that should be processed automatically

## Usage

### REST API Endpoints

#### List Available Queue Configurations

```
GET /api/sqs/replay/queues
```

Response:
```json
{
  "employee": {
    "sourceQueue": "employee-queue-dlq",
    "targetQueue": "employee-queue",
    "messageType": "com.sqs.sqsproject.employee.Employee",
    "transformerClass": "com.sqs.sqsproject.replay.transform.EmployeeStatusTransformer",
    "batchSize": "10"
  }
}
```

#### Replay Messages

```
POST /api/sqs/replay/{queueConfigId}?maxMessages={maxMessages}
```

Parameters:
- `queueConfigId`: The ID of the queue configuration to use (e.g., "employee")
- `maxMessages`: (Optional) The maximum number of messages to replay

Response:
```json
{
  "status": "success",
  "queueConfigId": "employee",
  "messagesProcessed": 5,
  "sourceQueue": "employee-queue-dlq",
  "targetQueue": "employee-queue"
}
```

## Creating Custom Transformers

To create a custom transformer, implement the `MessageTransformer` interface:

```java
@Component("customTransformer")  // Optional: Specify a bean name
public class CustomTransformer implements MessageTransformer<YourMessageType> {
    @Override
    public YourMessageType transform(YourMessageType message) {
        // Transform the message
        return modifiedMessage;
    }
}
```

Then configure it in your `application.properties` using one of two approaches:

### Option 1: Using fully qualified class name (original approach)

```properties
app.sqs.replay.queues.yourQueue.transformerClass=com.your.package.CustomTransformer
```

### Option 2: Using Spring bean name (new approach)

If you've specified a bean name in your `@Component` annotation, you can reference it directly:

```properties
app.sqs.replay.queues.yourQueue.transformerClass=@customTransformer
```

The bean name approach has several advantages:
- It uses Spring's dependency injection to get the transformer instance
- The transformer can have its own dependencies injected
- It avoids creating new instances via reflection

If no transformer is specified, messages will be replayed without transformation.

## Examples

### Manual Replay

1. Check available configurations:
   ```
   GET /api/sqs/replay/queues
   ```

2. Replay up to 10 messages from the employee DLQ:
   ```
   POST /api/sqs/replay/employee
   ```

3. Replay a specific number of messages:
   ```
   POST /api/sqs/replay/employee?maxMessages=5
   ```

### Scheduled Replay

To set up automatic scheduled replay:

1. Configure which queues should be automatically processed:
   ```properties
   # Enable scheduled replay
   app.sqs.replay.scheduled.enabled=true
   
   # Run every 5 minutes (300000 ms)
   app.sqs.replay.scheduled.fixed-rate-ms=300000
   
   # Process up to 50 messages per queue in each run
   app.sqs.replay.scheduled.max-messages=50
   
   # Only process these queues automatically
   app.sqs.replay.scheduled.eligible-queue-ids=employee
   ```

2. The application will automatically process eligible queues at the configured interval.

3. Monitor the application logs to see scheduled replay activity:
   ```
   INFO c.s.s.replay.service.ScheduledReplayService : Starting scheduled replay for 1 eligible queue(s)
   INFO c.s.s.replay.service.ScheduledReplayService : Processing scheduled replay for queue: employee
   INFO c.s.s.replay.service.ScheduledReplayService : Scheduled replay for queue employee processed 5 message(s)
   INFO c.s.s.replay.service.ScheduledReplayService : Completed scheduled replay execution
   ```

## Implementation Details

- `ReplayService`: Core service that handles message replay logic
- `ScheduledReplayService`: Service that automatically replays messages at fixed intervals
- `ReplayController`: REST controller for manual replay operations
- `MessageTransformer`: Interface for message transformation
- `ClassLoaderUtil`: Utility for dynamic class loading and bean retrieval
- `ReplayProperties`: Configuration properties with support for scheduled replay

### ClassLoaderUtil

The `ClassLoaderUtil` class provides two ways to get transformer instances:

1. `createTransformer(String transformerClassName, Class<T> messageType)`: Creates a new instance of the transformer using reflection (original approach)
2. `getTransformer(String transformerNameOrClass, Class<T> messageType)`: Gets a transformer either by bean name (prefixed with '@') or by class name

## Error Handling

- If a message fails to process, it remains in the source queue
- Only successfully processed messages are deleted from the source queue
- Detailed error logs are generated for failed operations