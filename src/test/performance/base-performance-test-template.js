import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metric to track error rate
const errorRate = new Rate('errors');

export let options = {
    // Test configuration
    stages: [
        { duration: '30s', target: 10 }, // simulate ramp-up of traffic from 1 to 10 users over 30 seconds.
        { duration: '1m', target: 10 }, // stay at 10 users for 1 minute
        { duration: '30s', target: 0 }, // ramp-down to 0 users
    ],
    thresholds: {
        // Assert that the error rate is less than 1%.
        http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
        errors: ['rate<0.01'], // less than 1% errors
    }
};

export default function() {
    group('Base Test Scenarios', () => {
        // Example scenario
        group('Example Scenario', () => {
            const payload = JSON.stringify({
                key: 'value'
            });

            const params = {
                headers: {
                    'Content-Type': 'application/json',
                },
            };

            const res = http.post('https://api.example.com/endpoint', payload, params);
            
            check(res, {
                'status is 200': (r) => r.status === 200,
                'body contains expected key': (r) => r.body.includes('expectedKey')
            }) || errorRate.add(1);
        });
    });

    sleep(1); // Think time between requests
}