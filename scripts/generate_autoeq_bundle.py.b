"Simgot",
        "DUNU",
    ):
        if name.lower().startswith(prefix.lower()):
            return prefix
    return name.split()[0] if name.strip() else ""


def source_rank(source: str, priority: list[str]) -> int:
    try:
        return priority.index(source)
    except ValueError:
        return 1000 + abs(hash(source)) % 100


def git_sha(root: Path) -> str:
    try:
        return (
            subprocess.check_output(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                stderr=subprocess.DEVNULL,
            )
            .decode()
            .strip()
        )
    except Exception:
        return "unknown"


def collect(root: Path, priority: list[str], mode: str) -> list[dict]:
    results = root / "results"
    files = sorted(results.rglob("*FixedBandEQ.txt"))
    # Also accept FixedBandEq.txt casing variants
    files += [p for p in results.rglob("*FixedBandEq.txt") if p not in files]
    best: dict[str, tuple[int, dict]] = {}
    all_recs: list[dict] = []

    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        preamp, gains = parse_fixed_band(text)
        if gains is None:
            continue
        rel = path.relative_to(results)
        parts = rel.parts
        source = parts[0] if parts else ""
        # results/<source>/<rig>/.../<model>/<file>
        rig = parts[1] if len(parts) > 2 else ""
        name = path.parent.name
        rid = str(path.parent.relative_to(results)).replace("\\", "/")
        rec = {
            "id": rid,
            "name": name,
            "brand": brand_of(name),
            "source": source,
            "rig": rig,
            "preampDb": preamp,
            "gains": [round(g, 1) for g in gains],
        }
        if mode == "full":
            all_recs.append(rec)
            continue
        key = name.casefold()
        rank = source_rank(source, priority)
        prev = best.get(key)
        if prev is None or rank < prev[0]:
            best[key] = (rank, rec)

    if mode == "full":
        return sorted(all_recs, key=lambda r: r["name"].casefold())
    return sorted((r for _, r in best.values()), key=lambda r: r["name"].casefold())


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--autoeq-root", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--mode", choices=("dedupe", "full"), default="dedupe")
    ap.add_argument(
        "--priority",
        default=",".join(DEFAULT_PRIORITY),
        help="Comma-separated source preference for dedupe",
    )
    ap.add_argument("--with-sidecar", action="store_true")
    args = ap.parse_args()

    priority = [s.strip() for s in args.priority.split(",") if s.strip()]
    presets = collect(args.autoeq_root, priority, args.mode)
    sha = git_sha(args.autoeq_root)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    payload = {
        "version": 1,
        "bandsHz": BANDS,
        "autoeqGitSha": sha,
        "generatedAt": now,
        "mode": args.mode,
        "count": len(presets),
        "presets": presets,
    }
    raw = json.dumps(payload, separators=(",", ":")).encode()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.out, "wb", compresslevel=9) as f:
        f.write(raw)
    print(
        f"Wrote {len(presets)} presets → {args.out} "
        f"({args.out.stat().st_size} bytes gzip, mode={args.mode}, sha={sha[:12]})"
    )

    if args.with_sidecar:
        notice = args.out.parent / "NOTICE"
        notice.write_text(MIT_NOTICE, encoding="utf-8")
        manifest = {
            "autoeqGitSha": sha,
            "generatedAt": now,
            "count": len(presets),
            "bandsHz": BANDS,
            "mode": args.mode,
            "file": args.out.name,
        }
        (args.out.parent / "MANIFEST.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Wrote sidecars NOTICE + MANIFEST.json in {args.out.parent}")


if __name__ == "__main__":
    main()
