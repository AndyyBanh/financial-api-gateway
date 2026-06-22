import http from 'k6/http';
import { check, sleep } from 'k6';

const TOKEN = 'PUT_VALUE_HERE';
const SENDER_ACCOUNT_NUMBER = 'PUT_VALUE_HERE';
const RECIPIENT_ACCOUNT_NUMBER = 'PUT_VALUE_HERE';

export const options = {
    vus: 50,
    duration: '30s',
};

export default function () {
    const url = 'http://localhost:8080/api/v1/transactions';

    const payload = JSON.stringify({
        senderId: SENDER_ACCOUNT_NUMBER,
        recipientId: RECIPIENT_ACCOUNT_NUMBER,
        amount: 1.00,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${TOKEN}`,
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.1);
}