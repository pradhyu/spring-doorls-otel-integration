#!/bin/bash

echo "Sending requests to drools-otel-app on localhost:8080..."

echo "1. Health check..."
curl -i http://localhost:8080/api/rules/health
echo -e "\n"

echo "2. Rule evaluation (Gold tier, Jane Doe)..."
curl -i -X POST -H "Content-Type: application/json" -d @sample-request.json http://localhost:8080/api/rules/execute
echo -e "\n"

echo "3. Customer evaluation (Platinum tier, Robert Johnson)..."
curl -i -X POST -H "Content-Type: application/json" -d @customer-input.json http://localhost:8080/api/rules/evaluate-customer
echo -e "\n"

echo "4. Forward Chaining Rule evaluation (Regular to Platinum upgrade, Richard Harris)..."
curl -i -X POST -H "Content-Type: application/json" -d @vip-request.json http://localhost:8080/api/rules/execute
echo -e "\n"

echo "5. Database Fetch Rule evaluation (Fetch M102 from HSQLDB -> Platinum, Alice Wonderland)..."
curl -i -X POST -H "Content-Type: application/json" -d @db-request.json http://localhost:8080/api/rules/execute
echo -e "\n"

echo "Done sending test requests! Check Jaeger at http://localhost:16686"
