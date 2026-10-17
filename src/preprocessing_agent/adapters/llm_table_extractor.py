"""Call the application's configured AI provider to extract tables from a page image."""
from __future__ import annotations

import base64
import hashlib
import json
import logging
import os
import time
import uuid
from typing import Any, Mapping
from collections.abc import Sequence
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

_LOGGER = logging.getLogger(__name__)


class AiProviderTableExtractor:
    def __init__(self, base_url: str, internal_token: str, solo_player_id: str, *,
                 provider: str, model: str, reasoning: str, endpoint_id: str = "", timeout: float = 180.0) -> None:
        if not all((base_url, internal_token, solo_player_id, provider, model, reasoning)) or timeout <= 0:
            raise ValueError("configured AI provider route and positive timeout are required")
        self._base_url = base_url.rstrip("/")
        self._internal_token = internal_token
        self._solo_player_id = solo_player_id
        self._provider = provider
        self._model = model
        self._reasoning = reasoning
        self._endpoint_id = endpoint_id
        self._timeout = timeout

    @property
    def model(self) -> str:
        return self._model

    @classmethod
    def from_environment(cls) -> "AiProviderTableExtractor | None":
        if os.getenv("PREPROCESSING_LLM_TABLES_ENABLED", "true").casefold() in {"0", "false", "no", "off"}:
            return None
        required = ("PREPROCESSING_AI_PROVIDER_URL", "PREPROCESSING_AI_INTERNAL_TOKEN",
                    "PREPROCESSING_AI_SOLO_PLAYER_ID", "PREPROCESSING_AI_PROVIDER", "PREPROCESSING_AI_MODEL",
                    "PREPROCESSING_AI_REASONING")
        if any(not os.getenv(name, "").strip() for name in required):
            return None
        try:
            timeout = float(os.getenv("PREPROCESSING_AI_TIMEOUT_SECONDS", "180"))
            return cls(os.environ[required[0]], os.environ[required[1]], os.environ[required[2]],
                       provider=os.environ[required[3]], model=os.environ[required[4]],
                       reasoning=os.environ[required[5]], endpoint_id=os.getenv("PREPROCESSING_AI_ENDPOINT_ID", ""),
                       timeout=timeout)
        except (ValueError, KeyError):
            return None

    def extract(self, image: bytes, *, page_width: float, page_height: float) -> list[dict[str, Any]]:
        started = time.monotonic()
        image_uri = "data:image/png;base64," + base64.b64encode(image).decode("ascii")
        fingerprint = hashlib.sha256(image).hexdigest()[:16]
        operation_key = f"preprocessing-table:{fingerprint}:{uuid.uuid4()}"
        payload = {
            "soloPlayerId": self._solo_player_id,
            "endpointId": self._endpoint_id or None,
            "provider": self._provider,
            "model": self._model,
            "reasoning": self._reasoning,
            "operationKey": operation_key,
            "imageDataUri": image_uri,
        }
        body = json.dumps(payload, separators=(",", ":")).encode()
        endpoint = self._base_url + "/internal/gm/preprocessing/table-extraction"
        _diagnostic("dev_preprocessing_table_llm stage=provider_request_started pageImageBytes=%d requestBytes=%d provider=%s model=%s operationKey=%s route=internal_ai_provider outcome=started",
                    len(image), len(body), self._provider, self._model, operation_key)
        request = Request(endpoint, data=body, headers={
            "X-Internal-Token": self._internal_token, "Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self._timeout) as response:
                response_payload = json.loads(response.read())
            tables = response_payload.get("tables") if isinstance(response_payload, Mapping) else None
            if not isinstance(tables, list):
                raise ValueError("TABLE_LLM_INVALID_RESPONSE")
            _diagnostic("dev_preprocessing_table_llm stage=provider_response_received pageImageBytes=%d requestBytes=%d actualTableCount=%d elapsedMs=%d route=validated_internal_provider_response outcome=success",
                        len(image), len(body), len(tables), round((time.monotonic() - started) * 1000))
            return tables
        except HTTPError as exc:
            _diagnostic("dev_preprocessing_table_llm stage=provider_request_failed pageImageBytes=%d requestBytes=%d httpStatus=%d elapsedMs=%d route=none recoveryAction=keep_pymupdf_text outcome=warning",
                        len(image), len(body), exc.code, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_REQUEST_FAILED") from exc
        except (URLError, TimeoutError, OSError, ValueError, KeyError, TypeError) as exc:
            _diagnostic("dev_preprocessing_table_llm stage=provider_request_failed pageImageBytes=%d requestBytes=%d failureType=%s elapsedMs=%d route=none recoveryAction=keep_pymupdf_text outcome=warning",
                        len(image), len(body), type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_RESPONSE_INVALID") from exc

    def resolve(self, image: bytes, *, candidate_tables: list[dict[str, Any]]) -> list[dict[str, Any]]:
        started = time.monotonic()
        image_uri = "data:image/png;base64," + base64.b64encode(image).decode("ascii")
        operation_key = f"preprocessing-table-resolution:{hashlib.sha256(image).hexdigest()[:16]}:{uuid.uuid4()}"
        payload = {"soloPlayerId": self._solo_player_id, "endpointId": self._endpoint_id or None,
                   "provider": self._provider, "model": self._model, "reasoning": self._reasoning,
                   "operationKey": operation_key, "imageDataUri": image_uri,
                   "candidateTables": candidate_tables}
        body = json.dumps(payload, separators=(",", ":")).encode()
        endpoint = self._base_url + "/internal/gm/preprocessing/table-resolution"
        _diagnostic("dev_preprocessing_table_resolution stage=request_started candidateTableCount=%d imageBytes=%d requestBytes=%d provider=%s model=%s operationKey=%s route=internal_ai_provider outcome=started",
                    len(candidate_tables), len(image), len(body), self._provider, self._model, operation_key)
        request = Request(endpoint, data=body, headers={
            "X-Internal-Token": self._internal_token, "Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self._timeout) as response:
                response_payload = json.loads(response.read())
            tables = response_payload.get("tables") if isinstance(response_payload, Mapping) else None
            if not isinstance(tables, list):
                raise ValueError("TABLE_LLM_INVALID_RESOLUTION_RESPONSE")
            _diagnostic("dev_preprocessing_table_resolution stage=response_received candidateTableCount=%d actualTableCount=%d requestBytes=%d elapsedMs=%d route=validated_internal_provider_response outcome=success",
                        len(candidate_tables), len(tables), len(body), round((time.monotonic() - started) * 1000))
            return tables
        except HTTPError as exc:
            _diagnostic("dev_preprocessing_table_resolution stage=request_failed candidateTableCount=%d requestBytes=%d httpStatus=%d elapsedMs=%d route=none recoveryAction=preserve_native_table_cells outcome=warning",
                        len(candidate_tables), len(body), exc.code, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_RESOLUTION_FAILED") from exc
        except (URLError, TimeoutError, OSError, ValueError, KeyError, TypeError) as exc:
            _diagnostic("dev_preprocessing_table_resolution stage=request_failed candidateTableCount=%d requestBytes=%d failureType=%s elapsedMs=%d route=none recoveryAction=preserve_native_table_cells outcome=warning",
                        len(candidate_tables), len(body), type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_RESOLUTION_INVALID") from exc

    def detect_candidates(self, image: bytes, *, page_numbers: Sequence[int]) -> list[int]:
        started = time.monotonic()
        image_uri = "data:image/png;base64," + base64.b64encode(image).decode("ascii")
        operation_key = f"preprocessing-table-screen:{hashlib.sha256(image).hexdigest()[:16]}:{uuid.uuid4()}"
        payload = {
            "soloPlayerId": self._solo_player_id,
            "endpointId": self._endpoint_id or None,
            "provider": self._provider,
            "model": self._model,
            "reasoning": self._reasoning,
            "operationKey": operation_key,
            "imageDataUri": image_uri,
            "pageNumbers": list(page_numbers),
        }
        body = json.dumps(payload, separators=(",", ":")).encode()
        endpoint = self._base_url + "/internal/gm/preprocessing/table-candidates"
        _diagnostic("dev_preprocessing_table_llm stage=candidate_screen_request_started pageCount=%d imageBytes=%d requestBytes=%d provider=%s model=%s operationKey=%s route=internal_ai_provider outcome=started",
                    len(page_numbers), len(image), len(body), self._provider, self._model, operation_key)
        request = Request(endpoint, data=body, headers={
            "X-Internal-Token": self._internal_token, "Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self._timeout) as response:
                response_payload = json.loads(response.read())
            candidates = response_payload.get("candidatePageNumbers") if isinstance(response_payload, Mapping) else None
            if (not isinstance(candidates, list)
                    or any(type(number) is not int or number not in page_numbers for number in candidates)
                    or len(set(candidates)) != len(candidates)):
                raise ValueError("TABLE_LLM_INVALID_CANDIDATE_RESPONSE")
            _diagnostic("dev_preprocessing_table_llm stage=candidate_screen_response_received expectedPageCount=%d actualCandidateCount=%d elapsedMs=%d route=validated_internal_provider_response outcome=success",
                        len(page_numbers), len(candidates), round((time.monotonic() - started) * 1000))
            return candidates
        except HTTPError as exc:
            _diagnostic("dev_preprocessing_table_llm stage=candidate_screen_request_failed pageCount=%d requestBytes=%d httpStatus=%d elapsedMs=%d route=none recoveryAction=extract_native_candidates_only outcome=warning",
                        len(page_numbers), len(body), exc.code, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_CANDIDATE_SCREEN_FAILED") from exc
        except (URLError, TimeoutError, OSError, ValueError, KeyError, TypeError) as exc:
            _diagnostic("dev_preprocessing_table_llm stage=candidate_screen_request_failed pageCount=%d requestBytes=%d failureType=%s elapsedMs=%d route=none recoveryAction=extract_native_candidates_only outcome=warning",
                        len(page_numbers), len(body), type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise RuntimeError("TABLE_LLM_CANDIDATE_SCREEN_INVALID") from exc


def page_table_blocks(tables: list[dict[str, Any]], *, page_number: int,
                      page_width: float, page_height: float) -> tuple[
                          list[dict[str, Any]], list[tuple[float, float, float, float]], list[dict[str, Any]]
                      ]:
    """Project tables; reject one whose cell lies outside its region."""
    blocks: list[dict[str, Any]] = []
    regions: list[tuple[float, float, float, float]] = []
    rejected: list[dict[str, Any]] = []
    accepted_signatures: list[tuple[tuple[int, int, str], ...]] = []
    for table_index, table in enumerate(tables):
        if not isinstance(table, Mapping):
            raise ValueError("TABLE_LLM_INVALID_TABLE")
        region = _normalized_box(table.get("bbox"), page_width, page_height)
        cells = table.get("cells")
        if not isinstance(cells, list) or not cells:
            raise ValueError("TABLE_LLM_EMPTY_TABLE")
        table_id = f"p{page_number}-llm-table-{table_index}"
        table_blocks: list[dict[str, Any]] = []
        for cell_index, cell in enumerate(cells):
            if not isinstance(cell, Mapping) or type(cell.get("row")) is not int or type(cell.get("column")) is not int:
                raise ValueError("TABLE_LLM_INVALID_CELL")
            row, column, text = cell["row"], cell["column"], cell.get("text")
            if row < 0 or column < 0 or not isinstance(text, str):
                raise ValueError("TABLE_LLM_INVALID_CELL")
            text = text.strip()
            if not text:
                continue
            box = _normalized_box(cell.get("bbox"), page_width, page_height)
            if not _contains(region, box):
                rejected.append({"table_index": table_index, "cell_index": cell_index,
                                 "row": row, "column": column,
                                 "failure_code": "TABLE_LLM_CELL_OUTSIDE_TABLE",
                                 "table_bbox": list(region), "cell_bbox": list(box)})
                break
            table_blocks.append({"block_id": f"{table_id}-r{row}-c{column}", "table_id": table_id,
                                 "kind": "table_cell", "row": row, "column": column,
                                 "text": text, "bbox": box, "table_extraction_method": "llm_vision"})
        else:
            signature = tuple(sorted(
                (cell["row"], cell["column"], " ".join(cell["text"].split()).casefold())
                for cell in cells if isinstance(cell, Mapping)
                and type(cell.get("row")) is int and type(cell.get("column")) is int
                and isinstance(cell.get("text"), str)
            ))
            duplicate_index = next((index for index, (prior_signature, prior_region) in enumerate(
                zip(accepted_signatures, regions)
            ) if signature and signature == prior_signature and _box_iou(region, prior_region) >= .8), None)
            if duplicate_index is not None:
                rejected.append({"table_index": table_index,
                                 "failure_code": "TABLE_LLM_DUPLICATE_OVERLAPPING_REGION",
                                 "duplicate_of_table_index": duplicate_index})
                continue
            blocks.extend(table_blocks)
            regions.append(region)
            accepted_signatures.append(signature)
    return blocks, regions, rejected


def _normalized_box(value: Any, width: float, height: float) -> tuple[float, float, float, float]:
    if not isinstance(value, (list, tuple)) or len(value) != 4 or any(type(item) is not int for item in value):
        raise ValueError("TABLE_LLM_INVALID_BBOX")
    left, top, right, bottom = value
    if not (0 <= left < right <= 1000 and 0 <= top < bottom <= 1000):
        raise ValueError("TABLE_LLM_INVALID_BBOX")
    return (left * width / 1000, top * height / 1000,
            right * width / 1000, bottom * height / 1000)


def _contains(outer: tuple[float, ...], inner: tuple[float, float, float, float]) -> bool:
    tolerance = 0.015 * max(outer[2] - outer[0], outer[3] - outer[1])
    return (inner[0] >= outer[0] - tolerance and inner[1] >= outer[1] - tolerance
            and inner[2] <= outer[2] + tolerance and inner[3] <= outer[3] + tolerance)


def _box_iou(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    intersection = max(0.0, min(left[2], right[2]) - max(left[0], right[0])) * max(
        0.0, min(left[3], right[3]) - max(left[1], right[1]))
    left_area = (left[2] - left[0]) * (left[3] - left[1])
    right_area = (right[2] - right[0]) * (right[3] - right[1])
    return intersection / max(left_area + right_area - intersection, 1.0)


def _diagnostic(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").casefold() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)
