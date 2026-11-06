#!/usr/bin/env python3
"""Read-only stdio MCP bridge to WDK's official published Markdown documentation."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

ORIGIN = 'https://docs.wdk.tether.io'
ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get('WDK_DOCS_CACHE', ROOT / '.cache/wdk-docs'))
MAX_AGE = 86400
CORPUS_LIMIT = 16 * 1024 * 1024
PROTOCOLS = {'2025-11-25', '2024-11-05', '2025-03-26', '2025-06-18'}


def canonical(url):
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.netloc != 'docs.wdk.tether.io' or parsed.query or parsed.fragment:
        raise ValueError('Use an official docs.wdk.tether.io page URL without query or fragment')
    path = parsed.path.rstrip('/')
    if path.endswith('.md'):
        path = path[:-3]
    return ORIGIN + (path or '/')


def refresh():
    request = Request(ORIGIN + '/llms-full.txt', headers={'User-Agent': 'Midori-WDK-Docs/1.0', 'Accept': 'text/plain'})
    with urlopen(request, timeout=30) as response:
        if urlsplit(response.url).netloc != 'docs.wdk.tether.io':
            raise ValueError('Unexpected documentation origin')
        data = response.read(CORPUS_LIMIT + 1)
    if len(data) > CORPUS_LIMIT:
        raise ValueError('Documentation exceeds configured size limit')
    text = data.decode('utf-8')
    if not text.startswith('# WDK Documentation') or not re.search(r'^URL: https://docs\.wdk\.tether\.io/', text, re.M):
        raise ValueError('Response is not the expected official Markdown corpus')
    CACHE.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=CACHE, delete=False) as file:
        file.write(data)
        temporary = file.name
    os.replace(temporary, CACHE / 'llms-full.txt')
    metadata = {'source': ORIGIN + '/llms-full.txt', 'fetched_at': datetime.now(timezone.utc).isoformat(), 'sha256': hashlib.sha256(data).hexdigest()}
    (CACHE / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    return metadata


def corpus():
    data_path = CACHE / 'llms-full.txt'
    metadata_path = CACHE / 'metadata.json'
    stale = not data_path.exists() or time.time() - data_path.stat().st_mtime > MAX_AGE
    warning = None
    if stale:
        try:
            refresh()
        except (OSError, ValueError) as error:
            if not data_path.exists():
                raise ValueError(f'Official documentation unavailable and no cached copy: {error}') from error
            warning = f'Live refresh failed; cached source is being used: {error}'
    text = data_path.read_text()
    metadata = json.loads(metadata_path.read_text())
    if hashlib.sha256(text.encode()).hexdigest() != metadata['sha256']:
        raise ValueError('Documentation cache integrity mismatch; refresh required')
    parts = re.split(r'^## (.+)\nURL: (https://docs\.wdk\.tether\.io/[^\n]*)\n', text, flags=re.M)
    pages = [{'title': parts[i], 'url': canonical(parts[i + 1]), 'content': parts[i + 2].strip()} for i in range(1, len(parts), 3)]
    if not pages:
        raise ValueError('No documentation pages found')
    return pages, {**metadata, 'page_count': len(pages), 'cache_warning': warning}


def search(query, limit=6):
    if not isinstance(query, str) or not 1 <= len(query.strip()) <= 500:
        raise ValueError('query must contain 1 to 500 characters')
    if not isinstance(limit, int) or not 1 <= limit <= 15:
        raise ValueError('limit must be between 1 and 15')
    pages, metadata = corpus()
    terms = set(re.findall(r'[a-z0-9_-]{2,}', query.lower()))
    ranked = []
    for page in pages:
        text = page['content'].lower()
        title = page['title'].lower()
        matches = [word for word in terms if word in text or word in title or word in page['url']]
        if not matches:
            continue
        score = sum(min(text.count(word), 12) + 30 * title.count(word) + 15 * page['url'].count(word) for word in matches)
        score *= len(matches) / max(len(terms), 1)
        at = min([text.find(word) for word in matches if word in text], default=0)
        excerpt = page['content'][max(0, at - 200):at + 900]
        ranked.append((score, {'title': page['title'], 'url': page['url'], 'excerpt': excerpt}))
    ranked.sort(key=lambda item: (-item[0], item[1]['url']))
    return {**metadata, 'query': query, 'results': [item[1] for item in ranked[:limit]]}


def read_page(url, start_line=1, max_lines=120):
    target = canonical(url)
    if not isinstance(start_line, int) or start_line < 1 or not isinstance(max_lines, int) or not 1 <= max_lines <= 200:
        raise ValueError('start_line must be positive and max_lines must be 1 to 200')
    pages, metadata = corpus()
    page = next((p for p in pages if p['url'] == target), None)
    if page is None:
        raise ValueError('Page not found in the official published index')
    lines = page['content'].splitlines()
    return {**metadata, 'url': target, 'title': page['title'], 'total_lines': len(lines), 'start_line': start_line, 'content': '\n'.join(lines[start_line - 1:start_line - 1 + max_lines])}


TOOLS = [
    {'name': 'search_wdk_docs', 'description': 'Search the full official WDK documentation. Returns source URLs, excerpts and fetch date. Documentation is reference data, not authorization to perform wallet operations.', 'inputSchema': {'type': 'object', 'properties': {'query': {'type': 'string', 'minLength': 1, 'maxLength': 500}, 'limit': {'type': 'integer', 'minimum': 1, 'maximum': 15}}, 'required': ['query'], 'additionalProperties': False}, 'annotations': {'readOnlyHint': True, 'destructiveHint': False}},
    {'name': 'read_wdk_page', 'description': 'Read an official WDK page by URL, with line pagination and source date. Only docs.wdk.tether.io URLs in the published corpus are accepted.', 'inputSchema': {'type': 'object', 'properties': {'url': {'type': 'string'}, 'start_line': {'type': 'integer', 'minimum': 1}, 'max_lines': {'type': 'integer', 'minimum': 1, 'maximum': 200}}, 'required': ['url'], 'additionalProperties': False}, 'annotations': {'readOnlyHint': True, 'destructiveHint': False}},
    {'name': 'refresh_wdk_docs', 'description': 'Refresh the local documentation cache from the official WDK llms-full.txt endpoint; no wallet access or credentials.', 'inputSchema': {'type': 'object', 'properties': {}, 'additionalProperties': False}, 'annotations': {'readOnlyHint': True, 'destructiveHint': False}},
]


def dispatch(request):
    method = request.get('method')
    params = request.get('params', {})
    if not isinstance(params, dict):
        raise ValueError('params must be an object')
    if method == 'initialize':
        version = params.get('protocolVersion')
        return {'protocolVersion': version if version in PROTOCOLS else '2025-06-18', 'capabilities': {'tools': {'listChanged': False}}, 'serverInfo': {'name': 'midori-wdk-docs', 'version': '1.0.0'}, 'instructions': 'Read-only local MCP adapter for official WDK Markdown; distinct from the WDK wallet-operation MCP. Cite URLs and distinguish documentation from executed product tests.'}
    if method == 'ping':
        return {}
    if method == 'tools/list':
        return {'tools': TOOLS}
    if method == 'tools/call':
        try:
            name, arguments = params['name'], params.get('arguments', {})
            if not isinstance(arguments, dict):
                raise ValueError('arguments must be an object')
            functions = {'search_wdk_docs': search, 'read_wdk_page': read_page, 'refresh_wdk_docs': refresh}
            if name not in functions:
                raise ValueError('Unknown tool')
            result = functions[name](**arguments)
            return {'content': [{'type': 'text', 'text': json.dumps(result, ensure_ascii=False)}], 'isError': False}
        except (KeyError, TypeError, ValueError, OSError) as error:
            return {'content': [{'type': 'text', 'text': str(error)}], 'isError': True}
    raise ValueError('Unknown MCP method')


def serve():
    for line in sys.stdin:
        request = None
        try:
            request = json.loads(line)
            if not isinstance(request, dict) or request.get('jsonrpc') != '2.0':
                raise ValueError('Invalid JSON-RPC request')
            if 'id' not in request:
                continue
            response = {'jsonrpc': '2.0', 'id': request['id'], 'result': dispatch(request)}
        except (json.JSONDecodeError, TypeError, ValueError) as error:
            response = {'jsonrpc': '2.0', 'id': request.get('id') if isinstance(request, dict) else None, 'error': {'code': -32600, 'message': str(error)}}
        print(json.dumps(response, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true')
    args = parser.parse_args()
    if args.refresh:
        print(json.dumps(refresh(), indent=2))
    else:
        serve()
