#!/usr/bin/env python3
"""Validate a release tag and resolve release notes from GitHub Release or CHANGELOG.md."""
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


def extract_changelog_section(changelog_path: Path, version: str) -> str:
    """Extract notes under the matching `## [version]` or `## version` heading."""
    if not changelog_path.is_file():
        return ""
    heading_re = re.compile(
        rf"^##\s+\[?v?{re.escape(version)}\]?(?:\s.*)?$",
        re.IGNORECASE,
    )
    next_heading_re = re.compile(r"^##\s+")
    lines = changelog_path.read_text(encoding="utf-8").splitlines()
    collecting = False
    collected: list[str] = []
    for line in lines:
        if not collecting:
            if heading_re.match(line.strip()):
                collecting = True
        else:
            if next_heading_re.match(line):
                break
            collected.append(line)
    return "\n".join(collected).strip()


def fetch_github_release(repo: str, tag: str, token: str):
    """Fetch release metadata by tag, returning None if GitHub returns HTTP 404."""
    url = f"https://api.github.com/repos/{repo}/releases/tags/{urllib.parse.quote(tag, safe='')}"
    request = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "ContactQuests-release-workflow",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise ValueError(f"Failed to query GitHub Release for {tag!r}: HTTP {error.code}") from error


def main():
    tag = os.environ["RELEASE_TAG"]
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GH_TOKEN"]
    event_name = os.environ.get("GITHUB_EVENT_NAME", "")

    properties = {}
    for line in Path("gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    version = properties["mod_version"]
    if tag.removeprefix("v") != version:
        raise ValueError(f"Release tag {tag!r} does not match mod_version {version!r}")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)[.\d]*)?-1\.21\.1", version):
        raise ValueError("Only release/alpha/beta/rc versions for Minecraft 1.21.1 may be published")
    if properties["minecraft_version"] != "1.21.1":
        raise ValueError("This workflow is configured for the NeoForge 1.21.1 branch")

    release = fetch_github_release(repo, tag, token)
    is_prerelease_version = bool(re.search(r"-(alpha|beta|rc)", version))

    if release is not None:
        if release["draft"]:
            raise ValueError("Publish the GitHub Release before distributing it")
        prerelease = bool(release["prerelease"])
        if prerelease:
            version_type = "alpha" if "-alpha" in version else "beta"
        elif is_prerelease_version:
            raise ValueError("Mark alpha/beta/rc GitHub Releases as prereleases")
        else:
            version_type = "release"
        body = (release.get("body") or "").strip()
        if not body:
            body = extract_changelog_section(Path("CHANGELOG.md"), version)
        if not body:
            raise ValueError("The GitHub Release must contain release notes")
        release_exists = True
        release_name = release.get("name") or f"ContactQuests {version}"
        release_url = release["html_url"]
    else:
        if event_name == "release":
            raise ValueError(f"Published GitHub Release for tag {tag!r} was not found")
        body = extract_changelog_section(Path("CHANGELOG.md"), version)
        if not body:
            raise ValueError(
                f"No published GitHub Release found for {tag!r} and CHANGELOG.md has no notes for {version!r}"
            )
        prerelease = is_prerelease_version
        version_type = "alpha" if "-alpha" in version else ("beta" if prerelease else "release")
        release_exists = False
        release_name = f"ContactQuests {version}"
        release_url = f"https://github.com/{repo}/releases/tag/{urllib.parse.quote(tag, safe='')}"

    out = Path("release-dist")
    out.mkdir(exist_ok=True)
    (out / "CHANGELOG.md").write_text(body + "\n", encoding="utf-8")
    (out / "release.json").write_text(json.dumps({
        "tag": tag,
        "version": version,
        "versionType": version_type,
        "prerelease": prerelease,
        "releaseExists": release_exists,
        "name": release_name,
        "url": release_url,
        "minecraftVersion": "1.21.1",
        "loader": "neoforge",
    }, indent=2) + "\n", encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(
            f"tag={tag}\n"
            f"version={version}\n"
            f"version-type={version_type}\n"
            f"prerelease={'true' if prerelease else 'false'}\n"
            f"release-exists={'true' if release_exists else 'false'}\n"
        )
    print(f"Validated {tag}: {version_type}, Minecraft 1.21.1, NeoForge (release_exists={release_exists})")


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError) as error:
        sys.exit(str(error))
