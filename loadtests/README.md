# Load tests (k6)

Load test for `POST /api/v1/transactions`, sent through the gateway. The path covers the rate limiter, JWT auth, routing, two account lookups, a DB insert and a Kafka publish. Latest results are in the main README under "Performance & Metrics".

## Requirements
- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) (tested with k6 v2.0.0)
- Docker

## 1. Start from a clean stack
```bash
# Removes all containers and database volumes (deletes all local data), so every run starts the same
docker compose down -v

# Start with the load-test overrides: raised rate limit, INFO logs, heap caps for Docker Desktop's ~4 GB VM
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d --build
```
Wait until the services have started (the gateway answers on `http://localhost:8080`).

## 2. Create the test user and accounts
```bash
# Register; note the userId in the response
curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"loadtest@example.com","password":"loadtest-pass"}'

# Log in; note the token in the response
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"loadtest@example.com","password":"loadtest-pass"}'

# Create the sender and recipient accounts; note each accountNumber
curl -X POST localhost:8080/api/v1/accounts -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' \
  -d '{"userId":"<userId>","accountType":"CHECKING"}'
curl -X POST localhost:8080/api/v1/accounts -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' \
  -d '{"userId":"<userId>","accountType":"SAVINGS"}'

# Fund the sender (there is no deposit API by design)
docker compose exec account-db psql -U postgres -d accountdb \
  -c "UPDATE accounts SET balance = 1000000 WHERE account_number = '<sender accountNumber>';"
```

## 3. Run
```bash
k6 run \
  -e SCENARIO=load -e RATE=200 -e DURATION=2m \
  -e EMAIL=loadtest@example.com -e PASSWORD=loadtest-pass \
  -e SENDER=<sender accountNumber> -e RECIPIENT=<recipient accountNumber> \
  --summary-export=loadtests/results/load-200.json \
  loadtests/create-transaction.js
```
- `SCENARIO=smoke`: 1 virtual user for 30 seconds. A quick check, and a JVM warm-up before a measured run.
- `SCENARIO=load`: exactly `RATE` requests/second for `DURATION`. Defaults are 200 and 5m.
- `SCENARIO=stress`: ramps up in steps to `MAX_RATE` requests/second over 5 minutes, e.g. `-e MAX_RATE=400` goes 25 → 50 → 100 → 200 → 400.
- `BASE_URL` defaults to `http://localhost:8080`.

The script logs in once in `setup()`. The summary shows p50/p90/p95/p99, and `--summary-export` saves it as JSON. `loadtests/results/` is git-ignored.

`load` and `stress` use an *open model*: k6 starts requests on schedule whether or not earlier ones have finished. That makes the reported req/s the real throughput, and slow responses show up as latency.

For repeatable numbers, start from step 1 before each measured run. Every run adds thousands of transactions and queues events for the account and fraud services, so later runs would otherwise start with more data and a Kafka backlog.

## Pass/fail rules
A run fails (k6 exit code 99) when any of these is crossed:
- more than 1% of transaction requests fail
- p95 is above 300 ms, or p99 is above 800 ms
- fewer than 99% of checks pass (status 200 and the transaction is `PENDING`)
- any iteration is dropped, meaning k6 had no free virtual user, so the system couldn't keep up with the target rate

## Notes
- The rate limiter stays on during load tests, so its Redis call is part of the measured latency. The override only raises its limit, because k6 sends everything from one IP. The normal default is 5 requests per 5 minutes per IP.
- To watch the async pipeline keep up during a run: `docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --all-groups --describe` (the `LAG` column).
- Balances are `Float`, so a large seeded balance won't visibly change by 0.01 per transaction. This doesn't affect the latency numbers.
