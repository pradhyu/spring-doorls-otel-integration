#!/usr/bin/env python3
import sys
import urllib.request
import json
from typing import Dict, List, Optional

def fetch_trace(trace_id: str, jaeger_host: str = "localhost", jaeger_port: int = 16686) -> Optional[dict]:
    url = f"http://{jaeger_host}:{jaeger_port}/api/traces/{trace_id}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            if response.status == 200:
                return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"Error fetching trace from {url}: {e}", file=sys.stderr)
    return None

class SpanNode:
    def __init__(self, span_data: dict):
        self.span_id = span_data["spanID"]
        self.operation_name = span_data["operationName"]
        self.start_time = span_data["startTime"]  # microseconds
        self.duration = span_data["duration"]      # microseconds
        
        # Extract tags
        self.tags = {}
        for tag in span_data.get("tags", []):
            self.tags[tag["key"]] = tag["value"]
            
        self.parent_id = None
        for ref in span_data.get("references", []):
            if ref["refType"] == "CHILD_OF":
                self.parent_id = ref["spanID"]
                break
                
        self.children: List['SpanNode'] = []

def build_span_tree(trace_data: dict) -> List[SpanNode]:
    spans_data = trace_data["data"][0]["spans"]
    
    # Create all nodes
    nodes: Dict[str, SpanNode] = {}
    for span in spans_data:
        nodes[span["spanID"]] = SpanNode(span)
        
    # Link children and find roots
    roots: List[SpanNode] = []
    for node in nodes.values():
        if node.parent_id and node.parent_id in nodes:
            nodes[node.parent_id].children.append(node)
        else:
            roots.append(node)
            
    # Sort children and roots by startTime
    for node in nodes.values():
        node.children.sort(key=lambda x: x.start_time)
    roots.sort(key=lambda x: x.start_time)
    
    return roots

def print_tree(node: SpanNode, prefix: str = "", is_last: bool = True, root_start_time: int = 0):
    # Format duration (microseconds to ms)
    dur_ms = node.duration / 1000.0
    
    # Calculate offset relative to trace start
    offset_ms = (node.start_time - root_start_time) / 1000.0
    
    # Extract interesting tags
    extra = []
    if "app.request_id" in node.tags:
        extra.append(f"req_id={node.tags['app.request_id']}")
    if "app.transaction_id" in node.tags:
        extra.append(f"tx_id={node.tags['app.transaction_id']}")
    if "drools.rule_name" in node.tags:
        extra.append(f"rule={node.tags['drools.rule_name']}")
    if "drools.rules_fired" in node.tags:
        extra.append(f"fired={node.tags['drools.rules_fired']}")
    if "http.status_code" in node.tags:
        extra.append(f"status={node.tags['http.status_code']}")
        
    tag_str = f" ({', '.join(extra)})" if extra else ""
    
    # Print current node
    marker = "└── " if is_last else "├── "
    print(f"{prefix}{marker}{node.operation_name} [{dur_ms:.2f}ms @ +{offset_ms:.2f}ms]{tag_str}")
    
    # Recursively print children
    new_prefix = prefix + ("    " if is_last else "│   ")
    for i, child in enumerate(node.children):
        print_tree(child, new_prefix, i == len(node.children) - 1, root_start_time)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 visualize_trace.py <trace_id> [jaeger_host] [jaeger_port]")
        sys.exit(1)
        
    trace_id = sys.argv[1]
    jaeger_host = sys.argv[2] if len(sys.argv) > 2 else "localhost"
    jaeger_port = int(sys.argv[3]) if len(sys.argv) > 3 else 16686
    
    trace_data = fetch_trace(trace_id, jaeger_host, jaeger_port)
    if not trace_data or not trace_data.get("data") or len(trace_data["data"]) == 0:
        print(f"No trace data found for ID: {trace_id}")
        sys.exit(1)
        
    roots = build_span_tree(trace_data)
    if not roots:
        print("Could not build span tree.")
        sys.exit(1)
        
    # Get trace start time from the earliest root
    trace_start = roots[0].start_time
    
    print(f"\nTrace: {trace_id}")
    print("=" * (len(trace_id) + 7))
    for i, root in enumerate(roots):
        print_tree(root, is_last=(i == len(roots) - 1), root_start_time=trace_start)
    print()

if __name__ == "__main__":
    main()
