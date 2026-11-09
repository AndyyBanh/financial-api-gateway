import http from 'k6/http';
import { check, fail } from 'k6';

// Load test for POST /api/v1/transactions through the gateway (JWT auth + rate limiter + routing).
// Usage and setup: see loadtests/README.md. Every value comes from -e flags, nothing is hard-coded.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL;
const PASSWORD = __ENV.PASSWORD;
const SENDER = __ENV.SENDER;
const RECIPIENT = __ENV.RECIPIENT;

const SCENARIO = __ENV.SCENARIO || 'smoke';
const RATE = Number(__ENV.RATE || 200);         // requests per second for the `load` scenario
const DURATION = __ENV.DURATION || '5m';        // length of the `load` scenario
const MAX_RATE = Number(__ENV.MAX_RATE || 800); // top of the `stress` ramp

// Open model (arrival-rate executors): k6 starts requests at a fixed rate whether or not earlier
// ones have finished, so the reported req/s is the real throughput and slow responses show up as
// latency instead of quietly lowering the request rate.
const scenarios = {
    smoke: {
        executor: 'constant-vus',
        vus: 1,
        duration: '30s',
    },
    load: {
        executor: 'constant-arrival-rate',
        rate: RATE,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: Math.max(50, Math.ceil(RATE / 2)),
        maxVUs: 1000,
    },
    stress: {
        executor: 'ramping-arrival-rate',
        startRate: Math.ceil(MAX_RATE / 16),
        timeUnit: '1s',
        preAllocatedVUs: Math.max(50, Math.ceil(MAX_RATE / 4)),
        maxVUs: 2000,
        stages: [
            { target: Math.ceil(MAX_RATE / 8), duration: '1m' },
            { target: Math.ceil(MAX_RATE / 4), duration: '1m' },
            { target: Math.ceil(MAX_RATE / 2), duration: '1m' },
            { target: MAX_RATE, duration: '1m' },
            { target: MAX_RATE, duration: '1m' },
        ],
    },
};

if (!scenarios[SCENARIO]) {
    throw new Error(`Unknown SCENARIO "${SCENARIO}", expected one of: ${Object.keys(scenarios).join(', ')}`);
}

export const options = {
    scenarios: { [SCENARIO]: scenarios[SCENARIO] },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        // Only the transaction requests count; the single login call in setup() is tagged separately
        'http_req_failed{name:create_transaction}': ['rate<0.01'],
        'http_req_duration{name:create_transaction}': ['p(95)<300', 'p(99)<800'],
        checks: ['rate>0.99'],
        // Arrival-rate runs drop iterations when no VU is free, i.e. the system couldn't keep up
        dropped_iterations: ['count<1'],
    },
};

export function setup() {
    for (const [name, value] of Object.entries({ EMAIL, PASSWORD, SENDER, RECIPIENT })) {
        if (!value) fail(`Missing -e ${name}=... (see loadtests/README.md)`);
    }

    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
    );
    if (res.status !== 200) {
        fail(`Login failed with status ${res.status}: ${res.body}`);
    }
    return { token: res.json('token') };
}

export default function (data) {
    const payload = JSON.stringify({
        senderId: SENDER,
        recipientId: RECIPIENT,
        amount: 0.01,
    });

    const res = http.post(`${BASE_URL}/api/v1/transactions`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`,
        },
        tags: { name: 'create_transaction' },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'transaction is PENDING': (r) => r.status === 200 && r.json('status') === 'PENDING',
    });
}
