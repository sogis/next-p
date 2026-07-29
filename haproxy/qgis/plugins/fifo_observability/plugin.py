"""QGIS Server observability hook used only by the FIFO integration tests."""

import json
import os
import socket
import sys
import time

from qgis.server import QgsServerFilter


class RequestStartedFilter(QgsServerFilter):
    """Log one structured event before QGIS dispatches a core-service request."""

    def onRequestReady(self) -> bool:
        """Called after parsing and immediately before QGIS core services."""
        handler = self.serverInterface().requestHandler()
        parameters = handler.parameterMap()
        request_id_query = parameters.get("FIFO_REQUEST_ID", "")
        run_id_query = parameters.get("FIFO_RUN_ID", "")

        try:
            request_id_header = handler.requestHeader("X-Request-Id")
            run_id_header = handler.requestHeader("X-Run-Id")
        except Exception as error:  # Keep the probe diagnosable across images.
            request_id_header = ""
            run_id_header = ""
            header_error = str(error)
        else:
            header_error = ""

        # Health checks are deliberately unmarked and must not pollute a test
        # run's event sequence. A marker can be carried in either location for
        # diagnostic purposes, but feasibility requires the HTTP header too.
        if not any((request_id_header, run_id_header, request_id_query, run_id_query)):
            return True

        event = {
            "event": "FIFO_REQUEST_STARTED",
            "event_version": 1,
            "monotonic_ns": time.monotonic_ns(),
            "request_id": request_id_header or request_id_query,
            "request_id_header": request_id_header,
            "request_id_query": request_id_query,
            "run_id": run_id_header or run_id_query,
            "run_id_header": run_id_header,
            "run_id_query": run_id_query,
            "worker_pid": os.getpid(),
            "container_hostname": socket.gethostname(),
            "header_error": header_error,
            "service": parameters.get("SERVICE", ""),
            "operation": parameters.get("REQUEST", ""),
        }
        sys.stderr.write(
            "FIFO_REQUEST_STARTED "
            + json.dumps(event, separators=(",", ":"), sort_keys=True)
            + "\n"
        )
        sys.stderr.flush()
        return True


class FifoObservabilityPlugin:
    def __init__(self, server_iface):
        # Keep a Python reference for the lifetime of the server. The C++
        # registry does not guarantee ownership of a SIP-wrapped filter.
        self._request_started_filter = RequestStartedFilter(server_iface)
        server_iface.registerFilter(self._request_started_filter, 1)
