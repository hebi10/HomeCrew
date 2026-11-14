"""Exercise real HTTP serialization against a local fixture, with no paid API calls."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
from pathlib import Path
import sys
import threading
import unittest
from unittest import mock
import urllib.error
import urllib.request

sys.path.insert(0, str(Path(__file__).parents[1]))
import publisher


class HttpFixtureTests(unittest.TestCase):
    def test_authorization_json_upload_and_error_redaction_through_actual_http(self):
        received = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_POST(self):
                raw = self.rfile.read(int(self.headers.get('Content-Length', '0')))
                received.append((self.path, self.headers.get('Authorization'), self.headers.get('Content-Type'), raw))
                if self.path.endswith('/blocked'):
                    self.send_response(403)
                    self.end_headers()
                    self.wfile.write(b'private-server-data')
                else:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/json')
                    self.end_headers()
                    self.wfile.write(b'{"id":"fixture"}')
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        def opener(request, **kwargs):
            # Transport must address this app's official package before fixture rerouting.
            self.assertTrue(request.full_url.startswith(publisher.API))
            self.assertIn('/applications/com.hebi.homecrew/edits', request.full_url)
            url = f'http://127.0.0.1:{server.server_port}' + request.full_url[len(publisher.API):]
            local = urllib.request.Request(url, data=request.data, headers=dict(request.header_items()), method=request.method)
            return urllib.request.urlopen(local, **kwargs)
        try:
            transport = publisher.HttpTransport('fixture-token', opener)
            self.assertEqual(transport('POST', '/edits', {'test': 1}), {'id': 'fixture'})
            transport('POST', '/edits/fixture/bundles?uploadType=media', b'AAB fixture bytes', 'application/octet-stream')
            with self.assertRaises(publisher.ReleaseError) as error:
                transport('POST', '/edits/blocked', {})
            self.assertNotIn('private-server-data', str(error.exception))
            self.assertEqual(received[0][1], 'Bearer fixture-token')
            self.assertEqual(json.loads(received[0][3]), {'test': 1})
            self.assertTrue(received[1][0].startswith('/upload/'))
            self.assertEqual(received[1][2:], ('application/octet-stream', b'AAB fixture bytes'))
        finally:
            server.shutdown()
            server.server_close()
            worker.join()

    def test_transient_get_503_retries_with_backoff(self):
        attempts = []
        def opener(request, **kwargs):
            attempts.append(request.get_method())
            if len(attempts) < 3:
                raise urllib.error.HTTPError(
                    request.full_url, 503, 'temporary outage', {},
                    io.BytesIO(b'private-server-response')
                )
            return io.BytesIO(b'{"bundles": [{"versionCode": 11}]}')

        with mock.patch.object(publisher.time, 'sleep') as sleep:
            response = publisher.HttpTransport('test-token', opener)('GET', '/edits/123/bundles')

        self.assertEqual(response, {'bundles': [{'versionCode': 11}]})
        self.assertEqual(attempts, ['GET', 'GET', 'GET'])
        self.assertEqual(sleep.call_args_list, [mock.call(1), mock.call(2)])

    def test_retries_never_replay_mutations_or_permanent_errors(self):
        for method, status in [('POST', 503), ('PUT', 503), ('DELETE', 503), ('GET', 403)]:
            with self.subTest(method=method, status=status):
                attempts = []
                def opener(request, **kwargs):
                    attempts.append(request.get_method())
                    raise urllib.error.HTTPError(
                        request.full_url, status, 'private-response', {},
                        io.BytesIO(b'private-response-body')
                    )

                with mock.patch.object(publisher.time, 'sleep') as sleep:
                    with self.assertRaises(publisher.PlayApiError) as caught:
                        publisher.HttpTransport('private-test-token', opener)(method, '/edits/123')

                self.assertEqual(attempts, [method])
                self.assertEqual(caught.exception.http_status, status)
                self.assertNotIn('private-', str(caught.exception))
                sleep.assert_not_called()

    def test_exhausted_transient_get_503_raises_after_four_attempts(self):
        attempts = []
        def opener(request, **kwargs):
            attempts.append(request.get_method())
            raise urllib.error.HTTPError(
                request.full_url, 503, 'private-response', {},
                io.BytesIO(b'private-response-body')
            )

        with mock.patch.object(publisher.time, 'sleep') as sleep:
            with self.assertRaises(publisher.PlayApiError) as caught:
                publisher.HttpTransport('private-test-token', opener)('GET', '/edits/123/tracks')

        self.assertEqual(attempts, ['GET'] * 4)
        self.assertEqual(caught.exception.http_status, 503)
        self.assertEqual(sleep.call_args_list, [mock.call(1), mock.call(2), mock.call(4)])
        self.assertNotIn('private-', str(caught.exception))
