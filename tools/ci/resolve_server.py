#!/usr/bin/env python3
"""
Resolves a server jar download URL for CI smoke tests.

Usage: resolve_server.py <paper|folia|purpur> <version-spec>

version-spec:
  1.21.4        an exact version
  latest        the newest release version
  latest-1.21   the newest version starting with "1.21"

Prints "<version> <download-url>" on stdout.
Only the Python standard library is used.
"""
import json
import re
import sys
import urllib.error
import urllib.request

USER_AGENT = "LifeCore-CI/1.0 (https://github.com/markroger12/lifesteal)"
RELEASE = re.compile(r"^\d+\.\d+(?:\.\d+)?$")


def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def version_key(version):
    return tuple(int(part) for part in version.split("."))


def collect_versions(node, out):
    """Collects every release-looking version string anywhere in a JSON document."""
    if isinstance(node, str):
        if RELEASE.match(node):
            out.add(node)
    elif isinstance(node, dict):
        for key, value in node.items():
            if isinstance(key, str) and RELEASE.match(key):
                out.add(key)
            collect_versions(value, out)
    elif isinstance(node, list):
        for value in node:
            collect_versions(value, out)


def pick(versions, spec):
    if spec == "latest":
        candidates = versions
    elif spec.startswith("latest-"):
        prefix = spec[len("latest-"):]
        candidates = [v for v in versions if v == prefix or v.startswith(prefix + ".")]
    else:
        return spec
    if not candidates:
        raise SystemExit(f"no version matches '{spec}' (known: {sorted(versions, key=version_key)[-10:]})")
    return max(candidates, key=version_key)


def papermc_fill(project, spec):
    versions = set()
    collect_versions(get_json(f"https://fill.papermc.io/v3/projects/{project}"), versions)
    version = pick(versions, spec)
    base = f"https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds"
    try:
        build = get_json(base + "/latest")
    except urllib.error.HTTPError:
        builds = get_json(base)
        if isinstance(builds, dict):
            builds = builds.get("builds", [])
        stable = [b for b in builds if str(b.get("channel", "")).upper() == "STABLE"] or builds
        build = max(stable, key=lambda b: b.get("id", 0))
    downloads = build["downloads"]
    entry = downloads.get("server:default") or next(iter(downloads.values()))
    return version, entry["url"]


def papermc_v2(project, spec):
    data = get_json(f"https://api.papermc.io/v2/projects/{project}")
    version = pick({v for v in data["versions"] if RELEASE.match(v)}, spec)
    builds = get_json(f"https://api.papermc.io/v2/projects/{project}/versions/{version}/builds")["builds"]
    stable = [b for b in builds if b.get("channel") == "default"] or builds
    build = stable[-1]
    name = build["downloads"]["application"]["name"]
    return version, (f"https://api.papermc.io/v2/projects/{project}/versions/{version}"
                     f"/builds/{build['build']}/downloads/{name}")


def purpur(spec):
    data = get_json("https://api.purpurmc.org/v2/purpur")
    version = pick({v for v in data["versions"] if RELEASE.match(v)}, spec)
    return version, f"https://api.purpurmc.org/v2/purpur/{version}/latest/download"


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    platform, spec = sys.argv[1].lower(), sys.argv[2]
    if platform in ("paper", "folia"):
        try:
            version, url = papermc_fill(platform, spec)
        except Exception as fill_error:  # noqa: BLE001 - fall back to the legacy API
            print(f"Fill API failed ({fill_error}), trying the v2 API", file=sys.stderr)
            version, url = papermc_v2(platform, spec)
    elif platform == "purpur":
        version, url = purpur(spec)
    else:
        raise SystemExit(f"unsupported platform '{platform}'")
    print(version, url)


if __name__ == "__main__":
    main()
