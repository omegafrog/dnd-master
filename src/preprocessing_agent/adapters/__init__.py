def main() -> int:
    # Lazy import avoids a cycle when extraction adapters are loaded by the
    # application service.
    from .process_cli import main as _main
    return _main()


__all__ = ["main"]
