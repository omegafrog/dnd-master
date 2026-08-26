"""JSON serialization and schema validation for domain contracts."""

from __future__ import annotations

import json
from dataclasses import fields, is_dataclass
from enum import Enum
from pathlib import Path
from typing import Any, TypeVar, get_args, get_origin, get_type_hints

from . import models

T = TypeVar("T")


def to_dict(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if is_dataclass(value):
        return {item.name: to_dict(getattr(value, item.name)) for item in fields(value)}
    if isinstance(value, dict) or hasattr(value, "items"):
        return {str(key): to_dict(item) for key, item in value.items()}
    if isinstance(value, (tuple, list, frozenset)):
        return [to_dict(item) for item in value]
    return value


def to_json(value: Any) -> str:
    return json.dumps(to_dict(value), ensure_ascii=False, sort_keys=True)


def from_dict(model_type: type[T], value: Any) -> T:
    if not is_dataclass(model_type) or not isinstance(value, dict):
        raise TypeError("from_dict requires a dataclass type and mapping")
    hints = get_type_hints(model_type)
    kwargs = {}
    for item in fields(model_type):
        if item.name in value:
            kwargs[item.name] = _convert(hints[item.name], value[item.name])
    return model_type(**kwargs)


def from_json(model_type: type[T], payload: str) -> T:
    return from_dict(model_type, json.loads(payload))


def _convert(annotation: Any, value: Any) -> Any:
    origin = get_origin(annotation)
    args = get_args(annotation)
    if isinstance(annotation, type) and issubclass(annotation, Enum):
        return annotation(value)
    if origin in (tuple, list):
        item_type = args[0] if args else Any
        converted = [_convert(item_type, item) for item in value]
        return tuple(converted) if origin is tuple else converted
    if origin is dict:
        return {key: _convert(args[1], item) for key, item in value.items()}
    if origin is not None and type(None) in args:
        actual = next(item for item in args if item is not type(None))
        return None if value is None else _convert(actual, value)
    if isinstance(annotation, type) and is_dataclass(annotation):
        return from_dict(annotation, value)
    return value


def schema_path(name: str) -> Path:
    return Path(__file__).resolve().parents[3] / "schemas" / name


def validate_json(instance: Any, schema: dict[str, Any]) -> None:
    """Validate the JSON-Schema subset used by the public contracts."""
    errors: list[str] = []
    _validate(instance, {**schema, "__root__": schema}, "$", errors)
    if errors:
        raise ValueError("; ".join(errors))


def _validate(value: Any, schema: dict[str, Any], path: str, errors: list[str]) -> None:
    if "$ref" in schema:
        if not schema["$ref"].startswith("#/$defs/"):
            errors.append(f"{path}: unsupported schema reference")
            return
        root = schema.get("__root__")
        if root is None:
            errors.append(f"{path}: schema reference has no root")
            return
        name = schema["$ref"].split("/")[-1]
        referenced = dict(root.get("$defs", {}).get(name, {}))
        referenced["__root__"] = root
        _validate(value, referenced, path, errors)
        return
    if "anyOf" in schema:
        branch_errors: list[list[str]] = []
        for branch in schema["anyOf"]:
            current: list[str] = []
            _validate(value, {**branch, "__root__": schema.get("__root__", schema)}, path, current)
            if not current:
                return
            branch_errors.append(current)
        errors.append(f"{path}: does not match any schema branch")
        return
    expected = schema.get("type")
    valid_type = {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
        "null": value is None,
    }
    if expected:
        expected_types = expected if isinstance(expected, list) else [expected]
        if not any(valid_type.get(item, True) for item in expected_types):
            errors.append(f"{path}: expected {expected}")
            return
    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: value is not in enum")
    if isinstance(value, dict):
        for required in schema.get("required", []):
            if required not in value:
                errors.append(f"{path}: missing required field {required}")
        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            errors.extend(f"{path}: unexpected field {key}" for key in value if key not in properties)
        for key, child in value.items():
            if key in properties:
                _validate(child, {**properties[key], "__root__": schema.get("__root__", schema)}, f"{path}.{key}", errors)
    if isinstance(value, list) and "items" in schema:
        for index, child in enumerate(value):
            _validate(child, {**schema["items"], "__root__": schema.get("__root__", schema)}, f"{path}[{index}]", errors)
    if isinstance(value, str) and "minLength" in schema and len(value) < schema["minLength"]:
        errors.append(f"{path}: string is too short")
    if isinstance(value, int) and "minimum" in schema and value < schema["minimum"]:
        errors.append(f"{path}: number is below minimum")
