#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${DOCLING_VENV_DIR:-${ROOT_DIR}/.venv-docling}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

command -v "${PYTHON_BIN}" >/dev/null || { echo "python3 required" >&2; exit 1; }
"${PYTHON_BIN}" -m venv "${VENV_DIR}"
"${VENV_DIR}/bin/python" -m pip install --upgrade pip
"${VENV_DIR}/bin/python" -m pip install "docling>=2.0,<3" "easyocr>=1.7,<2" "pymupdf>=1.24,<2" "fastapi>=0.115,<1" "uvicorn[standard]>=0.34,<1"
echo "Docling ready. Start: ${VENV_DIR}/bin/python ${ROOT_DIR}/scripts/docling-service.py"
