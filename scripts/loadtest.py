#!/usr/bin/env python3
"""Lumina Gateway 并发压测脚本。

用法: python scripts/loadtest.py [非流式总数] [非流式并发] [流式总数] [流式并发]
"""
import concurrent.futures
import hashlib
import hmac
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:18080/v1/chat/completions"
SECRET = "demo-secret-please-change"
APP_KEY = "demo-app"

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def sign(ts, nonce):
    return hmac.new(SECRET.encode(),
                    ("POST\n/v1/chat/completions\n" + ts + "\n" + nonce).encode(),
                    hashlib.sha256).hexdigest()


def do_request(stream=False):
    ts = str(int(time.time()))
    nonce = uuid.uuid4().hex
    body = {"model": "mock-model", "messages": [{"role": "user", "content": "hello"}]}
    if stream:
        body["stream"] = True
    headers = {
        "Content-Type": "application/json",
        "X-App-Key": APP_KEY,
        "X-Timestamp": ts,
        "X-Nonce": nonce,
        "X-Signature": sign(ts, nonce),
        "X-Route-Tag": "mock",
    }
    req = urllib.request.Request(BASE, data=json.dumps(body).encode("utf-8"), headers=headers)
    start = time.perf_counter()
    try:
        resp = opener.open(req, timeout=60)
        raw = resp.read()
        latency = time.perf_counter() - start
        frames = raw.decode("utf-8").count("data:") if stream else 0
        return resp.status, latency, frames
    except urllib.error.HTTPError as e:
        return e.code, time.perf_counter() - start, 0
    except Exception as e:
        return type(e).__name__, time.perf_counter() - start, 0


def percentile(values, p):
    if not values:
        return 0
    values = sorted(values)
    idx = min(int(len(values) * p), len(values) - 1)
    return values[idx]


def run(total, concurrency, stream):
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as ex:
        results = list(ex.map(lambda _: do_request(stream), range(total)))
    latencies = [r[1] * 1000 for r in results if r[0] == 200]
    ok = sum(1 for r in results if r[0] == 200)
    report = {
        "total": total,
        "ok": ok,
        "errors": total - ok,
        "p50_ms": round(percentile(latencies, 0.5), 2),
        "p95_ms": round(percentile(latencies, 0.95), 2),
        "p99_ms": round(percentile(latencies, 0.99), 2),
        "avg_ms": round(statistics.mean(latencies), 2) if latencies else 0,
    }
    if stream:
        frames = [r[2] for r in results if r[0] == 200]
        report["avg_frames"] = round(statistics.mean(frames), 1) if frames else 0
    return report


if __name__ == "__main__":
    total = int(sys.argv[1]) if len(sys.argv) > 1 else 500
    conc = int(sys.argv[2]) if len(sys.argv) > 2 else 50
    stream_total = int(sys.argv[3]) if len(sys.argv) > 3 else 100
    stream_conc = int(sys.argv[4]) if len(sys.argv) > 4 else 20

    print(f"=== 非流式压测: {total} 请求 / {conc} 并发 ===")
    print(json.dumps(run(total, conc, False), ensure_ascii=False, indent=2))
    print(f"=== 流式压测: {stream_total} SSE / {stream_conc} 并发 ===")
    print(json.dumps(run(stream_total, stream_conc, True), ensure_ascii=False, indent=2))
