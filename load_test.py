#!/usr/bin/env python3
import json
import urllib.request
import concurrent.futures
import time
import random
import sys

def load_payloads():
    payloads = []
    for filename in ["sample-request.json", "vip-request.json", "db-request.json"]:
        try:
            with open(filename, "r") as f:
                payloads.append(json.load(f))
        except Exception as e:
            print(f"Could not load {filename}: {e}", file=sys.stderr)
    return payloads

def send_request(url, payload):
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        url,
        data=data,
        headers={'Content-Type': 'application/json'}
    )
    try:
        with urllib.request.urlopen(req) as response:
            return response.status
    except Exception as e:
        return 500

def main():
    url = "http://localhost:8081/api/proxy/execute"
    num_requests = 1000
    concurrency = 25
    
    payloads = load_payloads()
    if not payloads:
        print("No payloads loaded. Exiting.")
        sys.exit(1)
        
    print(f"Starting load test: sending {num_requests} requests with concurrency {concurrency} to {url}...")
    
    start_time = time.time()
    results = []
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = []
        for _ in range(num_requests):
            payload = random.choice(payloads)
            futures.append(executor.submit(send_request, url, payload))
            
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
            
    end_time = time.time()
    elapsed = end_time - start_time
    
    success = results.count(200)
    failed = len(results) - success
    
    print("\n--- Load Test Results ---")
    print(f"Total Requests: {len(results)}")
    print(f"Success (200 OK): {success}")
    print(f"Failed / Errors: {failed}")
    print(f"Time Elapsed: {elapsed:.2f} seconds")
    print(f"Throughput: {len(results)/elapsed:.2f} req/sec")

if __name__ == "__main__":
    main()
