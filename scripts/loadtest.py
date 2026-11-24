#!/usr/bin/env python3
"""Lumina Gateway 并发压测脚本（HTTP keep-alive 连接复用，测量网关真实开销）。

用法: python scripts/loadtest.py [非流式总数] [非流式并发] [流式总数] [流式并发]

说明：每个 worker 线程复用同一条 HTTP 连接（keep-alive），避免 TCP 握手/Nagle 开销污染测量；
对比「每请求新建连接」的 urllib 方案，本脚本反映的是网关本身的延迟与吞吐。
"""
import concurrent.futures
import hashlib
import hmac
import http.client
import json
import statistics
import sys
import time
import uuid

HOST = "localhost"
PORT = 18080
PATH = "/v1/chat/completions"
SECRET = "demo-secret-please-change"
APP_KEY = "demo-app"


def _send(conn, stream):
    ts = str(int(time.time()))
    nonce = uuid.uuid4().hex
    sig = hmac.new(SECRET.encode(),
                   ("POST\n" + PATH + "\n" + ts + "\n" + nonce).encode(),
                   hashlib.sha256).hexdigest()
    body = {"model": "mock-model", "messages": [{"role": "user", "content": "hello"}]}
    if stream:
        body["stream"] = True
    headers = {
        "Content-Type": "application/json",
        "X-App-Key": APP_KEY,
        "X-Timestamp": ts,
        "X-Nonce": nonce,
        "X-Signature": sig,
        "X-Route-Tag": "mock",
    }
    start = time.perf_counter()
    conn.request("POST", PATH, body=json.dumps(body).encode("utf-8"), headers=headers)
    resp = conn.getresponse()
    raw = resp.read()
    latency = (time.perf_counter() - start) * 1000
    frames = raw.decode("utf-8").count("data:") if stream else 0
    return resp.status, latency, frames


def worker(requests, stream):
    conn = http.client.HTTPConnection(HOST, PORT)
    latencies = []
    ok = 0
    frames = []
    try:
        for _ in range(requests):
            status, latency, frame = _send(conn, stream)
            if status == 200:
                ok += 1
                latencies.append(latency)
                frames.append(frame)
    finally:
        conn.close()
    return ok, latencies, frames


def percentile(values, p):
    if not values:
        return 0
    values = sorted(values)
    return values[min(int(len(values) * p), len(values) - 1)]


def jvm_metric(name):
    """从 actuator 读取 JVM 指标（线程/内存），用于压测期间的资源监控。"""
    try:
        req = urllib.request.Request(f"http://{HOST}:{PORT}/actuator/metrics/{name}")
        with urllib.request.urlopen(req, timeout=5) as r:
            data = json.loads(r.read())
            return data["measurements"][0]["value"]
    except Exception:
        return None


def jvm_snapshot(label):
    threads = jvm_metric("jvm.threads.live")
    memory = jvm_metric("jvm.memory.used")
    if threads is not None and memory is not None:
        print(f"[{label}] JVM 线程: {threads}, 堆内存已用: {memory / 1024 / 1024:.1f} MB")
    else:
        print(f"[{label}] JVM 指标不可用")


def run(total, concurrency, stream):
    per_worker = max(1, total // concurrency)
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as ex:
        results = list(ex.map(lambda _: worker(per_worker, stream), range(concurrency)))

    ok = sum(r[0] for r in results)
    latencies = [l for r in results for l in r[1]]
    report = {
        "total": concurrency * per_worker,
        "ok": ok,
        "errors": concurrency * per_worker - ok,
        "avg_ms": round(statistics.mean(latencies), 2) if latencies else 0,
        "p50_ms": round(percentile(latencies, 0.5), 2),
        "p95_ms": round(percentile(latencies, 0.95), 2),
        "p99_ms": round(percentile(latencies, 0.99), 2),
    }
    if stream:
        frames = [f for r in results for f in r[2]]
        report["avg_frames"] = round(statistics.mean(frames), 1) if frames else 0
    return report


if __name__ == "__main__":
    total = int(sys.argv[1]) if len(sys.argv) > 1 else 500
    conc = int(sys.argv[2]) if len(sys.argv) > 2 else 50
    stream_total = int(sys.argv[3]) if len(sys.argv) > 3 else 100
    stream_conc = int(sys.argv[4]) if len(sys.argv) > 4 else 20

    jvm_snapshot("压测前")
    print(f"=== 非流式压测(keep-alive): {total} 请求 / {conc} 并发 ===")
    print(json.dumps(run(total, conc, False), ensure_ascii=False, indent=2))
    print(f"=== 流式压测(keep-alive): {stream_total} SSE / {stream_conc} 并发 ===")
    print(json.dumps(run(stream_total, stream_conc, True), ensure_ascii=False, indent=2))
    jvm_snapshot("压测后")
