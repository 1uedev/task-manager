"""The operations template of a version (TASK-132, DEC-162, rule 2; TASK-136, finding F12 of the review of TASK-132).

Builds the directory task-manager-VERSION with the layout of the repository, so that every link of the guides works:

  deploy/                 the template with every file compose.yaml and compose.proxy.yaml mount, and .env.example with
                          the digests of exactly this version
  docs/                   the three operation guides and every guide in docs/ they link directly
  tools/restore/          the restore check that docs/BACKUP_RESTORE.md describes

A relative link of a bundled guide to a file outside the bundle (for example the source of the demonstration) becomes a
link to that file in the repository at the tag. A link that points to nothing in the repository refuses the bundle, so a
guide never ships with a dead reference.

  python3 bundle.py VERSION DIGESTS_ENV TARGET_DIRECTORY
"""

from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "https://github.com/1uedev/task-manager"
DEPLOY = ["compose.yaml", "compose.proxy.yaml", "Caddyfile", "check-images.sh", "db-init.sh", "db-roles.sh",
          "db-roles.sql", "archive-wal.sh", "restore-place.sh", ".gitignore"]
GUIDES = ["INSTALL.md", "BACKUP_RESTORE.md", "OPERATIONS.md"]
TOOLS = ["tools/restore"]
LINK = re.compile(r"\]\(([^)\s]+)\)")


class BundleError(Exception):
    pass


def linked_guides(root: Path) -> list[str]:
    """The operation guides and every Markdown file in docs/ they link directly; further links lead to the tag."""
    found = list(GUIDES)
    for name in GUIDES:
        for target in LINK.findall((root / "docs" / name).read_text(encoding="utf-8")):
            path = target.split("#", 1)[0]
            if not path or "://" in path or path.startswith("mailto:") or "/" in path or not path.endswith(".md"):
                continue
            if (root / "docs" / path).is_file() and path not in found:
                found.append(path)
    return found


def rewrite_links(text: str, source: Path, bundle: Path, root: Path, tag: str) -> str:
    def replace(match: re.Match) -> str:
        target = match.group(1)
        path, _, anchor = target.partition("#")
        if not path or "://" in path or path.startswith("mailto:"):
            return match.group(0)
        resolved = (source.parent / path).resolve()
        try:
            relative = resolved.relative_to(root.resolve())
        except ValueError as outside:
            raise BundleError(f"{source.relative_to(root)} links outside the repository: {target}") from outside
        if (bundle / relative).exists():
            return match.group(0)
        if not resolved.exists():
            raise BundleError(f"{source.relative_to(root)} links to something that does not exist: {target}")
        kind = "tree" if resolved.is_dir() else "blob"
        url = f"{REPOSITORY}/{kind}/{tag}/{relative.as_posix()}" + (f"#{anchor}" if anchor else "")
        return f"]({url})"

    return LINK.sub(replace, text)


def build(version: str, digests: dict[str, str], target: Path, root: Path = ROOT) -> Path:
    tag = f"v{version}"
    bundle = target / f"task-manager-{version}"
    if bundle.exists():
        raise BundleError(f"{bundle} exists already")
    (bundle / "deploy").mkdir(parents=True)
    (bundle / "docs").mkdir()
    for name in DEPLOY:
        shutil.copy2(root / "deploy" / name, bundle / "deploy" / name)
    example = (root / "deploy" / ".env.example").read_text(encoding="utf-8")
    for key in ("TASK_MANAGER_IMAGE", "TASK_MANAGER_BACKUP_IMAGE"):
        value = digests.get(key, "")
        if not re.fullmatch(r"ghcr\.io/1uedev/task-manager(-backup)?@sha256:[0-9a-f]{64}", value):
            raise BundleError(f"{key} is not a digest of the registry")
        example, count = re.subn(rf"(?m)^{key}=.*$", f"{key}={value}", example)
        if count != 1:
            raise BundleError(f"{key} is missing from deploy/.env.example")
    (bundle / "deploy" / ".env.example").write_text(example, encoding="utf-8")
    for tool in TOOLS:
        shutil.copytree(root / tool, bundle / tool, ignore=shutil.ignore_patterns("__pycache__"))
    guides = linked_guides(root)
    for name in guides:
        shutil.copy2(root / "docs" / name, bundle / "docs" / name)
    for name in guides:
        source = root / "docs" / name
        text = rewrite_links(source.read_text(encoding="utf-8"), source, bundle, root, tag)
        (bundle / "docs" / name).write_text(text, encoding="utf-8")
    mounted = set(re.findall(r"\./([A-Za-z0-9_.-]+):/", (root / "deploy" / "compose.yaml").read_text(encoding="utf-8")))
    missing = sorted(name for name in mounted if not (bundle / "deploy" / name).exists())
    if missing:
        raise BundleError("compose.yaml mounts files the bundle lacks: " + ", ".join(missing))
    return bundle


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    version, digests_file, target = argv[1], Path(argv[2]), Path(argv[3])
    digests = dict(line.split("=", 1) for line in digests_file.read_text(encoding="utf-8").splitlines() if "=" in line)
    try:
        bundle = build(version, digests, target)
    except BundleError as refused:
        print(f"Refused: {refused}", file=sys.stderr)
        return 1
    print(bundle)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
