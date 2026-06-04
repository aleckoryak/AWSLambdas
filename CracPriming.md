# The Mechanics of Priming
To understand why priming is necessary, we must look at the lifecycle of an AWS Lambda function using SnapStart and how the AWS SDK behaves out of the box.

## The Lazy-Loading Problem
By default, the AWS SDK for Java uses **lazy initialization**. When your Spring Boot application starts up, the `DynamoDbClient` bean is created, but the underlying heavy infrastructure—such as the HTTP client (Netty or the AWS URL Connection client), ClassLoaders for serialization/deserialization, Jackson JSON parsers, cryptographic modules, and IAM credential signers—does not actually initialize until you execute your **very first database query**.

Without priming, when AWS takes a memory snapshot of your initialized Spring Boot application, it saves a snapshot containing a lazy, un-warmed SDK client. When a cold start happens:

* 1. AWS restores the snapshot in under 500ms.
* 2. The first API call hits your Lambda function.
* 3. The Lambda function is forced to perform the heavy lifting of initializing the HTTP client, handling TLS handshakes, and loading hundreds of SDK classes during the execution phase. This adds a "hidden" 1 to 2-second penalty to your first invocation.

## The CRaC Solution
The **Coordinated Restore at Checkpoint (CRaC)** API solves this by allowing applications to intercept the checkpoint and restore events.

* 1. **Registration**: In the constructor, ``Core.getGlobalContext().register(this)`` alerts the AWS Lambda execution environment that this bean wants to participate in the lifecycle.
* 2. **The Checkpoint Stage**: When you publish a new version of your Lambda function, AWS builds the micro-VM, boots the JVM, and starts your Spring Boot application. Once the initialization phase finishes, AWS invokes the ``beforeCheckpoint()`` method of all registered resources before taking the snapshot.
* 3. **Eager Execution**: Inside ``beforeCheckpoint()``, we deliberately call a lightweight metadata operation (``dynamoDbClient.listTables()``). This forces the JVM to eagerly run through all the lazy-loading routines:

* 3.1. It loads all serialization and transport-layer Java classes into memory.
* 3.2. It spins up the background thread pools for the HTTP client.
* 3.3. It pre-configures cryptographic algorithms and TLS states.

* 4. **Snapshot Preservation**: ``Once beforeCheckpoint()`` completes, AWS takes the snapshot of the entire Firecracker VM memory state. That fully pre-warmed, pre-allocated client state is now saved into the snapshot.

## The Restore Stage
When a cold start occurs, AWS restores the memory snapshot. The JVM resumes execution instantaneously. When the first ``GET /SOME API`` request arrives, the ``Repository`` uses a ``DynamoDbClient`` whose connections, thread pools, and classes are already living in active memory. The first request executes almost as fast as a warm request, completely bypassing the lazy-loading tax.