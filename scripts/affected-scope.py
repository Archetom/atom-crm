#!/usr/bin/env python3
"""Conservative working-tree selection; this is not Java semantic analysis.

Only isolated, ordinary domain/application test edits select a smaller reactor.
Production changes keep full integration checks because paths cannot prove their
security, persistence, transaction or event impact. Keyword checks only widen.
"""

import pathlib
import re
import subprocess
import sys


SENSITIVE = re.compile(
    r"security|auth|permission|privilege|tenant|caller|principal|password|token|"
    r"transaction|after.?commit|cache|redis|event|repository|mapper|migration|"
    r"persist|datasource|database|jdbc|sql|testcontainers|container|integration|"
    r"architecture|boundary|spring|mockmvc|webtestclient|servicetemplate|"
    r"connector|credential|membership|datascope|fieldpolicy|marketingform|"
    r"installation|outbox|durable|worker|lease|claim",
    re.IGNORECASE,
)
DOCUMENT_NAMES = {"AGENTS.md", "CLAUDE.md", "README.md", "README.en.md", "CHANGELOG.md", "llms.txt"}
TEMPLATE = "src/main/resources/archetype-resources/"


def git(*args):
    return subprocess.check_output(["git", *args], stderr=subprocess.DEVNULL)


def docs(path):
    return path in DOCUMENT_NAMES or path.startswith("docs/") and path.endswith(".md")


def generated_docs(path):
    return docs(path) or path.startswith(".agents/skills/") and path.endswith(".md")


def changes():
    root = pathlib.Path(git("rev-parse", "--show-toplevel").decode().strip()).resolve()
    if root != pathlib.Path.cwd().resolve():
        return None, "not-independent-git-root"
    git("rev-parse", "--verify", "HEAD")
    if git("ls-files", "--unmerged", "-z"):
        return None, "unmerged-files"
    # Separate index/worktree comparisons also catch staged edits subsequently
    # reversed in the worktree. No rename detection means both old and new paths
    # participate, including moves out of a sensitive module.
    commands = [
        ("diff", "--name-only", "--no-renames", "-z", "--"),
        ("diff", "--cached", "--name-only", "--no-renames", "-z", "HEAD", "--"),
        ("ls-files", "--others", "--exclude-standard", "-z"),
    ]
    paths = set()
    for args in commands:
        paths.update(part.decode("utf-8", "strict") for part in git(*args).split(b"\0") if part)
    return paths, "working-tree"


def contents(path):
    """Inspect all three states; removal/rename must not hide sensitive content."""
    versions = []
    file = pathlib.Path(path)
    if file.is_symlink():
        raise ValueError("symlink")
    if file.exists():
        if not file.is_file() or file.stat().st_size > 1024 * 1024:
            raise ValueError("unsupported-file")
        versions.append(file.read_text(encoding="utf-8"))
    for revision in ("HEAD:" + path, ":" + path):
        try:
            value = git("show", revision)
        except subprocess.CalledProcessError:
            continue  # New or deleted paths may not exist in one of these states.
        if len(value) > 1024 * 1024:
            raise ValueError("unsupported-file")
        versions.append(value.decode("utf-8", "strict"))
    return "\n".join(versions)


def select(context):
    try:
        paths, reason = changes()
        if paths is None:
            return "full", "-", reason
        if not paths:
            return "full", "-", "no-working-tree-changes"
        # Files with unusual names, symlinks, or unknown types are never routed
        # through a narrower check, even when placed beside ordinary prose/tests.
        if any(any(ord(char) < 32 for char in path) or pathlib.Path(path).is_symlink() for path in paths):
            return "full", "-", "unsupported-path"
        if context == "archetype":
            if all(docs(path) for path in paths):
                return "docs", "-", "repository-prose-only"
            if all(docs(path) or path.startswith(TEMPLATE) and generated_docs(path[len(TEMPLATE):]) for path in paths):
                return "generate", "-", "generated-prose-only"
            return "full", "-", "archetype-code-or-unknown-change"
        if all(generated_docs(path) for path in paths):
            return "docs", "-", "application-prose-only"
        tests = [path for path in paths if not generated_docs(path)]
        modules = set()
        for path in tests:
            match = re.fullmatch(r"(domain|application)/src/test/java/(.+)\.java", path)
            if not match:
                return "full", "-", "production-contract-or-unknown-change"
            modules.add(match.group(1))
            if SENSITIVE.search(path) or SENSITIVE.search(contents(path)):
                return "full", "-", "sensitive-test-change"
        if len(modules) != 1:
            return "full", "-", "cross-module-change"
        return "module-tests", modules.pop(), "single-module-tests-only"
    except (OSError, subprocess.SubprocessError, UnicodeError, ValueError):
        return "full", "-", "git-or-file-state-unavailable"


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"archetype", "application"}:
        print("full - unknown-project-context")
    else:
        print(*select(sys.argv[1]))
