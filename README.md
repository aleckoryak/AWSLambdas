# AWS Lambda Examples

A multi-module Maven repository of self-contained AWS Lambda examples, each with
its own Terraform infrastructure. Add a new example by creating a module under
`examples/` and listing it in the root `pom.xml`.

| Module | What it shows |
|--------|---------------|
| [`examples/products-api`](examples/products-api) | API Gateway (HTTP API, **Cognito JWT auth**) → Lambda (**Spring Cloud Function**, Java 21, **SnapStart**) → **DynamoDB**, deployed to isolated **test** and **prod** environments. |

## Prerequisites

- Java 21 + Maven 3.9+
- Terraform >= 1.10
- AWS credentials configured (SSO / profile) with permission to create the resources

## Build

```bash
# Build everything
mvn clean package

# Or just one example (with its dependencies)
mvn -pl examples/products-api -am clean package
```

The shaded, Lambda-deployable jar is produced at
`examples/products-api/target/products-api.jar`.

---

# products-api

## Architecture

```
Client ──(Bearer JWT)──▶ API Gateway HTTP API
                         │  JWT authorizer validates token against Cognito user pool
                         ▼
                       Lambda  (Spring Cloud Function, Java 21, SnapStart alias "live")
                         │  IAM execution role (least privilege)
                         ▼
                       DynamoDB  (encrypted at rest)
```

Endpoints (both require `Authorization: Bearer <token>`):

- `POST /products` — body `{"name": "...", "price": 9.99}` → `201` with the created product
- `GET /products/{id}` → `200` with the product, or `404`

## Deploy

### 0. One-time: create the remote-state bucket

```bash
cd examples/products-api/terraform/bootstrap
terraform init
terraform apply -var="state_bucket_name=<your-globally-unique-bucket>"
```

Put the bucket name into `envs/test/backend.tf` and `envs/prod/backend.tf`
(replace `REPLACE_WITH_STATE_BUCKET`), or pass it at init time with
`-backend-config="bucket=<your-bucket>"`.

### 1. Build the jar

```bash
mvn -pl examples/products-api -am clean package
```

### 2. Deploy an environment

```bash
cd examples/products-api/terraform/envs/test     # or envs/prod
terraform init
terraform apply -var-file=test.tfvars             # or prod.tfvars
```

`terraform output` prints the API endpoint and Cognito ids.

> Re-deploying after a code change: re-run the Maven build, then
> `terraform apply` again — `source_code_hash` changes, so Terraform publishes a
> new Lambda version and repoints the `live` alias.

## Calling the API (get a Cognito token)

Create a user and set a permanent password (one-time):

```bash
POOL_ID=$(terraform output -raw cognito_user_pool_id)
CLIENT_ID=$(terraform output -raw cognito_user_pool_client_id)

aws cognito-idp admin-create-user \
  --user-pool-id "$POOL_ID" --username demo@example.com --message-action SUPPRESS
aws cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" --username demo@example.com \
  --password 'Demo-Passw0rd!' --permanent
```

Get an access token and call the API:

```bash
API=$(terraform output -raw api_endpoint)
TOKEN=$(aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "$CLIENT_ID" \
  --auth-parameters USERNAME=demo@example.com,PASSWORD='Demo-Passw0rd!' \
  --query 'AuthenticationResult.AccessToken' --output text)

# Create
curl -s -XPOST "$API/products" \
  -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' \
  -d '{"name":"Widget","price":9.99}'

# Fetch (use the id returned above)
curl -s "$API/products/<id>" -H "Authorization: Bearer $TOKEN"
```

Requests without a valid token get `401` from API Gateway.

## Environment differences

| Setting | test | prod |
|---|---|---|
| Lambda memory | 512 MB | 1024 MB |
| Log retention | 14 days | 90 days |
| DynamoDB PITR | off | on |
| Deletion protection | off | on |
| Customer-managed KMS | off (AWS-managed) | on |
| Cognito advanced security | AUDIT | ENFORCED |
| API throttle (burst/rate) | 50 / 100 | 100 / 200 |

State is isolated per environment (separate S3 keys), so changes to `test`
cannot affect `prod`.

## Security notes

- **No static credentials** — the Lambda uses its IAM execution role; Terraform
  uses your local AWS profile/SSO.
- **Least-privilege IAM** — the role allows only `GetItem`/`PutItem` on the one
  table ARN, scoped log writes, and X-Ray.
- **Encryption** — DynamoDB SSE on; Lambda env vars + DynamoDB use a CMK in prod;
  TLS enforced end-to-end; state bucket encrypted, versioned, TLS-only, private.
- **Auth** — every route requires a valid Cognito JWT.

## SnapStart & cold start

SnapStart is enabled (`apply_on = PublishedVersions`) and API Gateway invokes the
`live` alias, so requests always hit a snapshotted version. `SnapStartPriming`
(a CRaC `Resource`) warms the JSON serialization path before the checkpoint so
that work is captured in the snapshot. Priming is CPU-only — no network or
credentials are captured in the image.
