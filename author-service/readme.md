# Key Components Breakdown
* 1. Configuration & Priming (``/config``)
  * ``DynamoDbConfig.java``: Initializes the thread-safe ``DynamoDbEnhancedClient`` bean used across the application.
  * ``DynamoDbPrimingConfig.java``: Implements the ``org.crac.Resource`` interface. It forces the AWS SDK to make a dummy call (like ``listTables()``) during the build phase before the execution state snapshot is taken by SnapStart.
* 2. Models & Repositories (``/model`` & ``/repository``)
  * ``Author.java``: Uses the AWS SDK v2 enhanced annotations (e.g., ``@DynamoDbBean``, ``@DynamoDbPartitionKey`) to safely map your Java object structure to the schema defined in your DynamoDB resource.
  * ``ProductRepository.java``: Implements standard data retrieval patterns. Because Spring Cloud Data repositories introduce heavy reflection overhead (which slows down initialization), it's highly recommended to write a lightweight wrapper directly using the ``DynamoDbEnhancedClient`` to keep your cold start footprint as slim as possible.
* 3. Execution Context (``/resources``)
  * ``application.yml``: Contains minimal configurations required at startup to avoid long parameter resolution delays
* 4. Root Artifacts
  * ``pom.xml``: Coordinates dependencies and compiles your thin application code along with its runtime libraries into a single shaded delivery package inside the ``target/`` directory (e.g., ``target/author-service-1.0.0-aws.jar``).
  * ``template.yaml``: Governs AWS provisioning. When you execute ``sam build``, AWS SAM reads this file and populates a temporary, build-ready hidden directory named ``.aws-sam/`` at the root of your project directory before deployment.